package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bridge.CardCode;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** リソースパックのモデルを付けたアイテム。 */
final class Items {

    private Items() {
    }

    static ItemStack model(String path) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("luckycasino", path));
        return item;
    }

    static ItemStack card(int code) {
        return model("card/" + CardCode.modelName(code));
    }
}
