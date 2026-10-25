package dev.wrenchbridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Small per-protocol policy; transport, source borrowing, permissions and feedback are shared. */
interface NativeBlockProtocol {
    String sourceItem();
    BlockState project(BlockState base, BlockState selected, BlockState current);
    void apply(ServerPlayer player, BlockPos pos, BlockState selected, ItemStack source) throws ReflectiveOperationException;
}
