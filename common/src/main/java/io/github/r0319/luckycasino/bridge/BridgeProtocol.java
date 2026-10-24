package io.github.r0319.luckycasino.bridge;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Paper プラグインと Geyser 拡張の間の LAN 内 TCP 通信の取り決め。
 *
 * <pre>
 * 1 行 1 メッセージ（UTF-8、改行区切り）
 * 拡張 → Paper: HELLO &lt;版&gt; &lt;nonce&gt;
 * Paper → 拡張: AUTH &lt;HMAC-SHA256(共有鍵, "luckycasino-bridge:" + nonce) の16進&gt;
 * 拡張 → Paper: OK（鍵が違えば切断）
 * Paper → 拡張: CARD &lt;運び役の UUID&gt; &lt;カード番号&gt; &lt;表向き 0|1&gt; &lt;配り終え 0|1&gt;
 * 拡張 → Paper: ACK &lt;運び役の UUID&gt;
 * Paper → 拡張: REMOVE &lt;運び役の UUID&gt;
 * </pre>
 *
 * 認証だけで暗号化はしない。裏向きのカードは番号 0 で送るので、通信を覗かれても伏せ札は漏れない。
 */
public final class BridgeProtocol {

    public static final int VERSION = 1;
    public static final int DEFAULT_PORT = 25590;
    public static final int MAX_LINE_LENGTH = 256;
    public static final int MIN_KEY_LENGTH = 32;

    public static final String HELLO = "HELLO";
    public static final String AUTH = "AUTH";
    public static final String OK = "OK";
    public static final String CARD = "CARD";
    public static final String ACK = "ACK";
    public static final String REMOVE = "REMOVE";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private BridgeProtocol() {
    }

    public static String newNonce() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    /** 設定ファイルに書く共有鍵を作る（64 文字の 16 進）。 */
    public static String newKey() {
        return newNonce();
    }

    public static String sign(String key, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(("luckycasino-bridge:" + nonce).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean verify(String key, String nonce, String signature) {
        byte[] expected = sign(key, nonce).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }

    public static String card(UUID carrier, int code, boolean faceUp, boolean settled) {
        return CARD + " " + carrier + " " + code + " " + (faceUp ? 1 : 0) + " " + (settled ? 1 : 0);
    }

    public static String remove(UUID carrier) {
        return REMOVE + " " + carrier;
    }

    public static String ack(UUID carrier) {
        return ACK + " " + carrier;
    }
}
