package dev.wrenchbridge;

import dev.wrenchbridge.core.SharingPreferences;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class SharingControl {
  private SharingControl() {}

  public static void register() {
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(SharingControl.class);
  }

  public static boolean allows(PlayerEntity player, ItemStack held) {
    return SharingPreferences.allows(
        player.getUUID(), player.level.isClientSide, held.getItem() instanceof UniversalWrenchItem);
  }

  @SubscribeEvent
  public static void logout(
      net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
    SharingPreferences.forget(event.getPlayer().getUUID());
  }

  @SubscribeEvent
  public static void stopped(net.minecraftforge.fml.event.server.FMLServerStoppedEvent event) {
    SharingPreferences.clearServer();
  }
}
