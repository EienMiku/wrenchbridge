package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import dev.wrenchbridge.client.SourceViewClient;
import dev.wrenchbridge.core.NativeModes;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandler;
import org.lwjgl.glfw.GLFW;

/**
 * Actual integrated-client payload, native tooltip and item rendering checks in an isolated world.
 */
public final class SourceViewClientVerification {
  private static final String[] IDS = {
    "supplementaries:wrench",
    "supplementaries:wrench",
    "supplementaries:wrench",
    "supplementaries:wrench",
    "mekanism:configurator",
    "mekanism:energy_tablet",
    "pneumaticcraft:pneumatic_wrench",
    "wrenchbridge:mode_wrench"
  };
  private static int ticks, phase = -1, since, decorators, savedDecorators;
  private static boolean finished, changed;
  private static ItemStack expected, original;
  private static String originalMode;
  private static IItemHandler contents;
  private static final List<String> results = new ArrayList<>();

  private SourceViewClientVerification() {}

  public static void register(IEventBus bus) {
    bus.addListener(
        (FMLClientSetupEvent event) ->
            event.enqueueWork(
                () -> GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow())));
    bus.addListener(
        (RegisterItemDecorationsEvent event) ->
            event.register(
                BuiltInRegistries.ITEM.get(ResourceLocation.parse("wrenchbridge:mode_wrench")),
                (graphics, font, stack, x, y) -> {
                  decorators++;
                  graphics.fill(x + 14, y, x + 16, y + 3, 0xFFFFCC33);
                  return true;
                }));
    NeoForge.EVENT_BUS.addListener(
        (net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) -> {
          if (Tools.id(event.getItemStack()).equals("wrenchbridge:mode_wrench"))
            event
                .getToolTip()
                .add(Component.literal("Native gold instruction").withStyle(ChatFormatting.GOLD));
        });
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void server(java.util.function.Consumer<ServerPlayer> action) throws Exception {
    var game = Minecraft.getInstance();
    var id = game.player.getUUID();
    game.getSingleplayerServer()
        .submit(() -> action.accept(game.getSingleplayerServer().getPlayerList().getPlayer(id)))
        .get(5, TimeUnit.SECONDS);
  }

  private static ItemStack wrench() {
    var player = Minecraft.getInstance().player;
    return phase == 2
        ? player.getOffhandItem()
        : phase == 3 ? player.getInventory().getItem(10) : player.getMainHandItem();
  }

