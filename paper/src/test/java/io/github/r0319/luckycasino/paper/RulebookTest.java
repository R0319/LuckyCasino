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

/** ルールブックの全ページを作り、見本用に JSON で書き出す（build/rulebook）。折り返しと絵の重なりは見本で見る。 */
class RulebookTest {

    /** 本の 1 ページの行数。 */
    private static final int LINES = 14;

    @Test
    void everyPageFitsAndIsWritten() throws IOException {
        Path out = Path.of(System.getProperty("rulebook.out", "build/rulebook"));
        Files.createDirectories(out);
        for (GameType type : GameType.values()) {
            for (boolean pictures : new boolean[]{true, false}) {
                List<Component> pages = Rulebook.pages(type, pictures);
                for (Component page : pages) {
                    String text = PlainTextComponentSerializer.plainText().serialize(page);
                    long lines = text.chars().filter(c -> c == '\n').count() + 1;
                    assertTrue(lines <= LINES, type + " のページが " + lines + " 行: " + text);
                }
                String json = pages.stream().map(GsonComponentSerializer.gson()::serialize)
                        .collect(Collectors.joining(",\n", "[\n", "\n]\n"));
                Files.writeString(out.resolve(type.id() + (pictures ? "" : "-text") + ".json"), json, StandardCharsets.UTF_8);
            }
        }
    }
}
