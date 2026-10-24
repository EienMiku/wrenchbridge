package dev.wrenchbridge.client;

import dev.wrenchbridge.CatalogPacket;
import dev.wrenchbridge.ConfiguratorViews;
import dev.wrenchbridge.Tools;
import dev.wrenchbridge.VirtualModes;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;

/** Cache once per client tick; bag presence comes from the existing bounded server catalog API. */
public final class NativeRenderTools {
  private static final class View {
    public final ItemStack held;
    public final String mode;
    public final ItemStack nativeStack;

    public View(ItemStack held, String mode, ItemStack nativeStack) {
      this.held = held;
      this.mode = mode;
      this.nativeStack = nativeStack;
    }

    public ItemStack held() {
      return held;
    }

    public String mode() {
      return mode;
    }

    public ItemStack nativeStack() {
      return nativeStack;
    }

    public boolean equals(Object o) {
      if (!(o instanceof View)) return false;
      View other = (View) o;
      return java.util.Objects.equals(held, other.held)
          && java.util.Objects.equals(mode, other.mode)
          && java.util.Objects.equals(nativeStack, other.nativeStack);
    }

    public int hashCode() {
      return java.util.Objects.hash(held, mode, nativeStack);
    }
  }

  private static final Set<String> stored = new HashSet<>();
  private static final View[] views = new View[2];
  private static PlayerEntity owner;
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

  public static void tick(PlayerEntity player) {
    if (owner != player) {
      owner = player;
      stored.clear();
      views[0] = views[1] = null;
      ticks = refreshAt = 0;
      replyAt = -100;
    }
    if (player == null) return;
    if (!SharingPreferences.enabled(ClientOption.CONFIGURATOR_OVERLAY)) {
      views[0] = views[1] = null;
      stored.clear();
      replyAt = -100;
      refreshAt = ticks;
      return;
    }
    ticks++;
    boolean needsCatalog = false;
    for (Hand hand : Hand.values()) {
      ItemStack held = player.getItemInHand(hand);
      if (!ConfiguratorViews.selected(held)) {
        views[hand.ordinal()] = null;
        continue;
      }
      ItemStack source = ItemStack.EMPTY;
      for (int slot = 0; slot < player.inventory.getContainerSize(); slot++) {
        if (slot >= 36 && slot != 40) continue;
        ItemStack candidate = player.inventory.getItem(slot);
        if (!candidate.isEmpty() && Tools.id(candidate).equals(ConfiguratorViews.ITEM)) {
          source = candidate;
          break;
        }
      }
      if (source.isEmpty()) {
        needsCatalog = true;
        if (stored.contains(ConfiguratorViews.ITEM) && ticks - replyAt <= 40) {
          var id = new ResourceLocation(ConfiguratorViews.ITEM);
          if (net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id))
            source =
                new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id));
        }
      }
      views[hand.ordinal()] =
          new View(held, VirtualModes.mode(held), ConfiguratorViews.create(player, held, source));
    }
    if (needsCatalog && ticks >= refreshAt) {
      refreshAt = ticks + 20;
      CatalogPacket.request();
    }
  }

  public static ItemStack itemInHand(PlayerEntity player, Hand hand) {
    ItemStack actual = player.getItemInHand(hand);
    if (!SharingPreferences.enabled(ClientOption.CONFIGURATOR_OVERLAY)) return actual;
    View view = owner == player ? views[hand.ordinal()] : null;
    return view != null
            && view.held == actual
            && view.mode.equals(VirtualModes.mode(actual))
            && !view.nativeStack.isEmpty()
        ? view.nativeStack
        : actual;
  }
}
