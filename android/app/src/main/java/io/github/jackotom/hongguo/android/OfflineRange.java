package io.github.jackotom.hongguo.android;

import android.content.SharedPreferences;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import java.util.Map;

/** Persist only a resource fingerprint and strong validator, never a signed URL. */
final class OfflineRange {
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)");
    static final class Rejected extends IOException {
        Rejected() { super("断点下载响应未通过验证"); }
    }
    private final File part;
    private final SharedPreferences prefs;
    private final String key;

    OfflineRange(File part, SharedPreferences prefs, long id) {
        this.part = part;
        this.prefs = prefs;
        key = "range_" + id;
    }

    record Resume(long offset, String validator) { }

    Resume request(URI uri) throws Exception {
        String prior = prefs.getString(key, "");
        String scope = scope(uri) + "\n";
        long bytes = part.length();
        if (bytes > 0 && prior.startsWith(scope)) {
            String validator = prior.substring(scope.length());
            if (strong(validator)) {
                return new Resume(bytes, validator);
            }
        }
        return new Resume(0, "");
    }

    long accept(URI uri, int status, Map<String,String> headers, long requested, long limit) throws Exception {
        long length = Long.parseLong(headers.getOrDefault("content-length", "-1"));
        String etag = headers.getOrDefault("etag", "");
        if (length > limit || !headers.getOrDefault("content-encoding", "identity").equalsIgnoreCase("identity"))
            throw new IOException("下载响应长度或编码无效");
        if (status == 416 && requested > 0) throw new Rejected();
        if (status == 206) {
            var range = CONTENT_RANGE.matcher(headers.getOrDefault("content-range", ""));
            if (requested <= 0 || !strong(etag) || !(scope(uri) + "\n" + etag).equals(prefs.getString(key, ""))
                    || part.length() != requested || !range.matches()) throw new Rejected();
            long start = Long.parseLong(range.group(1)), end = Long.parseLong(range.group(2)), total = Long.parseLong(range.group(3));
            if (start != requested || total <= start || total > limit || end != total - 1
                    || length != end - start + 1) throw new Rejected();
            android.util.Log.i("HongguoDownload", "断点已核验，已保留字节数 " + requested);
            return total;
        }
        if (status != 200) throw new IOException("片源响应 HTTP " + status);
        try (var file = new RandomAccessFile(part, "rw")) {
            file.setLength(0);
            file.getFD().sync();
        }
        String value = strong(etag) ? scope(uri) + "\n" + etag : "";
        if (!prefs.edit().putString(key, value).commit()) throw new IOException("下载断点保存未完成");
        return length;
    }

    void forget() throws IOException {
        if (!prefs.edit().remove(key).commit()) throw new IOException("断点记录清理未完成");
    }

    private static boolean strong(String tag) { return tag.matches("\"[!#-~]{1,180}\""); }

    private static String scope(URI uri) throws Exception {
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        for (String family : new String[]{"qznovelvod.com", "fqnovelvod.com", "douyinvod.com"}) {
            if (host.endsWith("." + family)) { host = family; break; }
        }
        // The same immutable path can move between mirrors of one CDN family.
        // A 206 must still echo the exact strong ETag and completed range.
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(
            (host + "\n" + uri.getRawPath()).getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(digest);
    }
}
