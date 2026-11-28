package io.github.jackotom.hongguo.android;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** The same checked-address TLS and HTTP framing used by the native playback cache. */
final class OfflineMediaConnection implements AutoCloseable {
    private static final Set<String> UNIQUE = Set.of("content-length", "content-encoding", "content-range", "transfer-encoding", "location", "etag");
    private final SSLSocket tls;
    private final NetworkGuard.SocketGuard guard;
    final InputStream input;
    final int status;
    final Map<String,String> headers;

    OfflineMediaConnection(URI uri, Socket raw, PlaybackSources.Plan plan, long deadline, long offset, String validator) throws IOException {
        guard = NetworkGuard.guardSocket(raw, deadline);
        SSLSocket socket = null;
        try {
            raw.setSoTimeout(NetworkGuard.remaining(deadline, 15_000));
            socket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, uri.getHost(), 443, true);
            var params = socket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(params);
            socket.startHandshake();
            NetworkGuard.writeMediaRequest(socket.getOutputStream(), uri, plan.userAgent, plan.referer, offset, validator);
            input = new NetworkGuard.DeadlineInputStream(new BufferedInputStream(socket.getInputStream()), socket, deadline);
            String line = NetworkGuard.line(input, 8192);
            if (!line.matches("HTTP/1\\.[01] [0-9]{3}.*")) throw new IOException("媒体下载响应无效");
            status = Integer.parseInt(line.substring(9, 12));
            headers = new HashMap<>();
            int bytes = line.length();
            for (;;) {
                line = NetworkGuard.line(input, 8192);
                if (line.isEmpty()) break;
                bytes += line.length();
                int colon = line.indexOf(':');
                if (bytes > 65536 || colon <= 0) throw new IOException("媒体下载响应头无效");
                String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
                if (headers.containsKey(name) && UNIQUE.contains(name)) throw new IOException("媒体下载响应头重复");
                headers.put(name, line.substring(colon + 1).trim());
            }
            tls = socket;
        } catch (IOException | RuntimeException failure) {
            guard.close();
            if (socket != null) try { socket.close(); } catch (IOException ignored) { }
            throw failure;
        }
    }

    boolean redirected() { return status == 301 || status == 302 || status == 303 || status == 307 || status == 308; }
    @Override public void close() throws IOException { try { tls.close(); } finally { guard.close(); } }
}
