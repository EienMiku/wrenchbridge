package dev.wrenchbridge.client;

import dev.wrenchbridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * Shared native callback views, cached per client tick. Physical hand/inventory getters stay
 * untouched.
 */
public final class NativeClientViews {
  public static final class Projection {
    private final Hand hand;
    private final ItemStack held, tool;

    public Projection(Hand hand, ItemStack held, ItemStack tool) {
      this.hand = hand;
      this.held = held;
      this.tool = tool;
    }

    public Hand hand() {
      return hand;
    }

    public ItemStack held() {
      return held;
    }

    public ItemStack tool() {
      return tool;
    }
  }

  private static final class Cached {
    final ItemStack held, source, view;
    final Object data;

    Cached(ItemStack held, Object data, ItemStack source, ItemStack view) {
      this.held = held;
      this.data = data;
      this.source = source;
      this.view = view;
    }
  }

  private static final Cached[] cached = new Cached[2];
  private static PlayerEntity owner;

  private NativeClientViews() {}

  public static void tick(PlayerEntity player) {
    owner = player;
    cached[0] = cached[1] = null;
    if (player == null) {
      return;
    }
    for (Hand hand : Hand.values()) {
      ItemStack held = player.getItemInHand(hand);
      if (VirtualModes.isNative(held)) SourceViewClient.nativeSource(held);
    }
  }

  public static ItemStack view(PlayerEntity player, Hand hand) {
    if (player != Minecraft.getInstance().player) return ItemStack.EMPTY;
    if (owner != player) {
      owner = player;
      cached[0] = cached[1] = null;
    }
    ItemStack held = player.getItemInHand(hand);
    if (!VirtualModes.isNative(held)) return ItemStack.EMPTY;
    ItemStack source = SourceViewClient.nativeSource(held);
    Object data = held.getTag();
    Cached previous = cached[hand.ordinal()];
    if (previous != null
        && previous.held == held
        && previous.data == data
        && previous.source == source) return previous.view;
    ItemStack view = NativeToolViews.create(player, held, source);
    cached[hand.ordinal()] = new Cached(held, data, source, view);
    return view;
  }

  public static ItemStack itemInHand(PlayerEntity player, Hand hand, String nativeItem) {
    ItemStack actual = player.getItemInHand(hand);
    if (!NativeToolViews.selected(actual, nativeItem)) return actual;
    ItemStack view = view(player, hand);
    return view.isEmpty() ? actual : view;
  }

  /** Native main-hand input handlers can target an explicitly selected offhand universal wrench. */
  public static Projection input(PlayerEntity player, String nativeItem) {
    if (Tools.id(player.getMainHandItem()).equals(nativeItem)) return null; // Native behavior wins.
    Hand hand = Tools.universalHand(player);
    if (hand == null || !NativeToolViews.selected(player.getItemInHand(hand), nativeItem))
      return null;
    ItemStack view = view(player, hand);
    return view.isEmpty() ? null : new Projection(hand, player.getItemInHand(hand), view);
  }
}
