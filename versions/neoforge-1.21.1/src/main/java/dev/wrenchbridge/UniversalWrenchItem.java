package dev.wrenchbridge;

import java.util.List;
import net.minecraft.ChatFormatting;
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
import net.neoforged.neoforge.common.ItemAbility;

public final class UniversalWrenchItem extends Item {
    public UniversalWrenchItem(Properties properties) { super(properties); }

    @Override public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return WrenchContext.forced(stack) && (ability == ItemAbility.get("wrench_dismantle") || ability == ItemAbility.get("wrench_rotate")
                || ability == ItemAbility.get("wrench_connect"));
    }

    @Override public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return Targets.resolve(level.getBlockState(pos), level.getBlockEntity(pos)) != Targets.Kind.UNKNOWN;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wrench = player.getItemInHand(hand);
        if (VirtualModes.isNative(wrench) && VirtualModes.hasNativeMenu(wrench)) {
            if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) VirtualModes.openNativeMenu(serverPlayer, hand);
            return InteractionResultHolder.sidedSuccess(wrench, level.isClientSide);
        }
        return InteractionResultHolder.pass(wrench);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                         List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.wrenchbridge.dismantle").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.wrenchbridge.modes",
                Component.keybind("key.wrenchbridge.modes").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.wrenchbridge.native").withStyle(ChatFormatting.GRAY));
        if (VirtualModes.isNative(stack)) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(VirtualModes.item(stack));
            Component selection = id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                    ? NativeModeNames.choice(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)), VirtualModes.mode(stack))
                    : Component.literal(VirtualModes.item(stack));
            lines.add(Component.translatable("tooltip.wrenchbridge.profile",
                    selection.copy().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        }

    }
}
