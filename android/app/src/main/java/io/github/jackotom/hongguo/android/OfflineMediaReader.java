package io.github.jackotom.hongguo.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.Socket;

/** File append is allowed only after the exact Content-Range and resource validator agree. */
final class OfflineMediaReader {
    private OfflineMediaReader() { }

    static void fetch(OfflineTransfer transfer, PlaybackSources.Plan plan, File part, OfflineRange range) throws Exception {
        URI uri = NetworkGuard.validate(plan.url, false);
        long limit = plan.guestPlayback ? PublicPlaybackVerifier.MAX_FILE_BYTES : 384L * 1024 * 1024;
        long deadline = System.nanoTime() + 180_000_000_000L;
        boolean restarted = false;
        for (int redirects = 0; redirects <= 3; ) {
            OfflineRange.Resume resume = range.request(uri);
            try (Socket raw = transfer.connect(uri, deadline);
                 var reply = new OfflineMediaConnection(uri, raw, plan, deadline, resume.offset(), resume.validator())) {
                if (reply.redirected()) {
                    if (redirects++ == 3) throw new IOException("下载跳转次数过多");
                    uri = NetworkGuard.mediaRedirect(uri, reply.headers.get("location"));
                    continue;
                }
                try { transfer.total = range.accept(uri, reply.status, reply.headers, resume.offset(), limit); }
                catch (OfflineRange.Rejected rejected) {
                    if (resume.offset() == 0 || restarted || transfer.cancelled()) throw rejected;
                    range.forget();
                    restarted = true;
                    continue;
                }
                try (var output = new FileOutputStream(part, true)) {
                    long remaining = limit - part.length();
                    NetworkGuard.streamMediaBody(reply.input, output, reply.headers, remaining, deadline);
                    if (transfer.cancelled()) throw new java.io.InterruptedIOException("下载已暂停");
                    if (part.length() == 0 || transfer.total > 0 && part.length() != transfer.total)
                        throw new IOException("下载内容不完整");
                    output.getFD().sync();
                }
                return;
            }
        }
    }
}
