package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bridge.BridgeProtocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Velocity 上の Geyser 拡張へカードの状態を送る。切れたら自動で再接続し、今あるカードを送り直す。 */
final class BridgeClient {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final Logger logger;
    private final String host;
    private final int port;
    private final String key;
    private final Supplier<List<String>> resync;
    private final Map<UUID, CompletableFuture<Void>> pendingAcks = new ConcurrentHashMap<>();
    private volatile PrintWriter out;
    private volatile Socket socket;
    private volatile boolean running;
    private Thread thread;

    BridgeClient(Logger logger, String host, int port, String key, Supplier<List<String>> resync) {
        this.logger = logger;
        this.host = host;
        this.port = port;
        this.key = key;
        this.resync = resync;
    }

    void start() {
        running = true;
        thread = Thread.ofPlatform().name("LuckyCasino-Bridge").daemon().start(this::run);
    }

    void stop() {
        running = false;
        closeSocket();
        if (thread != null) {
            thread.interrupt();
        }
        pendingAcks.values().forEach(future -> future.complete(null));
    }

    boolean isConnected() {
        return out != null;
    }

    /** CARD を送り、拡張が受け取ったら完了する Future を返す。未接続ならすぐ完了する。 */
    CompletableFuture<Void> sendCard(UUID carrier, int code, boolean faceUp, boolean settled) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture<Void> previous = pendingAcks.put(carrier, future);
        if (previous != null) {
            previous.complete(null);
        }
        if (!send(BridgeProtocol.card(carrier, code, faceUp, settled))) {
            pendingAcks.remove(carrier);
            future.complete(null);
        }
        return future;
    }

    void sendRemove(UUID carrier) {
        send(BridgeProtocol.remove(carrier));
    }

    private boolean send(String line) {
        PrintWriter writer = out;
        if (writer == null) {
            return false;
        }
        synchronized (writer) {
            writer.println(line);
            return !writer.checkError();
        }
    }

    private void run() {
        long backoff = 1_000;
        while (running) {
            try (Socket s = new Socket()) {
                socket = s;
                s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                s.setTcpNoDelay(true);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);

                String[] hello = split(in.readLine());
                if (hello == null || hello.length != 3 || !hello[0].equals(BridgeProtocol.HELLO)) {
                    throw new IOException("unexpected greeting");
                }
                if (!hello[1].equals(String.valueOf(BridgeProtocol.VERSION))) {
                    throw new IOException("protocol version mismatch: " + hello[1]);
                }
                writer.println(BridgeProtocol.AUTH + " " + BridgeProtocol.sign(key, hello[2]));
                if (!BridgeProtocol.OK.equals(in.readLine())) {
                    throw new IOException(L10n.plain("認証に失敗しました（bridge.key が拡張の key と一致しているか確認してください）"));
                }
                logger.info(L10n.plain("Geyser 拡張（%s:%s）と接続しました", host, port));
                backoff = 1_000;
                out = writer;
                for (String line : resync.get()) {
                    send(line);
                }

                String line;
                while ((line = in.readLine()) != null) {
                    String[] parts = split(line);
                    if (parts.length == 2 && parts[0].equals(BridgeProtocol.ACK)) {
                        CompletableFuture<Void> future = pendingAcks.remove(UUID.fromString(parts[1]));
                        if (future != null) {
                            future.complete(null);
                        }
                    }
                }
                logger.warning(L10n.plain("Geyser 拡張との接続が切れました"));
            } catch (IOException | IllegalArgumentException e) {
                if (running) {
                    logger.warning(L10n.plain("Geyser 拡張に接続できません: %s（%s 秒後に再試行）", e.getMessage(), backoff / 1000));
                }
            } finally {
                out = null;
                socket = null;
                pendingAcks.values().forEach(future -> future.complete(null));
                pendingAcks.clear();
            }
            if (!running) {
                return;
            }
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                return;
            }
            backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
        }
    }

    private void closeSocket() {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String[] split(String line) {
        return line == null ? null : line.trim().split(" ");
    }
}
