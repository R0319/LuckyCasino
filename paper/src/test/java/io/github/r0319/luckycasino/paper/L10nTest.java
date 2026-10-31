package io.github.r0319.luckycasino.paper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 言語ファイルがコードの文（キー）とそろっているか。そろえるには python3 tools/i18n_keys.py。 */
class L10nTest {

    private static final Path LANG = Path.of("../resourcepacks/java/assets/luckycasino/lang");
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"");
    private static final Pattern JOINED = Pattern.compile(STRING + "(?:\\s*\\+\\s*" + STRING + ")*");
    private static final Pattern JAPANESE = Pattern.compile("[\\u3040-\\u30ff\\u4e00-\\u9fff\\uff01-\\uff5e]");
    /** tools/i18n_keys.py の IGNORE と同じ（表示しない日本語）。 */
    private static final Set<String> IGNORE = Set.of("の目", "のゾロ目", "チェリー ", "つ", "が 3 つ");

    @Test
    void everyKeyIsTranslated() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Path root : new Path[]{Path.of("src/main/java"), Path.of("../common/src/main/java")}) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    keys.addAll(keys(Files.readString(file, StandardCharsets.UTF_8)));
                }
            }
        }
        Map<String, String> en = read("en_us");
        Map<String, String> ja = read("ja_jp");
        Set<String> missing = new TreeSet<>(keys);
        missing.removeAll(en.keySet());
        Set<String> unused = new TreeSet<>(en.keySet());
        unused.removeAll(keys);
        assertTrue(missing.isEmpty(), "英訳がありません（python3 tools/i18n_keys.py）: " + missing);
        assertTrue(unused.isEmpty(), "使っていない英訳があります（python3 tools/i18n_keys.py）: " + unused);
        assertEquals(en.keySet(), ja.keySet(), "ja_jp.json は en_us.json と同じキーを全部持つ");
        ja.forEach((k, v) -> assertEquals(k, v, "ja_jp.json はキーと同じ文"));
    }

    /**
     * マインクラフトの翻訳の書式（%s・%1$s・%%）として正しいか。ほかの % があると、クライアントは数を入れずに文をそのまま出す。
     * 英訳は数の個数がキーと同じであること。
     */
    @Test
    void formatsAreValidForMinecraft() throws IOException {
        Pattern token = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
        read("en_us").forEach((key, value) -> {
            for (String text : new String[]{key, value}) {
                Matcher m = token.matcher(text);
                StringBuilder rest = new StringBuilder();
                int last = 0;
                while (m.find()) {
                    assertTrue(m.group(2).equals("s") || m.group(2).equals("%"), "使えない書式 " + m.group() + ": " + text);
                    rest.append(text, last, m.start());
                    last = m.end();
                }
                rest.append(text.substring(last));
                assertTrue(rest.indexOf("%") < 0, "% は %% と書く: " + text);
            }
            assertEquals(arguments(key), arguments(value), "数の個数が違う: " + key + " → " + value);
        });
    }

    private static long arguments(String text) {
        return Pattern.compile("%(?:\\d+\\$)?s").matcher(text).results().count();
    }

    @Test
    void localizesWithStyleAndArguments() {
        Component text = L10n.tr("ハイ（%s より上）", L10n.tr("赤"));
        assertEquals("High (above Red)", plain(L10n.localize(L10n.Lang.EN, text)));
        assertEquals("ハイ（赤 より上）", plain(L10n.localize(L10n.Lang.JA, text)));
        assertEquals("  1 or 11", plain(L10n.localize(L10n.Lang.EN, L10n.tr("  1 か 11"))));
        assertEquals("Return rate 97.0%", L10n.text(L10n.Lang.EN, "還元率 %s%%", "97.0"));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Set<String> keys(String source) {
        Set<String> out = new TreeSet<>();
        Matcher m = JOINED.matcher(stripComments(source));
        while (m.find()) {
            StringBuilder text = new StringBuilder();
            Matcher part = STRING.matcher(m.group());
            while (part.find()) {
                text.append(unescape(part.group()));
            }
            String key = text.toString().replaceFirst("^ +", "");
            if (JAPANESE.matcher(key).find() && !IGNORE.contains(key)) {
                out.add(key);
            }
        }
        return out;
    }

    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            if (src.startsWith("//", i)) {
                int end = src.indexOf('\n', i);
                i = end < 0 ? src.length() : end;
            } else if (src.startsWith("/*", i)) {
                i = src.indexOf("*/", i) + 2;
            } else if (src.charAt(i) == '"') {
                Matcher m = STRING.matcher(src);
                m.find(i);
                out.append(m.group());
                i = m.end();
            } else if (src.charAt(i) == '\'') {
                int end = src.indexOf('\'', i + (src.charAt(i + 1) == '\\' ? 3 : 2));
                out.append(src, i, end + 1);
                i = end + 1;
            } else {
                out.append(src.charAt(i++));
            }
        }
        return out.toString();
    }

    private static String unescape(String literal) {
        return JsonParser.parseString(literal).getAsString();
    }

    private static Map<String, String> read(String name) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(LANG.resolve(name + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> map = new TreeMap<>();
        json.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        return map;
    }
}
