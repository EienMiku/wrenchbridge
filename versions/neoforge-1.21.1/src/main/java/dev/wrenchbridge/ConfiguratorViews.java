package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import dev.wrenchbridge.core.NativeModes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Read-only native representation for Mekanism's face-overlay renderer. Never equipped or consumed. */
public final class ConfiguratorViews {
    public static final String ITEM = "mekanism:configurator";
    private ConfiguratorViews() {}

    public static boolean selected(ItemStack held) {
        return VirtualModes.isNative(held) && ITEM.equals(VirtualModes.item(held));
    }

    public static ItemStack create(Player player, ItemStack held, ItemStack source) {
        if (!selected(held) || source.isEmpty() || !Tools.id(source).equals(ITEM)
                || !ClassProbe.is(source.getItem(), "mekanism.common.item.ItemConfigurator")) return ItemStack.EMPTY;
        ItemStack view = source.copyWithCount(1);
        String mode = VirtualModes.mode(held);
        try {
            if (!mode.isEmpty() && !NativeModes.select(view.getItem(), view, player, mode)) return ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return ItemStack.EMPTY;
        }
        return view;
    }
}
