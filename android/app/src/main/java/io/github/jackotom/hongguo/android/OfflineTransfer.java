package io.github.jackotom.hongguo.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Downloads an already authorized media plan with guarded HTTPS and a resumable file. */
final class OfflineTransfer {
    private static final long MAX_BYTES = 384L * 1024 * 1024;
    private static final OkHttpClient HTTP = new OkHttpClient.Builder().followRedirects(false)
        .followSslRedirects(false).retryOnConnectionFailure(false).callTimeout(180, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).dns(host -> {
            try { return Arrays.asList(NetworkGuard.publicAddresses(new URI("https", host, "/", null))); }
            catch (Exception unavailable) {
                var failure = new UnknownHostException("下载域名解析未完成");
                failure.initCause(unavailable); throw failure;
            }
        }).build();

    private volatile Call call;
    private volatile boolean paused;
    volatile long total = -1;

    void cancel() {
        paused = true;
        Call current = call;
        if (current != null) current.cancel();
    }

    boolean cancelled() { return paused || Thread.currentThread().isInterrupted(); }

    private void ensureRunning() throws InterruptedIOException {
        if (cancelled()) throw new InterruptedIOException("下载已暂停");
    }

    void fetch(PlaybackSources.Plan plan, File part, OfflineRange range) throws Exception {
        URI uri = NetworkGuard.validate(plan.url, false);
        long limit = plan.guestPlayback ? PublicPlaybackVerifier.MAX_FILE_BYTES : MAX_BYTES;
        boolean restarted = false;
        for (int redirects = 0; redirects <= 3; ) {
            ensureRunning();
            Request.Builder request = new Request.Builder().url(uri.toASCIIString())
                .header("User-Agent", plan.userAgent).header("Accept-Encoding", "identity");
            if (!plan.referer.isEmpty()) request.header("Referer", plan.referer);
            long requested = range.request(uri, request);
            call = HTTP.newCall(request.build());
            try {
                ensureRunning();
                try (Response response = call.execute()) {
                    if (response.isRedirect()) {
                        if (redirects == 3) throw new IOException("下载跳转次数过多");
                        uri = NetworkGuard.mediaRedirect(uri, response.header("Location"));
                        redirects++;
                        continue;
                    }
                    if (response.body() == null) throw new IOException("下载内容为空");
                    try { total = range.accept(uri, response, requested, limit); }
                    catch (OfflineRange.Rejected rejected) {
                        if (requested == 0 || restarted) throw rejected;
                        ensureRunning();
                        range.forget();
                        restarted = true;
                        continue;
                    }
                    try (var src = response.body().byteStream(); var out = new FileOutputStream(part, true)) {
                        long written = part.length();
                        byte[] buffer = new byte[64 * 1024];
                        for (int count; (count = src.read(buffer)) != -1; ) {
                            ensureRunning();
                            if (count > limit - written || total > 0 && count > total - written)
                                throw new IOException("下载超出安全长度");
                            out.write(buffer, 0, count);
                            written += count;
                        }
                        ensureRunning();
                        if (written == 0 || total > 0 && total != written) throw new IOException("下载内容不完整");
                        out.getFD().sync();
                    }
                    return;
                }
            } finally { call = null; }
        }
        throw new IOException("下载地址未完成确认");
    }
}
