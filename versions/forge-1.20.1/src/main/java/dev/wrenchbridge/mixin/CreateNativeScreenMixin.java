package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wrenchbridge.client.NativeScreenBindings;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
  // Create 6.0.8 reads a missing block entity while rendering ordinary block previews.
  @WrapOperation(
      method = "renderRadialSectors",
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/world/level/block/entity/BlockEntity;getLevel()Lnet/minecraft/world/level/Level;"),
      require = 0)
  private Level wrenchbridge$previewLevel(BlockEntity entity, Operation<Level> original) {
    return entity == null ? null : original.call(entity);
  }

  @WrapOperation(
      method = "renderRadialSectors",
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/world/level/block/entity/BlockEntity;setLevel(Lnet/minecraft/world/level/Level;)V"),
      require = 0)
  private void wrenchbridge$previewWorld(
      BlockEntity entity, Level level, Operation<Void> original) {
    if (entity != null) original.call(entity, level);
  }

  @Shadow private List<BlockState> allStates;
  @Shadow private int selectedStateIndex;

  @Inject(method = "submitChange", at = @At("HEAD"), cancellable = true)
  private void wrenchbridge$submit(CallbackInfo info) {
    Screen screen = (Screen) (Object) this;
    if (NativeScreenBindings.bound(screen)) {
      if (selectedStateIndex >= 0 && selectedStateIndex < allStates.size())
        NativeScreenBindings.submit(screen, allStates.get(selectedStateIndex));
      else screen.onClose();
      info.cancel();
    }
  }

  @Inject(
      method = {"tick", "m_86600_"},
      at = @At("HEAD"),
      cancellable = true)
  private void wrenchbridge$validate(CallbackInfo info) {
    if (!NativeScreenBindings.valid((Screen) (Object) this)) {
      Minecraft.getInstance().setScreen(null);
      info.cancel();
    }
  }

  @Inject(
      method = {"removed", "m_7861_"},
      at = @At("HEAD"))
  private void wrenchbridge$closed(CallbackInfo info) {
    NativeScreenBindings.forget((Screen) (Object) this);
  }
}
