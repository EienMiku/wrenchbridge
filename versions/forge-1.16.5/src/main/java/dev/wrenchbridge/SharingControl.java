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
    SourceViewPacket.forget(event.getPlayer().getUUID());
    ServerRequests.forget(event.getPlayer().getUUID());
  }

  @SubscribeEvent
  public static void stopped(net.minecraftforge.fml.event.server.FMLServerStoppedEvent event) {
    SharingPreferences.clearServer();
    SourceViewPacket.clearServer();
    ServerRequests.clear();
  }

  @SubscribeEvent
  public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
    if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
      net.minecraft.server.MinecraftServer server =
          net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer();
      if (server != null) ServerRequests.tick(server);
    }
  }
}
