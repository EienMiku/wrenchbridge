package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Read-only native representation for Mekanism's face-overlay renderer. Never equipped or consumed. */
public final class ConfiguratorViews {
    public static final String ITEM = "mekanism:configurator";
    private ConfiguratorViews() {}

    public static boolean selected(ItemStack held) {
        return NativeToolViews.selected(held, ITEM);
    }

    public static ItemStack create(Player player, ItemStack held, ItemStack source) {
        if (!selected(held) || source.isEmpty() || !Tools.id(source).equals(ITEM)
                || !ClassProbe.is(source.getItem(), "mekanism.common.item.ItemConfigurator")) return ItemStack.EMPTY;
        return NativeToolViews.create(player, held, source);
    }
}
