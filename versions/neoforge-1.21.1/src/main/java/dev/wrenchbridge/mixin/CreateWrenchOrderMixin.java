package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.WrenchUseTransaction;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.equipment.wrench.WrenchEventHandler", remap = false)
public abstract class CreateWrenchOrderMixin {
    @WrapMethod(method = "useOwnWrenchLogicForCreateBlocks", require = 0)
    private static void wrenchbridge$afterProtection(PlayerInteractEvent.RightClickBlock event, Operation<Void> original) {
        if (!WrenchUseTransaction.deferCreate(event, () -> original.call(event))) original.call(event);
    }
}
