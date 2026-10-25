package dev.wrenchbridge.client;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import dev.wrenchbridge.core.SourceViewLimits;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
    Minecraft game = Minecraft.getInstance();
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
    Minecraft game = Minecraft.getInstance();
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
    if (!Minecraft.getInstance().isSameThread()) return ItemStack.EMPTY;
    try {
      return resolve(wrench);
    } catch (RuntimeException | LinkageError error) {
      return ItemStack.EMPTY;
    }
  }

  private static ItemStack resolve(ItemStack wrench) {
    if (inside || !VirtualModes.isNative(wrench)) return ItemStack.EMPTY;
    owner();
    Minecraft game = Minecraft.getInstance();
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
      for (int slot = 0; slot < game.player.getInventory().getContainerSize(); slot++) {
        if (slot >= 36 && slot != net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND)
          continue;
        ItemStack candidate = game.player.getInventory().getItem(slot);
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
      Item.TooltipContext context,
      java.util.function.Consumer<Component> lines,
      net.minecraft.world.item.TooltipFlag flag) {
    if (!Minecraft.getInstance().isSameThread()) return;
    if (inside
        || !VirtualModes.isNative(wrench)
        || !SharingPreferences.enabled(ClientOption.SOURCE_DETAILS)) return;
    Minecraft game = Minecraft.getInstance();
    if (game.player == null) return;
    ItemStack source = view(wrench);
    focused = VirtualModes.item(wrench);
    if (source.isEmpty()) {
      lines.accept(
          Component.translatable(
                  cached.containsKey(focused)
                      ? "tooltip.wrenchbridge.source_unavailable"
                      : "tooltip.wrenchbridge.source_waiting")
              .withStyle(ChatFormatting.DARK_GRAY));
      return;
    }
    inside = true;
    try {
      List<Component> details = source.copy().getTooltipLines(context, game.player, flag);
      lines.accept(Component.empty());
      lines.accept(
          Component.translatable("tooltip.wrenchbridge.source_details")
              .withStyle(ChatFormatting.DARK_GRAY));
      for (Component line : details) lines.accept(line);
    } catch (RuntimeException | LinkageError error) {
      lines.accept(
          Component.translatable("tooltip.wrenchbridge.source_unavailable")
              .withStyle(ChatFormatting.DARK_GRAY));
    } finally {
      inside = false;
    }
  }

  public static boolean barVisible(ItemStack wrench) {
    ItemStack source = view(wrench);
    if (source.isEmpty() || inside) return false;
    inside = true;
    try {
      return source.isBarVisible();
    } catch (RuntimeException | LinkageError error) {
      return false;
    } finally {
      inside = false;
    }
  }

  public static int barWidth(ItemStack wrench) {
    ItemStack source = view(wrench);
    if (source.isEmpty() || inside) return 0;
    inside = true;
    try {
      return Math.max(0, Math.min(13, source.getBarWidth()));
    } catch (RuntimeException | LinkageError error) {
      return 0;
    } finally {
      inside = false;
    }
  }

  public static int barColor(ItemStack wrench) {
    ItemStack source = view(wrench);
    if (source.isEmpty() || inside) return 0;
    inside = true;
    try {
      return source.getBarColor();
    } catch (RuntimeException | LinkageError error) {
      return 0;
    } finally {
      inside = false;
    }
  }

  public static void register(net.neoforged.bus.api.IEventBus bus) {
    bus.addListener(
        (net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent event) ->
            event.register(WrenchBridge.WRENCH.get(), SourceViewClient::decorate));
  }

  public static boolean decorate(
      net.minecraft.client.gui.GuiGraphicsExtractor graphics,
      net.minecraft.client.gui.Font font,
      ItemStack wrench,
      int x,
      int y) {
    ItemStack source = view(wrench);
    if (source.isEmpty() || inside) return false;
    inside = true;
    try {
      net.neoforged.neoforge.client.ItemDecoratorHandler.of(source)
          .render(graphics, font, source.copy(), x, y);
      return true;
    } catch (RuntimeException | LinkageError error) {
      return true;
    } finally {
      inside = false;
    }
  }
}
