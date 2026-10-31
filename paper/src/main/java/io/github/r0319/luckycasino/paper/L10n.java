package io.github.r0319.luckycasino.paper;

import com.google.gson.JsonObject;
import io.github.r0319.luckycasino.chinchiro.Chinchiro;
import io.github.r0319.luckycasino.slot.Paytable;
import io.github.r0319.luckycasino.slot.Symbol;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表示する文の言語。文はコードに日本語のまま書き、その日本語をそのまま翻訳のキーにする
 * （数を入れる文は %s。リソースパックの lang/en_us.json に英訳、ja_jp.json は同じ文）。
 * tr はクライアントが自分の言語で表示する翻訳の部品を作る。パックを入れていない人には、
 * config.yml の language（サーバーの既定の言語）の文が出る。ルールブックのように 1 人だけに見せる文は、
 * その人の言語（Player#locale）で text を使って組み立てる。
 */
final class L10n {

    enum Lang {
        JA("ja_jp"), EN("en_us");

        final String file;

        Lang(String file) {
            this.file = file;
        }
    }

    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s|%%");
    private static final Map<String, String> EN = load("en_us");
    private static Lang server = Lang.JA;

    private L10n() {
    }

    private static Map<String, String> load(String file) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = L10n.class.getResourceAsStream("/lang/" + file + ".json")) {
            if (in != null) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                json.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (Exception ignored) {
            // 読めなければ日本語のまま
        }
        return map;
    }

    /** config.yml の language（ja / en）。 */
    static void serverLanguage(String value) {
        server = value != null && value.toLowerCase(Locale.ROOT).startsWith("en") ? Lang.EN : Lang.JA;
    }

    static Lang serverLanguage() {
        return server;
    }

    /** その人のクライアントの言語（日本語でなければ英語）。 */
    static Lang of(Player player) {
        return player != null && player.locale().getLanguage().equals("ja") ? Lang.JA : Lang.EN;
    }

    /** 文の形（%s 入り）を lang の言語で。英訳が無ければ日本語のまま。 */
    static String format(Lang lang, String key) {
        return lang == Lang.EN ? EN.getOrDefault(key, key) : key;
    }

    /** lang の言語の文（%s に args を入れる）。 */
    static String text(Lang lang, String key, Object... args) {
        return fill(lang, format(lang, key), args);
    }

    /** 部品を lang の言語の文字列にする（tr で作った部品は訳す）。 */
    static String render(Lang lang, Component component) {
        StringBuilder out = new StringBuilder();
        if (component instanceof TranslatableComponent t) {
            out.append(text(lang, t.key(), t.arguments().stream().map(a -> (Object) a.asComponent()).toArray()));
        } else if (component instanceof TextComponent t) {
            out.append(t.content());
        }
        component.children().forEach(child -> out.append(render(lang, child)));
        return out.toString();
    }

    /** サーバーの既定の言語の文（ログなど）。 */
    static String plain(String key, Object... args) {
        return text(server, key, args);
    }

    /** 見る人の言語で出る文。先頭の空白（字下げ）はキーに含めず、そのまま前に付ける。 */
    static Component tr(String key, Object... args) {
        int indent = 0;
        while (indent < key.length() && key.charAt(indent) == ' ') {
            indent++;
        }
        if (indent > 0) {
            String rest = key.substring(indent);
            return rest.isEmpty() ? Component.text(key) : Component.text(key.substring(0, indent)).append(tr(rest, args));
        }
        ComponentLike[] parts = new ComponentLike[args.length];
        for (int i = 0; i < args.length; i++) {
            parts[i] = args[i] instanceof ComponentLike c ? c : Component.text(String.valueOf(args[i]));
        }
        return Component.translatable(key, format(server, key), parts);
    }

    /**
     * tr で作った部品を lang の言語の文字の部品に置き換える（本のように 1 人だけに見せるもの用）。
     * 色やフォントは残す。
     */
    static Component localize(Lang lang, Component component) {
        Component base;
        if (component instanceof TranslatableComponent t) {
            String format = format(lang, t.key());
            List<Component> args = t.arguments().stream().map(a -> localize(lang, a.asComponent())).toList();
            TextComponent.Builder builder = Component.text().style(t.style());
            Matcher m = ARG.matcher(format);
            int last = 0;
            int next = 0;
            while (m.find()) {
                if (m.start() > last) {
                    builder.append(Component.text(format.substring(last, m.start())));
                }
                if (m.group().equals("%%")) {
                    builder.append(Component.text("%"));
                } else {
                    int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                    if (index < args.size()) {
                        builder.append(args.get(index));
                    }
                }
                last = m.end();
            }
            if (last < format.length()) {
                builder.append(Component.text(format.substring(last)));
            }
            base = builder.build();
        } else {
            base = component.children(List.of());
        }
        for (Component child : component.children()) {
            base = base.append(localize(lang, child));
        }
        return base;
    }

    /** 増えた・減ったチップの数（+5 枚・±0・-5 枚）。 */
    static Component delta(long net) {
        return net > 0 ? tr("+%s 枚", net) : net == 0 ? Component.text("±0") : tr("%s 枚", net);
    }

    /** チンチロの役の名前。 */
    static Component hand(Chinchiro.Hand hand) {
        return switch (hand.kind()) {
            case ME -> tr("%s の目", hand.value());
            case ZORO -> tr("%s のゾロ目", hand.value());
            default -> tr(hand.kind().label());
        };
    }

    /** スロットの当たりの名前。 */
    static Component win(Paytable.Win win) {
        if (win.symbol() == null) {
            return Component.empty();
        }
        return win.symbol() == Symbol.CHERRY ? tr("チェリー %s つ", win.count()) : tr("%s が 3 つ", tr(win.symbol().displayName()));
    }

    /** 戻る倍率（100 分の 1 倍単位）。 */
    static Component times(int percent) {
        return tr("%s 倍", percent / 100 + "." + String.format("%02d", percent % 100));
    }

    static String fill(Lang lang, String format, Object... args) {
        Matcher m = ARG.matcher(format);
        StringBuilder out = new StringBuilder();
        int next = 0;
        while (m.find()) {
            String value;
            if (m.group().equals("%%")) {
                value = "%";
            } else {
                int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                Object arg = index < args.length ? args[index] : "";
                value = arg instanceof Component c ? render(lang, c) : String.valueOf(arg);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }
}
