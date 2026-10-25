package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.NativeModes;
import dev.wrenchbridge.core.SourceViewLimits;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.world.item.ItemStack;

/** Exercises the actual native source lookup and bounded snapshot wire format. */
public final class SourceViewVerification {
  private SourceViewVerification() {}

  private static void check(boolean ok, String text) {
    if (!ok) throw new AssertionError("source display: " + text);
  }

  private static ItemStack item(String id) {
    return new ItemStack(
        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.parse(id)));
  }

  private static void setup(net.minecraft.server.level.ServerPlayer p) {
    p.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    p.getInventory().clearContent();
  }

  public static String verify(net.minecraft.server.level.ServerLevel level) throws Exception {
    net.minecraft.server.level.ServerPlayer
        first = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level),
        second =
            net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                level,
                new com.mojang.authlib.GameProfile(
                    java.util.UUID.fromString("103160e4-c60e-4a39-8bf9-b8ca2c88ecba"),
                    "SourceViewPeer"));
    setup(first);
    setup(second);
    SourceViewPacket.forget(first.getUUID());
    SourceViewPacket.forget(second.getUUID());
    String id = "wrenchbridge:mode_wrench";
    ItemStack original = item(id), later = item(id), inside = item(id);
    original.setDamageValue(11);
    later.setDamageValue(22);
    inside.setDamageValue(33);
    java.lang.reflect.Field handlerField = VerificationMod.class.getDeclaredField("menuHandler");
    handlerField.setAccessible(true);
    Object saved = handlerField.get(null);
    Class<?> handlerType = handlerField.getType();
    var constructor = handlerType.getDeclaredConstructor();
    constructor.setAccessible(true);
    handlerField.set(null, constructor.newInstance());
    try {
      ItemStack bag = item("wrenchbridge_verification:menu_bag");
      net.neoforged.neoforge.items.IItemHandler slots =
          bag.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM);
      check(slots != null, "missing public test container");
      slots.extractItem(0, Integer.MAX_VALUE, false);
      check(slots.insertItem(0, inside, false).isEmpty(), "could not fill public container");
      ItemStack stored = slots.getStackInSlot(0);
      first.getInventory().setItem(0, original);
      first.getInventory().setItem(9, later);
      first.getInventory().setItem(10, bag);
      String mode = NativeModes.current(original.getItem(), original);
      SourceViewPacket.Reply ordinary =
          SourceViewPacket.capture(first, 41, Collections.singletonList(id));
      check(ordinary.entries.size() == 1, "source list");
      ItemStack view = SourceViewPacket.decodeStack(first, ordinary.entries.get(0).data);
      check(view.getDamageValue() == 11, "did not select first inventory source");
      view.setDamageValue(1);
      check(original.getDamageValue() == 11, "display copy modified actual source");
      first.getInventory().setItem(0, ItemStack.EMPTY);
      first.getInventory().setItem(9, ItemStack.EMPTY);
      SourceViewPacket.Reply carried =
          SourceViewPacket.capture(first, 42, Collections.singletonList(id));
      check(
          SourceViewPacket.decodeStack(first, carried.entries.get(0).data).getDamageValue() == 33,
          "container snapshot lost state");
      check(
          slots.getStackInSlot(0) == stored
              && inside.getCount() == 1
              && inside.getDamageValue() == 33,
          "snapshot extracted or modified the carried tool");
      check(
          mode.equals(NativeModes.current(original.getItem(), original)),
          "snapshot changed source mode");
      SourceViewPacket.Reply absent =
          SourceViewPacket.capture(second, 43, Collections.singletonList(id));
      check(absent.entries.get(0).data.length == 0, "another player received this player's tool");
      var wire = Unpooled.buffer();
      try {
        SourceViewPacket.writeRequest(
            wire, new SourceViewPacket.Request(49, Collections.singletonList(id)));
        var request = SourceViewPacket.readRequest(wire);
        check(
            request.token == 49 && request.ids.equals(Collections.singletonList(id)),
            "request codec");
        SourceViewPacket.writeReply(wire, carried);
        var reply = SourceViewPacket.readReply(wire);
        check(
            reply.token == 42
                && SourceViewPacket.decodeStack(first, reply.entries.get(0).data).getDamageValue()
                    == 33,
            "reply codec");
      } finally {
        wire.release();
      }
      check(
          SourceViewPacket.reply(
                  first, new SourceViewPacket.Request(51, Collections.singletonList(id)))
              != null,
          "initial source request rejected");
      check(
          SourceViewPacket.reply(
                  first, new SourceViewPacket.Request(52, Collections.singletonList(id)))
              == null,
          "changed nonce bypassed source rate limit");
      check(
          SourceViewPacket.decodeStack(first, new byte[SourceViewLimits.MAX_STACK_BYTES + 1])
              .isEmpty(),
          "oversized snapshot accepted");
      var invalid = Unpooled.buffer();
      try {
        invalid.writeInt(1);
        invalid.writeByte(SourceViewLimits.MAX_SOURCES + 1);
        boolean rejected = false;
        try {
          SourceViewPacket.readRequest(invalid);
        } catch (IllegalArgumentException expected) {
          rejected = true;
        }
        check(rejected, "oversized request accepted");
      } finally {
        invalid.release();
      }
      slots.extractItem(0, Integer.MAX_VALUE, false);
      check(
          SourceViewPacket.capture(first, 53, Collections.singletonList(id))
                  .entries
                  .get(0)
                  .data
                  .length
              == 0,
          "detached source did not clear view");
    } finally {
      SourceViewPacket.forget(first.getUUID());
      SourceViewPacket.forget(second.getUUID());
      handlerField.set(null, saved);
    }
    return "; source display: first-source priority, full ordinary/container snapshots, no"
        + " extraction or mutation, mode preservation, peer isolation, wire codecs, rate"
        + " limit and bounds";
  }
}
