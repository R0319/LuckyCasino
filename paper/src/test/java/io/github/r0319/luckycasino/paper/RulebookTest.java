package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** ルールブックの全ページを日本語と英語で作り、見本用に JSON で書き出す（build/rulebook、英語は -en）。折り返しと絵の重なりは見本で見る。 */
class RulebookTest {

    /** 本の 1 ページの行数。 */
    private static final int LINES = 14;

    @Test
    void everyPageFitsAndIsWritten() throws IOException {
        Path out = Path.of(System.getProperty("rulebook.out", "build/rulebook"));
        Files.createDirectories(out);
        for (GameType type : GameType.values()) {
            for (boolean pictures : new boolean[]{true, false}) {
                for (L10n.Lang lang : L10n.Lang.values()) {
                List<Component> pages = Rulebook.pages(type, pictures).stream().map(page -> L10n.localize(lang, page)).toList();
                for (Component page : pages) {
                    String text = PlainTextComponentSerializer.plainText().serialize(page);
                    long lines = text.chars().filter(c -> c == '\n').count() + 1;
                    assertTrue(lines <= LINES, type + " のページが " + lines + " 行: " + text);
                }
                String json = pages.stream().map(GsonComponentSerializer.gson()::serialize)
                        .collect(Collectors.joining(",\n", "[\n", "\n]\n"));
                String suffix = (pictures ? "" : "-text") + (lang == L10n.Lang.EN ? "-en" : "");
                Files.writeString(out.resolve(type.id() + suffix + ".json"), json, StandardCharsets.UTF_8);
                }
            }
        }
    }
}
