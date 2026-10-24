package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.SharingPreferences;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public final class SharingVerification {
  private SharingVerification() {}

  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError("sharing preference: " + message);
  }

  public static String verify(net.minecraft.world.server.ServerWorld level) throws Exception {
    net.minecraft.entity.player.ServerPlayerEntity
        first = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level),
        second =
            net.minecraftforge.common.util.FakePlayerFactory.get(
                level,
                new com.mojang.authlib.GameProfile(
                    java.util.UUID.fromString("0f609244-6bd8-47c2-867b-aed629c3a4b2"),
                    "SharingPeer"));
    setup(first);
    setup(second);
    SharingPreferences.forget(first.getUUID());
    SharingPreferences.forget(second.getUUID());
    BlockPos pos = new BlockPos(4, 110, 0);
    try {
      check(SharingPreferences.allows(first.getUUID(), false, false), "default disabled");
      for (Hand hand : Hand.values()) {
        first.inventory.clearContent();
        second.inventory.clearContent();
        ItemStack foreign = item("wrenchbridge:foreign_wrench");
        first.setItemInHand(hand, foreign);
        place(level, pos);
        click(first, level, pos, hand);
        check(level.isEmptyBlock(pos), "enabled foreign tool did not dismantle in " + hand);
        packet(first, false);
        check(
            !SharingPreferences.allows(first.getUUID(), false, false), "OFF not applied to sender");
        place(level, pos);
        click(first, level, pos, hand);
        check(!level.isEmptyBlock(pos), "disabled foreign tool still bridged in " + hand);
        second.setItemInHand(hand, item("wrenchbridge:foreign_wrench"));
        click(second, level, pos, hand);
        check(level.isEmptyBlock(pos), "one player disabled another player's sharing");
        ItemStack universal = new ItemStack(WrenchBridge.WRENCH.get());
        first.setItemInHand(hand, universal);
        place(level, pos);
        click(first, level, pos, hand);
        check(level.isEmptyBlock(pos), "OFF disabled universal wrench in " + hand);
        check(first.getItemInHand(hand) == universal, "universal item replaced");
        first.setItemInHand(hand, foreign);
        packet(first, true);
        place(level, pos);
        click(first, level, pos, hand);
        check(level.isEmptyBlock(pos), "ON did not restore foreign tool in " + hand);
      }
    } finally {
      SharingPreferences.forget(first.getUUID());
      SharingPreferences.forget(second.getUUID());
    }
    return "; sharing preference: default ON, OFF/ON, both hands, player isolation, universal"
               + " bypass and packet roundtrip";
  }

  private static void setup(net.minecraft.entity.player.ServerPlayerEntity p) {
    p.gameMode.setGameModeForPlayer(net.minecraft.world.GameType.SURVIVAL);
    p.setShiftKeyDown(true);
    p.setPos(5.5, 110, .5);
  }

  private static ItemStack item(String id) {
    return new ItemStack(
        net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
            new net.minecraft.util.ResourceLocation(id)));
  }

  private static void place(net.minecraft.world.server.ServerWorld level, BlockPos pos) {
    String id = "wrenchbridge_verification:machine";
    var block =
        net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
            new net.minecraft.util.ResourceLocation(id));
    level.setBlockAndUpdate(pos, block.defaultBlockState());
  }

  private static void packet(net.minecraft.entity.player.ServerPlayerEntity p, boolean enabled) {
    net.minecraft.network.PacketBuffer buffer =
        new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());
    try {
      SharingPacket.encode(new SharingPacket(enabled), buffer);
      check(buffer.readableBytes() == 1, "unexpected preference payload");
      SharingPacket decoded = SharingPacket.decode(buffer);
      check(decoded.enabled == enabled, "preference codec");
      SharingPacket.apply(decoded, p);
    } finally {
      buffer.release();
    }
  }

  private static void click(
      net.minecraft.entity.player.ServerPlayerEntity p,
      net.minecraft.world.server.ServerWorld level,
      BlockPos pos,
      Hand hand) {
    if (hand == Hand.OFF_HAND
        && attempt(p, level, pos, Hand.MAIN_HAND) != net.minecraft.util.ActionResultType.PASS)
      return;
    attempt(p, level, pos, hand);
  }

  private static net.minecraft.util.ActionResultType attempt(
      net.minecraft.entity.player.ServerPlayerEntity p,
      net.minecraft.world.server.ServerWorld level,
      BlockPos pos,
      Hand hand) {
    return p.gameMode.useItemOn(
        p,
        level,
        p.getItemInHand(hand),
        hand,
        new net.minecraft.util.math.BlockRayTraceResult(
            new net.minecraft.util.math.vector.Vector3d(4.99, 110.5, .5),
            net.minecraft.util.Direction.EAST,
            pos,
            false));
  }
}
