package dev.wrenchbridge.mixin;

import dev.wrenchbridge.BridgeEvents;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The bridge already uses normal interaction packets; do not also send PNC's third-party-wrench
 * packet.
 */
@Pseudo
@Mixin(targets = "me.desht.pneumaticcraft.common.event.MiscEventHandler", remap = false)
public abstract class PneumaticWrenchEventMixin {
  @Inject(method = "onModdedWrenchBlock", at = @At("HEAD"), cancellable = true, require = 0)
  private void wrenchbridge$singleRoute(
      PlayerInteractEvent.RightClickBlock event, CallbackInfo info) {
    if (BridgeEvents.ownsPneumaticClick(event)) info.cancel();
  }
}
