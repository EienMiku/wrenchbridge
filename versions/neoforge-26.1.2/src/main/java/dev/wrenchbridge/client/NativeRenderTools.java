package dev.wrenchbridge.client;

import dev.wrenchbridge.CatalogPacket;
import dev.wrenchbridge.ConfiguratorViews;
import dev.wrenchbridge.Tools;
import dev.wrenchbridge.VirtualModes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Cache once per client tick; bag presence comes from the existing bounded server catalog API. */
public final class NativeRenderTools {
  private record View(ItemStack held, String mode, ItemStack nativeStack) {}

  private static final Set<String> stored = new HashSet<>();
  private static final View[] views = new View[2];
  private static Player owner;
  private static int ticks, refreshAt, replyAt = -100;

  private NativeRenderTools() {}

  public static void catalog(List<String> rows) {
    stored.clear();
    for (String row : rows) {
      int separator = row.indexOf('|');
      if (separator > 0) stored.add(row.substring(0, separator));
    }
    replyAt = ticks;
  }

  public static void tick(Player player) {
    if (owner != player) {
      owner = player;
      stored.clear();
      views[0] = views[1] = null;
      ticks = refreshAt = 0;
      replyAt = -100;
    }
    if (player == null) return;
    ticks++;
    boolean needsCatalog = false;
    for (InteractionHand hand : InteractionHand.values()) {
      ItemStack held = player.getItemInHand(hand);
      if (!ConfiguratorViews.selected(held)) {
        views[hand.ordinal()] = null;
        continue;
      }
      ItemStack source = ItemStack.EMPTY;
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
        if (slot >= 36 && slot != net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND)
          continue;
        ItemStack candidate = player.getInventory().getItem(slot);
        if (!candidate.isEmpty() && Tools.id(candidate).equals(ConfiguratorViews.ITEM)) {
          source = candidate;
          break;
        }
      }
      if (source.isEmpty()) {
        needsCatalog = true;
        if (stored.contains(ConfiguratorViews.ITEM) && ticks - replyAt <= 40) {
          var id = Identifier.parse(ConfiguratorViews.ITEM);
          if (BuiltInRegistries.ITEM.containsKey(id))
            source = new ItemStack(BuiltInRegistries.ITEM.get(id).orElseThrow().value());
        }
      }
      views[hand.ordinal()] =
          new View(held, VirtualModes.mode(held), ConfiguratorViews.create(player, held, source));
    }
    if (needsCatalog && ticks >= refreshAt) {
      refreshAt = ticks + 20;
      ClientPacketDistributor.sendToServer(new CatalogPacket.Request());
    }
  }

  public static ItemStack itemInHand(Player player, InteractionHand hand) {
    ItemStack actual = player.getItemInHand(hand);
    View view = owner == player ? views[hand.ordinal()] : null;
    return view != null
            && view.held == actual
            && view.mode.equals(VirtualModes.mode(actual))
            && !view.nativeStack.isEmpty()
        ? view.nativeStack
        : actual;
  }
}
