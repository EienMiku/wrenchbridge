package dev.wrenchbridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Reuse Create's native commit code; only its known rotation properties are accepted from the GUI. */
final class CreateRotationProtocol implements NativeBlockProtocol {
    private static final Set<ResourceLocation> BLACKLIST = Set.of(ResourceLocation.parse("create:large_water_wheel"), ResourceLocation.parse("create:water_wheel_structural"));
    private record Access(Constructor<?> packet, Method handle, Set<Property<?>> properties) {}
    private static final class NativeAccess {
        static final Access VALUE = load();
        private static Access load() {
            try {
                Class<?> packet = Class.forName("com.simibubi.create.content.contraptions.wrench.RadialWrenchMenuSubmitPacket");
                Set<Property<?>> properties = new HashSet<>();
                // Common/server classes only: the native Screen class must never load on a dedicated server.
                for (String[] entry : new String[][] {
                        {"com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock", "AXIS"},
                        {"com.simibubi.create.content.kinetics.base.DirectionalKineticBlock", "FACING"},
                        {"com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock", "HORIZONTAL_AXIS"},
                        {"com.simibubi.create.content.kinetics.base.HorizontalKineticBlock", "HORIZONTAL_FACING"},
                        {"net.minecraft.world.level.block.HopperBlock", "FACING"},
                        {"com.simibubi.create.content.redstone.DirectedDirectionalBlock", "TARGET"},
                        {"com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock", "VERTICAL"}})
                    properties.add((Property<?>) Class.forName(entry[0]).getField(entry[1]).get(null));
                return new Access(packet.getConstructor(BlockPos.class, BlockState.class),
                        packet.getMethod("handle", ServerPlayer.class), Set.copyOf(properties));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                WrenchBridge.LOG.debug("Create rotation protocol unavailable", error); return null;
            }
        }
    }
    @Override public String sourceItem() { return NativeInputRule.CREATE_ROTATION.item(); }

    @Override public BlockState project(BlockState base, BlockState selected, BlockState current) {
        Access access = NativeAccess.VALUE;
        if (access == null || base.getBlock() != selected.getBlock() || base.getBlock() != current.getBlock()
                || BLACKLIST.contains(BuiltInRegistries.BLOCK.getKey(current.getBlock()))) return null;
        Property<?> changed = null;
        for (Property<?> property : base.getProperties()) {
            if (Objects.equals(base.getValue(property), selected.getValue(property))) continue;
            if (changed != null || !access.properties.contains(property)) return null;
            changed = property;
        }
        if (changed == null || !Objects.equals(current.getValue(changed), base.getValue(changed))) return null;
        return applyProperty(current, selected, changed);
    }
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState current, BlockState selected, Property<T> property) {
        return current.setValue(property, selected.getValue(property));
    }
    @Override public void apply(ServerPlayer player, BlockPos pos, BlockState selected, ItemStack source) throws ReflectiveOperationException {
        Access access = NativeAccess.VALUE;
        if (access == null) throw new IllegalStateException("Create rotation protocol unavailable");
        access.handle.invoke(access.packet.newInstance(pos, selected), player);
    }
}
