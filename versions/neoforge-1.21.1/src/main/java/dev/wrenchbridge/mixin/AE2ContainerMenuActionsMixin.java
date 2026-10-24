package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wrenchbridge.ContainerMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "appeng.menu.AEBaseMenu", remap = false)
public abstract class AE2ContainerMenuActionsMixin {
    @Shadow public abstract Player getPlayer();

    @WrapMethod(method = "clicked")
    private void wrenchbridge$click(int slot, int button, ClickType type, Player player, Operation<Void> original) {
        ContainerMenus.action(this, player, () -> { original.call(slot, button, type, player); return null; }, null);
    }
    @WrapMethod(method = "quickMoveStack")
    private ItemStack wrenchbridge$quickMove(Player player, int slot, Operation<ItemStack> original) {
        return ContainerMenus.action(this, player, () -> original.call(player, slot), ItemStack.EMPTY);
    }
    @WrapMethod(method = "receiveClientAction")
    private void wrenchbridge$button(String name, String data, Operation<Void> original) {
        ContainerMenus.action(this, getPlayer(), () -> { original.call(name, data); return null; }, null);
    }
    @WrapMethod(method = "doAction")
    private void wrenchbridge$action(ServerPlayer player, @Coerce Object action, int slot, long id, Operation<Void> original) {
        ContainerMenus.action(this, player, () -> { original.call(player, action, slot, id); return null; }, null);
    }
}
