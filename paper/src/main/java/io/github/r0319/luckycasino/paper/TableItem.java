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

/** 設置用のテーブルアイテム（種類ごと）とクラフトレシピ。 */
final class TableItem {

    private final Plugin plugin;
    private final NamespacedKey marker;

    TableItem(Plugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "table");
    }

    private NamespacedKey recipeKey(GameType type) {
        return new NamespacedKey(plugin, type.id() + "_table");
    }

    ItemStack create(GameType type) {
        ItemStack item = Menus.named(Items.model("table/" + type.id()), L10n.tr(type.itemName()).color(NamedTextColor.GOLD),
                List.of(L10n.tr("ブロックの上面に右クリックで置く（%s）", L10n.tr(type.singleBlock() ? "1 x 1 ブロック、高さ 2" : "4 x 2 ブロック"))
                                .color(NamedTextColor.GRAY),
                        L10n.tr("置いた人がスニーク + 右クリックで片付けられます").color(NamedTextColor.GRAY)));
        item.editPersistentDataContainer(pdc -> pdc.set(marker, PersistentDataType.STRING, type.id()));
        return item;
    }

    /** テーブルアイテムならその種類、違えば null。 */
    GameType typeOf(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String id = item.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
        return id == null ? null : GameType.byId(id);
    }

    /**
     * config.yml の recipes からレシピを登録する。材料は素材名か、#planks のようなタグ名。
     */
    void registerRecipes(ConfigurationSection recipes, Logger logger) {
        for (GameType type : GameType.values()) {
            plugin.getServer().removeRecipe(recipeKey(type));
            ConfigurationSection section = recipes == null ? null : recipes.getConfigurationSection(type.id());
            if (section != null && section.getBoolean("enabled", true)) {
                register(type, section, logger);
            }
        }
    }

    private void register(GameType type, ConfigurationSection section, Logger logger) {
        String where = "recipes." + type.id();
        List<String> shape = section.getStringList("shape");
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (shape.isEmpty() || shape.size() > 3 || ingredients == null) {
            logger.warning(L10n.plain("%s の shape か ingredients が不正なため、レシピを登録しません", where));
            return;
        }
        ShapedRecipe recipe = new ShapedRecipe(recipeKey(type), create(type));
        recipe.shape(shape.toArray(String[]::new));
        for (String key : ingredients.getKeys(false)) {
            RecipeChoice choice = choice(ingredients.getString(key, ""));
            if (choice == null || key.length() != 1) {
                logger.warning(L10n.plain("%s の材料 %s を読めないため、レシピを登録しません", where, key));
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
