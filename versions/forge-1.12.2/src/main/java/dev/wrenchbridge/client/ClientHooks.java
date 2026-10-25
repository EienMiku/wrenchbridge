package dev.wrenchbridge.client;

import dev.wrenchbridge.Tools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public final class ClientHooks {
  private static KeyBinding modes, settings;

  private ClientHooks() {}

  public static void nativeSnapshot(Runnable action) {
    net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(action);
  }

  public static void register() {
    SharingClient.register();
    modes = new KeyBinding("key.wrenchbridge.modes", Keyboard.KEY_G, "key.categories.wrenchbridge");
    ClientRegistry.registerKeyBinding(modes);
    settings =
        new KeyBinding(
            "key.wrenchbridge.settings", Keyboard.KEY_NONE, "key.categories.wrenchbridge");
    ClientRegistry.registerKeyBinding(settings);
    MinecraftForge.EVENT_BUS.register(ClientHooks.class);
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END || modes == null) return;
    SourceViewClient.tick();
    NativeRenderTools.tick(Minecraft.getMinecraft().player);
    while (settings.isPressed()) {
      if (Minecraft.getMinecraft().currentScreen == null) openSettings();
    }
    while (modes.isPressed()) {
      Minecraft game = Minecraft.getMinecraft();
      if (game.currentScreen == null
          && game.player != null
          && Tools.universalHand(game.player) != null) open();
    }
  }

  public static void openSettings() {
    Minecraft game = Minecraft.getMinecraft();
    game.displayGuiScreen(new SharingSettingsScreen(game.currentScreen));
  }

  public static void open() {
    Minecraft.getMinecraft()
        .displayGuiScreen(
            new ModesScreen(
                dev.wrenchbridge.Tools.universalHand(
                    net.minecraft.client.Minecraft.getMinecraft().player)));
  }

  public static void catalog(java.util.List<String> rows) {
    NativeRenderTools.catalog(rows);
    if (Minecraft.getMinecraft().currentScreen instanceof ModesScreen)
      ((ModesScreen) Minecraft.getMinecraft().currentScreen).catalog(rows);
  }
}
