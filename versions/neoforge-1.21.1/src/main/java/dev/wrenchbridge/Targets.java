package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class Targets {
    public enum Kind { UNKNOWN, COMMON, ANVIL, CREATE, PNEUMATIC, PRETTY }
    private Targets() {}
    public static Kind resolve(BlockState state, BlockEntity entity) {
        Object block = state.getBlock();
        if (state.is(TagKey.create(Registries.BLOCK, ResourceLocation.parse("wrenchbridge:common_wrench_targets"))))
            return Kind.COMMON;
        if (ClassProbe.is(block, "dev.dubhe.anvilcraft.api.hammer.IHammerRemovable")) return Kind.ANVIL;
        if (ClassProbe.is(block, "com.simibubi.create.content.equipment.wrench.IWrenchable")) return Kind.CREATE;
        if (ClassProbe.is(block, "me.desht.pneumaticcraft.api.block.IPneumaticWrenchable")) return Kind.PNEUMATIC;
        if (ClassProbe.is(block, "de.ellpeck.prettypipes.pipe.PipeBlock")) return Kind.PRETTY;
        if (ClassProbe.is(entity, "appeng.blockentity.AEBaseBlockEntity")
                || ClassProbe.is(entity, "com.enderio.enderio.foundation.block.entity.Wrenchable")
                || ClassProbe.is(block, "mekanism.common.block.BlockMekanism")
                || ClassProbe.is(block, "com.refinedmods.refinedstorage.common.support.AbstractBaseBlock")
                || ClassProbe.is(block, "mcjty.lib.blocks.BaseBlock")
                || ClassProbe.is(block, "owmii.powah.lib.block.AbstractEnergyBlock")
                || ClassProbe.is(block, "owmii.powah.block.energizing.EnergizingOrbBlock")
                || ClassProbe.is(block, "dev.technici4n.moderndynamics.MdBlock")
                || ClassProbe.is(block, "org.cyclops.integrateddynamics.block.BlockCable")
                || ClassProbe.is(block, "org.cyclops.integrateddynamics.core.block.BlockContainerCabled")
                || ClassProbe.is(block, "org.cyclops.integrateddynamics.core.block.BlockWithEntityGuiCabled")) return Kind.COMMON;
        if (state.is(TagKey.create(Registries.BLOCK, ResourceLocation.parse("anvilcraft:hammer_removable")))) return Kind.ANVIL;
        return Kind.UNKNOWN;
    }
}
