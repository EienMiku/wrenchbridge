package dev.wrenchbridge.client;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import dev.wrenchbridge.core.SourceViewLimits;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

/** Read-only source display; snapshots never occupy a player or container slot. */
public final class SourceViewClient {
  private static final class Cached {
    final ItemStack source;
    final int received;

    Cached(ItemStack source, int received) {
      this.source = source;
      this.received = received;
    }
  }

  private static Object connection, owner;
  private static int epoch, ticks, nextRequest;
  private static boolean inside;
  private static String focused;
  private static final Map<String, Integer> observed = new LinkedHashMap<String, Integer>();
  private static final Map<String, Cached> cached = new LinkedHashMap<String, Cached>();
  private static final Map<String, ItemStack> views = new HashMap<String, ItemStack>();

  private SourceViewClient() {}

  private static void owner() {
    Minecraft game = Minecraft.getMinecraft();
    Object current = game.player == null ? null : game.getConnection();
    if (connection != current || owner != game.player) {
      connection = current;
      owner = game.player;
      epoch++;
      ticks = nextRequest = 0;
      observed.clear();
      cached.clear();
      views.clear();
      focused = null;
    }
  }

  public static void tick() {
    owner();
    ticks++;
    views.clear();
    if (connection == null) {
      observed.clear();
      cached.clear();
      return;
    }
    observed
        .entrySet()
        .removeIf(entry -> ticks - entry.getValue() > SourceViewLimits.REFRESH_TICKS * 2);
    cached
        .entrySet()
        .removeIf(entry -> ticks - entry.getValue().received > SourceViewLimits.EXPIRE_TICKS * 2);
    if (ticks < nextRequest || observed.isEmpty()) return;
    List<String> ids = new ArrayList<String>(observed.keySet());
    Collections.sort(
        ids,
        (a, b) ->
            Integer.compare(
                cached.containsKey(a) ? cached.get(a).received : Integer.MIN_VALUE,
                cached.containsKey(b) ? cached.get(b).received : Integer.MIN_VALUE));
    if (focused != null && ids.remove(focused)) ids.add(0, focused);
    if (ids.size() > SourceViewLimits.MAX_SOURCES)
      ids = new ArrayList<String>(ids.subList(0, SourceViewLimits.MAX_SOURCES));
    nextRequest = ticks + SourceViewLimits.REFRESH_TICKS;
    SourceViewPacket.request(epoch, ids);
  }

  public static void receive(SourceViewPacket.Reply packet) {
    owner();
    Minecraft game = Minecraft.getMinecraft();
    if (connection == null || packet.token != epoch) return;
    for (SourceViewPacket.Entry entry : packet.entries)
      if (observed.containsKey(entry.id)) {
        ItemStack source = SourceViewPacket.decodeStack(game.player, entry.data);
        try {
          if (!source.isEmpty()
              && (!Tools.candidate(source)
                  || source.getItem() instanceof UniversalWrenchItem
                  || !Tools.id(source).equals(entry.id))) source = ItemStack.EMPTY;
        } catch (RuntimeException | LinkageError error) {
          source = ItemStack.EMPTY;
        }
        cached.put(entry.id, new Cached(source, ticks));
        views.remove(entry.id);
      }
    trim(cached);
  }

  private static <T> void trim(Map<String, T> map) {
    while (map.size() > SourceViewLimits.MAX_CACHED) map.remove(map.keySet().iterator().next());
  }

  public static ItemStack view(ItemStack wrench) {
    if (!SharingPreferences.enabled(ClientOption.SOURCE_DETAILS)) return ItemStack.EMPTY;
    return nativeSource(wrench);
  }

