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
          net.neoforged.fml.loading.FMLPaths.CONFIGDIR
              .get()
              .resolve("wrenchbridge-client.properties"));
    } catch (IOException | IllegalArgumentException error) {
      WrenchBridge.LOG.warn("Could not load client sharing preference", error);
    }
    net.neoforged.fml.ModList.get()
        .getModContainerById(WrenchBridge.ID)
        .orElseThrow(() -> new IllegalStateException("Missing mod container"))
        .registerExtensionPoint(
            net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
            (container, parent) -> new SharingSettingsScreen(parent));
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) -> tick());
  }

  public static void tick() {
    Minecraft game = Minecraft.getInstance();
    Object current = game.player == null ? null : game.getConnection();
    if (current == null) {
      connection = null;
      sent = null;
      return;
    }
    boolean enabled = SharingPreferences.clientEnabled();
    if (current != connection || sent == null || sent.booleanValue() != enabled) {
      net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
          new dev.wrenchbridge.SharingPacket(enabled));
      connection = current;
      sent = enabled;
    }
  }

  public static void toggle() throws IOException {
    SharingPreferences.setClientEnabled(!SharingPreferences.clientEnabled());
    tick();
  }
}
