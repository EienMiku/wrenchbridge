package dev.wrenchbridge.mixin;

import dev.wrenchbridge.NativeMenuContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "appeng.menu.locator.MenuLocators", remap = false)
public abstract class AE2MenuSourceMixin {
    @Inject(method = "forHand", at = @At("HEAD"), cancellable = true)
    private static void wrenchbridge$containerHand(Player player, InteractionHand hand, CallbackInfoReturnable<Object> result) {
        Object locator = NativeMenuContext.locator(player, hand, null);
        if (locator != null) result.setReturnValue(locator);
    }
    @Inject(method = "forItemUseContext", at = @At("HEAD"), cancellable = true)
    private static void wrenchbridge$containerTarget(UseOnContext context, CallbackInfoReturnable<Object> result) {
        // Match AE2's locator representation, including its horizontal-direction convention.
        var hit = new BlockHitResult(context.getClickLocation(), context.getHorizontalDirection(), context.getClickedPos(), context.isInside());
        Object locator = NativeMenuContext.locator(context.getPlayer(), context.getHand(), hit);
        if (locator != null) result.setReturnValue(locator);
    }
    @Inject(method = "getPlayerInventorySlotFromHand", at = @At("HEAD"), cancellable = true)
    private static void wrenchbridge$realSourceSlot(Player player, InteractionHand hand, CallbackInfoReturnable<Integer> result) {
        Integer slot = NativeMenuContext.inventorySlot(player, hand);
        if (slot != null) result.setReturnValue(slot);
    }
}
