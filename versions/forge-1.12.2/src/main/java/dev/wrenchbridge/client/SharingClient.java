package dev.wrenchbridge.client;

import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.SharingPreferences;
import java.io.IOException;
import net.minecraft.client.Minecraft;

public final class SharingClient {
  private static Object connection;
  private static Boolean sent;

  private SharingClient() {}

  public static void register() {
    try {
      SharingPreferences.load(
          new java.io.File(Minecraft.getMinecraft().gameDir, "config")
              .toPath()
              .resolve("wrenchbridge-client.properties"));
    } catch (IOException | IllegalArgumentException error) {
      WrenchBridge.LOG.warn("Could not load client sharing preference", error);
    }

    net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(SharingClient.class);
  }

  @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
  public static void onTick(
      net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
    if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START) tick();
  }

  public static void tick() {
    Minecraft game = Minecraft.getMinecraft();
    Object current = game.player == null ? null : game.getConnection();
    if (current == null) {
      connection = null;
      sent = null;
      return;
    }
    boolean enabled = SharingPreferences.clientEnabled();
    if (current != connection || sent == null || sent.booleanValue() != enabled) {
      dev.wrenchbridge.SharingPacket.send(enabled);
      connection = current;
      sent = enabled;
    }
  }

  public static void toggle() throws IOException {
    SharingPreferences.setClientEnabled(!SharingPreferences.clientEnabled());
    tick();
  }
}
