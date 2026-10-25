package dev.wrenchbridge.mixin;

import dev.wrenchbridge.client.NativeScreenBindings;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.wrench.RadialWrenchMenu", remap = false)
public abstract class CreateNativeScreenMixin {
    @Shadow private List<BlockState> allStates;
    @Shadow private int selectedStateIndex;
    @Inject(method = "submitChange", at = @At("HEAD"), cancellable = true)
    private void wrenchbridge$submit(CallbackInfo info) {
        Screen screen = (Screen) (Object) this;
        if (NativeScreenBindings.bound(screen)) {
            if (selectedStateIndex >= 0 && selectedStateIndex < allStates.size()) NativeScreenBindings.submit(screen, allStates.get(selectedStateIndex));
            else screen.onClose();
            info.cancel();
        }
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wrenchbridge$validate(CallbackInfo info) {
        if (!NativeScreenBindings.valid((Screen) (Object) this)) { Minecraft.getInstance().setScreen(null); info.cancel(); }
    }
    @Inject(method = "removed", at = @At("HEAD"))
    private void wrenchbridge$closed(CallbackInfo info) { NativeScreenBindings.forget((Screen) (Object) this); }
}
