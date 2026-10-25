package dev.wrenchbridge;

import dev.wrenchbridge.core.SharingPreferences;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class SharingControl {
  private SharingControl() {}

  public static void register() {
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(SharingControl.class);
  }

  public static boolean allows(EntityPlayer player, ItemStack held) {
    return SharingPreferences.allows(
        player.getUniqueID(), player.world.isRemote, held.getItem() instanceof UniversalWrenchItem);
  }

  @SubscribeEvent
  public static void logout(
      net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
    SharingPreferences.forget(event.player.getUniqueID());
    SourceViewPacket.forget(event.player.getUniqueID());
  }
}
