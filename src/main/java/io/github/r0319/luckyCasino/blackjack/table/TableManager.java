package io.github.r0319.luckyCasino.blackjack.table;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Loads, saves, and caches {@link BlackjackTable} instances.
 * Each table is persisted to {@code plugins/LuckyCasino/tables/<tableId>.json}.
 */
public class TableManager {

    private final JavaPlugin plugin;
    private final Path tablesDir;
    private final Gson gson;
    private final Map<String, BlackjackTable> tables = new HashMap<>();

    public TableManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tablesDir = plugin.getDataFolder().toPath().resolve("tables");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadAll();
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private void loadAll() {
        try {
            Files.createDirectories(tablesDir);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "tables ディレクトリの作成に失敗しました", e);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tablesDir, "*.json")) {
            for (Path file : stream) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    BlackjackTable table = gson.fromJson(reader, BlackjackTable.class);
                    if (table != null && table.getTableId() != null) {
                        // Always start in WAITING regardless of what was persisted —
                        // prevents the table being stuck in BETTING/DEALING etc. after a crash.
                        table.setState(TableState.WAITING);
                        tables.put(table.getTableId(), table);
                        plugin.getLogger().info("テーブルを読み込みました: " + table.getTableId());
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "テーブルファイルの読み込みに失敗: " + file, e);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "tables ディレクトリの読み込みに失敗しました", e);
        }
    }

    /** Writes the table to disk and updates the in-memory cache. */
    public void save(BlackjackTable table) {
        try {
            Files.createDirectories(tablesDir);
            Path file = tablesDir.resolve(table.getTableId() + ".json");
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(table, writer);
            }
            tables.put(table.getTableId(), table);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "テーブルの保存に失敗: " + table.getTableId(), e);
        }
    }

    // ── Lookup helpers ───────────────────────────────────────────────────────

    public BlackjackTable getTable(String id) {
        return tables.get(id);
    }

    public Collection<BlackjackTable> getAllTables() {
        return Collections.unmodifiableCollection(tables.values());
    }

    /**
     * Returns the first table found, or creates a brand-new one if none exist.
     * Suitable for single-table setups.
     */
    public BlackjackTable getDefaultTable() {
        if (tables.isEmpty()) {
            BlackjackTable t = new BlackjackTable(UUID.randomUUID().toString());
            save(t);
            return t;
        }
        return tables.values().iterator().next();
    }

    /** Returns an existing table by ID, or creates and persists a new one. */
    public BlackjackTable getOrCreate(String id) {
        return tables.computeIfAbsent(id, k -> {
            BlackjackTable t = new BlackjackTable(k);
            save(t);
            return t;
        });
    }
}
