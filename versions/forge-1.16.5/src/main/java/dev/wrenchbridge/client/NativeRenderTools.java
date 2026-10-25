package dev.wrenchbridge.client;

import dev.wrenchbridge.ConfiguratorViews;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/** Mekanism's renderer is one consumer of the shared native callback views. */
public final class NativeRenderTools {
  private NativeRenderTools() {}

  public static void tick(PlayerEntity player) {
    NativeClientViews.tick(player);
  }

  public static ItemStack itemInHand(PlayerEntity player, Hand hand) {
    return SharingPreferences.enabled(ClientOption.CONFIGURATOR_OVERLAY)
        ? NativeClientViews.itemInHand(player, hand, ConfiguratorViews.ITEM)
        : player.getItemInHand(hand);
  }
}
