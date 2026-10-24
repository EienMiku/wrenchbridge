package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.PacketDistributor;

/** Native AE2 screens with a real carried donor, including a capability container slot. */
public final class ContainerMenus {
  private static final Map<UUID, Entry> OPEN = new HashMap<>();
  private static final Map<ItemStack, Entry> ALIASES = new IdentityHashMap<>();
  private static final ThreadLocal<ItemStack> ACTIVE = new ThreadLocal<>();
  private static ItemStack clientView = ItemStack.EMPTY;

  private static final class Entry {
    final ServerPlayerEntity player;
    final ItemStack inventoryTool;
    final StoredTools.MenuSource stored;

    Entry(ServerPlayerEntity player, ItemStack inventoryTool, StoredTools.MenuSource stored) {
      this.player = player;
      this.inventoryTool = inventoryTool;
      this.stored = stored;
    }

    boolean available() {
      if (stored != null) return stored.available();
      for (int i = 0; i < player.inventory.getContainerSize(); i++)
        if (player.inventory.getItem(i) == inventoryTool) return true;
      return false;
    }

    ItemStack view() {
      return ACTIVE.get() != null ? ACTIVE.get() : stored != null ? stored.view() : inventoryTool;
    }

    <T> T use(Supplier<T> action, T rejected) {
      if (!available()) return rejected;
      if (stored != null) return stored.use(tool -> with(tool, action), rejected);
      return with(inventoryTool, action);
    }
  }

  private ContainerMenus() {}

  private static <T> T with(ItemStack tool, Supplier<T> action) {
    ItemStack previous = ACTIVE.get();
    ACTIVE.set(tool);
    try {
      return action.get();
    } finally {
      if (previous == null) ACTIVE.remove();
      else ACTIVE.set(previous);
    }
  }

  public static void register() {
    ModePacket.CHANNEL
        .messageBuilder(Snapshot.class, 3, NetworkDirection.PLAY_TO_CLIENT)
        .encoder((p, b) -> b.writeItem(p.tool))
        .decoder(b -> new Snapshot(b.readItem()))
        .consumer(
            (p, c) -> {
              c.get()
                  .enqueueWork(
                      () ->
                          dev.wrenchbridge.client.ClientHooks.nativeSnapshot(
                              () -> clientView = p.tool));
              c.get().setPacketHandled(true);
            })
        .add();
  }

  public static ActionResultType open(
      ServerPlayerEntity player, Hand hand, BlockRayTraceResult hit) {
    String id = VirtualModes.item(player.getItemInHand(hand));
    ItemStack source = StoredTools.peek(player, id);
    if (source.isEmpty() || !ClassProbe.is(source.getItem(), "appeng.items.tools.NetworkToolItem"))
      return ActionResultType.FAIL;
    StoredTools.MenuSource stored = null;
    boolean ordinary = false;
    for (int i = 0; i < player.inventory.getContainerSize(); i++)
      if (player.inventory.getItem(i) == source) {
        ordinary = true;
        break;
      }
    if (!ordinary) stored = StoredTools.menuSource(player, id);
    if (!ordinary && stored == null) return ActionResultType.FAIL;
    forget(player);
    Entry entry = new Entry(player, ordinary ? source : ItemStack.EMPTY, stored);
    OPEN.put(player.getUUID(), entry);
    ModePacket.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(source));
    return entry.use(
        () ->
            WrenchContext.runServer(
                player,
                hand,
                entry.view(),
                () -> {
                  try {
                    if (hit != null) {
                      ClassProbe.compatibleMethod(
                              entry.view().getItem().getClass(),
                              "serverSideToolLogic",
                              VirtualModes.context(player, hand, hit, entry.view()))
                          .invoke(
                              entry.view().getItem(),
                              VirtualModes.context(player, hand, hit, entry.view()));
                    } else {
                      Object locator =
                          Class.forName("appeng.container.ContainerLocator")
                              .getMethod("forHand", PlayerEntity.class, Hand.class)
                              .invoke(null, player, hand);
                      Object type =
                          Class.forName("appeng.container.me.networktool.NetworkToolContainer")
                              .getField("TYPE")
                              .get(null);
                      ClassProbe.compatibleMethod(
                              Class.forName("appeng.container.ContainerOpener"),
                              "openContainer",
                              type,
                              player,
                              locator)
                          .invoke(null, type, player, locator);
                    }
                    return ActionResultType.SUCCESS;
                  } catch (ReflectiveOperationException | RuntimeException error) {
                    forget(player);
                    WrenchBridge.LOG.warn("Native network menu rejected", error);
                    return ActionResultType.FAIL;
                  }
                }),
        ActionResultType.FAIL);
  }

  public static ItemStack tool(PlayerEntity player) {
    if (player.level.isClientSide) return clientView;
    Entry e = OPEN.get(player.getUUID());
    return e != null && e.available() ? e.view() : ItemStack.EMPTY;
  }

  public static Object guiObject(
      ItemStack held, PlayerEntity player, net.minecraft.world.World world, BlockPos pos) {
    if (!(held.getItem() instanceof UniversalWrenchItem)) return null;
    ItemStack real = tool(player);
    if (real.isEmpty()) return null;
    try {
      Object result =
          Class.forName("appeng.api.implementations.guiobjects.IGuiItem")
              .getMethod(
                  "getGuiObject",
                  ItemStack.class,
                  int.class,
                  net.minecraft.world.World.class,
                  BlockPos.class)
              .invoke(real.getItem(), real, -1, world, pos);
      if (!world.isClientSide) ALIASES.put(real, OPEN.get(player.getUUID()));
      return result;
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }

  public static ItemStack nativeStack(ItemStack original) {
    Entry e = ALIASES.get(original);
    return e != null && e.available() ? e.view() : original;
  }

  public static PlayerEntity menuPlayer(Object menu) {
    try {
      return ((PlayerInventory)
              Class.forName("appeng.container.AEBaseContainer")
                  .getMethod("getPlayerInventory")
                  .invoke(menu))
          .player;
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }

  public static ItemStack current(PlayerInventory inventory) {
    ItemStack real = tool(inventory.player);
    return real.isEmpty() ? inventory.getSelected() : real;
  }

  public static <T> T action(Object menu, PlayerEntity player, Supplier<T> action, T rejected) {
    if (player.level.isClientSide) return action.get();
    Entry e = OPEN.get(player.getUUID());
    return e == null ? action.get() : e.use(action, rejected);
  }

  public static void tick(ServerPlayerEntity player) {
    Entry e = OPEN.get(player.getUUID());
    if (e == null) return;
    String menu = player.containerMenu.getClass().getName();
    if (!menu.startsWith("appeng.container.")) forget(player);
    else if (!e.available()) {
      player.closeContainer();
      forget(player);
    }
  }

  public static void forget(ServerPlayerEntity player) {
    Entry old = OPEN.remove(player.getUUID());
    if (old != null) ALIASES.values().removeIf(e -> e == old);
  }

  public static final class Snapshot {
    final ItemStack tool;

    public Snapshot(ItemStack tool) {
      this.tool = tool.copy();
    }
  }
}
