package dev.wrenchbridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wrenchbridge.Tools;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

public final class ClientHooks {
  private static KeyMapping modes, settings;

  private ClientHooks() {}

  public static void register(IEventBus bus) {
    SharingClient.register();
    SourceViewClient.register(bus);
    bus.addListener(
        (RegisterKeyMappingsEvent event) -> {
          modes =
              new KeyMapping(
                  "key.wrenchbridge.modes",
                  InputConstants.Type.KEYSYM,
                  GLFW.GLFW_KEY_G,
                  "key.categories.wrenchbridge");
          event.register(modes);
          settings =
              new KeyMapping(
                  "key.wrenchbridge.settings",
                  InputConstants.Type.KEYSYM,
                  GLFW.GLFW_KEY_UNKNOWN,
                  "key.categories.wrenchbridge");
          event.register(settings);
        });
    MinecraftForge.EVENT_BUS.addListener(
        (TickEvent.ClientTickEvent event) -> {
          if (event.phase != TickEvent.Phase.END) return;
          var game = Minecraft.getInstance();
          SourceViewClient.tick();
          NativeRenderTools.tick(game.player);
          if (modes == null) return;
          while (settings.consumeClick()) {
            if (game.screen == null) openSettings();
          }
          while (modes.consumeClick()) {
            if (game.screen == null) open();
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
    if (Minecraft.getInstance().screen instanceof ModesScreen screen) screen.catalog(rows);
  }
}
