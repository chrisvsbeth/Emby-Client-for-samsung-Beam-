package com.emby.client;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;

public class HlsPlaybackEngine {
    private static final String TAG = "HlsPlaybackEngine";
    private static final int SEGMENT_BUFFER_AHEAD = 3;

    private MediaPlayer mediaPlayer;
    private LocalHttpServer httpServer;
    private String m3u8Url;
    private String localStreamUrl;
    private Listener listener;
    private volatile boolean stopped = false;
    private int durationMs;
    private boolean prepared = false;

    public interface Listener {
        void onPrepared(int durationMs);
        void onError(int what, int extra);
        void onCompletion();
        void onInfo(String message);
    }

    public HlsPlaybackEngine() {
        mediaPlayer = new MediaPlayer();
    }

    public void setDataSource(String url) {
        this.m3u8Url = url;
    }

    public void setDisplay(SurfaceHolder holder) {
        mediaPlayer.setDisplay(holder);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void prepareAsync() {
        if (m3u8Url == null) {
            if (listener != null) listener.onError(-1, 0);
            return;
        }

        httpServer = new LocalHttpServer();
        httpServer.start(m3u8Url);
        localStreamUrl = "http://127.0.0.1:" + httpServer.getPort() + "/stream.ts";

        Log.i(TAG, "Local stream URL: " + localStreamUrl);

        try {
            mediaPlayer.setDataSource(localStreamUrl);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    prepared = true;
                    durationMs = mp.getDuration();
                    Log.i(TAG, "MediaPlayer prepared, duration: " + durationMs);
                    if (listener != null) listener.onPrepared(durationMs);
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                    if (listener != null) listener.onError(what, extra);
                    return true;
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.i(TAG, "MediaPlayer completed");
                    if (listener != null) listener.onCompletion();
                }
            });
            mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    Log.i(TAG, "Seek complete, position: " + mp.getCurrentPosition());
                }
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "Error setting data source: " + e.getMessage());
            if (listener != null) listener.onError(-1, 0);
        }
    }

    public void start() {
        if (prepared) mediaPlayer.start();
    }

    public void pause() {
        if (prepared) mediaPlayer.pause();
    }

    public boolean isPlaying() {
        return prepared && mediaPlayer.isPlaying();
    }

    public int getCurrentPosition() {
        if (!prepared) return 0;
        try { return mediaPlayer.getCurrentPosition(); } catch (Exception e) { return 0; }
    }

    public int getDuration() {
        return durationMs;
    }

    public void seekTo(final int positionMs) {
        if (!prepared) return;
        Log.i(TAG, "Seek to: " + positionMs + "ms");
        try {
            mediaPlayer.seekTo(positionMs);
        } catch (Exception e) {
            Log.e(TAG, "Seek error: " + e.getMessage());
        }
    }

    public void stop() {
        stopped = true;
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            }
        } catch (Exception e) {
            Log.e(TAG, "Stop error: " + e.getMessage());
        }
        if (httpServer != null) httpServer.stop();
    }

    public void release() {
        stop();
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Release error: " + e.getMessage());
        }
        if (httpServer != null) httpServer.release();
    }

    // --- Local HTTP Server ---
    private class LocalHttpServer {
        private ServerSocket serverSocket;
        private int port;
        private Thread serverThread;
        private volatile boolean serverStopped = false;

        void start(String playlistUrl) {
            try {
                serverSocket = new ServerSocket(0);
                port = serverSocket.getLocalPort();
                Log.i(TAG, "Local HTTP server starting on port " + port);
                serverStopped = false;
                final String url = playlistUrl;
                serverThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        runServer(url);
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();
            } catch (IOException e) {
                Log.e(TAG, "Failed to start local server: " + e.getMessage());
                if (listener != null) listener.onError(-2, 0);
            }
        }

        int getPort() {
            return port;
        }

        void stop() {
            serverStopped = true;
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }

        void release() {
            stop();
            if (serverThread != null) {
                serverThread.interrupt();
                serverThread = null;
            }
        }

        private void runServer(String playlistUrl) {
            while (!serverStopped && !stopped) {
                try {
                    Socket client = serverSocket.accept();
                    handleClient(client, playlistUrl);
                } catch (IOException e) {
                    if (!serverStopped && !stopped) {
                        Log.e(TAG, "Server accept error: " + e.getMessage());
                    }
                }
            }
        }

        private void handleClient(Socket client, String playlistUrl) {
            BufferedInputStream in = null;
            OutputStream out = null;
            try {
                in = new BufferedInputStream(client.getInputStream());
                out = client.getOutputStream();

                // Read HTTP request
                StringBuilder request = new StringBuilder();
                int b;
                boolean headersEnd = false;
                while ((b = in.read()) != -1) {
                    request.append((char) b);
                    if (request.toString().endsWith("\r\n\r\n")) {
                        headersEnd = true;
                        break;
                    }
                    if (request.length() > 4096) break;
                }

                if (!headersEnd) {
                    sendHttpResponse(out, 400, "Bad Request");
                    return;
                }

                String requestStr = request.toString();
                Log.d(TAG, "HTTP request: " + requestStr.substring(0, Math.min(200, requestStr.length())));

                // Check for Range header
                long rangeStart = 0;
                boolean hasRange = false;
                if (requestStr.contains("Range: bytes=")) {
                    try {
                        String rangeLine = requestStr.substring(requestStr.indexOf("Range: bytes="));
                        rangeLine = rangeLine.substring("Range: bytes=".length());
                        rangeLine = rangeLine.split("\r\n")[0].split(" ")[0];
                        if (rangeLine.endsWith("-")) {
                            rangeLine = rangeLine.substring(0, rangeLine.length() - 1);
                        }
                        rangeStart = Long.parseLong(rangeLine);
                        hasRange = true;
                        Log.d(TAG, "Range request: bytes=" + rangeStart + "-");
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing Range header: " + e.getMessage());
                    }
                }

                // Stream segments (response headers are sent after first segment data is ready)
                streamSegments(out, playlistUrl, rangeStart);

            } catch (IOException e) {
                Log.e(TAG, "Client handler error: " + e.getMessage());
            } finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    if (client != null) client.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        private void sendHttpResponse(OutputStream out, int code, String message) throws IOException {
            String reason = (code == 200) ? "OK" : (code == 206) ? "Partial Content" : (code == 400) ? "Bad Request" : "Error";
            String resp = "HTTP/1.1 " + code + " " + reason + "\r\nContent-Length: " + message.length() + "\r\nConnection: close\r\n\r\n" + message;
            out.write(resp.getBytes());
            out.flush();
        }

        private void streamSegments(OutputStream out, String playlistUrl, long rangeStart) {
            int segmentIndex = 0;
            int bytesStreamed = 0;
            boolean endListSeen = false;
            boolean headersSent = false;
            int consecutiveEmptyFetches = 0;
            final int maxEmptyFetches = 30;

            // Outer loop: re-fetch playlist for live streaming
            while (!serverStopped && !stopped && !endListSeen && consecutiveEmptyFetches < maxEmptyFetches) {
                M3u8Playlist playlist = fetchPlaylist(playlistUrl);
                if (playlist == null) {
                    consecutiveEmptyFetches++;
                    sleepMs(1000);
                    continue;
                }

                endListSeen = playlist.hasEndList;
                List<M3u8Segment> segments = playlist.segments;

                if (segments.isEmpty()) {
                    consecutiveEmptyFetches++;
                    sleepMs(1000);
                    continue;
                }

                // Handle Range requests by skipping segments
                if (segmentIndex == 0 && rangeStart > 0 && bytesStreamed == 0) {
                    long approxBytesPerSegment = 200 * 1024;
                    int skipSegments = (int) (rangeStart / approxBytesPerSegment);
                    if (skipSegments > 0 && skipSegments < segments.size()) {
                        segmentIndex = skipSegments;
                        Log.d(TAG, "Range request: skipping to segment " + segmentIndex);
                    }
                }

                // Download and stream segments
                while (segmentIndex < segments.size() && !serverStopped && !stopped) {
                    M3u8Segment segment = segments.get(segmentIndex);
                    Log.d(TAG, "Streaming segment " + segmentIndex + ": " + segment.url);
                    byte[] data = downloadFile(segment.url);

                    if (data != null && data.length > 0) {
                        if (!headersSent) {
                            // Send response headers now that we have data
                            StringBuilder responseHeaders = new StringBuilder();
                            responseHeaders.append("HTTP/1.1 200 OK\r\n");
                            responseHeaders.append("Content-Type: video/MP2T\r\n");
                            responseHeaders.append("Access-Control-Allow-Origin: *\r\n");
                            responseHeaders.append("Connection: close\r\n");
                            responseHeaders.append("Cache-Control: no-cache\r\n");
                            responseHeaders.append("Pragma: no-cache\r\n");
                            responseHeaders.append("\r\n");
                            try {
                                out.write(responseHeaders.toString().getBytes());
                                out.flush();
                                headersSent = true;
                            } catch (IOException e) {
                                Log.d(TAG, "Client disconnected while sending headers");
                                return;
                            }
                        }

                        try {
                            out.write(data);
                            out.flush();
                            bytesStreamed += data.length;
                            consecutiveEmptyFetches = 0;
                            Log.d(TAG, "Wrote segment " + segmentIndex + ": " + data.length + " bytes, total=" + bytesStreamed);
                        } catch (IOException e) {
                            Log.d(TAG, "Client disconnected while streaming segment " + segmentIndex);
                            return;
                        }
                    } else {
                        Log.d(TAG, "Segment " + segmentIndex + " data empty (null=" + (data==null) + ")");
                    }

                    segmentIndex++;
                }

                if (!endListSeen) {
                    sleepMs(2000);
                }
            }

            // If we never sent headers, notify the client of failure
            if (!headersSent && !serverStopped && !stopped) {
                try {
                    sendHttpResponse(out, 500, "Stream unavailable");
                } catch (IOException e) { }
            }

            Log.i(TAG, "Streaming complete: " + bytesStreamed + " bytes, " + segmentIndex + " segments");
        }

        private M3u8Playlist fetchPlaylist(String urlStr) {
            java.net.Socket socket = null;
            try {
                URL parsedUrl = new URL(urlStr);
                String host = parsedUrl.getHost();
                int port = parsedUrl.getPort();
                if (port <= 0) port = parsedUrl.getDefaultPort();

                socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), 15000);
                // Longer timeout for waiting for segment entries to appear in playlist
                socket.setSoTimeout(15000);

                String path = parsedUrl.getFile();
                if (path == null || path.length() == 0) path = "/";

                StringBuilder request = new StringBuilder();
                request.append("GET ").append(path).append(" HTTP/1.0\r\n");
                request.append("Host: ").append(host).append(":").append(port).append("\r\n");
                request.append("Connection: close\r\n");
                request.append("User-Agent: Mozilla/5.0 (Linux; Android 4.0.4; GT-I8530)\r\n");
                request.append("X-Emby-Client: Emby Client\r\n");
                request.append("X-Emby-Client-Version: 1.0.2\r\n");
                request.append("X-Emby-Device-Id: android-samsung-galaxy-beam-gt-i8530\r\n");
                request.append("X-Emby-Device-Name: Samsung Galaxy Beam\r\n");
                request.append("\r\n");

                java.io.OutputStream sockOut = socket.getOutputStream();
                sockOut.write(request.toString().getBytes());
                sockOut.flush();

                java.io.InputStream sockIn = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(sockIn));

                String statusLine = reader.readLine();
                if (statusLine == null || !statusLine.contains("200")) {
                    Log.w(TAG, "Playlist fetch bad status: " + statusLine);
                    return null;
                }

                String headerLine;
                while ((headerLine = reader.readLine()) != null && headerLine.length() > 0) { }

                // Extract api_key from m3u8 URL — needed for authenticating segment requests
                String apiKey = "";
                String query = parsedUrl.getQuery();
                if (query != null) {
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.startsWith("api_key=")) {
                            apiKey = param;
                            break;
                        }
                    }
                }

                M3u8Playlist playlist = new M3u8Playlist();
                String line;
                int segmentDuration = 0;
                String baseUrl = getBaseUrl(urlStr);

                // Read lines with timeout - the server sends m3u8 content incrementally
                // We read whatever is available within the timeout, then return partial results
                while (true) {
                    try {
                        line = reader.readLine();
                    } catch (java.net.SocketTimeoutException e) {
                        // No more data available yet - return what we have
                        break;
                    }
                    if (line == null) break;

                    line = line.trim();
                    if (line.startsWith("#EXTINF:")) {
                        try {
                            String durationStr = line.substring(8);
                            if (durationStr.contains(",")) {
                                durationStr = durationStr.substring(0, durationStr.indexOf(","));
                            }
                            segmentDuration = (int) (Double.parseDouble(durationStr) * 1000);
                        } catch (Exception e) {
                            segmentDuration = 4000;
                        }
                    } else if (line.startsWith("#EXT-X-ENDLIST")) {
                        playlist.hasEndList = true;
                    } else if (!line.startsWith("#") && line.length() > 0) {
                        M3u8Segment seg = new M3u8Segment();
                        seg.durationMs = segmentDuration;
                        if (line.startsWith("http://") || line.startsWith("https://")) {
                            seg.url = line;
                        } else {
                            if (line.startsWith("/")) {
                                seg.url = getServerOrigin(urlStr) + line;
                            } else {
                                seg.url = baseUrl + "/" + line;
                            }
                        }
                        // Emby requires api_key for segment authentication
                        if (apiKey.length() > 0) {
                            seg.url = seg.url + "?" + apiKey;
                        }
                        playlist.segments.add(seg);
                        segmentDuration = 0;
                    }
                }

                Log.d(TAG, "Fetched playlist: " + playlist.segments.size() + " segments, endList=" + playlist.hasEndList);
                return playlist;
            } catch (Exception e) {
                Log.w(TAG, "Error fetching playlist: " + e.getMessage(), e);
                return null;
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (Exception e) { }
                }
            }
        }

        private byte[] downloadFile(String urlStr) {
            Socket socket = null;
            try {
                URL parsedUrl = new URL(urlStr);
                String host = parsedUrl.getHost();
                int port = parsedUrl.getPort();
                if (port <= 0) port = parsedUrl.getDefaultPort();

                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), 15000);
                socket.setSoTimeout(60000);

                String path = parsedUrl.getFile();
                if (path == null || path.length() == 0) path = "/";

                StringBuilder request = new StringBuilder();
                request.append("GET ").append(path).append(" HTTP/1.0\r\n");
                request.append("Host: ").append(host).append(":").append(port).append("\r\n");
                request.append("Connection: close\r\n");
                request.append("User-Agent: Mozilla/5.0 (Linux; Android 4.0.4; GT-I8530)\r\n");
                request.append("X-Emby-Client: Emby Client\r\n");
                request.append("X-Emby-Client-Version: 1.0.2\r\n");
                request.append("X-Emby-Device-Id: android-samsung-galaxy-beam-gt-i8530\r\n");
                request.append("X-Emby-Device-Name: Samsung Galaxy Beam\r\n");
                request.append("\r\n");

                OutputStream sockOut = socket.getOutputStream();
                sockOut.write(request.toString().getBytes());
                sockOut.flush();

                BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());

                String statusLine = readLine(bis);
                Log.d(TAG, "Segment status: " + statusLine);
                if (statusLine == null || !statusLine.contains("200")) {
                    Log.w(TAG, "Segment fetch bad status: " + statusLine + " for " + urlStr);
                    return null;
                }

                // Parse headers to determine how to read body
                boolean chunked = false;
                int contentLength = -1;
                String headerLine;
                while ((headerLine = readLine(bis)) != null && headerLine.length() > 0) {
                    String lower = headerLine.toLowerCase();
                    if (lower.startsWith("content-length:")) {
                        contentLength = Integer.parseInt(headerLine.substring(15).trim());
                    } else if (lower.startsWith("transfer-encoding:")) {
                        if (lower.contains("chunked")) chunked = true;
                    }
                }

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(65536);
                byte[] buf = new byte[8192];
                int n;

                if (chunked) {
                    // Read chunked transfer encoding
                    while (true) {
                        String chunkSizeLine = readLine(bis);
                        if (chunkSizeLine == null) break;
                        int chunkSize;
                        try {
                            chunkSize = Integer.parseInt(chunkSizeLine.trim(), 16);
                        } catch (Exception e) {
                            break;
                        }
                        if (chunkSize == 0) break; // final chunk
                        int remaining = chunkSize;
                        while (remaining > 0) {
                            int toRead = Math.min(remaining, buf.length);
                            n = bis.read(buf, 0, toRead);
                            if (n == -1) break;
                            baos.write(buf, 0, n);
                            remaining -= n;
                        }
                        // Read trailing CRLF after chunk data
                        readLine(bis);
                    }
                } else if (contentLength >= 0) {
                    // Read exact number of bytes
                    int remaining = contentLength;
                    while (remaining > 0) {
                        int toRead = Math.min(remaining, buf.length);
                        n = bis.read(buf, 0, toRead);
                        if (n == -1) break;
                        baos.write(buf, 0, n);
                        remaining -= n;
                    }
                } else {
                    // Read until connection close
                    while ((n = bis.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                }

                byte[] result = baos.toByteArray();
                Log.d(TAG, "Downloaded " + result.length + " bytes" + (chunked ? " (chunked)" : contentLength >= 0 ? " (content-length)" : " (eof)") + " firstByte=0x" + Integer.toHexString(result.length > 0 ? (result[0] & 0xFF) : 0));
                return result;
            } catch (Exception e) {
                Log.w(TAG, "Error downloading segment: " + e.getMessage());
                return null;
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (Exception e) { }
                }
            }
        }

        private String readLine(BufferedInputStream bis) throws IOException {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(256);
            int b;
            while ((b = bis.read()) != -1) {
                if (b == '\r') {
                    bis.mark(1);
                    int next = bis.read();
                    if (next == '\n') break;
                    bis.reset();
                    baos.write(b);
                } else if (b == '\n') {
                    break;
                } else {
                    baos.write(b);
                }
            }
            if (baos.size() == 0 && b == -1) return null;
            return new String(baos.toByteArray());
        }

        private String getBaseUrl(String url) {
            int slash = url.lastIndexOf('/');
            if (slash > 8) return url.substring(0, slash);
            return url;
        }

        private String getServerOrigin(String url) {
            try {
                URL u = new URL(url);
                return u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
            } catch (Exception e) {
                return url;
            }
        }

        private void sleepMs(long ms) {
            try { Thread.sleep(ms); } catch (InterruptedException e) { }
        }
    }

    // --- M3U8 Data Classes ---
    private static class M3u8Segment {
        String url;
        int durationMs;
    }

    private static class M3u8Playlist {
        List<M3u8Segment> segments = new ArrayList<M3u8Segment>();
        boolean hasEndList = false;
    }
}
