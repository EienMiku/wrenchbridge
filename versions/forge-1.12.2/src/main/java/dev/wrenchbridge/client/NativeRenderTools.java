package dev.wrenchbridge.client;

import dev.wrenchbridge.ConfiguratorViews;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** Mekanism's renderer is one consumer of the shared native callback views. */
public final class NativeRenderTools {
  private NativeRenderTools() {}

  public static void tick(EntityPlayer player) {
    NativeClientViews.tick(player);
  }

  public static ItemStack itemInHand(EntityPlayer player, EnumHand hand) {
    return SharingPreferences.enabled(ClientOption.CONFIGURATOR_OVERLAY)
        ? NativeClientViews.itemInHand(player, hand, ConfiguratorViews.ITEM)
        : player.getHeldItem(hand);
  }
}
