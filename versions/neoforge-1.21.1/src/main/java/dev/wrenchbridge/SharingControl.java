package dev.wrenchbridge;

import dev.wrenchbridge.core.SharingPreferences;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

public final class SharingControl {
  private SharingControl() {}

  public static void register() {
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(SharingControl.class);
  }

  public static boolean allows(Player player, ItemStack held) {
    return SharingPreferences.allows(
        player.getUUID(),
        player.level().isClientSide,
        held.getItem() instanceof UniversalWrenchItem);
  }

  @SubscribeEvent
  public static void logout(
      net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
    SharingPreferences.forget(event.getEntity().getUUID());
    SourceViewPacket.forget(event.getEntity().getUUID());
    ServerRequests.forget(event.getEntity().getUUID());
    NativeBlockActions.forget(event.getEntity().getUUID());
  }

  @SubscribeEvent
  public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
    SharingPreferences.clearServer();
    SourceViewPacket.clearServer();
    ServerRequests.clear();
    NativeBlockActions.clear();
  }

  @SubscribeEvent
  public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
    ServerRequests.tick(event.getServer());
  }
}
