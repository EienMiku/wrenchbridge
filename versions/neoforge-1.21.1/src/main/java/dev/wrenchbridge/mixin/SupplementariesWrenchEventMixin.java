package dev.wrenchbridge.mixin;

import dev.wrenchbridge.BridgeEvents;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve normal rotation, while known cross-mod sneak dismantling belongs to the bridge. */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.events.platform.ServerEventsForge", remap = false)
public abstract class SupplementariesWrenchEventMixin {
    @Inject(method = {"onUseBlock", "onUseBlockHP"}, at = @At("HEAD"), cancellable = true)
    private static void wrenchbridge$sharedClick(PlayerInteractEvent.RightClickBlock event, CallbackInfo info) {
        if (BridgeEvents.ownsSupplementariesClick(event)) info.cancel();
    }
}
