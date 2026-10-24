package dev.wrenchbridge;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.IWorldReader;

public final class UniversalWrenchItem extends Item {
    public UniversalWrenchItem(Properties properties) { super(properties); }
    @Override public boolean doesSneakBypassUse(ItemStack stack, IWorldReader level, BlockPos pos, PlayerEntity player) {
        return Targets.resolve(level.getBlockState(pos), level.getBlockEntity(pos)) != Targets.Kind.UNKNOWN;
    }
    @Override public ActionResult<ItemStack> use(World level, PlayerEntity player, Hand hand) {
        if (level.isClientSide) dev.wrenchbridge.client.ClientHooks.open();
        return ActionResult.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, World level, List<ITextComponent> lines, ITooltipFlag flag) {
        lines.add(new TranslationTextComponent("tooltip.wrenchbridge.dismantle"));
        lines.add(new TranslationTextComponent("tooltip.wrenchbridge.modes"));
        lines.add(new TranslationTextComponent("tooltip.wrenchbridge.native"));
    }
}
