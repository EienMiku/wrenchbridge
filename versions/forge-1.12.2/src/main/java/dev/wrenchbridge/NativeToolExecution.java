package dev.wrenchbridge;

import dev.wrenchbridge.core.NativeModes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** One real borrowed-tool transaction, shared by use callbacks and native GUI actions. */
public final class NativeToolExecution {
  private NativeToolExecution() {}

  public static <T> T use(
      EntityPlayerMP player,
      EnumHand hand,
      String nativeItem,
      StoredTools.Action<T> action,
      T unavailable)
      throws ReflectiveOperationException {
    ItemStack held = player.getHeldItem(hand);
    if (player.isSpectator() || !NativeToolViews.selected(held, nativeItem)) return unavailable;
    String selectedMode = VirtualModes.mode(held);
    return StoredTools.use(
        player,
        nativeItem,
        source -> {
          var item = source.getItem();
          String originalMode = NativeModes.current(item, source);
          boolean changeMode = !selectedMode.isEmpty() && !selectedMode.equals(originalMode);
          try {
            if (changeMode && !NativeModes.select(item, source, player, selectedMode))
              return unavailable;
            return action.run(source);
          } finally {
            try {
              if (changeMode && !source.isEmpty())
                NativeModes.select(item, source, player, originalMode);
            } finally {
              if (player.getHeldItem(hand) != held) {
                player.setHeldItem(hand, held);
                WrenchBridge.LOG.warn(
                    "Blocked a native held-item replacement during {} simulation", nativeItem);
              }
            }
          }
        },
        unavailable);
  }
}
