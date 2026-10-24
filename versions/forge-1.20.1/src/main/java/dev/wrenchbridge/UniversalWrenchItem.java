package dev.wrenchbridge;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.ToolAction;

public final class UniversalWrenchItem extends Item {
    public UniversalWrenchItem(Properties properties) { super(properties); }

    @Override public boolean canPerformAction(ItemStack stack, ToolAction ability) {
        return ability == ToolAction.get("wrench_dismantle") || ability == ToolAction.get("wrench_rotate")
                || ability == ToolAction.get("wrench_connect");
    }

    @Override public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return Targets.resolve(level.getBlockState(pos), level.getBlockEntity(pos)) != Targets.Kind.UNKNOWN;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) dev.wrenchbridge.client.ClientHooks.open();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, Level context,
                                         List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.wrenchbridge.dismantle"));
        lines.add(Component.translatable("tooltip.wrenchbridge.modes"));
        lines.add(Component.translatable("tooltip.wrenchbridge.native"));
    }
}
