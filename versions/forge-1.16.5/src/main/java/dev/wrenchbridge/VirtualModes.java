package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import dev.wrenchbridge.core.NativeModes;
import java.util.List;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockRayTraceResult;

/**
 * Only the universal wrench owns simulated profiles. Native items keep their identity and selected
 * mode.
 */
public final class VirtualModes {
  private static final String KEY = "wrenchbridge_profile";

  private VirtualModes() {}

  private static CompoundNBT data(ItemStack wrench) {
    return wrench.getOrCreateTag().copy().getCompound(KEY);
  }

  public static String item(ItemStack wrench) {
    return data(wrench).getString("item");
  }

  public static String mode(ItemStack wrench) {
    return data(wrench).getString("mode");
  }

  public static boolean isNative(ItemStack wrench) {
    return wrench.getItem() instanceof UniversalWrenchItem && !item(wrench).isEmpty();
  }

  public static boolean select(
      ServerPlayerEntity player, Hand hand, String itemId, String requestedMode) {
    ItemStack wrench = player.getItemInHand(hand);
    if (!(wrench.getItem() instanceof UniversalWrenchItem) || player.isSpectator()) return false;
    CompoundNBT root = wrench.getOrCreateTag().copy();
    CompoundNBT selected = root.getCompound(KEY);
    if (itemId.equals("wrenchbridge:universal_wrench") && requestedMode.isEmpty()) {
      selected.remove("item");
      selected.remove("mode");
    } else {
      ItemStack source = StoredTools.peek(player, itemId);
      if (source.isEmpty()) return false;
      List<String> choices = NativeModes.choices(source.getItem(), source, player);
      String choice = requestedMode;
      if (choice.isEmpty() && !choices.isEmpty()) {
        choice = selected.getCompound("remembered").getString(itemId);
        if (!choices.contains(choice)) choice = NativeModes.current(source.getItem(), source);
      }
      if (!choice.isEmpty() && !choices.contains(choice)) return false;
      selected.putString("item", itemId);
      selected.putString("mode", choice);
      CompoundNBT remembered = selected.getCompound("remembered");
      remembered.putString(itemId, choice);
      selected.put("remembered", remembered);
    }
    root.put(KEY, selected);
    wrench.setTag(root);
    player.inventory.setChanged();
    player.inventoryMenu.broadcastChanges();
    player.containerMenu.broadcastChanges();
    return true;
  }

  public static ActionResultType useOn(
      ServerPlayerEntity player, Hand hand, BlockRayTraceResult hit) {
    try (var feedback = WrenchContext.serverAction(player)) {
      return useNativeOn(player, hand, hit);
    }
  }

  private static ActionResultType useNativeOn(
      ServerPlayerEntity player, Hand hand, BlockRayTraceResult hit) {
    ItemStack wrench = player.getItemInHand(hand);
    String id = item(wrench), selectedMode = mode(wrench);
    if ((id.equals("ae2:network_tool") || id.equals("appliedenergistics2:network_tool"))
        && !player.isShiftKeyDown()) return openNativeMenu(player, hand, hit);
    try {
      return NativeToolExecution.use(
          player,
          hand,
          id,
          source -> {
            Item originalItem = source.getItem();
            boolean pneumatic =
                ClassProbe.is(
                    originalItem, "me.desht.pneumaticcraft.common.item.PneumaticWrenchItem");
            if (pneumatic
                && ((Number)
                            originalItem
                                .getClass()
                                .getMethod("getPressure", ItemStack.class)
                                .invoke(originalItem, source))
                        .floatValue()
                    <= 0.1F) return ActionResultType.FAIL;
            String originalMode = NativeModes.current(originalItem, source);
            boolean changeMode = !selectedMode.isEmpty() && !selectedMode.equals(originalMode);
            try {
              if (changeMode && !NativeModes.select(originalItem, source, player, selectedMode))
                return ActionResultType.FAIL;
              Targets.Kind target =
                  Targets.resolve(
                      player.getLevel().getBlockState(hit.getBlockPos()),
                      player.getLevel().getBlockEntity(hit.getBlockPos()));
              // Match the physical tool's shared-dismantle routing before a native callback can
              // switch modes
              // or rotate the target. A pneumatic tool on its own machine must still run its
              // air-cost path.
              if (player.isShiftKeyDown()
                  && Tools.active(source)
                  && target != Targets.Kind.UNKNOWN
                  && !(pneumatic && target == Targets.Kind.PNEUMATIC))
                return BridgeEvents.dispatch(player, hand, hit, source);
              ItemUseContext context = context(player, hand, hit, source);
              ActionResultType result = source.getItem().onItemUseFirst(source, context);
              if (result != ActionResultType.PASS) return result;
              result = originalItem.useOn(context);
              if (result != ActionResultType.PASS) return result;
              if (pneumatic
                  && Targets.resolve(
                          player.getLevel().getBlockState(hit.getBlockPos()),
                          player.getLevel().getBlockEntity(hit.getBlockPos()))
                      == Targets.Kind.PNEUMATIC) return ActionResultType.PASS;
              if (player.isShiftKeyDown() && Tools.active(source))
                return BridgeEvents.dispatch(player, hand, hit, source);
              if (ClassProbe.is(originalItem, "dev.dubhe.anvilcraft.item.AnvilHammerItem")) {
                ClassProbe.compatibleMethod(
                        originalItem.getClass(),
                        "useBlock",
                        player,
                        hit.getBlockPos(),
                        player.getLevel(),
                        source,
                        hand,
                        hit)
                    .invoke(null, player, hit.getBlockPos(), player.getLevel(), source, hand, hit);
                return ActionResultType.SUCCESS;
              }
              // Configuration modes that pass must not fall through to another mod's static
              // wrench-tag handler.
              if (!Tools.active(source)) return ActionResultType.PASS;
              return BridgeEvents.nativeBlockUse(player, hand, hit, source);
            } finally {
              try {
                if (changeMode && !source.isEmpty())
                  NativeModes.select(originalItem, source, player, originalMode);
              } finally {
                // A native tool may attempt to replace its held form. That is outside the
                // simulation contract.
                if (player.getItemInHand(hand) != wrench) {
                  player.setItemInHand(hand, wrench);
                  WrenchBridge.LOG.warn(
                      "Blocked a native held-item replacement during {} simulation", id);
                }
              }
            }
          },
          ActionResultType.FAIL);
    } catch (ReflectiveOperationException | RuntimeException error) {
      WrenchBridge.LOG.warn("Native simulation rejected {} / {}", id, selectedMode, error);
      return ActionResultType.FAIL;
    }
  }

  public static boolean hasNativeMenu(ItemStack wrench) {
    ResourceLocation id = ResourceLocation.tryParse(item(wrench));
    if (id == null || !net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id))
      return false;
    Item tool = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
    return ClassProbe.is(tool, "dev.dubhe.anvilcraft.item.AnvilHammerItem")
        || ClassProbe.is(tool, "appeng.items.tools.NetworkToolItem");
  }

  public static ActionResultType openNativeMenu(ServerPlayerEntity player, Hand hand) {
    return ContainerMenus.open(player, hand, null);
  }

  private static ActionResultType openNativeMenu(
      ServerPlayerEntity player, Hand hand, BlockRayTraceResult hit) {
    return ContainerMenus.open(player, hand, hit);
  }

  public static ItemUseContext context(
      ServerPlayerEntity player, Hand hand, BlockRayTraceResult hit, ItemStack source) {
    return new ItemUseContext(player, hand, hit) {
      @Override
      public ItemStack getItemInHand() {
        return source;
      }
    };
  }
}
