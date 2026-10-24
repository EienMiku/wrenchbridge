package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Arrays;

public final class Targets {
    public enum Kind { UNKNOWN, COMMON, ANVIL, CREATE, PNEUMATIC, PRETTY }
    private Targets() {}
    public static Kind resolve(BlockState state, TileEntity entity) {
        Object block = state.getBlock();
        if (ClassProbe.is(block, "com.simibubi.create.content.equipment.wrench.IWrenchable")
                || ClassProbe.is(block, "com.simibubi.create.content.contraptions.wrench.IWrenchable")) return Kind.CREATE;
        if (ClassProbe.is(block, "me.desht.pneumaticcraft.api.block.IPneumaticWrenchable")) return Kind.PNEUMATIC;
        if (ClassProbe.is(block, "de.ellpeck.prettypipes.pipe.PipeBlock")) return Kind.PRETTY;
        String namespace = ForgeRegistries.BLOCKS.getKey(state.getBlock()).getNamespace();
        return Arrays.asList("appliedenergistics2", "ae2", "mekanism", "refinedstorage", "powah", "integrateddynamics",
                "integratedtunnels", "rftoolsbase", "rftoolsutility", "rftoolsbuilder", "rftoolscontrol", "xnet", "enderio")
                .contains(namespace) ? Kind.COMMON : Kind.UNKNOWN;
    }
}
