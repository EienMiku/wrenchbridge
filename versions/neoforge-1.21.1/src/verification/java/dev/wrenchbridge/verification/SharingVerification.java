package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.SharingPreferences;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class SharingVerification {
  private SharingVerification() {}

  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError("sharing preference: " + message);
  }

  public static String verify(net.minecraft.server.level.ServerLevel level) throws Exception {
    net.minecraft.server.level.ServerPlayer
        first = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level),
        second =
            net.neoforged.neoforge.common.util.FakePlayerFactory.get(
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
      for (InteractionHand hand : InteractionHand.values()) {
        first.getInventory().clearContent();
        second.getInventory().clearContent();
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

  private static void setup(net.minecraft.server.level.ServerPlayer p) {
    p.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    p.setShiftKeyDown(true);
    p.setPos(5.5, 110, .5);
  }

  private static ItemStack item(String id) {
    return new ItemStack(
        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.parse(id)));
  }

  private static void place(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
    String id = "wrenchbridge_verification:machine";
    var block =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
            net.minecraft.resources.ResourceLocation.parse(id));
    level.setBlockAndUpdate(pos, block.defaultBlockState());
  }

  private static void packet(net.minecraft.server.level.ServerPlayer p, boolean enabled) {
    io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
    try {
      SharingPacket.CODEC.encode(buffer, new SharingPacket(enabled));
      check(buffer.readableBytes() == 1, "unexpected preference payload");
      SharingPacket decoded = SharingPacket.CODEC.decode(buffer);
      check(decoded.enabled() == enabled, "preference codec");
      SharingPacket.apply(decoded, p);
    } finally {
      buffer.release();
    }
  }

  private static void click(
      net.minecraft.server.level.ServerPlayer p,
      net.minecraft.server.level.ServerLevel level,
      BlockPos pos,
      InteractionHand hand) {
    if (hand == InteractionHand.OFF_HAND
        && attempt(p, level, pos, InteractionHand.MAIN_HAND)
            != net.minecraft.world.InteractionResult.PASS) return;
    attempt(p, level, pos, hand);
  }

  private static net.minecraft.world.InteractionResult attempt(
      net.minecraft.server.level.ServerPlayer p,
      net.minecraft.server.level.ServerLevel level,
      BlockPos pos,
      InteractionHand hand) {
    return p.gameMode.useItemOn(
        p,
        level,
        p.getItemInHand(hand),
        hand,
        new net.minecraft.world.phys.BlockHitResult(
            new net.minecraft.world.phys.Vec3(4.99, 110.5, .5),
            net.minecraft.core.Direction.EAST,
            pos,
            false));
  }
}
