package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import dev.wrenchbridge.core.NativeModes;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

/** Simulated profiles belong only to the universal wrench. */
public final class VirtualModes {
  private static final String KEY = "wrenchbridge_profile";

  private VirtualModes() {}

  private static NBTTagCompound data(ItemStack stack) {
    return stack.hasTagCompound()
        ? stack.getTagCompound().getCompoundTag(KEY)
        : new NBTTagCompound();
  }

  public static String item(ItemStack stack) {
    return data(stack).getString("item");
  }

  public static String mode(ItemStack stack) {
    return data(stack).getString("mode");
  }

  public static boolean isNative(ItemStack stack) {
    return stack.getItem() instanceof UniversalWrenchItem && !item(stack).isEmpty();
  }

  public static boolean select(EntityPlayerMP player, EnumHand hand, String id, String requested) {
    ItemStack wrench = player.getHeldItem(hand);
    if (!(wrench.getItem() instanceof UniversalWrenchItem) || player.isSpectator()) return false;
    NBTTagCompound root =
        wrench.hasTagCompound() ? wrench.getTagCompound().copy() : new NBTTagCompound();
    NBTTagCompound profile = root.getCompoundTag(KEY);
    if (id.equals("wrenchbridge:universal_wrench") && requested.isEmpty()) {
      profile.removeTag("item");
      profile.removeTag("mode");
    } else {
      ItemStack source = StoredTools.peek(player, id);
      if (source.isEmpty()) return false;
      List<String> choices = NativeModes.choices(source.getItem(), source, player);
      String choice = requested;
      if (choice.isEmpty() && !choices.isEmpty()) {
        choice = profile.getCompoundTag("remembered").getString(id);
        if (!choices.contains(choice)) choice = NativeModes.current(source.getItem(), source);
      }
      if (!choice.isEmpty() && !choices.contains(choice)) return false;
      profile.setString("item", id);
      profile.setString("mode", choice);
      NBTTagCompound remembered = profile.getCompoundTag("remembered");
      remembered.setString(id, choice);
      profile.setTag("remembered", remembered);
    }
    root.setTag(KEY, profile);
    wrench.setTagCompound(root);
    player.inventory.markDirty();
    player.inventoryContainer.detectAndSendChanges();
    player.openContainer.detectAndSendChanges();
    return true;
  }

  public static boolean hasNativeMenu(ItemStack wrench) {
    Item item = Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(item(wrench)));
    return ClassProbe.is(item, "appeng.items.tools.ToolNetworkTool");
  }

  public static EnumActionResult useOn(EntityPlayerMP player, EnumHand hand, RayTraceResult hit) {
    ItemStack wrench = player.getHeldItem(hand);
    String id = item(wrench), selected = mode(wrench);
    if (hasNativeMenu(wrench) && !player.isSneaking()) return openNativeMenu(player, hand, hit);
    try {
      return StoredTools.use(
          player,
          id,
          source -> {
            Item nativeItem = source.getItem();
            String original = NativeModes.current(nativeItem, source);
            boolean change = !selected.isEmpty() && !selected.equals(original);
            try {
              if (change && !NativeModes.select(nativeItem, source, player, selected))
                return EnumActionResult.FAIL;
              if (player.isSneaking()
                  && Tools.active(source)
                  && BridgeEvents.knownTarget(
                      player.world.getBlockState(hit.getBlockPos()).getBlock()))
                return BridgeEvents.dispatch(player, hand, hit, source);
              return WrenchContext.runServer(
                  player,
                  hand,
                  source,
                  () -> {
                    float x = (float) (hit.hitVec.x - hit.getBlockPos().getX()),
                        y = (float) (hit.hitVec.y - hit.getBlockPos().getY()),
                        z = (float) (hit.hitVec.z - hit.getBlockPos().getZ());
                    EnumActionResult result =
                        nativeItem.onItemUseFirst(
                            player, player.world, hit.getBlockPos(), hit.sideHit, x, y, z, hand);
                    if (result == EnumActionResult.PASS)
                      result =
                          nativeItem.onItemUse(
                              player, player.world, hit.getBlockPos(), hand, hit.sideHit, x, y, z);
                    if (result == EnumActionResult.PASS && Tools.active(source))
                      return BridgeEvents.blockUse(player, hand, hit);
                    return result;
                  });
            } finally {
              try {
                if (change && !source.isEmpty())
                  NativeModes.select(nativeItem, source, player, original);
              } finally {
                if (player.getHeldItem(hand) != wrench) player.setHeldItem(hand, wrench);
              }
            }
          },
          EnumActionResult.FAIL);
    } catch (ReflectiveOperationException | RuntimeException error) {
      WrenchBridge.LOG.warn("Native simulation rejected {}", id, error);
      return EnumActionResult.FAIL;
    }
  }

  public static EnumActionResult openNativeMenu(
      EntityPlayerMP player, EnumHand hand, RayTraceResult hit) {
    return ContainerMenus.open(player, hand, hit);
  }
}
