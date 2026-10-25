package dev.wrenchbridge;

import dev.wrenchbridge.core.RequestBudget;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Main-thread ingress guards. Pending refreshes contain identities, never cached inventories. */
public final class ServerRequests {
  private static final Map<UUID, ModeState> MODES = new HashMap<>();
  private static final Map<UUID, Integer> CATALOG_LAST = new HashMap<>();
  private static final Map<UUID, Boolean> CATALOG_PENDING = new HashMap<>();

  private static final class ModeState {
    final RequestBudget budget;
    ItemStack held;
    ModePacket request;
    String data;

    ModeState(int tick) {
      budget = new RequestBudget(8, 2, tick);
    }
  }

  private ServerRequests() {}

  private static String profile(ItemStack held) {
    return VirtualModes.item(held) + "\n" + VirtualModes.mode(held);
  }

  private static boolean same(ModePacket a, ModePacket b) {
    return b != null
        && a.itemId().equals(b.itemId())
        && a.mode().equals(b.mode())
        && a.hand() == b.hand();
  }

  static boolean mode(ModePacket packet, EntityPlayerMP player) {
    if (player.isSpectator()) return false;
    ItemStack held = player.getHeldItem(packet.hand());
    if (!(held.getItem() instanceof UniversalWrenchItem)) return false;
    int now = player.getServer().getTickCounter();
    ModeState state = MODES.computeIfAbsent(player.getUniqueID(), id -> new ModeState(now));
    if (held == state.held && same(packet, state.request) && profile(held).equals(state.data))
      return false;
    if (!state.budget.acquire(now)) return false;
    if (!ModePacket.select(packet, player)) return false;
    state.held = held;
    state.request = packet;
    state.data = profile(held);
    return true;
  }

  static CatalogPacket.Reply catalog(EntityPlayerMP player) {
    if (player.isSpectator() || Tools.universalHand(player) == null) return null;
    int now = player.getServer().getTickCounter();
    Integer last = CATALOG_LAST.get(player.getUniqueID());
    if (last != null && now - last < 10) {
      CATALOG_PENDING.put(player.getUniqueID(), true);
      return null;
    }
    CATALOG_LAST.put(player.getUniqueID(), now);
    CATALOG_PENDING.remove(player.getUniqueID());
    try {
      return new CatalogPacket.Reply(StoredTools.catalog(player));
    } catch (RuntimeException | LinkageError error) {
      // Coalesced work runs on the server tick, outside the network handler's failure boundary.
      WrenchBridge.LOG.debug("Native tool catalog was unavailable", error);
      return new CatalogPacket.Reply(java.util.Collections.emptyList());
    }
  }

  static void tick(net.minecraft.server.MinecraftServer server) {
    if (!CATALOG_PENDING.isEmpty()) {
      for (UUID id : new java.util.ArrayList<>(CATALOG_PENDING.keySet())) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(id);
        if (player == null || player.isSpectator() || Tools.universalHand(player) == null) {
          CATALOG_PENDING.remove(id);
          continue;
        }
        CatalogPacket.Reply reply = catalog(player);
        if (reply != null && player.connection != null) ModePacket.CHANNEL.sendTo(reply, player);
      }
    }
    SourceViewPacket.tick(server);
  }

  static void forget(UUID player) {
    MODES.remove(player);
    CATALOG_LAST.remove(player);
    CATALOG_PENDING.remove(player);
  }

  static void clear() {
    MODES.clear();
    CATALOG_LAST.clear();
    CATALOG_PENDING.clear();
  }
}
