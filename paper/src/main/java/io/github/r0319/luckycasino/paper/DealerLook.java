package io.github.r0319.luckycasino.paper;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Mannequin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ディーラーの見た目（卓ごとに tables.yml へ保存する）。
 *
 * @param skin      スキンにするプレイヤー名（null なら既定のスキン）。名前だけ渡し、スキンはクライアントが取りに行く
 * @param name      頭の上の名前（null なら「ディーラー」）
 * @param equipment 着せている物。見た目だけで、取り出せない
 */
record DealerLook(String skin, String name, Map<EquipmentSlot, ItemStack> equipment) {

    static final DealerLook DEFAULT = new DealerLook(null, null, Map.of());

    /** メニューに並べる順。 */
    static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND);

    static final int MAX_NAME = 32;

    DealerLook {
        Map<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        equipment.forEach((slot, item) -> {
            if (item != null && !item.isEmpty()) {
                copy.put(slot, item.asOne());
            }
        });
        equipment = Map.copyOf(copy);
    }

    DealerLook withSkin(String value) {
        return new DealerLook(value, name, equipment);
    }

    DealerLook withName(String value) {
        return new DealerLook(skin, value, equipment);
    }

    DealerLook withItem(EquipmentSlot slot, ItemStack item) {
        Map<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
        copy.putAll(equipment);
        if (item == null || item.isEmpty()) {
            copy.remove(slot);
        } else {
            copy.put(slot, item);
        }
        return new DealerLook(skin, name, copy);
    }

    Component nameText() {
        return Component.text(name == null ? "ディーラー" : name, NamedTextColor.GOLD);
    }

    void apply(Mannequin dealer) {
        dealer.setProfile(skin == null ? Mannequin.defaultProfile() : ResolvableProfile.resolvableProfile().name(skin).build());
        dealer.setDescription(nameText());
        EntityEquipment gear = dealer.getEquipment();
        // Mannequin は Mob ではないので落とす確率は決められない。倒されたときの落とし物は TableManager で消す
        for (EquipmentSlot slot : SLOTS) {
            gear.setItem(slot, equipment.get(slot));
        }
    }

    /** Minecraft のプレイヤー名として使える形か（英数字と _ の 3〜16 文字）。 */
    static boolean validSkin(String value) {
        return value.matches("[A-Za-z0-9_]{3,16}");
    }

    void save(ConfigurationSection section) {
        section.set("skin", skin);
        section.set("name", name);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipment.entrySet()) {
            section.set("equipment." + entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
        }
    }

    static DealerLook load(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack item = section.getItemStack("equipment." + slot.name().toLowerCase(Locale.ROOT));
            if (item != null) {
                equipment.put(slot, item);
            }
        }
        return new DealerLook(section.getString("skin"), section.getString("name"), equipment);
    }
}
