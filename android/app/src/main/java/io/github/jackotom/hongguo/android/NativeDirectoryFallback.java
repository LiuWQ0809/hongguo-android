package io.github.jackotom.hongguo.android;

import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

final class NativeDirectoryFallback {
    interface Directory { JSONObject read(long deadline) throws Exception; }

    static JSONObject read(long deadline, Directory preload, Directory detail) throws Exception {
        long now = System.nanoTime();
        long preloadDeadline = deadline - now > TimeUnit.SECONDS.toNanos(5)
            ? deadline - TimeUnit.SECONDS.toNanos(3) : deadline;
        JSONObject partial = null;
        Exception missing = null;
        checkCancelled();
        try {
            partial = preload.read(preloadDeadline);
            if (partial.optInt("episodeCount") <= partial.getJSONArray("episodes").length()) return partial;
        } catch (Exception failure) {
            checkCancelled();
            if (!(failure instanceof NativeSeriesDetail.MissingDirectory || failure instanceof SocketTimeoutException ||
                    failure instanceof SocketException || failure instanceof UnknownHostException || failure instanceof EOFException)) throw failure;
            missing = failure;
        }
        checkCancelled();
        if (deadline - System.nanoTime() < TimeUnit.SECONDS.toNanos(1)) {
            if (partial != null) return partial;
            throw missing;
        }
        try {
            JSONObject candidate = detail.read(deadline);
            checkCancelled();
            if (deadline - System.nanoTime() <= 0) throw new SocketTimeoutException("红果原生分集目录读取超时");
            return partial == null || NativeSeriesDetail.addsEpisodes(partial, candidate) ? candidate : partial;
        } catch (Exception failure) {
            checkCancelled();
            if (failure instanceof InterruptedException ||
                    failure instanceof InterruptedIOException && !(failure instanceof SocketTimeoutException)) throw failure;
            if (partial != null) return partial;
            if (missing != null && missing != failure) failure.addSuppressed(missing);
            throw failure;
        }
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("红果原生分集目录读取已取消");
    }
}