  public static ItemStack nativeSource(ItemStack wrench) {
    if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) return ItemStack.EMPTY;
    try {
      return resolve(wrench);
    } catch (RuntimeException | LinkageError error) {
      return ItemStack.EMPTY;
    }
  }

  private static ItemStack resolve(ItemStack wrench) {
    if (inside || !VirtualModes.isNative(wrench)) return ItemStack.EMPTY;
    owner();
    Minecraft game = Minecraft.getMinecraft();
    if (game.player == null || connection == null) return ItemStack.EMPTY;
    String id = VirtualModes.item(wrench);
    if (id.isEmpty() || id.length() > SourceViewLimits.MAX_ID_BYTES) return ItemStack.EMPTY;
    observed.put(id, ticks);
    trim(observed);
    ItemStack existing = views.get(id);
    if (existing != null) return existing;
    Cached entry = cached.get(id);
    ItemStack source = ItemStack.EMPTY;
    if (entry != null && ticks - entry.received <= SourceViewLimits.EXPIRE_TICKS)
      source = entry.source;
    else
      for (int slot = 0; slot < game.player.inventory.getSizeInventory(); slot++) {
        if (slot >= 36 && slot != 40) continue;
        ItemStack candidate = game.player.inventory.getStackInSlot(slot);
        if (!candidate.isEmpty()
            && !(candidate.getItem() instanceof UniversalWrenchItem)
            && Tools.candidate(candidate)
            && Tools.id(candidate).equals(id)) {
          source = candidate;
          break;
        }
      }
    ItemStack display = source.copy();
    views.put(id, display);
    return display;
  }

  public static void appendTooltip(
      ItemStack wrench,
      net.minecraft.world.World context,
      List<String> lines,
      net.minecraft.client.util.ITooltipFlag flag) {
    if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) return;
    if (inside
        || !VirtualModes.isNative(wrench)
        || !SharingPreferences.enabled(ClientOption.SOURCE_DETAILS)) return;
    Minecraft game = Minecraft.getMinecraft();
    if (game.player == null) return;
    ItemStack source = view(wrench);
    focused = VirtualModes.item(wrench);
    if (source.isEmpty()) {
      lines.add(
          TextFormatting.DARK_GRAY
              + net.minecraft.client.resources.I18n.format(
                  cached.containsKey(focused)
                      ? "tooltip.wrenchbridge.source_unavailable"
                      : "tooltip.wrenchbridge.source_waiting"));
      return;
    }
    inside = true;
    try {
      List<String> details = source.copy().getTooltip(game.player, flag);
      lines.add("");
      lines.add(
          TextFormatting.DARK_GRAY
              + net.minecraft.client.resources.I18n.format("tooltip.wrenchbridge.source_details"));
      for (String line : details) lines.add(line);
    } catch (RuntimeException | LinkageError error) {
      lines.add(
          TextFormatting.DARK_GRAY
              + net.minecraft.client.resources.I18n.format(
                  "tooltip.wrenchbridge.source_unavailable"));
    } finally {
      inside = false;
    }
  }

  public static boolean barVisible(ItemStack wrench) {
    ItemStack source = view(wrench);
    if (source.isEmpty() || inside) return false;
    inside = true;
    try {
      return source.getItem().showDurabilityBar(source);
    } catch (RuntimeException | LinkageError error) {
      return false;
    } finally {
      inside = false;
    }
  }

  public static double barFraction(ItemStack wrench) {
    ItemStack source = view(wrench);
    if (source.isEmpty() || inside) return 1;
    inside = true;
    try {
      double result = source.getItem().getDurabilityForDisplay(source);
      return Double.isFinite(result) ? Math.max(0, Math.min(1, result)) : 1;
    } catch (RuntimeException | LinkageError error) {
      return 1;
    } finally {
      inside = false;
    }
  }

  public static int barColor(ItemStack wrench) {
    ItemStack source = view(wrench);
    if (source.isEmpty() || inside) return 0;
    inside = true;
    try {
      return source.getItem().getRGBDurabilityForDisplay(source);
    } catch (RuntimeException | LinkageError error) {
      return 0;
    } finally {
      inside = false;
    }
  }
}
