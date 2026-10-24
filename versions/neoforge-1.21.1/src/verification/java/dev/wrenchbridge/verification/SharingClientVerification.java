package dev.wrenchbridge.verification;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.client.ClientHooks;
import dev.wrenchbridge.client.ModesScreen;
import dev.wrenchbridge.client.SharingSettingsScreen;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Exercises the real settings buttons, saved preference and client-to-server payload. */
public final class SharingClientVerification {
  private static int ticks, phase, since;
  private static boolean finished;
  private static final boolean RESUME = Boolean.getBoolean("wrenchbridge.verifySharingResume");
  private static final boolean OFFLINE = Boolean.getBoolean("wrenchbridge.verifySettingsOffline");

  private SharingClientVerification() {}

  public static void register(IEventBus bus) {
    bus.addListener(
        (FMLClientSetupEvent event) ->
            event.enqueueWork(
                () -> GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow())));
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void tick() {
    if (finished) return;
    ticks++;
    if (ticks > 1800) {
      finish("FAIL: client sharing check timed out");
      return;
    }
    var game = Minecraft.getInstance();
    if (OFFLINE) {
      if (game.screen instanceof TitleScreen && game.getOverlay() == null) {
        try {
          check(game.player == null && game.level == null, "offline check loaded a world");
          var menu = openModListSettings();
          for (ClientOption option : ClientOption.values())
            button(game.screen, option.translationKey, true).onPress();
          check(!SharingPreferences.clientDefaults(), "offline options did not change");
          button(game.screen, "setting.wrenchbridge.reset").onPress();
          check(SharingPreferences.clientDefaults(), "offline reset failed");
          button(game.screen, "gui.done").onPress();
          check(game.screen == menu, "offline Done did not return to mod list");
          game.setScreen(new TitleScreen());
          ClientHooks.openSettings();
          check(game.screen instanceof SharingSettingsScreen, "settings need a player or wrench");
          finish(
              "PASS: actual mod-list configuration button, four options and reset without a loaded"
                  + " world, direct settings entry without a wrench");
        } catch (Throwable error) {
          WrenchBridge.LOG.error("Offline settings check failed", error);
          finish("FAIL: " + error);
        }
      }
      return;
    }
    if (game.player == null || game.level == null || game.getSingleplayerServer() == null) return;
    try {
      if (phase == 0) {
        check(SharingPreferences.clientEnabled() == !RESUME, "initial saved preference");
        for (ClientOption option : ClientOption.values())
          check(SharingPreferences.enabled(option) == !RESUME, "saved display option " + option);
        var key =
            java.util.Arrays.stream(game.options.keyMappings)
                .filter(mapping -> mapping.getName().equals("key.wrenchbridge.settings"))
                .findFirst()
                .orElseThrow();
        check(
            key.getDefaultKey().equals(InputConstants.UNKNOWN),
            "settings shortcut has an unexpected default");
        key.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H));
        KeyMapping.resetMapping();
        game.options.save();
        var id = game.player.getUUID();
        game.getSingleplayerServer()
            .submit(
                () -> {
                  var player = game.getSingleplayerServer().getPlayerList().getPlayer(id);
                  player.getInventory().clearContent();
                  player.inventoryMenu.broadcastChanges();
                })
            .get(3, TimeUnit.SECONDS);
        game.setScreen(null);
        phase = 10;
        since = ticks;
      } else if (phase == 10 && ticks - since >= 15) {
        check(
            game.player.getMainHandItem().isEmpty() && game.player.getOffhandItem().isEmpty(),
            "hotkey check is not empty-handed");
        KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_H));
        phase = 11;
        since = ticks;
      } else if (phase == 11 && ticks - since >= 3) {
        check(
            game.screen instanceof SharingSettingsScreen,
            "bound settings shortcut did not work empty-handed");
        game.setScreen(null);
        var menu = openModListSettings();
        check(game.screen instanceof SharingSettingsScreen, "mod-list configuration entry");
        check(
            button(game.screen, "gui.done").getY() + 20 <= game.screen.height,
            "footer outside screen");
        if (!RESUME)
          for (ClientOption option : ClientOption.values())
            button(game.screen, option.translationKey, true).onPress();
        phase = 1;
        since = ticks;
      } else if (phase == 1 && ticks - since >= 15) {
        check(!SharingPreferences.clientEnabled(), "OFF not reflected locally");
        check(
            !serverEnabled(),
            RESUME ? "saved OFF not synchronized on login" : "UI OFF not synchronized");
        Path config = FMLPaths.CONFIGDIR.get().resolve("wrenchbridge-client.properties");
        check(Files.readString(config).contains("shareOtherWrenches=false"), "OFF not saved");
        SharingPreferences.load(config);
        check(!SharingPreferences.clientEnabled(), "reload lost OFF");
        for (ClientOption option : ClientOption.values())
          check(!SharingPreferences.enabled(option), "saved option lost OFF: " + option);
        if (!RESUME) {
          try (var screenshot = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            screenshot.writeToFile(Path.of("client-settings.png"));
          }
        }
        button(game.screen, "setting.wrenchbridge.other_wrenches", false).onPress();
        phase = 2;
        since = ticks;
      } else if (phase == 2 && ticks - since >= 15) {
        check(SharingPreferences.clientEnabled() && serverEnabled(), "UI ON not synchronized");
        var id = game.player.getUUID();
        game.getSingleplayerServer()
            .submit(
                () -> {
                  var player = game.getSingleplayerServer().getPlayerList().getPlayer(id);
                  player.setItemInHand(
                      InteractionHand.MAIN_HAND, new ItemStack(WrenchBridge.WRENCH.get()));
                  player
                      .getInventory()
                      .setItem(
                          9,
                          new ItemStack(
                              net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                  net.minecraft.resources.ResourceLocation.parse(
                                      dev.wrenchbridge.ConfiguratorViews.ITEM))));
                  check(
                      dev.wrenchbridge.VirtualModes.select(
                          player,
                          InteractionHand.MAIN_HAND,
                          dev.wrenchbridge.ConfiguratorViews.ITEM,
                          "CONFIGURATE_ITEMS"),
                      "could not select overlay test profile");
                  player.inventoryMenu.broadcastChanges();
                })
            .get(3, TimeUnit.SECONDS);
        game.setScreen(null);
        phase = 3;
        since = ticks;
      } else if (phase == 3 && ticks - since >= 15) {
        check(
            game.player.getMainHandItem().is(WrenchBridge.WRENCH.get()),
            "held universal not synced");
        dev.wrenchbridge.client.NativeRenderTools.tick(game.player);
        check(
            dev.wrenchbridge.client.NativeRenderTools.itemInHand(
                    game.player, InteractionHand.MAIN_HAND)
                == game.player.getMainHandItem(),
            "disabled overlay still exposes a simulated view");
        game.setScreen(new ModesScreen(InteractionHand.MAIN_HAND));
        int textOnlyWidth = button(game.screen, "screen.wrenchbridge.shared").getWidth();
        button(game.screen, "screen.wrenchbridge.settings").onPress();
        check(game.screen instanceof SharingSettingsScreen, "palette settings entry");
        button(game.screen, "setting.wrenchbridge.reset").onPress();
        check(SharingPreferences.clientDefaults(), "reset did not restore all options");
        var donor = game.player.getInventory().getItem(9).copy();
        dev.wrenchbridge.client.NativeRenderTools.tick(game.player);
        check(
            dev.wrenchbridge.Tools.id(
                    dev.wrenchbridge.client.NativeRenderTools.itemInHand(
                        game.player, InteractionHand.MAIN_HAND))
                .equals(dev.wrenchbridge.ConfiguratorViews.ITEM),
            "enabled overlay did not restore the simulated view");
        check(
            ItemStack.isSameItemSameComponents(donor, game.player.getInventory().getItem(9)),
            "overlay option changed the source tool");
        check(
            !button(game.screen, "setting.wrenchbridge.reset").active,
            "reset should be inactive at defaults");
        button(game.screen, "gui.done").onPress();
        check(game.screen instanceof ModesScreen, "Done did not return to palette");
        check(
            button(game.screen, "screen.wrenchbridge.shared").getWidth() == textOnlyWidth - 24,
            "menu did not reclaim icon space");
        button(game.screen, "screen.wrenchbridge.settings").onPress();
        if (!RESUME)
          for (ClientOption option : ClientOption.values())
            button(game.screen, option.translationKey, true).onPress();
        phase = 4;
        since = ticks;
      } else if (phase == 4 && ticks - since >= 15) {
        check(serverEnabled() == RESUME, "final synchronization");
        finish(
            "PASS: actual mod-list entry, empty-hand bound shortcut, four options/reset, native"
                + " overlay OFF/ON, menu icon layout, palette return, UI OFF/ON, real C2S"
                + " synchronization, persistence"
                + (RESUME ? ", saved OFF restored on next login" : ", OFF saved for next launch"));
      }
    } catch (Throwable error) {
      WrenchBridge.LOG.error("Client sharing verification failed", error);
      finish("FAIL: " + error);
    }
  }

  private static net.neoforged.neoforge.client.gui.ModListScreen openModListSettings() {
    var game = Minecraft.getInstance();
    var menu = new net.neoforged.neoforge.client.gui.ModListScreen(new TitleScreen());
    game.setScreen(menu);
    var list =
        menu.children().stream()
            .filter(net.neoforged.neoforge.client.gui.widget.ModListWidget.class::isInstance)
            .map(net.neoforged.neoforge.client.gui.widget.ModListWidget.class::cast)
            .findFirst()
            .orElseThrow();
    var entry =
        list.children().stream()
            .filter(candidate -> candidate.getInfo().getModId().equals(WrenchBridge.ID))
            .findFirst()
            .orElseThrow();
    menu.setSelected(entry);
    var configure = button(menu, "fml.menu.mods.config");
    check(configure.active, "actual mod-list configuration button is disabled");
    configure.onPress();
    check(
        game.screen instanceof SharingSettingsScreen,
        "actual mod-list configuration button did not open settings");
    return menu;
  }

  private static Button button(Screen screen, String key) {
    return buttonWithLabel(screen, Component.translatable(key).getString());
  }

  private static Button button(Screen screen, String key, boolean enabled) {
    return buttonWithLabel(
        screen,
        Component.translatable(key, Component.translatable(enabled ? "options.on" : "options.off"))
            .getString());
  }

  private static Button buttonWithLabel(Screen screen, String label) {
    return screen.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(button -> button.getMessage().getString().equals(label))
        .findFirst()
        .orElseThrow();
  }

  private static boolean serverEnabled() throws Exception {
    var game = Minecraft.getInstance();
    var id = game.player.getUUID();
    return game.getSingleplayerServer()
        .submit(() -> SharingPreferences.allows(id, false, false))
        .get(3, TimeUnit.SECONDS);
  }

  private static void finish(String result) {
    if (finished) return;
    finished = true;
    try {
      Files.writeString(
          Path.of(
              OFFLINE
                  ? "settings-offline-result.txt"
                  : RESUME ? "sharing-client-resume-result.txt" : "sharing-client-result.txt"),
          result + "\n");
    } catch (Exception error) {
      WrenchBridge.LOG.error("Could not save sharing check result", error);
    }
    WrenchBridge.LOG.info(result);
    Minecraft.getInstance().stop();
  }
}
