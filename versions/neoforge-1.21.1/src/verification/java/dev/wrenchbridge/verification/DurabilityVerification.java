package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;

/** Native last-durability behavior, including a consumed tool returned from a carried container. */
public final class DurabilityVerification {
  private DurabilityVerification() {}

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError("native break: " + message);
  }

  private static ItemStack item(String id) {
    return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
  }

  public static String verify(ServerLevel level) throws Exception {
    if (!net.neoforged.fml.ModList.get().isLoaded("supplementaries")) return "";
    var player = FakePlayerFactory.getMinecraft(level);
    player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    player.setShiftKeyDown(false);
    player.setPose(net.minecraft.world.entity.Pose.STANDING);
    BlockPos pos = new BlockPos(4, 98, 0);
    player.moveTo(4.5, 99, 2.5, 180, 0);
    var hit =
        new BlockHitResult(Vec3.atLowerCornerOf(pos).add(0.5, 1, 0.5), Direction.UP, pos, false);
    var facing = BlockStateProperties.HORIZONTAL_FACING;
    var before =
        Blocks.WHITE_GLAZED_TERRACOTTA.defaultBlockState().setValue(facing, Direction.NORTH);
    level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
    String id = "supplementaries:wrench";
    int cases = 0;
    for (InteractionHand hand : InteractionHand.values()) {
      for (boolean stored : new boolean[] {false, true}) {
        player.getInventory().clearContent();
        ItemStack held = item("wrenchbridge:universal_wrench");
        ItemStack last = item(id), backup = item(id);
        last.setDamageValue(last.getMaxDamage() - 1);
        backup.setDamageValue(5);
        IItemHandler bagItems = null;
        if (stored) {
          ItemStack bag = item("sophisticatedbackpacks:backpack");
          Object wrapper =
              Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper")
                  .getMethod("fromStack", ItemStack.class)
                  .invoke(null, bag);
          wrapper
              .getClass()
              .getMethod("setContentsUuid", UUID.class)
              .invoke(wrapper, UUID.randomUUID());
          bagItems =
              (IItemHandler) wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
          check(bagItems.insertItem(0, last, false).isEmpty(), "could not store last-use tool");
          check(bagItems.insertItem(1, backup, false).isEmpty(), "could not store backup");
          player.getInventory().setItem(8, bag);
        } else {
          player.getInventory().setItem(9, last);
          player.getInventory().setItem(10, backup);
        }
        player.setItemInHand(hand, held);
        check(VirtualModes.select(player, hand, id, ""), "could not select last-use tool");
        level.setBlockAndUpdate(pos, before);
        player.gameMode.useItemOn(player, level, held, hand, hit);
        check(
            level.getBlockState(pos).getValue(facing) != Direction.NORTH,
            "last use did not rotate");
        check(
            player.getItemInHand(hand) == held && held.getCount() == 1,
            "native break removed universal wrench");
        check(
            stored
                ? bagItems.getStackInSlot(0).isEmpty()
                : player.getInventory().getItem(9).isEmpty(),
            "native broken tool was restored");
        ItemStack remaining = StoredTools.peek(player, id);
        check(
            !remaining.isEmpty() && remaining.getDamageValue() == 5,
            "backup was consumed in the same action");
        var snapshot = SourceViewPacket.capture(player, 91, java.util.List.of(id));
        check(
            SourceViewPacket.decodeStack(player, snapshot.entries.get(0).data).getDamageValue()
                == 5,
            "display did not advance to the same backup source");
        level.setBlockAndUpdate(pos, before);
        player.gameMode.useItemOn(player, level, held, hand, hit);
        check(
            level.getBlockState(pos).getValue(facing) != Direction.NORTH
                && StoredTools.peek(player, id).getDamageValue() == 6,
            "next action did not use backup");
        if (stored) player.getInventory().setItem(8, ItemStack.EMPTY);
        else player.getInventory().setItem(10, ItemStack.EMPTY);
        level.setBlockAndUpdate(pos, before);
        check(
            StoredTools.peek(player, id).isEmpty(),
            "source remained after removing backup: " + hand + "/" + stored);
        check(
            VirtualModes.useOn(player, hand, hit) == net.minecraft.world.InteractionResult.FAIL,
            "direct use accepted a missing source: " + hand + "/" + stored);
        player.gameMode.useItemOn(player, level, held, hand, hit);
        check(
            level.getBlockState(pos).equals(before) && player.getItemInHand(hand) == held,
            "missing source changed state="
                + !level.getBlockState(pos).equals(before)
                + ", changed held="
                + (player.getItemInHand(hand) != held)
                + ", "
                + hand
                + "/"
                + stored);
        check(
            VirtualModes.isNative(held) && VirtualModes.item(held).equals(id),
            "missing source changed selected profile");
        check(
            SourceViewPacket.capture(player, 92, java.util.List.of(id)).entries.get(0).data.length
                == 0,
            "display retained a consumed source");
        cases++;
      }
    }
    player.getInventory().clearContent();
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    return "; native last durability: "
        + cases
        + " Supplementaries main/offhand inventory/backpack cases; tool consumed, universal"
        + " retained, backup untouched until next action, missing source denied and display"
        + " advanced";
  }
}
