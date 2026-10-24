package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Optional AE2 locator. Native menus keep their protocol; container access stays server-owned. */
public final class ContainerMenus {
    private record Entry(UUID token, StoredTools.MenuSource source) {}
    private static final Map<UUID, Entry> SOURCES = new HashMap<>();
    private static Constructor<?> constructor;
    private ContainerMenus() {}

    public static void register() {
        if (constructor != null) return;
        try {
            Class<?> api = Class.forName("appeng.menu.locator.ItemMenuHostLocator");
            // Implement the optional interface only when present, without bundling foreign API classes.
            String name = "dev/wrenchbridge/AE2StoredToolLocator", parent = Type.getInternalName(Binding.class);
            String desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(UUID.class), Type.getType(CompoundTag.class), Type.getType(BlockHitResult.class));
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, parent, new String[] {Type.getInternalName(api)});
            var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
            init.visitCode();
            for (int i = 0; i < 4; i++) init.visitVarInsn(Opcodes.ALOAD, i);
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", desc, false);
            init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd(); writer.visitEnd();
            Class<?> implementation = MethodHandles.lookup().defineClass(writer.toByteArray());
            constructor = implementation.getConstructor(UUID.class, CompoundTag.class, BlockHitResult.class);
            BiConsumer<Binding, FriendlyByteBuf> write = (binding, buffer) -> {
                buffer.writeUUID(binding.token); buffer.writeNbt(binding.initial);
                buffer.writeBoolean(binding.hit != null); if (binding.hit != null) buffer.writeBlockHitResult(binding.hit);
            };
            Function<FriendlyByteBuf, Object> read = buffer -> create(buffer.readUUID(), buffer.readNbt(), buffer.readBoolean() ? buffer.readBlockHitResult() : null);
            Class.forName("appeng.menu.locator.MenuLocators").getMethod("register", Class.class, BiConsumer.class, Function.class)
                    .invoke(null, implementation, write, read);
        } catch (ReflectiveOperationException | LinkageError error) {
            constructor = null;
            throw new IllegalStateException("Could not register AE2 carried-container menus", error);
        }
    }

    private static Object create(UUID token, CompoundTag snapshot, BlockHitResult hit) {
        try { return constructor.newInstance(token, snapshot, hit); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    public static UUID bind(StoredTools.MenuSource source) {
        UUID token = UUID.randomUUID(); SOURCES.put(source.player().getUUID(), new Entry(token, source)); return token;
    }
    public static Object locator(Player player, UUID token, BlockHitResult hit) {
        if (token == null || !(player instanceof ServerPlayer)) return null;
        Entry entry = SOURCES.get(player.getUUID());
        if (entry == null || !entry.token.equals(token) || !entry.source.available()) return null;
        return create(token, (CompoundTag) entry.source.view().save(player.registryAccess()), hit);
    }

    public static class Binding {
        private final UUID token;
        private final CompoundTag initial;
        private final BlockHitResult hit;
        private ItemStack clientView;
        public Binding(UUID token, CompoundTag initial, BlockHitResult hit) {
            this.token = token; this.initial = initial == null ? new CompoundTag() : initial; this.hit = hit;
        }
        public BlockHitResult hitResult() { return hit; }
        public Integer getPlayerInventorySlot() { return null; }
        public ItemStack locateItem(Player player) {
            if (player.level().isClientSide) {
                if (clientView == null) clientView = ItemStack.parseOptional(player.registryAccess(), initial);
                return clientView;
            }
            Entry entry = SOURCES.get(player.getUUID());
            return entry != null && entry.token.equals(token) && entry.source.player() == player ? entry.source.view() : ItemStack.EMPTY;
        }
    }

    private static Binding binding(Object menu) {
        if (!ClassProbe.is(menu, "appeng.menu.AEBaseMenu")) return null;
        try { return menu.getClass().getMethod("getLocator").invoke(menu) instanceof Binding binding ? binding : null; }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static <T> T action(Object menu, Player player, Supplier<T> operation, T rejected) {
        Binding binding = binding(menu);
        if (binding == null || !(player instanceof ServerPlayer server)) return operation.get();
        Entry entry = SOURCES.get(player.getUUID());
        if (entry == null || !entry.token.equals(binding.token) || entry.source.player() != player) { server.closeContainer(); return rejected; }
        return entry.source.use(tool -> operation.get(), rejected);
    }
    public static void tick(ServerPlayer player) {
        Entry entry = SOURCES.get(player.getUUID());
        if (entry == null) return;
        Binding binding = binding(player.containerMenu);
        if (binding == null || !binding.token.equals(entry.token)) forget(player);
        else if (!entry.source.available()) { player.closeContainer(); forget(player); }
    }
    public static void forget(ServerPlayer player) { SOURCES.remove(player.getUUID()); }
}
