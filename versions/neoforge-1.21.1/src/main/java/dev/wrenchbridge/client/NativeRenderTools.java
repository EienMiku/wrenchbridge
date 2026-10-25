package dev.wrenchbridge.client;

import dev.wrenchbridge.ConfiguratorViews;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Mekanism's renderer is one consumer of the shared native callback views. */
public final class NativeRenderTools {
    private NativeRenderTools() {}
    public static void tick(Player player) { NativeClientViews.tick(player); }
    public static ItemStack itemInHand(Player player, InteractionHand hand) {
        return SharingPreferences.enabled(ClientOption.CONFIGURATOR_OVERLAY)
                ? NativeClientViews.itemInHand(player, hand, ConfiguratorViews.ITEM) : player.getItemInHand(hand);
    }
}
