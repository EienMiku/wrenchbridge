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
          net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR
              .get()
              .resolve("wrenchbridge-client.properties"));
    } catch (IOException | IllegalArgumentException error) {
      WrenchBridge.LOG.warn("Could not load client sharing preference", error);
    }
    net.minecraftforge.fml.ModLoadingContext.get()
        .registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () ->
                new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                    (minecraft, parent) -> new SharingSettingsScreen(parent)));
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
        (net.minecraftforge.event.TickEvent.ClientTickEvent event) -> {
          if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) tick();
        });
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
      dev.wrenchbridge.SharingPacket.send(enabled);
      connection = current;
      sent = enabled;
    }
  }

  public static void toggle(dev.wrenchbridge.core.SharingPreferences.ClientOption option)
      throws IOException {
    SharingPreferences.setClientOption(option, !SharingPreferences.enabled(option));
    tick();
  }

  public static void reset() throws IOException {
    SharingPreferences.resetClientOptions();
    tick();
  }

  public static void toggle() throws IOException {
    SharingPreferences.setClientEnabled(!SharingPreferences.clientEnabled());
    tick();
  }
}
