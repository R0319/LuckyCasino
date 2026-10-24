package io.github.r0319.luckycasino.paper;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 席にいるプレイヤーが持っているチップ（手元 + 賭けている分）の控え。変わるたびにファイルへ書く。
 * サーバーが落ちても、次の起動時にこの控えからお金を戻す。
 */
final class ChipLedger {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Long> chips = new HashMap<>();

    ChipLedger(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                try {
                    chips.put(UUID.fromString(key), yaml.getLong(key));
                } catch (IllegalArgumentException e) {
                    logger.warning("chips.yml の不正な行を読み飛ばしました: " + key);
                }
            }
        }
    }

    /** 前回の終了時に戻せなかった分を取り出して控えを空にする。 */
    Map<UUID, Long> drain() {
        Map<UUID, Long> out = new HashMap<>(chips);
        chips.clear();
        save();
        return out;
    }

    void set(UUID player, long amount) {
        if (amount <= 0) {
            chips.remove(player);
        } else {
            chips.put(player, amount);
        }
        save();
    }

    void remove(UUID player) {
        if (chips.remove(player) != null) {
            save();
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        chips.forEach((uuid, amount) -> yaml.set(uuid.toString(), amount));
        try {
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "chips.yml を保存できません", e);
        }
    }
}
