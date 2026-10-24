package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import io.netty.buffer.ByteBuf;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

/** Native AE2 screens with a real carried donor, including a capability container slot. */
public final class ContainerMenus {
  private static final Map<UUID, Entry> OPEN = new HashMap<>();
  private static final Map<ItemStack, Entry> ALIASES = new IdentityHashMap<>();
  private static final ThreadLocal<ItemStack> ACTIVE = new ThreadLocal<>();
  private static ItemStack clientView = ItemStack.EMPTY;

  private static final class Entry {
    final EntityPlayerMP player;
    final ItemStack inventoryTool;
    final StoredTools.MenuSource stored;

    Entry(EntityPlayerMP player, ItemStack inventoryTool, StoredTools.MenuSource stored) {
      this.player = player;
      this.inventoryTool = inventoryTool;
      this.stored = stored;
    }

    boolean available() {
      if (stored != null) return stored.available();
      for (int i = 0; i < player.inventory.getSizeInventory(); i++)
        if (player.inventory.getStackInSlot(i) == inventoryTool) return true;
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
    ModePacket.CHANNEL.registerMessage(SnapshotHandler.class, Snapshot.class, 3, Side.CLIENT);
  }

  public static EnumActionResult open(EntityPlayerMP player, EnumHand hand, RayTraceResult hit) {
    String id = VirtualModes.item(player.getHeldItem(hand));
    ItemStack source = StoredTools.peek(player, id);
    if (source.isEmpty() || !ClassProbe.is(source.getItem(), "appeng.items.tools.ToolNetworkTool"))
      return EnumActionResult.FAIL;
    StoredTools.MenuSource stored = null;
    boolean ordinary = false;
    for (int i = 0; i < player.inventory.getSizeInventory(); i++)
      if (player.inventory.getStackInSlot(i) == source) {
        ordinary = true;
        break;
      }
    if (!ordinary) stored = StoredTools.menuSource(player, id);
    if (!ordinary && stored == null) return EnumActionResult.FAIL;
    forget(player);
    Entry entry = new Entry(player, ordinary ? source : ItemStack.EMPTY, stored);
    OPEN.put(player.getUniqueID(), entry);
    ModePacket.CHANNEL.sendTo(new Snapshot(source), player);
    return entry.use(
        () ->
            WrenchContext.runServer(
                player,
                hand,
                entry.view(),
                () -> {
                  try {
                    BlockPos pos = hit == null ? BlockPos.ORIGIN : hit.getBlockPos();
                    EnumFacing face = hit == null ? null : hit.sideHit;
                    float x = hit == null ? 0F : (float) (hit.hitVec.x - pos.getX()),
                        y = hit == null ? 0F : (float) (hit.hitVec.y - pos.getY()),
                        z = hit == null ? 0F : (float) (hit.hitVec.z - pos.getZ());
                    Class.forName("appeng.items.tools.ToolNetworkTool")
                        .getMethod(
                            "serverSideToolLogic",
                            ItemStack.class,
                            EntityPlayer.class,
                            EnumHand.class,
                            net.minecraft.world.World.class,
                            BlockPos.class,
                            EnumFacing.class,
                            float.class,
                            float.class,
                            float.class)
                        .invoke(
                            entry.view().getItem(),
                            entry.view(),
                            player,
                            hand,
                            player.world,
                            pos,
                            face,
                            x,
                            y,
                            z);
                    return EnumActionResult.SUCCESS;
                  } catch (ReflectiveOperationException | RuntimeException error) {
                    forget(player);
                    WrenchBridge.LOG.warn("Native network menu rejected", error);
                    return EnumActionResult.FAIL;
                  }
                }),
        EnumActionResult.FAIL);
  }

  public static ItemStack tool(EntityPlayer player) {
    if (player.world.isRemote) return clientView;
    Entry e = OPEN.get(player.getUniqueID());
    return e != null && e.available() ? e.view() : ItemStack.EMPTY;
  }

  public static Object guiObject(
      ItemStack held, EntityPlayer player, net.minecraft.world.World world, BlockPos pos) {
    if (!(held.getItem() instanceof UniversalWrenchItem)) return null;
    ItemStack real = tool(player);
    if (real.isEmpty()) return null;
    try {
      Object result =
          Class.forName("appeng.api.implementations.guiobjects.IGuiItem")
              .getMethod(
                  "getGuiObject", ItemStack.class, net.minecraft.world.World.class, BlockPos.class)
              .invoke(real.getItem(), real, world, pos);
      if (!world.isRemote) ALIASES.put(real, OPEN.get(player.getUniqueID()));
      return result;
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }

  public static ItemStack nativeStack(ItemStack original) {
    Entry e = ALIASES.get(original);
    return e != null && e.available() ? e.view() : original;
  }

  public static EntityPlayer menuPlayer(Object menu) {
    try {
      return ((InventoryPlayer)
              Class.forName("appeng.container.AEBaseContainer")
                  .getMethod("getPlayerInv")
                  .invoke(menu))
          .player;
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }

  public static ItemStack current(InventoryPlayer inventory) {
    ItemStack real = tool(inventory.player);
    return real.isEmpty() ? inventory.getCurrentItem() : real;
  }

  public static <T> T action(Object menu, EntityPlayer player, Supplier<T> action, T rejected) {
    if (player.world.isRemote) return action.get();
    Entry e = OPEN.get(player.getUniqueID());
    return e == null ? action.get() : e.use(action, rejected);
  }

  public static void tick(EntityPlayerMP player) {
    Entry e = OPEN.get(player.getUniqueID());
    if (e == null) return;
    String menu = player.openContainer.getClass().getName();
    if (!menu.startsWith("appeng.container.")) forget(player);
    else if (!e.available()) {
      player.closeScreen();
      forget(player);
    }
  }

  public static void forget(EntityPlayerMP player) {
    Entry old = OPEN.remove(player.getUniqueID());
    if (old != null) ALIASES.values().removeIf(e -> e == old);
  }

  public static final class Snapshot implements IMessage {
    ItemStack tool = ItemStack.EMPTY;

    public Snapshot() {}

    Snapshot(ItemStack tool) {
      this.tool = tool.copy();
    }

    public void fromBytes(ByteBuf buffer) {
      tool = ByteBufUtils.readItemStack(buffer);
    }

    public void toBytes(ByteBuf buffer) {
      ByteBufUtils.writeItemStack(buffer, tool);
    }
  }

  public static final class SnapshotHandler implements IMessageHandler<Snapshot, IMessage> {
    public IMessage onMessage(Snapshot packet, MessageContext context) {
      dev.wrenchbridge.client.ClientHooks.nativeSnapshot(() -> clientView = packet.tool);
      return null;
    }
  }
}
