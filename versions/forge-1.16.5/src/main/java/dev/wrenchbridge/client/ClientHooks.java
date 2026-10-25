package dev.wrenchbridge.client;

import dev.wrenchbridge.Tools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientHooks {
  private static KeyBinding modes, settings;

  private ClientHooks() {}

  public static void nativeSnapshot(Runnable action) {
    Minecraft.getInstance().execute(action);
  }

  public static void register(IEventBus bus) {
    SharingClient.register();
    bus.addListener(
        (FMLClientSetupEvent event) ->
            event.enqueueWork(
                () -> {
                  modes =
                      new KeyBinding(
                          "key.wrenchbridge.modes", GLFW.GLFW_KEY_G, "key.categories.wrenchbridge");
                  ClientRegistry.registerKeyBinding(modes);
                  settings =
                      new KeyBinding(
                          "key.wrenchbridge.settings",
                          GLFW.GLFW_KEY_UNKNOWN,
                          "key.categories.wrenchbridge");
                  ClientRegistry.registerKeyBinding(settings);
                }));
    MinecraftForge.EVENT_BUS.addListener(
        (TickEvent.ClientTickEvent event) -> {
          if (event.phase != TickEvent.Phase.END) return;
          SourceViewClient.tick();
          NativeRenderTools.tick(Minecraft.getInstance().player);
          if (modes == null) return;
          while (settings.consumeClick()) {
            if (Minecraft.getInstance().screen == null) openSettings();
          }
          while (modes.consumeClick()) {
            Minecraft game = Minecraft.getInstance();
            if (game.screen == null
                && game.player != null
                && Tools.universalHand(game.player) != null) open();
          }
        });
  }

  public static void openSettings() {
    var game = Minecraft.getInstance();
    game.setScreen(new SharingSettingsScreen(game.screen));
  }

  public static void open() {
    var game = Minecraft.getInstance();
    if (game.player == null) return;
    var hand = Tools.universalHand(game.player);
    if (hand != null) game.setScreen(new ModesScreen(hand));
  }

  public static void catalog(java.util.List<String> rows) {
    NativeRenderTools.catalog(rows);
    if (Minecraft.getInstance().screen instanceof ModesScreen)
      ((ModesScreen) Minecraft.getInstance().screen).catalog(rows);
  }
}
