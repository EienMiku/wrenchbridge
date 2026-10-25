package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.NativeInputRule;
import dev.wrenchbridge.client.NativeClientCallbacks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.wrench.RadialWrenchHandler", remap = false)
public abstract class CreateNativeInputMixin {
    @WrapMethod(method = "onKeyInput")
    private static void wrenchbridge$input(int key, boolean pressed, Operation<Void> original) {
        NativeClientCallbacks.input(NativeInputRule.CREATE_ROTATION, () -> original.call(key, pressed));
    }
    @Redirect(method = "onKeyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack wrenchbridge$heldView(LocalPlayer player) { return NativeClientCallbacks.mainHand(player); }
}
