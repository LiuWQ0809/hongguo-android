package io.github.jackotom.hongguo.android;

import java.io.File;
import java.io.InterruptedIOException;
import java.io.IOException;
import java.net.URI;
import java.net.Socket;

/** Transfers media over the native route while retaining verified partial bytes. */
final class OfflineTransfer {
    private volatile Socket connection;
    private volatile boolean paused;
    volatile long total = -1;

    void cancel() {
        paused = true;
        Socket current = connection;
        if (current != null) try { current.close(); } catch (IOException ignored) { }
    }

    boolean cancelled() { return paused || Thread.currentThread().isInterrupted(); }

    private void ensureRunning() throws InterruptedIOException {
        if (cancelled()) throw new InterruptedIOException("下载已暂停");
    }

    void fetch(PlaybackSources.Plan plan, File part, OfflineRange range) throws Exception {
        OfflineMediaReader.fetch(this, plan, part, range);
    }

    Socket connect(URI uri, long deadline) throws IOException {
        ensureRunning();
        connection = NetworkGuard.connectPublic(NetworkGuard.publicAddresses(uri, deadline), deadline);
        try { ensureRunning(); return connection; }
        catch (IOException stopped) { connection.close(); throw stopped; }
    }
}
