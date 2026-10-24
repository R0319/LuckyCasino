package io.github.r0319.luckycasino.geyser;

import io.github.r0319.luckycasino.bridge.BridgeProtocol;
import io.github.r0319.luckycasino.bridge.CardCode;
import org.geysermc.geyser.api.extension.ExtensionLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Paper プラグインからの接続を受け付ける LAN 内 TCP サーバー。 */
final class BridgeServer {

    private static final int MAX_CLIENTS = 8;
    private static final int HANDSHAKE_TIMEOUT_MS = 5_000;

    interface Listener {
        void onCard(UUID carrier, CardRegistry.CardState state);

        void onRemove(UUID carrier);
    }

    private final ExtensionLogger logger;
    private final CardRegistry registry;
    private final Listener listener;
    private final String key;
    private final InetAddress bindAddress;
    private final int port;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextOwner = new AtomicLong(1);
    private volatile ServerSocket serverSocket;

    BridgeServer(ExtensionLogger logger, CardRegistry registry, Listener listener, String key, InetAddress bindAddress, int port) {
        this.logger = logger;
        this.registry = registry;
        this.listener = listener;
        this.key = key;
        this.bindAddress = bindAddress;
        this.port = port;
    }

    void start() throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(bindAddress, port));
        serverSocket = socket;
        Thread.ofPlatform().name("LuckyCasino-Bridge-Accept").daemon().start(this::acceptLoop);
        logger.info("Paper からの接続を " + bindAddress.getHostAddress() + ":" + port + " で待っています");
    }

    void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        for (Socket client : clients) {
            closeQuietly(client);
        }
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                if (clients.size() >= MAX_CLIENTS) {
                    logger.warning("接続数の上限に達したため " + client.getRemoteSocketAddress() + " を拒否しました");
                    closeQuietly(client);
                    continue;
                }
                clients.add(client);
                Thread.ofVirtual().name("LuckyCasino-Bridge-" + client.getRemoteSocketAddress()).start(() -> handle(client));
            } catch (SocketException e) {
                return; // stop() で閉じられた
            } catch (IOException e) {
                logger.error("接続の受け付けに失敗しました", e);
            }
        }
    }

    private void handle(Socket client) {
        long owner = nextOwner.getAndIncrement();
        String remote = String.valueOf(client.getRemoteSocketAddress());
        boolean authenticated = false;
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true, StandardCharsets.UTF_8)) {
            client.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            client.setTcpNoDelay(true);

            String nonce = BridgeProtocol.newNonce();
            out.println(BridgeProtocol.HELLO + " " + BridgeProtocol.VERSION + " " + nonce);
            String[] auth = split(readLine(in));
            if (auth == null || auth.length != 2 || !auth[0].equals(BridgeProtocol.AUTH)
                    || !BridgeProtocol.verify(key, nonce, auth[1])) {
                logger.warning(remote + " の認証に失敗しました（共有鍵が一致しません）");
                return;
            }
            authenticated = true;
            out.println(BridgeProtocol.OK);
            client.setSoTimeout(0);
            logger.info("Paper（" + remote + "）と接続しました");

            String line;
            while ((line = readLine(in)) != null) {
                handleMessage(split(line), owner, out, remote);
            }
        } catch (IOException e) {
            if (!(e instanceof SocketException)) {
                logger.warning(remote + " との通信でエラー: " + e.getMessage());
            }
        } finally {
            clients.remove(client);
            registry.removeOwner(owner);
            if (authenticated) {
                logger.info("Paper（" + remote + "）との接続が切れました");
            }
        }
    }

    private void handleMessage(String[] parts, long owner, PrintWriter out, String remote) {
        if (parts == null || parts.length == 0) {
            return;
        }
        try {
            switch (parts[0]) {
                case BridgeProtocol.CARD -> {
                    if (parts.length != 5) {
                        throw new IllegalArgumentException("CARD needs 4 arguments");
                    }
                    UUID carrier = UUID.fromString(parts[1]);
                    int code = Integer.parseInt(parts[2]);
                    if (!CardCode.isValid(code)) {
                        throw new IllegalArgumentException("card code out of range");
                    }
                    CardRegistry.CardState state = new CardRegistry.CardState(code, flag(parts[3]), flag(parts[4]), owner);
                    if (!registry.put(carrier, state)) {
                        logger.warning("登録カード数が上限（" + CardRegistry.MAX_CARDS + "）に達しました");
                        return;
                    }
                    listener.onCard(carrier, state);
                    out.println(BridgeProtocol.ack(carrier));
                }
                case BridgeProtocol.REMOVE -> {
                    if (parts.length != 2) {
                        throw new IllegalArgumentException("REMOVE needs 1 argument");
                    }
                    UUID carrier = UUID.fromString(parts[1]);
                    registry.remove(carrier, owner);
                    listener.onRemove(carrier);
                }
                default -> throw new IllegalArgumentException("unknown message");
            }
        } catch (IllegalArgumentException e) {
            logger.warning(remote + " から不正なメッセージ: " + e.getMessage());
        }
    }

    private static boolean flag(String value) {
        return switch (value) {
            case "0" -> false;
            case "1" -> true;
            default -> throw new IllegalArgumentException("flag must be 0 or 1");
        };
    }

    private static String[] split(String line) {
        return line == null ? null : line.trim().split(" ");
    }

    /** 1 行が長すぎる接続は切る（BufferedReader.readLine は上限なしで読むため自前で読む）。 */
    private static String readLine(Reader in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                return line.toString();
            }
            if (c != '\r') {
                line.append((char) c);
            }
            if (line.length() > BridgeProtocol.MAX_LINE_LENGTH) {
                throw new IOException("line too long");
            }
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
