package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.SharingPreferences;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

public final class SharingVerification {
  private SharingVerification() {}

  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError("sharing preference: " + message);
  }

  public static String verify(net.minecraft.world.WorldServer level) throws Exception {
    net.minecraft.entity.player.EntityPlayerMP
        first = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level),
        second =
            net.minecraftforge.common.util.FakePlayerFactory.get(
                level,
                new com.mojang.authlib.GameProfile(
                    java.util.UUID.fromString("0f609244-6bd8-47c2-867b-aed629c3a4b2"),
                    "SharingPeer"));
    setup(first);
    setup(second);
    SharingPreferences.forget(first.getUniqueID());
    SharingPreferences.forget(second.getUniqueID());
    BlockPos pos = new BlockPos(4, 110, 0);
    try {
      check(SharingPreferences.allows(first.getUniqueID(), false, false), "default disabled");
      for (EnumHand hand : EnumHand.values()) {
        first.inventory.clear();
        second.inventory.clear();
        ItemStack foreign = item("wrenchbridge:foreign_wrench");
        first.setHeldItem(hand, foreign);
        place(level, pos);
        click(first, level, pos, hand);
        check(level.isAirBlock(pos), "enabled foreign tool did not dismantle in " + hand);
        packet(first, false);
        check(
            !SharingPreferences.allows(first.getUniqueID(), false, false),
            "OFF not applied to sender");
        place(level, pos);
        click(first, level, pos, hand);
        check(!level.isAirBlock(pos), "disabled foreign tool still bridged in " + hand);
        second.setHeldItem(hand, item("wrenchbridge:foreign_wrench"));
        click(second, level, pos, hand);
        check(level.isAirBlock(pos), "one player disabled another player's sharing");
        ItemStack universal = new ItemStack(WrenchBridge.WRENCH);
        first.setHeldItem(hand, universal);
        place(level, pos);
        click(first, level, pos, hand);
        check(level.isAirBlock(pos), "OFF disabled universal wrench in " + hand);
        check(first.getHeldItem(hand) == universal, "universal item replaced");
        first.setHeldItem(hand, foreign);
        packet(first, true);
        place(level, pos);
        click(first, level, pos, hand);
        check(level.isAirBlock(pos), "ON did not restore foreign tool in " + hand);
      }
    } finally {
      SharingPreferences.forget(first.getUniqueID());
      SharingPreferences.forget(second.getUniqueID());
    }
    return "; sharing preference: default ON, OFF/ON, both hands, player isolation, universal"
               + " bypass and packet roundtrip";
  }

  private static void setup(net.minecraft.entity.player.EntityPlayerMP p) {
    p.interactionManager.setGameType(net.minecraft.world.GameType.SURVIVAL);
    p.setSneaking(true);
    p.setPosition(5.5, 110, .5);
  }

  private static ItemStack item(String id) {
    return new ItemStack(
        net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(
            new net.minecraft.util.ResourceLocation(id)));
  }

  private static void place(net.minecraft.world.WorldServer level, BlockPos pos) {
    String id = "mekanism:wrenchbridge_verification_machine";
    var block =
        net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getValue(
            new net.minecraft.util.ResourceLocation(id));
    level.setBlockState(pos, block.getDefaultState());
  }

  private static void packet(net.minecraft.entity.player.EntityPlayerMP p, boolean enabled) {
    io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
    try {
      SharingPacket encoded = new SharingPacket(enabled);
      encoded.toBytes(buffer);
      check(buffer.readableBytes() == 1, "unexpected preference payload");
      SharingPacket decoded = new SharingPacket();
      decoded.fromBytes(buffer);
      check(decoded.enabled == enabled, "preference codec");
      SharingPacket.apply(decoded, p);
    } finally {
      buffer.release();
    }
  }

  private static void click(
      net.minecraft.entity.player.EntityPlayerMP p,
      net.minecraft.world.WorldServer level,
      BlockPos pos,
      EnumHand hand) {
    if (hand == EnumHand.OFF_HAND
        && attempt(p, level, pos, EnumHand.MAIN_HAND) != net.minecraft.util.EnumActionResult.PASS)
      return;
    attempt(p, level, pos, hand);
  }

  private static net.minecraft.util.EnumActionResult attempt(
      net.minecraft.entity.player.EntityPlayerMP p,
      net.minecraft.world.WorldServer level,
      BlockPos pos,
      EnumHand hand) {
    return p.interactionManager.processRightClickBlock(
        p,
        level,
        p.getHeldItem(hand),
        hand,
        pos,
        net.minecraft.util.EnumFacing.EAST,
        .99F,
        .5F,
        .5F);
  }
}
