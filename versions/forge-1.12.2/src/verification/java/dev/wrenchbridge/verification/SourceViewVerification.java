package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.NativeModes;
import dev.wrenchbridge.core.SourceViewLimits;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.item.ItemStack;

/** Exercises the actual native source lookup and bounded snapshot wire format. */
public final class SourceViewVerification {
  private SourceViewVerification() {}

  private static void check(boolean ok, String text) {
    if (!ok) throw new AssertionError("source display: " + text);
  }

  private static ItemStack item(String id) {
    return new ItemStack(
        net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(
            new net.minecraft.util.ResourceLocation(id)));
  }

  private static void setup(net.minecraft.entity.player.EntityPlayerMP p) {
    p.interactionManager.setGameType(net.minecraft.world.GameType.SURVIVAL);
    p.inventory.clear();
  }

  public static String verify(net.minecraft.world.WorldServer level) throws Exception {
    net.minecraft.entity.player.EntityPlayerMP
        first = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level),
        second =
            net.minecraftforge.common.util.FakePlayerFactory.get(
                level,
                new com.mojang.authlib.GameProfile(
                    java.util.UUID.fromString("103160e4-c60e-4a39-8bf9-b8ca2c88ecba"),
                    "SourceViewPeer"));
    setup(first);
    setup(second);
    SourceViewPacket.forget(first.getUniqueID());
    SourceViewPacket.forget(second.getUniqueID());
    String id = "wrenchbridge:mode_wrench";
    ItemStack original = item(id), later = item(id), inside = item(id);
    original.setItemDamage(11);
    later.setItemDamage(22);
    inside.setItemDamage(33);

    try {
      ItemStack bag = item("wrenchbridge:verification_bag");
      net.minecraftforge.items.IItemHandler slots =
          bag.getCapability(
              net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
      check(slots != null, "missing public test container");
      slots.extractItem(0, Integer.MAX_VALUE, false);
      check(slots.insertItem(0, inside, false).isEmpty(), "could not fill public container");
      ItemStack stored = slots.getStackInSlot(0);
      first.inventory.setInventorySlotContents(0, original);
      first.inventory.setInventorySlotContents(9, later);
      first.inventory.setInventorySlotContents(10, bag);
      String mode = NativeModes.current(original.getItem(), original);
      SourceViewPacket.Reply ordinary =
          SourceViewPacket.capture(first, 41, Collections.singletonList(id));
      check(ordinary.entries.size() == 1, "source list");
      ItemStack view = SourceViewPacket.decodeStack(first, ordinary.entries.get(0).data);
      check(view.getItemDamage() == 11, "did not select first inventory source");
      view.setItemDamage(1);
      check(original.getItemDamage() == 11, "display copy modified actual source");
      first.inventory.setInventorySlotContents(0, ItemStack.EMPTY);
      first.inventory.setInventorySlotContents(9, ItemStack.EMPTY);
      SourceViewPacket.Reply carried =
          SourceViewPacket.capture(first, 42, Collections.singletonList(id));
      check(
          SourceViewPacket.decodeStack(first, carried.entries.get(0).data).getItemDamage() == 33,
          "container snapshot lost state");
      check(
          slots.getStackInSlot(0) == stored
              && inside.getCount() == 1
              && inside.getItemDamage() == 33,
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
                && SourceViewPacket.decodeStack(first, reply.entries.get(0).data).getItemDamage()
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
      SourceViewPacket.forget(first.getUniqueID());
      SourceViewPacket.forget(second.getUniqueID());
    }
    return "; source display: first-source priority, full ordinary/container snapshots, no"
        + " extraction or mutation, mode preservation, peer isolation, wire codecs, rate"
        + " limit and bounds";
  }
}
