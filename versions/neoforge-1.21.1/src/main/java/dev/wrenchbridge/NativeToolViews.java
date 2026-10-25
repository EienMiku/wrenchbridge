package dev.wrenchbridge;

import dev.wrenchbridge.core.NativeModes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared read-only projection. A returned stack is never installed in a player or container slot. */
public final class NativeToolViews {
    private NativeToolViews() {}

    public static boolean selected(ItemStack held, String nativeItem) {
        return VirtualModes.isNative(held) && nativeItem.equals(VirtualModes.item(held));
    }

    public static ItemStack create(Player player, ItemStack held, ItemStack source) {
        if (!VirtualModes.isNative(held) || source.isEmpty() || source.getItem() instanceof UniversalWrenchItem
                || !Tools.id(source).equals(VirtualModes.item(held)) || !Tools.candidate(source)) return ItemStack.EMPTY;
        ItemStack view = source.copyWithCount(1);
        String mode = VirtualModes.mode(held);
        try {
            if (!mode.isEmpty() && !NativeModes.select(view.getItem(), view, player, mode)) return ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException error) { return ItemStack.EMPTY; }
        return view;
    }
}
