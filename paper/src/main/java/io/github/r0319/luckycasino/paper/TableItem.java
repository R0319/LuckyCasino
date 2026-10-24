package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Logger;

/** 設置用のテーブルアイテムとクラフトレシピ。 */
final class TableItem {

    private final NamespacedKey marker;
    private final NamespacedKey recipeKey;

    TableItem(Plugin plugin) {
        this.marker = new NamespacedKey(plugin, "table");
        this.recipeKey = new NamespacedKey(plugin, "blackjack_table");
    }

    ItemStack create() {
        ItemStack item = Menus.named(Items.model("table/blackjack"), Component.text("ブラックジャックテーブル", NamedTextColor.GOLD),
                List.of(Component.text("ブロックの上面に右クリックで置く", NamedTextColor.GRAY),
                        Component.text("置いた人がスニーク + 右クリックで片付けられます", NamedTextColor.GRAY)));
        item.editPersistentDataContainer(pdc -> pdc.set(marker, PersistentDataType.STRING, "blackjack"));
        return item;
    }

    boolean isTable(ItemStack item) {
        return item != null && !item.isEmpty()
                && "blackjack".equals(item.getPersistentDataContainer().get(marker, PersistentDataType.STRING));
    }

    /**
     * config.yml の table-recipe からレシピを登録する。材料は素材名か、#planks のようなタグ名。
     */
    void registerRecipe(Plugin plugin, ConfigurationSection section, Logger logger) {
        plugin.getServer().removeRecipe(recipeKey);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        List<String> shape = section.getStringList("shape");
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (shape.isEmpty() || shape.size() > 3 || ingredients == null) {
            logger.warning("table-recipe の shape か ingredients が不正なため、レシピを登録しません");
            return;
        }
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, create());
        recipe.shape(shape.toArray(String[]::new));
        for (String key : ingredients.getKeys(false)) {
            RecipeChoice choice = choice(ingredients.getString(key, ""));
            if (choice == null || key.length() != 1) {
                logger.warning("table-recipe の材料 " + key + " を読めないため、レシピを登録しません");
                return;
            }
            recipe.setIngredient(key.charAt(0), choice);
        }
        plugin.getServer().addRecipe(recipe);
    }

    private static RecipeChoice choice(String value) {
        if (value.startsWith("#")) {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(value.substring(1)), Material.class);
            return tag == null ? null : new RecipeChoice.MaterialChoice(tag);
        }
        Material material = Material.matchMaterial(value);
        return material == null ? null : new RecipeChoice.MaterialChoice(material);
    }
}
