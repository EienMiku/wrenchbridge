package dev.wrenchbridge;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.ItemAbility;

public final class UniversalWrenchItem extends Item {
    public UniversalWrenchItem(Properties properties) { super(properties); }

    @Override public boolean canPerformAction(net.minecraft.world.item.ItemInstance stack, ItemAbility ability) {
        return ability == ItemAbility.get("wrench_dismantle") || ability == ItemAbility.get("wrench_rotate")
                || ability == ItemAbility.get("wrench_connect");
    }

    @Override public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return Targets.resolve(level.getBlockState(pos), level.getBlockEntity(pos)) != Targets.Kind.UNKNOWN;
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) dev.wrenchbridge.client.ClientHooks.open();
        return InteractionResult.SUCCESS;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                         net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.wrenchbridge.dismantle"));
        lines.accept(Component.translatable("tooltip.wrenchbridge.modes"));
        lines.accept(Component.translatable("tooltip.wrenchbridge.native"));
    }
}