  private static void setup(int selected) throws Exception {
    phase = selected;
    since = ticks;
    changed = false;
    savedDecorators = decorators;
    server(
        player -> {
          try {
            player.getInventory().clearContent();
            player.getInventory().selected = 0;
            ItemStack donor =
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(IDS[selected])));
            donor.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Source " + selected).withStyle(ChatFormatting.AQUA));
            if (donor.isDamageableItem()) donor.setDamageValue(selected == 0 ? 17 : 31);
            if (selected == 5) {
              var energy =
                  Class.forName("mekanism.common.util.StorageUtils")
                      .getMethod("getEnergyContainer", ItemStack.class, int.class)
                      .invoke(null, donor, 0);
              check(energy != null, "missing configurator energy container");
              Class.forName("mekanism.api.energy.IEnergyContainer")
                  .getMethod("setEnergy", long.class)
                  .invoke(energy, 24000L);
            }
            if (selected == 6) {
              var air =
                  (Optional<?>)
                      Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities")
                          .getMethod("getAirHandler", ItemStack.class)
                          .invoke(null, donor);
              Class.forName("me.desht.pneumaticcraft.api.tileentity.IAirHandler")
                  .getMethod("addAir", int.class)
                  .invoke(air.orElseThrow(), 5000);
            }
            if (selected == 7) NativeModes.select(donor.getItem(), donor, player, "WRENCH");
            contents = null;
            if (selected == 0) {
              player.getInventory().setItem(2, donor);
              ItemStack later = donor.copy();
              later.setDamageValue(39);
              player.getInventory().setItem(9, later);
            } else {
              ItemStack bag =
                  new ItemStack(
                      BuiltInRegistries.ITEM.get(
                          ResourceLocation.parse("sophisticatedbackpacks:backpack")));
              var wrapper =
                  Class.forName(
                          "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper")
                      .getMethod("fromStack", ItemStack.class)
                      .invoke(null, bag);
              wrapper
                  .getClass()
                  .getMethod("setContentsUuid", UUID.class)
                  .invoke(wrapper, UUID.randomUUID());
              contents =
                  (IItemHandler)
                      wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
              check(contents.insertItem(0, donor, false).isEmpty(), "could not store source");
              player.getInventory().setItem(8, bag);
              donor = contents.getStackInSlot(0);
            }
            original = donor;
            expected = donor.copy();
            originalMode = NativeModes.current(donor.getItem(), donor);
            InteractionHand hand =
                selected == 2 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            var held = new ItemStack(WrenchBridge.WRENCH.get());
            player.setItemInHand(hand, held);
            check(
                VirtualModes.select(player, hand, IDS[selected], selected == 7 ? "CONFIG" : ""),
                "could not select source");
            if (selected == 3) {
              player.getInventory().setItem(10, held);
              player.setItemInHand(hand, ItemStack.EMPTY);
            }
            player.inventoryMenu.broadcastChanges();
          } catch (Exception error) {
            throw new RuntimeException(error);
          }
        });
    Minecraft.getInstance().setScreen(new Preview());
  }

  private static void tick() {
    if (finished) return;
    var game = Minecraft.getInstance();
    if (++ticks > 2400) {
      finish("FAIL: source display client timed out");
      return;
    }
    if (game.player == null || game.getSingleplayerServer() == null) return;
    try {
      if (phase < 0) {
        SharingPreferences.resetClientOptions();
        check(
            ClientOption.values().length == 5 && SharingPreferences.clientDefaults(),
            "five default options");
        setup(0);
        return;
      }
      ItemStack held = wrench();
      if (!held.is(WrenchBridge.WRENCH.get())) return;
      ItemStack view = SourceViewClient.view(held);
      if (ticks - since < 35 || !ItemStack.isSameItemSameComponents(view, expected)) return;
      check(held.isBarVisible() == expected.isBarVisible(), "native bar visibility " + phase);
      if (expected.isBarVisible()) {
        check(held.getBarWidth() == expected.getBarWidth(), "native bar width " + phase);
        check(held.getBarColor() == expected.getBarColor(), "native bar color " + phase);
      }
      var context = Item.TooltipContext.of(game.level);
      var nativeLines = expected.copy().getTooltipLines(context, game.player, TooltipFlag.ADVANCED);
      var lines = held.getTooltipLines(context, game.player, TooltipFlag.ADVANCED);
      check(
          Collections.indexOfSubList(lines, nativeLines) >= 0,
          "full styled native tooltip missing " + phase);
      server(
          player -> {
            check(
                ItemStack.isSameItemSameComponents(original, expected),
                "display mutated original " + phase);
            check(
                NativeModes.current(original.getItem(), original).equals(originalMode),
                "display changed native mode");
            if (contents != null)
              check(contents.getStackInSlot(0) == original, "display extracted stored tool");
          });
      if (phase == 1 && !changed) {
        server(
            player -> {
              try {
                check(
                    StoredTools.use(
                        player,
                        IDS[phase],
                        stack -> {
                          stack.setDamageValue(stack.getDamageValue() + 1);
                          return true;
                        },
                        false),
                    "native source update failed");
                original = contents.getStackInSlot(0);
                expected = original.copy();
              } catch (Exception error) {
                throw new RuntimeException(error);
              }
            });
        changed = true;
        since = ticks;
        return;
      }
      if (phase == 1 || phase == 5) {
        try (var frame = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
          frame.writeToFile(Path.of(phase == 1 ? "source-durability.png" : "source-energy.png"));
        }
      }
      if (phase == 7) {
        check(decorators > savedDecorators, "registered native item decoration missing");
        SharingPreferences.setClientOption(ClientOption.SOURCE_DETAILS, false);
        check(
            SourceViewClient.view(held).isEmpty() && !held.isBarVisible(),
            "disabled source bar still present");
        var disabled = held.getTooltipLines(context, game.player, TooltipFlag.ADVANCED);
        check(
            Collections.indexOfSubList(disabled, nativeLines) < 0,
            "disabled native tooltip still present");
        SharingPreferences.resetClientOptions();
        check(SharingPreferences.clientDefaults(), "reset lost default source status");
        server(
            player -> {
              player.getInventory().setItem(8, ItemStack.EMPTY);
              SourceViewPacket.changed(player);
              player.inventoryMenu.broadcastChanges();
            });
        phase = 8;
        since = ticks;
        return;
      }
      if (phase == 8) return;
      results.add(
          IDS[phase]
              + ": full styled tooltip and native bar; "
              + (phase == 0
                  ? "first ordinary source"
                  : phase == 2
                      ? "stored offhand"
                      : phase == 3 ? "unheld inventory item" : "Sophisticated Backpack"));
      setup(phase + 1);
    } catch (Throwable error) {
      WrenchBridge.LOG.error("Source display client check failed", error);
      finish("FAIL: " + error);
    }
  }

  private static void finish(String result) {
    finished = true;
    try {
      Files.writeString(Path.of("source-view-client-result.txt"), result);
    } catch (Exception error) {
      WrenchBridge.LOG.error("Could not save source display result", error);
    }
    WrenchBridge.LOG.info(result);
    Minecraft.getInstance().stop();
  }

  private static final class Preview extends Screen {
    Preview() {
      super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
      graphics.fill(0, 0, width, height, 0xFF20242C);
      ItemStack held = wrench();
      graphics.renderItem(held, 16, 18);
      graphics.renderItemDecorations(font, held, 16, 18);
      graphics.renderTooltip(font, held, 20, 48);
      if (phase == 8 && ticks - since >= 30) {
        try {
          check(
              SourceViewClient.view(held).isEmpty() && !held.isBarVisible(),
              "detached bag left stale status");
          finish(
              "PASS: "
                  + String.join("; ", results)
                  + "; native decorator; real source cost refresh; unchanged native mode and"
                  + " identity; five defaults, OFF/reset and detached bag invalidation");
        } catch (Throwable error) {
          finish("FAIL: " + error);
        }
      }
    }
  }
}
