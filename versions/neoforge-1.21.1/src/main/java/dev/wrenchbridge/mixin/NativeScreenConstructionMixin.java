package dev.wrenchbridge.mixin;

import dev.wrenchbridge.client.NativeClientCallbacks;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One construction hook for declared native screens, including deferred screen openers. */
@Mixin(value = Screen.class, remap = false)
public abstract class NativeScreenConstructionMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void wrenchbridge$created(Component title, CallbackInfo info) {
        NativeClientCallbacks.screenCreated((Screen) (Object) this);
    }
}
