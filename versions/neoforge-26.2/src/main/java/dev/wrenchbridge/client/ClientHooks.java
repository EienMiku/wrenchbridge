package dev.wrenchbridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wrenchbridge.Tools;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
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
                  KeyMapping.Category.register(
                      Identifier.fromNamespaceAndPath("wrenchbridge", "modes")));
          event.register(modes);
          settings =
              new KeyMapping(
                  "key.wrenchbridge.settings",
                  InputConstants.Type.KEYSYM,
                  GLFW.GLFW_KEY_UNKNOWN,
                  modes.getCategory());
          event.register(settings);
        });
    NeoForge.EVENT_BUS.addListener(
        (ClientTickEvent.Post event) -> {
          var game = Minecraft.getInstance();
          SourceViewClient.tick();
          NativeRenderTools.tick(game.player);
          if (modes == null) return;
          while (settings.consumeClick()) {
            if (game.gui.screen() == null) openSettings();
          }
          while (modes.consumeClick()) {
            if (game.gui.screen() == null) open();
          }
        });
  }

  public static void openSettings() {
    var game = Minecraft.getInstance();
    game.gui.setScreen(new SharingSettingsScreen(game.gui.screen()));
  }

  public static void open() {
    var game = Minecraft.getInstance();
    if (game.player == null) return;
    var hand = Tools.universalHand(game.player);
    if (hand != null) game.gui.setScreen(new ModesScreen(hand));
  }

  public static void catalog(java.util.List<String> rows) {

    if (Minecraft.getInstance().gui.screen() instanceof ModesScreen screen) screen.catalog(rows);
  }
}
