package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import dev.wrenchbridge.core.NativeModes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Server-only discovery through container APIs. Never edits a foreign container's serialized data.
 */
public final class StoredTools {
  private static final class Located {
    public final ItemStack root;
    public final IItemHandler inventory;
    public final int slot;
    public final ItemStack tool;

    public Located(ItemStack root, IItemHandler inventory, int slot, ItemStack tool) {
      this.root = root;
      this.inventory = inventory;
      this.slot = slot;
      this.tool = tool;
    }

    public ItemStack root() {
      return root;
    }

    public IItemHandler inventory() {
      return inventory;
    }

    public int slot() {
      return slot;
    }

    public ItemStack tool() {
      return tool;
    }

    public boolean equals(Object o) {
      if (!(o instanceof Located)) return false;
      Located other = (Located) o;
      return java.util.Objects.equals(root, other.root)
          && java.util.Objects.equals(inventory, other.inventory)
          && java.util.Objects.equals(slot, other.slot)
          && java.util.Objects.equals(tool, other.tool);
    }

    public int hashCode() {
      return java.util.Objects.hash(root, inventory, slot, tool);
    }
  }

  private StoredTools() {}

  private static List<ItemStack> roots(PlayerEntity player) {
    List<ItemStack> roots = new ArrayList<>();
    for (int slot = 0; slot < player.inventory.getContainerSize(); slot++)
      roots.add(player.inventory.getItem(slot));
    try {
      Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
      Object optional =
          ClassProbe.compatibleMethod(api, "getCuriosInventory", player).invoke(null, player);
      if (optional instanceof Optional<?> value && value.isPresent()) {
        Object equipped = value.get().getClass().getMethod("getEquippedCurios").invoke(value.get());
        if (equipped instanceof IItemHandler items)
          for (int slot = 0; slot < Math.min(64, items.getSlots()); slot++)
            roots.add(items.getStackInSlot(slot));
      }
    } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
      // Curios is optional. PlayerEntity inventory containers remain available if its API differs.
    }
    return roots;
  }

  private static IItemHandler inventory(ItemStack root) {
    return root.isEmpty() || root.getCount() != 1
        ? null
        : root.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
            .orElse(null);
  }

  private static List<Located> find(PlayerEntity player) {
    List<Located> found = new ArrayList<>();
    int inspected = 0;
    Set<IItemHandler> visited =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    for (ItemStack root : roots(player)) {
      try {
        IItemHandler items = inventory(root);
        if (items == null || !visited.add(items)) continue;
        for (int slot = 0; slot < Math.min(512, items.getSlots()) && inspected++ < 2048; slot++) {
          ItemStack stack = items.getStackInSlot(slot);
          if (Tools.candidate(stack)
              && stack.getItem() != WrenchBridge.WRENCH.get()
              && !items.extractItem(slot, 1, true).isEmpty())
            found.add(new Located(root, items, slot, stack));
          if (found.size() >= 64) return found;
        }
      } catch (RuntimeException error) {
        WrenchBridge.LOG.debug("Container declined tool discovery", error);
      }
    }
    return found;
  }

  public static List<String> catalog(ServerPlayerEntity player) {
    Set<String> rows = new LinkedHashSet<>();
    if (Tools.universalHand(player) == null) return java.util.Collections.emptyList();
    for (Located entry : find(player)) {
      String id = Tools.id(entry.tool);
      if (id.length() > 128) continue;
      rows.add(id + "|");
      for (String mode : NativeModes.choices(entry.tool.getItem(), entry.tool, player)) {
        if (id.length() <= 128 && mode.length() <= 64 && rows.size() < 256)
          rows.add(id + "|" + mode);
      }
      if (rows.size() >= 256) break;
    }
    return new ArrayList<>(rows);
  }

  public static ItemStack peek(ServerPlayerEntity player, String id) {
    for (int slot = 0; slot < player.inventory.getContainerSize(); slot++) {
      if (slot >= 36 && slot != 40) continue;
      ItemStack stack = player.inventory.getItem(slot);
      if (eligible(stack, id)) return stack;
    }
    for (Located entry : find(player)) if (eligible(entry.tool, id)) return entry.tool;
    return ItemStack.EMPTY;
  }

  private static boolean eligible(ItemStack stack, String id) {
    return Tools.candidate(stack)
        && !(stack.getItem() instanceof UniversalWrenchItem)
        && Tools.id(stack).equals(id);
  }

  @FunctionalInterface
  public interface Action<T> {
    T run(ItemStack tool) throws ReflectiveOperationException;
  }

  public static <T> T use(ServerPlayerEntity player, String id, Action<T> action, T unavailable)
      throws ReflectiveOperationException {
    for (int slot = 0; slot < player.inventory.getContainerSize(); slot++) {
      if (slot >= 36 && slot != 40) continue;
      ItemStack stack = player.inventory.getItem(slot);
      if (eligible(stack, id)) {
        try {
          return action.run(stack);
        } finally {
          changed(player);
        }
      }
    }
    for (Located entry : find(player)) {
      if (!eligible(entry.tool, id)) continue;
      ItemStack extracted = entry.inventory.extractItem(entry.slot, 1, false);
      if (extracted.isEmpty()) continue;
      try {
        if (!eligible(extracted, id) || extracted.getCount() != 1) return unavailable;
        return action.run(extracted);
      } finally {
        ItemStack remainder = extracted;
        if (roots(player).stream().anyMatch(root -> root == entry.root))
          remainder = entry.inventory.insertItem(entry.slot, extracted, false);
        preserve(player, remainder);
        changed(player);
      }
    }
    player.displayClientMessage(
        new net.minecraft.util.text.TranslationTextComponent("message.wrenchbridge.source_missing"),
        true);
    return unavailable;
  }

  /** A menu binds one carried container and slot; it never edits getStackInSlot's result. */
  public static final class MenuSource {
    private final ServerPlayerEntity player;
    private final Located entry;
    private ItemStack expected, view, active;
    private boolean valid = true;

    private MenuSource(ServerPlayerEntity player, Located entry) {
      this.player = player;
      this.entry = entry;
      expected = entry.tool.copy();
      view = expected.copy();
    }

    public ServerPlayerEntity player() {
      return player;
    }

    public boolean available() {
      if (!valid) return false;
      if (active != null) return true;
      try {
        return roots(player).stream().anyMatch(root -> root == entry.root)
            && ItemStack.matches(expected, entry.inventory.getStackInSlot(entry.slot));
      } catch (RuntimeException error) {
        return false;
      }
    }

    /** Outside a transaction this is a read-only view, never a mutable container stack. */
    public ItemStack view() {
      return available() ? active != null ? active : view : ItemStack.EMPTY;
    }

    public <T> T use(java.util.function.Function<ItemStack, T> action, T unavailable) {
      if (active != null) return action.apply(active);
      if (!available()
          || !ItemStack.matches(expected, entry.inventory.extractItem(entry.slot, 1, true))) {
        valid = false;
        return unavailable;
      }
      ItemStack extracted = entry.inventory.extractItem(entry.slot, 1, false);
      if (!ItemStack.matches(expected, extracted)) {
        valid = false;
        preserve(player, extracted);
        return unavailable;
      }
      active = extracted;
      try {
        return action.apply(extracted);
      } finally {
        try {
          expected = extracted.copy();
          view = expected.copy();
          ItemStack remainder = extracted;
          if (roots(player).stream().anyMatch(root -> root == entry.root)
              || player.inventory.getCarried() == entry.root)
            try {
              remainder = entry.inventory.insertItem(entry.slot, extracted, false);
            } catch (RuntimeException error) {
              valid = false;
              WrenchBridge.LOG.warn("Container declined menu tool return", error);
            }
          if (!remainder.isEmpty()) {
            valid = false;
            preserve(player, remainder);
          }
        } finally {
          active = null;
          player.inventory.setChanged();
          player.inventoryMenu.broadcastChanges();
        }
      }
    }
  }

  public static MenuSource menuSource(ServerPlayerEntity player, String id) {
    for (Located entry : find(player))
      if (eligible(entry.tool, id) && entry.tool.getCount() == 1)
        return new MenuSource(player, entry);
    return null;
  }

  public static void forget(ServerPlayerEntity player) {
    ContainerMenus.forget(player);
  }

  private static void preserve(ServerPlayerEntity player, ItemStack remainder) {
    if (!remainder.isEmpty() && !player.inventory.add(remainder)) player.drop(remainder, false);
  }

  private static void changed(ServerPlayerEntity player) {
    player.inventory.setChanged();
    player.inventoryMenu.broadcastChanges();
    player.containerMenu.broadcastChanges();
  }
}
