package dev.wrenchbridge;

import dev.wrenchbridge.core.NativeModes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.items.IItemHandler;

/**
 * Server-only discovery through container APIs. Never edits a foreign container's serialized data.
 */
public final class StoredTools {
  private static final class Located {
    final ItemStack root, tool;
    final IItemHandler inventory;
    final int slot;

    Located(ItemStack root, IItemHandler inventory, int slot, ItemStack tool) {
      this.root = root;
      this.inventory = inventory;
      this.slot = slot;
      this.tool = tool;
    }
  }

  private StoredTools() {}

  private static final class Curios {
    static final Class<?> API = api();

    private static Class<?> api() {
      try {
        return Class.forName("top.theillusivec4.curios.api.CuriosApi");
      } catch (ClassNotFoundException | LinkageError error) {
        return null;
      }
    }

    static final ClassValue<Optional<java.lang.reflect.Method>> INVENTORY =
        new ClassValue<Optional<java.lang.reflect.Method>>() {
          @Override
          protected Optional<java.lang.reflect.Method> computeValue(Class<?> type) {
            if (API == null) return Optional.empty();
            for (var method : API.getMethods()) {
              Class<?>[] params = method.getParameterTypes();
              if (method.getName().equals("getCuriosInventory")
                  && params.length == 1
                  && params[0].isAssignableFrom(type)) return Optional.of(method);
            }
            return Optional.empty();
          }
        };
    static final ClassValue<Optional<java.lang.reflect.Method>> EQUIPPED =
        new ClassValue<Optional<java.lang.reflect.Method>>() {
          @Override
          protected Optional<java.lang.reflect.Method> computeValue(Class<?> type) {
            try {
              return Optional.of(type.getMethod("getEquippedCurios"));
            } catch (NoSuchMethodException | LinkageError | RuntimeException error) {
              return Optional.empty();
            }
          }
        };
  }

  private static List<ItemStack> roots(PlayerEntity player) {
    List<ItemStack> roots = new ArrayList<>();
    for (int slot = 0; slot < player.inventory.getContainerSize(); slot++)
      roots.add(player.inventory.getItem(slot));
    try {
      var method = Curios.INVENTORY.get(player.getClass());
      Object optional = !method.isPresent() ? null : method.get().invoke(null, player);
      if (optional instanceof Optional<?> value && value.isPresent()) {
        var getter = Curios.EQUIPPED.get(value.get().getClass());
        Object equipped = !getter.isPresent() ? null : getter.get().invoke(value.get());
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

  public static Map<String, ItemStack> snapshots(PlayerEntity player, List<String> ids) {
    Map<String, ItemStack> result = new java.util.LinkedHashMap<>();
    Set<String> missing = new LinkedHashSet<>(ids);
    for (int slot = 0; slot < player.inventory.getContainerSize(); slot++) {
      if (slot >= 36 && slot != 40) continue;
      ItemStack stack = player.inventory.getItem(slot);
      String id = Tools.id(stack);
      if (!stack.isEmpty() && missing.contains(id) && eligible(stack, id)) {
        result.put(id, stack.copy());
        missing.remove(id);
      }
    }
    if (!missing.isEmpty())
      for (Located entry : find(player)) {
        String id = Tools.id(entry.tool);
        if (missing.remove(id)) result.put(id, entry.tool.copy());
        if (missing.isEmpty()) break;
      }
    return result;
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
        try {
          preserve(player, returnTool(player, entry, extracted, false).remainder);
        } finally {
          changed(player);
        }
      }
    }
    player.displayClientMessage(
        new TranslationTextComponent("message.wrenchbridge.source_missing"), true);
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
          if (extracted.isEmpty()) valid = false;
          Returned returned = returnTool(player, entry, extracted, true);
          ItemStack remainder = returned.remainder;
          if (returned.failed) valid = false;
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

  private static final class Returned {
    final ItemStack remainder;
    final boolean failed;

    Returned(ItemStack remainder, boolean failed) {
      this.remainder = remainder;
      this.failed = failed;
    }
  }

  /**
   * Preserve the post-action tool even if a foreign handler mutates the insert argument then
   * throws.
   */
  private static Returned returnTool(
      ServerPlayerEntity player, Located entry, ItemStack extracted, boolean menu) {
    if (extracted.isEmpty()) return new Returned(ItemStack.EMPTY, false);
    ItemStack backup = extracted.copy();
    int before = -1;
    try {
      if (roots(player).stream().noneMatch(root -> root == entry.root)
          && !(menu && player.inventory.getCarried() == entry.root))
        return new Returned(backup, true);
      before = matchingCount(entry.inventory.getStackInSlot(entry.slot), backup);
      return new Returned(
          java.util.Objects.requireNonNull(
              entry.inventory.insertItem(entry.slot, extracted, false)),
          false);
    } catch (RuntimeException error) {
      // Some handlers accept the item before a failing callback. Do not duplicate that accepted
      // copy.
      if (before >= 0)
        try {
          int accepted =
              Math.max(
                  0, matchingCount(entry.inventory.getStackInSlot(entry.slot), backup) - before);
          backup.shrink(Math.min(backup.getCount(), accepted));
        } catch (RuntimeException unreadable) {
          error.addSuppressed(unreadable);
        }
      WrenchBridge.LOG.warn(
          "Container declined borrowed tool return; preserving the unreturned remainder", error);
      return new Returned(backup, true);
    }
  }

  private static int matchingCount(ItemStack stack, ItemStack expected) {
    if (stack.isEmpty() || expected.isEmpty()) return 0;
    ItemStack a = stack.copy(), b = expected.copy();
    a.setCount(1);
    b.setCount(1);
    return ItemStack.matches(a, b) ? stack.getCount() : 0;
  }

  private static void preserve(ServerPlayerEntity player, ItemStack remainder) {
    if (!remainder.isEmpty() && !player.inventory.add(remainder)) player.drop(remainder, false);
  }

  private static void changed(ServerPlayerEntity player) {
    SourceViewPacket.changed(player);
    player.inventory.setChanged();
    player.inventoryMenu.broadcastChanges();
    player.containerMenu.broadcastChanges();
  }
}
