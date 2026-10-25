package dev.wrenchbridge.verification;

import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.ModePacket;
import dev.wrenchbridge.StoredTools;
import dev.wrenchbridge.VirtualModes;
import dev.wrenchbridge.core.NativeModes;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("wrenchbridge_verification")
public final class VerificationMod {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("wrenchbridge_verification");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("wrenchbridge_verification");
    private static final DeferredRegister.Items MODE_ITEMS = DeferredRegister.createItems("wrenchbridge");
    private static final java.util.function.Supplier<Machine> MACHINE = BLOCKS.registerBlock("machine", Machine::new);
    private static final java.util.function.Supplier<Block> NATIVE_UNKNOWN = BLOCKS.registerBlock("native_unknown", properties -> new Block(properties.strength(1)));
    private static final java.util.function.Supplier<Item> TOOL = MODE_ITEMS.registerItem("foreign_wrench", properties -> new Item(properties.stacksTo(1)));
    private static final java.util.function.Supplier<ModeTool> MODE_TOOL = MODE_ITEMS.registerItem("mode_wrench", properties -> new ModeTool(properties.stacksTo(1).durability(64)));
    private static final java.util.function.Supplier<Item> MENU_BAG = ITEMS.registerItem("menu_bag", Item::new);
    private static final class MenuHandler extends net.neoforged.neoforge.items.ItemStackHandler {
        boolean denyExtraction, denyReturn, throwReturn, mutateThenThrow, throwAfterReturn;
        int reads;
        MenuHandler() { super(1); }
        @Override public ItemStack getStackInSlot(int slot) { reads++; return super.getStackInSlot(slot); }
        @Override public ItemStack extractItem(int slot, int count, boolean simulate) { return denyExtraction ? ItemStack.EMPTY : super.extractItem(slot, count, simulate); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (throwReturn) throw new IllegalStateException("expected container return refusal");
            if (mutateThenThrow && !simulate) { stack.setCount(0); throw new IllegalStateException("expected input mutation before refusal"); }
            ItemStack remainder = denyReturn ? stack : super.insertItem(slot, stack, simulate);
            if (throwAfterReturn && !simulate) throw new IllegalStateException("expected failure after acceptance");
            return remainder;
        }
    }
    private static MenuHandler menuHandler;
    private static int nativeCalls;
    private static int simulatedCalls;
    private static boolean deny;
    private static boolean denyUse;
    private static boolean tryReplace;
    private static InteractionHand lastNativeHand;
    private static InteractionHand lastSimulatedHand;
    private static InteractionHand lastClickHand;
    private static int unknownNativeCalls;
    private static boolean throwInNativeUnknown;

    public VerificationMod(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); MODE_ITEMS.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> event.registerItem(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM, (stack, context) -> menuHandler, MENU_BAG.get()));
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT
                && Boolean.getBoolean("wrenchbridge.verifyClient")) ClientVerification.register(bus);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT
                && Boolean.getBoolean("wrenchbridge.verifySharingClient")) SharingClientVerification.register(bus);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT
                && Boolean.getBoolean("wrenchbridge.verifySourceViewClient")) SourceViewClientVerification.register(bus);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayerInteractEvent.RightClickBlock.class, event -> {
            if (Boolean.getBoolean("wrenchbridge.verify") && event.getPos().getY() == 95
                    && (event.getLevel().getBlockState(event.getPos()).getBlock().getClass().getName().contains("refinedstorage")
                        || dev.wrenchbridge.Tools.id(event.getItemStack()).equals("supplementaries:wrench")))
                WrenchBridge.LOG.info("Expanded click trace: {} shift={} pose={} canceled={} block={} item={} result={}",
                        dev.wrenchbridge.Tools.id(event.getItemStack()), event.getEntity().isShiftKeyDown(), event.getEntity().getPose(),
                        event.isCanceled(), event.getUseBlock(), event.getUseItem(), event.getCancellationResult());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent.class, event -> {
            if (Boolean.getBoolean("wrenchbridge.verify") && event.getPos().getY() == 95
                    && event.getLevel().getBlockState(event.getPos()).getBlock().getClass().getName().contains("refinedstorage"))
                WrenchBridge.LOG.info("Expanded phase trace: {} phase={} canceled={} result={}",
                        dev.wrenchbridge.Tools.id(event.getItemStack()), event.getUsePhase(), event.isCanceled(), event.getCancellationResult());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, event -> {
            if (deny) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, event -> {
            if (denyUse) event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.RightClickBlock.class, event -> {
            if (event.getLevel().getBlockState(event.getPos()).getBlock() != NATIVE_UNKNOWN.get()
                    || !event.getItemStack().is(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/wrench")))) return;
            if (throwInNativeUnknown) throw new IllegalStateException("expected native rejection");
            if (deny || denyUse) { event.setCanceled(true); event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL); return; }
            if (!event.getLevel().isClientSide) {
                unknownNativeCalls++;
                event.getLevel().setBlockAndUpdate(event.getPos(), Blocks.AIR.defaultBlockState());
            }
            event.setCanceled(true); event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            if (!Boolean.getBoolean("wrenchbridge.verify")) return;
            String result;
            try {
                if (Boolean.getBoolean("wrenchbridge.verifyReviewOnly")) {
                    result = "PASS: " + ReviewVerification.verify(event.getServer().overworld());
                } else {
                verify(event.getServer().overworld());
                String compat = verifyInstalledMods(event.getServer().overworld());
                compat += verifyBackpack(event.getServer().overworld());
                compat += verifyModeUiContracts(event.getServer().overworld());
                compat += verifyExpandedMods(event.getServer().overworld());
                compat += verifyGlazedTerracottaRotation(event.getServer().overworld());
                compat += verifyOffhand(event.getServer().overworld());
                compat += verifyOffhandMods(event.getServer().overworld());
                compat += verifyIntegratedMachines(event.getServer().overworld());
                compat += verifyPrettyPipeLimits(event.getServer().overworld());
                compat += verifyConfiguratorViews(event.getServer().overworld());
                compat += verifySharedUseIntent(event.getServer().overworld());
                compat += verifyNetworkMenuContext(event.getServer().overworld());
                compat += verifyContainerMenus(event.getServer().overworld());
                compat += FeedbackVerification.verify(event.getServer().overworld());
                compat += SharingVerification.verify(event.getServer().overworld());
                compat += SourceViewVerification.verify(event.getServer().overworld());
                compat += DurabilityVerification.verify(event.getServer().overworld());
                compat += ReviewVerification.verify(event.getServer().overworld());
                result = "PASS: native dispatch, protection, mode guard, inventory conservation, invalid packet, enum modes" + compat;
                }
            }
            catch (Throwable error) { WrenchBridge.LOG.error("Verification failed", error); result = "FAIL: " + error; }
            try { Files.writeString(Path.of("verification-result.txt"), result); }
            catch (java.io.IOException error) { throw new RuntimeException(error); }
            event.getServer().halt(false);
        });
    }

    public enum Mode { CONFIG, WRENCH }
    public static final class ModeTool extends Item {
        public ModeTool(Properties properties) { super(properties); }
        public Mode getMode(ItemStack stack) {
            return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("wrench") ? Mode.WRENCH : Mode.CONFIG;
        }
        public void setMode(ItemStack stack, Player player, Mode mode) {
            CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            data.putBoolean("wrench", mode == Mode.WRENCH); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
        @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            if (getMode(context.getItemInHand()) != Mode.CONFIG) return net.minecraft.world.InteractionResult.PASS;
            check(context.getPlayer().getItemInHand(context.getHand()).is(WrenchBridge.WRENCH.get()), "simulation equipped a native item");
            context.getItemInHand().setDamageValue(context.getItemInHand().getDamageValue() + 1);
            simulatedCalls++;
            lastSimulatedHand = context.getHand();
            if (tryReplace) context.getPlayer().setItemInHand(context.getHand(), new ItemStack(Items.DIAMOND));
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
    }
    public static final class Machine extends Block {
        public Machine(Properties properties) { super(properties.strength(1)); }
        @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (player.isShiftKeyDown() && stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/wrench")))) {
                if (!level.isClientSide) { nativeCalls++; lastNativeHand = hand; level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    /** Mirrors the client's hand order: a consumed or refused main-hand action stops the click. */
    private static net.minecraft.world.InteractionResult click(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
        for (InteractionHand hand : InteractionHand.values()) {
            lastClickHand = hand;
            var result = player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand, hit);
            if (result.consumesAction() || result == net.minecraft.world.InteractionResult.FAIL) return result;
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.isEmpty()) {
                result = player.gameMode.useItem(player, level, stack, hand);
                if (result.consumesAction()) return result;
            }
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    private static String verifyOffhand(ServerLevel level) throws Exception {
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent(); player.setShiftKeyDown(true);
        player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(4, 100, 0);
        player.moveTo(4.5, 101, 2.5, 180, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack main = new ItemStack(Items.STICK);
        ItemStack off = new ItemStack(WrenchBridge.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        check(dev.wrenchbridge.Tools.universalHand(player) == InteractionHand.OFF_HAND, "G did not find offhand universal wrench");
        check(off.getItem().use(level, player, InteractionHand.OFF_HAND).getResult() == net.minecraft.world.InteractionResult.PASS,
                "offhand air use opened the mode palette");
        for (ItemStack stack : java.util.List.of(off, new ItemStack(TOOL.get()))) {
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
            level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState()); int calls = nativeCalls;
            var action = click(player, level, hit);
            check(level.isEmptyBlock(pos) && nativeCalls == calls + 1 && lastNativeHand == InteractionHand.OFF_HAND,
                    "offhand shared dismantling failed or ran twice: " + dev.wrenchbridge.Tools.id(stack) + " result=" + action
                            + " calls=" + (nativeCalls - calls) + " lastHand=" + lastNativeHand + " remaining=" + level.getBlockState(pos));
            check(player.getMainHandItem() == main && player.getOffhandItem() == stack, "offhand dismantling moved either hand");
            level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
            try { deny = true; click(player, level, hit); } finally { deny = false; }
            check(!level.isEmptyBlock(pos) && nativeCalls == calls + 1, "offhand bypassed block protection");
            try { denyUse = true; click(player, level, hit); } finally { denyUse = false; }
            check(!level.isEmptyBlock(pos) && nativeCalls == calls + 1, "offhand bypassed interaction denial");
        }
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        ItemStack donor = new ItemStack(MODE_TOOL.get()); player.getInventory().setItem(9, donor);
        // The wire payload must preserve which hand opened the palette.
        var encoded = io.netty.buffer.Unpooled.buffer();
        ModePacket packet = new ModePacket("wrenchbridge:mode_wrench", "CONFIG", true);
        try { ModePacket.CODEC.encode(encoded, packet); check(ModePacket.CODEC.decode(encoded).equals(packet), "mode packet lost its hand"); }
        finally { encoded.release(); }
        select(packet, player);
        check(VirtualModes.mode(off).equals("CONFIG") && NativeModes.current(MODE_TOOL.get(), donor).equals("CONFIG"),
                "offhand selection modified a native tool");
        int calls = simulatedCalls;
        try { tryReplace = true; click(player, level, hit); } finally { tryReplace = false; }
        check(simulatedCalls == calls + 1 && lastSimulatedHand == InteractionHand.OFF_HAND && donor.getDamageValue() == 1,
                "offhand simulation lost the hand or real source cost");
        check(player.getMainHandItem() == main && player.getOffhandItem() == off, "offhand simulation replaced a held item");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            check(!player.getInventory().getItem(slot).is(Items.DIAMOND), "offhand simulation created a conversion output");
        ItemStack before = off.copy();
        select(new ModePacket("wrenchbridge:mode_wrench", "WRENCH"), player);
        select(new ModePacket("wrenchbridge:mode_wrench", "INVALID", true), player);
        check(ItemStack.isSameItemSameComponents(off, before), "wrong-hand or invalid selection changed offhand profile");
        ItemStack other = new ItemStack(WrenchBridge.WRENCH.get()); player.setItemInHand(InteractionHand.MAIN_HAND, other);
        check(dev.wrenchbridge.Tools.universalHand(player) == InteractionHand.MAIN_HAND, "G did not prefer main universal wrench");
        select(new ModePacket("wrenchbridge:mode_wrench", "WRENCH", true), player);
        check(VirtualModes.item(other).isEmpty() && VirtualModes.mode(off).equals("WRENCH"), "offhand packet changed the main wrench");
        // Both hands can dismantle: only the main hand should execute.
        level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState()); calls = nativeCalls;
        click(player, level, hit);
        check(level.isEmptyBlock(pos) && nativeCalls == calls + 1 && lastNativeHand == InteractionHand.MAIN_HAND,
                "both hands executed one dismantling click");
        select(new ModePacket("wrenchbridge:mode_wrench", "CONFIG"), player);
        level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState()); calls = simulatedCalls;
        click(player, level, hit);
        check(!level.isEmptyBlock(pos) && simulatedCalls == calls + 1 && lastSimulatedHand == InteractionHand.MAIN_HAND,
                "offhand dismantled after main-hand configuration consumed the click");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); before = other.copy();
        select(new ModePacket("wrenchbridge:mode_wrench", "WRENCH", true), player);
        check(ItemStack.isSameItemSameComponents(other, before), "stale offhand selection fell back to the main hand");
        // Exercise source discovery from either opposite hand independently of the client's hand priority.
        player.setItemInHand(InteractionHand.OFF_HAND, donor); player.getInventory().setItem(9, ItemStack.EMPTY);
        int damage = donor.getDamageValue();
        player.gameMode.useItemOn(player, level, other, InteractionHand.MAIN_HAND, hit);
        check(donor.getDamageValue() == damage + 1 && player.getMainHandItem() == other && player.getOffhandItem() == donor,
                "main-hand simulation moved or failed to charge an offhand donor");
        player.setItemInHand(InteractionHand.OFF_HAND, off); player.setItemInHand(InteractionHand.MAIN_HAND, donor);
        select(new ModePacket("wrenchbridge:mode_wrench", "CONFIG", true), player); damage = donor.getDamageValue();
        player.gameMode.useItemOn(player, level, off, InteractionHand.OFF_HAND, hit);
        check(donor.getDamageValue() == damage + 1 && player.getMainHandItem() == donor && player.getOffhandItem() == off,
                "offhand simulation moved or failed to charge a main-hand donor");
        check(dev.wrenchbridge.WrenchContext.hand() == null && !dev.wrenchbridge.WrenchContext.forced(off), "wrench context leaked after offhand use");
        player.getInventory().clearContent(); level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return "; offhand: shared and simulated actions, correct hand, real costs, G targeting, packet roundtrip and rejection, main-hand priority, single action, protection";
    }

    private static ItemStack preparedTool(ServerPlayer player, String id) throws Exception {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
        if (id.equals("pneumaticcraft:pneumatic_wrench")) {
            var air = (java.util.Optional<?>) Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities")
                    .getMethod("getAirHandler", ItemStack.class).invoke(null, stack);
            Class.forName("me.desht.pneumaticcraft.api.tileentity.IAirHandler").getMethod("addAir", int.class).invoke(air.orElseThrow(), 5000);
        }
        var modes = NativeModes.choices(stack.getItem(), stack, player);
        for (String preferred : new String[] {"WRENCH", "ALL", "DEFAULT", "NORMAL"}) {
            if (modes.contains(preferred)) { NativeModes.select(stack.getItem(), stack, player, preferred); break; }
        }
        return stack;
    }

    private static String verifyOffhandMods(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("pneumaticcraft")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        String[] targets = {"ae2:interface", "mekanism:basic_energy_cube", "create:shaft", "anvilcraft:royal_steel_block",
                "refinedstorage:controller", "enderio:alloy_smelter", "pneumaticcraft:air_compressor", "prettypipes:pipe",
                "powah:energy_cell_starter", "rftoolsutility:crafter1", "integrateddynamics:energy_battery",
                "moderndynamics:item_pipe", "minecraft:observer", "integrateddynamics:mechanical_squeezer"};
        String[] tools = {"mekanism:configurator", "ae2:certus_quartz_wrench", "create:wrench", "anvilcraft:anvil_hammer",
                "refinedstorage:wrench", "enderio:yeta_wrench", "pneumaticcraft:pneumatic_wrench", "prettypipes:wrench",
                "powah:wrench", "rftoolsbase:smartwrench", "integrateddynamics:wrench", "moderndynamics:wrench",
                "pipez:wrench", "supplementaries:wrench"};
        BlockPos pos = new BlockPos(4, 100, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var failures = new java.util.ArrayList<String>();
        var report = new com.google.gson.JsonArray();
        for (boolean simulate : new boolean[] {false, true}) {
            var sources = new java.util.ArrayList<>(java.util.List.of(tools));
            if (!simulate) sources.add("wrenchbridge:universal_wrench");
            for (String source : sources) for (String target : targets) {
                // Physical own-mod behavior remains under its native handler. Exercise the bridge's cross-mod scope.
                if (!simulate && source.split(":")[0].equals(target.split(":")[0]) && !source.startsWith("ae2:")) continue;
                player.getInventory().clearContent(); player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                ItemStack main = new ItemStack(Items.STICK); player.setItemInHand(InteractionHand.MAIN_HAND, main);
                ItemStack donor = preparedTool(player, source);
                ItemStack held = simulate ? new ItemStack(WrenchBridge.WRENCH.get()) : donor;
                player.setItemInHand(InteractionHand.OFF_HAND, held);
                String originalMode = NativeModes.current(donor.getItem(), donor);
                if (simulate) {
                    player.getInventory().setItem(9, donor);
                    String selectionMode = NativeModes.choices(donor.getItem(), donor, player).contains(originalMode) ? originalMode : "";
                    select(new ModePacket(source, selectionMode, true), player);
                    check(VirtualModes.item(held).equals(source), "could not select offhand simulation " + source);
                }
                Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(target));
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(pos, block.defaultBlockState());
                block.setPlacedBy(level, pos, level.getBlockState(pos), player, new ItemStack(block.asItem()));
                var row = new com.google.gson.JsonObject(); row.addProperty("source", source); row.addProperty("target", target); row.addProperty("simulated", simulate);
                try {
                    var result = click(player, level, hit); boolean removed = level.isEmptyBlock(pos);
                    row.addProperty("removed", removed); row.addProperty("result", result.name());
                    boolean nativeDenial = target.equals("refinedstorage:controller");
                    if (nativeDenial) row.addProperty("reference", "main-hand native wrench also denied; denial parity only");
                    if (removed == nativeDenial) failures.add(source + (simulate ? " simulated" : " physical") + " -> " + target + " / " + result);
                    if (player.getMainHandItem() != main || player.getOffhandItem() != held
                            || simulate && (player.getInventory().getItem(9) != donor || !NativeModes.current(donor.getItem(), donor).equals(originalMode)))
                        failures.add("identity/mode: " + source + " -> " + target);
                    // An original callback may alter pose. Reset before the permission probe.
                    player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                    level.setBlockAndUpdate(pos, block.defaultBlockState());
                    try { deny = true; click(player, level, hit); } finally { deny = false; }
                    if (level.isEmptyBlock(pos)) failures.add("protection: " + source + " -> " + target);
                } catch (Throwable error) { row.addProperty("error", error.toString()); failures.add(source + " -> " + target + ": " + error); }
                finally { deny = false; }
                report.add(row);
            }
        }
        // A normal (non-sneaking) action must reach the native item with the offhand context and charge it once.
        player.getInventory().clearContent(); player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
        ItemStack held = new ItemStack(WrenchBridge.WRENCH.get()), donor = preparedTool(player, "supplementaries:wrench");
        player.setItemInHand(InteractionHand.OFF_HAND, held); player.getInventory().setItem(9, donor);
        select(new ModePacket("supplementaries:wrench", "", true), player);
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        level.setBlockAndUpdate(pos, Blocks.WHITE_GLAZED_TERRACOTTA.defaultBlockState().setValue(facing, Direction.NORTH));
        int damage = donor.getDamageValue(); click(player, level, hit);
        check(level.getBlockState(pos).getValue(facing) != Direction.NORTH && donor.getDamageValue() == damage + 1
                && player.getOffhandItem() == held && player.getMainHandItem().isEmpty(), "offhand native rotation failed, replaced an item or charged twice");
        player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        ItemStack pneumatic = preparedTool(player, "pneumaticcraft:pneumatic_wrench"); player.getInventory().setItem(9, pneumatic);
        select(new ModePacket("pneumaticcraft:pneumatic_wrench", "", true), player);
        var handler = (java.util.Optional<?>) Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities")
                .getMethod("getAirHandler", ItemStack.class).invoke(null, pneumatic);
        Class<?> air = Class.forName("me.desht.pneumaticcraft.api.tileentity.IAirHandler");
        Block compressor = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pneumaticcraft:air_compressor"));
        level.setBlockAndUpdate(pos, compressor.defaultBlockState()); click(player, level, hit);
        int remaining = ((Number) air.getMethod("getAir").invoke(handler.orElseThrow())).intValue();
        check(level.isEmptyBlock(pos) && remaining == 4950 && player.getOffhandItem() == held,
                "offhand pneumatic simulation did not charge air exactly once");
        air.getMethod("addAir", int.class).invoke(handler.orElseThrow(), -remaining);
        level.setBlockAndUpdate(pos, compressor.defaultBlockState()); click(player, level, hit);
        check(!level.isEmptyBlock(pos), "offhand empty pneumatic tool fell back to free removal");
        Files.writeString(Path.of("offhand-verification.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        check(failures.isEmpty(), "offhand mod failures: " + failures);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); player.getInventory().clearContent();
        return "; offhand real mods: " + report.size() + " physical/simulated cases with protection (RS denial parity), glazed terracotta rotation and real durability";
    }

    private static String verifyContainerMenus(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("ae2") || !net.neoforged.fml.ModList.get().isLoaded("sophisticatedbackpacks")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        Class<?> locatorApi = Class.forName("appeng.menu.locator.ItemMenuHostLocator");
        Class<?> locators = Class.forName("appeng.menu.locator.MenuLocators");
        Class<?> toolHost = Class.forName("appeng.items.contents.NetworkToolMenuHost");
        Class<?> nativeMenu = Class.forName("appeng.menu.me.networktool.NetworkToolMenu");
        var report = new com.google.gson.JsonArray();
        for (InteractionHand hand : InteractionHand.values()) {
            player.getInventory().clearContent();
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            ItemStack held = new ItemStack(WrenchBridge.WRENCH.get()); player.setItemInHand(hand, held);
            ItemStack bag = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
            Object wrapper = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper").getMethod("fromStack", ItemStack.class).invoke(null, bag);
            wrapper.getClass().getMethod("setContentsUuid", java.util.UUID.class).invoke(wrapper, java.util.UUID.randomUUID());
            var contents = (net.neoforged.neoforge.items.IItemHandler) wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
            contents.insertItem(0, preparedTool(player, "ae2:network_tool"), false); player.getInventory().setItem(8, bag);
            var source = StoredTools.menuSource(player, "ae2:network_tool"); check(source != null, "missing stored menu source");
            var token = dev.wrenchbridge.ContainerMenus.bind(source);
            Object locator = source.use(tool -> dev.wrenchbridge.NativeMenuContext.runContainer(player, hand, tool, token, () -> {
                try { return locators.getMethod("forHand", Player.class, InteractionHand.class).invoke(null, player, hand); }
                catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
            }), null);
            check(locator != null && locatorApi.getMethod("getPlayerInventorySlot").invoke(locator) == null, "stored menu still needs a player slot");
            Object host = locatorApi.getMethod("locate", Player.class, Class.class).invoke(locator, player, toolHost);
            var menu = (net.minecraft.world.inventory.AbstractContainerMenu) nativeMenu.getConstructor(int.class, net.minecraft.world.entity.player.Inventory.class, toolHost)
                    .newInstance(77, player.getInventory(), host);
            Class.forName("appeng.menu.AEBaseMenu").getMethod("setLocator", Class.forName("appeng.menu.locator.MenuHostLocator")).invoke(menu, locator);
            player.containerMenu = menu;
            ItemStack cards = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:speed_card")), 3);
            menu.setCarried(cards); menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            check(menu.getCarried().isEmpty() && menu.getSlot(0).getItem().getCount() == 3, "native card insertion failed");
            check(contents.getStackInSlot(0).getOrDefault(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY).stream().mapToInt(ItemStack::getCount).sum() == 3,
                    "native card changes did not persist inside the real backpack tool");
            menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            check(menu.getCarried().getCount() == 3 && menu.getSlot(0).getItem().isEmpty(), "native card removal failed");
            menu.setCarried(ItemStack.EMPTY);
            try { source.use(tool -> { throw new IllegalStateException("expected menu callback failure"); }, false); }
            catch (IllegalStateException expected) { check(expected.getMessage().contains("expected menu"), "unexpected callback failure"); }
            check(source.available() && !contents.getStackInSlot(0).isEmpty() && player.getItemInHand(hand) == held, "exception lost a stored menu tool or changed hands");
            player.getInventory().setItem(8, ItemStack.EMPTY);
            check(!source.available() && ((ItemStack) locatorApi.getMethod("locateItem", Player.class).invoke(locator, player)).isEmpty(), "detached backpack stayed accessible");
            check(!source.use(tool -> true, false) && !contents.getStackInSlot(0).isEmpty(), "detached backpack was extracted remotely");
            player.getInventory().setItem(8, bag); player.containerMenu = player.inventoryMenu; dev.wrenchbridge.ContainerMenus.forget(player);
            var row = new com.google.gson.JsonObject(); row.addProperty("hand", hand.name()); row.addProperty("full_player_inventory", true);
            row.addProperty("native_cards_insert_remove_saved", true); row.addProperty("callback_failure_preserves_tool", true); row.addProperty("detached_bag_rejected", true);
            row.addProperty("held_item_preserved", true); report.add(row);
        }
        player.getInventory().clearContent();
        for (String failure : new String[] {"extract-denied", "return-denied", "return-throws"}) {
            menuHandler = new MenuHandler();
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WrenchBridge.WRENCH.get()));
            player.getInventory().setItem(8, new ItemStack(MENU_BAG.get()));
            menuHandler.insertItem(0, preparedTool(player, "ae2:network_tool"), false);
            var source = StoredTools.menuSource(player, "ae2:network_tool");
            check(source != null, "missing controlled menu source");
            menuHandler.denyExtraction = failure.equals("extract-denied");
            menuHandler.denyReturn = failure.equals("return-denied"); menuHandler.throwReturn = failure.equals("return-throws");
            boolean ran = source.use(tool -> {
                tool.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Menu return probe"));
                return true;
            }, false);
            check(ran != menuHandler.denyExtraction, "container refusal ran a native action");
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(8),
                    entity -> entity.getItem().has(DataComponents.CUSTOM_NAME) && entity.getItem().getHoverName().getString().equals("Menu return probe"));
            check(menuHandler.denyExtraction ? drops.isEmpty() && !menuHandler.getStackInSlot(0).isEmpty()
                    : menuHandler.getStackInSlot(0).isEmpty() && drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 1,
                    "refused menu return lost or duplicated a tool");
            drops.forEach(net.minecraft.world.entity.Entity::discard);
            var row = new com.google.gson.JsonObject(); row.addProperty("case", failure); row.addProperty("tool_conserved", true); report.add(row);
        }
        menuHandler = null; player.getInventory().clearContent();
        Files.writeString(Path.of("container-menu-verification.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        return "; stored AE2 menus: main/offhand with full inventory, native card insert/remove persisted in Sophisticated Backpack, callback cleanup, detached bag denied, unchanged held wrench, extraction/return refusal and throwing return conserve tools";
    }

    private static String verifyNetworkMenuContext(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("ae2")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
        BlockPos pos = new BlockPos(14, 108, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.EAST, pos, false);
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:interface")).defaultBlockState());
        Class<?> locators = Class.forName("appeng.menu.locator.MenuLocators");
        Class<?> locatorApi = Class.forName("appeng.menu.locator.ItemMenuHostLocator");
        Class<?> hostApi = Class.forName("appeng.menu.locator.MenuHostLocator");
        Class<?> toolHost = Class.forName("appeng.items.contents.NetworkToolMenuHost");
        var report = new com.google.gson.JsonArray();
        for (InteractionHand hand : InteractionHand.values()) {
            player.getInventory().clearContent();
            ItemStack wrench = new ItemStack(WrenchBridge.WRENCH.get()), source = preparedTool(player, "ae2:network_tool");
            player.setItemInHand(hand, wrench); player.getInventory().setItem(9, source);
            select(new ModePacket("ae2:network_tool", "", hand == InteractionHand.OFF_HAND), player);
            var context = dev.wrenchbridge.VirtualModes.context(player, hand, hit, source);
            Object locator = dev.wrenchbridge.NativeMenuContext.run(player, hand, 9, source, () -> {
                try { return locators.getMethod("forItemUseContext", net.minecraft.world.item.context.UseOnContext.class).invoke(null, context); }
                catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
            });
            check(locatorApi.getMethod("getPlayerInventorySlot").invoke(locator).equals(9), "AE2 menu locator still points at the held universal wrench");
            BlockHitResult bound = (BlockHitResult) locatorApi.getMethod("hitResult").invoke(locator);
            // AE2 records the player's horizontal direction, rather than the clicked face, in this locator.
            check(bound != null && bound.getBlockPos().equals(pos) && bound.getDirection() == context.getHorizontalDirection(), "AE2 menu locator lost the clicked network");
            check(locatorApi.getMethod("locateItem", Player.class).invoke(locator, player) == source, "AE2 menu locator does not resolve the real network tool");
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            Object decoded;
            try {
                locators.getMethod("writeToPacket", net.minecraft.network.FriendlyByteBuf.class, hostApi).invoke(null, buffer, locator);
                decoded = locators.getMethod("readFromPacket", net.minecraft.network.FriendlyByteBuf.class).invoke(null, buffer);
            } finally { buffer.release(); }
            check(locatorApi.getMethod("getPlayerInventorySlot").invoke(decoded).equals(9)
                    && ((BlockHitResult) locatorApi.getMethod("hitResult").invoke(decoded)).getBlockPos().equals(pos), "AE2's menu packet did not preserve source and target");
            Object host = locatorApi.getMethod("locate", Player.class, Class.class).invoke(decoded, player, toolHost);
            Object expected = Class.forName("appeng.api.networking.GridHelper").getMethod("getNodeHost", Level.class, BlockPos.class).invoke(null, level, pos);
            check(host != null && expected != null && toolHost.getMethod("getGridHost").invoke(host) == expected,
                    "network tool host is not bound to the clicked node host");
            Class<?> itemHost = Class.forName("appeng.api.implementations.menuobjects.ItemMenuHost");
            check(itemHost.getMethod("getItemStack").invoke(host) == source && (Boolean) itemHost.getMethod("isValid").invoke(host), "network menu lost its real donor");
            player.getInventory().setItem(9, ItemStack.EMPTY);
            check(!(Boolean) itemHost.getMethod("isValid").invoke(host), "network menu stayed valid after the donor left its source slot");
            player.getInventory().setItem(9, source);
            check(dev.wrenchbridge.NativeMenuContext.inventorySlot(player, hand) == null && player.getItemInHand(hand) == wrench,
                    "native menu binding leaked or replaced a held item");
            Object normal = locators.getMethod("forHand", Player.class, InteractionHand.class).invoke(null, player, hand);
            check(locatorApi.getMethod("getPlayerInventorySlot").invoke(normal).equals(hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40),
                    "normal AE2 tools inherited the simulated source slot");
            var row = new com.google.gson.JsonObject(); row.addProperty("hand", hand.name()); row.addProperty("source_slot", 9);
            row.addProperty("clicked_network_bound", true); row.addProperty("native_packet_roundtrip", true); row.addProperty("missing_source_invalidates", true); row.addProperty("scope_cleanup", true);
            report.add(row);
        }
        player.getInventory().clearContent(); level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        Files.writeString(Path.of("ae2-network-menu-verification.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        return "; AE2 network menu: main/offhand source slot and clicked node host binding, native locator packet roundtrip, missing donor invalidation, no held-item replacement or context leak";
    }

    private static String verifySharedUseIntent(ServerLevel level) throws Exception {
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(12, 106, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.EAST, pos, false);
        player.moveTo(14.5, 107, 0.5, 90, 0);
        var tag = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/wrench"));
        var report = new com.google.gson.JsonArray();
        for (InteractionHand hand : InteractionHand.values()) {
            player.getInventory().clearContent(); player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
            ItemStack wrench = new ItemStack(WrenchBridge.WRENCH.get()); player.setItemInHand(hand, wrench);
            check(!wrench.is(tag) && wrench.getTags().noneMatch(t -> dev.wrenchbridge.core.ToolRules.WRENCH_TAGS.contains(t.location().toString()))
                    && !wrench.getItem().builtInRegistryHolder().is(tag)
                    && !wrench.canPerformAction(net.neoforged.neoforge.common.ItemAbility.get("wrench_rotate")),
                    "shared wrench advertises native actions outside an intentional use");
            if (net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
                Block machine = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:enrichment_chamber"));
                level.setBlockAndUpdate(pos, machine.defaultBlockState()); BlockState initial = level.getBlockState(pos);
                player.gameMode.useItemOn(player, level, wrench, hand, hit);
                check(level.getBlockState(pos).equals(initial) && player.getItemInHand(hand) == wrench,
                        "ordinary shared use rotated a Mekanism machine: " + hand);
                player.closeContainer();
                ItemStack donor = preparedTool(player, "mekanism:configurator"); player.getInventory().setItem(9, donor);
                String sourceMode = NativeModes.current(donor.getItem(), donor);
                select(new ModePacket("mekanism:configurator", "ROTATE", hand == InteractionHand.OFF_HAND), player);
                player.gameMode.useItemOn(player, level, wrench, hand, hit);
                check(!level.getBlockState(pos).equals(initial) && player.getItemInHand(hand) == wrench
                        && NativeModes.current(donor.getItem(), donor).equals(sourceMode), "explicit simulated rotation was lost: " + hand);
                select(new ModePacket("wrenchbridge:universal_wrench", "", hand == InteractionHand.OFF_HAND), player);
                player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                level.setBlockAndUpdate(pos, initial);
                try { deny = true; player.gameMode.useItemOn(player, level, wrench, hand, hit); } finally { deny = false; }
                check(level.getBlockState(pos).equals(initial), "scoped shared use bypassed protection");
                player.gameMode.useItemOn(player, level, wrench, hand, hit);
                check(level.isEmptyBlock(pos), "intent gating broke shared dismantling: " + hand);
                if (net.neoforged.fml.ModList.get().isLoaded("create")) {
                    player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
                    Block shaft = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:shaft"));
                    BlockState shaftInitial = shaft.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X);
                    BlockHitResult shaftHit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                    level.setBlockAndUpdate(pos, shaftInitial);
                    player.gameMode.useItemOn(player, level, wrench, hand, shaftHit);
                    check(level.getBlockState(pos).equals(shaftInitial), "shared ordinary use rotated a Create shaft");
                    ItemStack original = preparedTool(player, "mekanism:configurator"); player.setItemInHand(hand, original);
                    player.gameMode.useItemOn(player, level, original, hand, shaftHit);
                    BlockState nativeResult = level.getBlockState(pos);
                    check(nativeResult.getBlock() == shaft && !nativeResult.equals(shaftInitial), "native configurator WRENCH did not rotate Create shaft");
                    player.setItemInHand(hand, wrench);
                    NativeModes.select(donor.getItem(), donor, player, "CONFIGURATE_ITEMS");
                    level.setBlockAndUpdate(pos, shaftInitial);
                    select(new ModePacket("mekanism:configurator", "WRENCH", hand == InteractionHand.OFF_HAND), player);
                    player.gameMode.useItemOn(player, level, wrench, hand, shaftHit);
                    check(level.getBlockState(pos).equals(nativeResult) && player.getItemInHand(hand) == wrench
                            && player.getInventory().getItem(9) == donor && NativeModes.current(donor.getItem(), donor).equals("CONFIGURATE_ITEMS"),
                            "simulated configurator WRENCH did not match native Create rotation or changed the real tool");
                    select(new ModePacket("wrenchbridge:universal_wrench", "", hand == InteractionHand.OFF_HAND), player);
                }
            }
            // A mod unknown to Targets still receives conventional wrench traits during the native event.
            level.setBlockAndUpdate(pos, NATIVE_UNKNOWN.get().defaultBlockState());
            check(dev.wrenchbridge.Targets.resolve(level.getBlockState(pos), null) == dev.wrenchbridge.Targets.Kind.UNKNOWN, "native unknown fixture became a known target");
            int calls = unknownNativeCalls;
            player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
            player.gameMode.useItemOn(player, level, wrench, hand, hit);
            check(!level.isEmptyBlock(pos) && unknownNativeCalls == calls, "ordinary use triggered an unknown mod's wrench action");
            player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            player.gameMode.useItemOn(player, level, wrench, hand, hit);
            check(level.isEmptyBlock(pos) && unknownNativeCalls == calls + 1, "unknown native event lost shared sneak compatibility");
            level.setBlockAndUpdate(pos, NATIVE_UNKNOWN.get().defaultBlockState());
            boolean rejected = false;
            try {
                throwInNativeUnknown = true;
                player.gameMode.useItemOn(player, level, wrench, hand, hit);
            } catch (RuntimeException error) { rejected = true; }
            finally { throwInNativeUnknown = false; }
            check(rejected && dev.wrenchbridge.WrenchContext.hand() == null && !wrench.is(tag)
                    && !wrench.getItem().builtInRegistryHolder().is(tag) && !dev.wrenchbridge.WrenchContext.forced(wrench),
                    "native exception leaked wrench traits into later clicks");
            check(player.getItemInHand(hand) == wrench, "intent gating changed the real held item");
            var row = new com.google.gson.JsonObject(); row.addProperty("hand", hand.name());
            row.addProperty("ordinary_mekanism_rotation", false); row.addProperty("explicit_simulated_rotation", true);
            row.addProperty("native_configurator_wrench_create_rotation", true); row.addProperty("simulated_configurator_wrench_create_rotation", true);
            row.addProperty("shared_sneak_dismantle", true); row.addProperty("protection", true);
            row.addProperty("unknown_native_sneak_compatibility", true); row.addProperty("exception_cleanup", true); report.add(row);
        }
        player.getInventory().clearContent(); player.setShiftKeyDown(false); player.setPose(net.minecraft.world.entity.Pose.STANDING);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        Files.writeString(Path.of("shared-intent-verification.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        return "; shared intent: main/offhand ordinary Mekanism/Create use does not rotate, explicit ROTATE and native/simulated configurator WRENCH Create rotation retained, shared sneak pickup, protection, unknown native event compatibility and exception cleanup";
    }

    private static String verifyPrettyPipeLimits(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("prettypipes")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos pos = new BlockPos(10, 104, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var report = new com.google.gson.JsonArray();
        for (String id : new String[] {"prettypipes:item_terminal", "prettypipes:pressurizer"})
                for (InteractionHand hand : InteractionHand.values())
                for (String source : new String[] {"prettypipes:wrench", "wrenchbridge:universal_wrench", "mekanism:configurator", "ae2:certus_quartz_wrench", "simulate_pretty"}) {
            player.getInventory().clearContent(); player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            ItemStack held = source.equals("simulate_pretty") ? new ItemStack(WrenchBridge.WRENCH.get()) : preparedTool(player, source);
            player.setItemInHand(hand, held);
            if (source.equals("simulate_pretty")) {
                player.getInventory().setItem(9, preparedTool(player, "prettypipes:wrench"));
                select(new ModePacket("prettypipes:wrench", "", hand == InteractionHand.OFF_HAND), player);
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState());
            check(dev.wrenchbridge.Targets.resolve(level.getBlockState(pos), level.getBlockEntity(pos)) == dev.wrenchbridge.Targets.Kind.UNKNOWN,
                    "unsupported Pretty Pipes target was forced into a dismantle protocol");
            player.gameMode.useItemOn(player, level, held, hand, hit);
            check(!level.isEmptyBlock(pos) && player.getItemInHand(hand) == held, "Pretty Pipes native pickup limit was bypassed: " + id + " / " + source);
            var row = new com.google.gson.JsonObject(); row.addProperty("block", id); row.addProperty("hand", hand.name());
            row.addProperty("source", source); row.addProperty("removed", false); row.addProperty("reference", "native Pretty Pipes wrench only picks up PipeBlock; mine these machines normally");
            report.add(row);
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); player.getInventory().clearContent();
        Files.writeString(Path.of("pretty-pipes-limits.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        return "; Pretty Pipes limits: " + report.size() + " native/shared/simulated main/offhand terminal and pressurizer refusal parity cases";
    }

    private static String verifyConfiguratorViews(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        int cases = 0;
        for (InteractionHand hand : InteractionHand.values()) {
            player.getInventory().clearContent();
            ItemStack held = new ItemStack(WrenchBridge.WRENCH.get()); player.setItemInHand(hand, held);
            ItemStack donor = preparedTool(player, "mekanism:configurator"); player.getInventory().setItem(9, donor);
            ItemStack before = donor.copy();
            for (String mode : NativeModes.choices(donor.getItem(), donor, player)) {
                select(new ModePacket("mekanism:configurator", mode, hand == InteractionHand.OFF_HAND), player);
                ItemStack saved = held.copy();
                ItemStack view = dev.wrenchbridge.ConfiguratorViews.create(player, held, donor);
                check(view != donor && view.is(donor.getItem()) && NativeModes.current(view.getItem(), view).equals(mode), "render view did not carry the selected native mode");
                check(ItemStack.isSameItemSameComponents(donor, before) && ItemStack.isSameItemSameComponents(held, saved)
                        && player.getItemInHand(hand) == held && player.getInventory().getItem(9) == donor, "rendering changed a real item or its native settings");
                check(dev.wrenchbridge.ConfiguratorViews.create(player, held, ItemStack.EMPTY).isEmpty(), "render view invented a missing source");
                cases++;
            }
            select(new ModePacket("wrenchbridge:universal_wrench", "", hand == InteractionHand.OFF_HAND), player);
            check(dev.wrenchbridge.ConfiguratorViews.create(player, held, donor).isEmpty(), "shared wrench gained a configurator overlay");
        }
        player.getInventory().clearContent();
        return "; Mekanism render views: " + cases + " mode/hand cases, native components, unchanged real tool/held identity, absent source and shared mode rejected";
    }

    private static String verifyIntegratedMachines(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("integrateddynamics")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(10, 104, 0);
        player.moveTo(10.5, 105, 2.5, 180, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var family = new java.util.ArrayList<Block>();
        for (Block block : BuiltInRegistries.BLOCK)
            if (dev.wrenchbridge.core.ClassProbe.is(block, "org.cyclops.integrateddynamics.core.block.BlockWithEntityGuiCabled")) family.add(block);
        check(!family.isEmpty(), "no GUI-cabled machines found");
        var report = new com.google.gson.JsonArray();
        for (Block block : family) for (InteractionHand hand : InteractionHand.values())
                for (String flow : new String[] {"native", "universal", "mekanism", "simulate_id", "simulate_supplementaries"}) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            player.getInventory().clearContent(); player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            if (hand == InteractionHand.OFF_HAND) player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            String sourceId = switch (flow) {
                case "native", "simulate_id" -> "integrateddynamics:wrench";
                case "mekanism" -> "mekanism:configurator";
                case "simulate_supplementaries" -> "supplementaries:wrench";
                default -> "wrenchbridge:universal_wrench";
            };
            ItemStack donor = preparedTool(player, sourceId);
            ItemStack held = flow.startsWith("simulate_") ? new ItemStack(WrenchBridge.WRENCH.get()) : donor;
            player.setItemInHand(hand, held);
            if (flow.startsWith("simulate_")) {
                player.getInventory().setItem(9, donor);
                select(new ModePacket(sourceId, sourceId.startsWith("integrateddynamics:") ? "DEFAULT" : "", hand == InteractionHand.OFF_HAND), player);
            }
            var box = new net.minecraft.world.phys.AABB(pos).inflate(3);
            for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) entity.discard();
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(pos, block.defaultBlockState());
            block.setPlacedBy(level, pos, level.getBlockState(pos), player, new ItemStack(block.asItem()));
            check(dev.wrenchbridge.Targets.resolve(level.getBlockState(pos), level.getBlockEntity(pos)) == dev.wrenchbridge.Targets.Kind.COMMON,
                    "GUI-cabled machine was not detected: " + id);
            boolean squeezer = id.equals("integrateddynamics:mechanical_squeezer");
            if (squeezer) {
                Object machine = level.getBlockEntity(pos);
                machine.getClass().getMethod("setEnergy", int.class).invoke(machine, 12345);
                var inventory = (net.minecraft.world.Container) machine.getClass().getMethod("getInventory").invoke(machine);
                inventory.setItem(0, new ItemStack(Items.COBBLESTONE, 7));
                var tank = (net.neoforged.neoforge.fluids.capability.IFluidHandler) machine.getClass().getMethod("getTank").invoke(machine);
                check(tank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 750),
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 750, "could not seed squeezer tank");
            }
            var action = click(player, level, hit);
            check(level.isEmptyBlock(pos), "GUI-cabled dismantling failed: " + id + " / " + hand + " / " + flow + " / " + action
                    + " / last hand=" + lastClickHand + " / native mode=" + NativeModes.current(donor.getItem(), donor));
            check(player.getItemInHand(hand) == held, "GUI-cabled dismantling replaced a tool");
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).toList();
            check(drops.stream().filter(stack -> stack.is(block.asItem())).mapToInt(ItemStack::getCount).sum() == 1,
                    "GUI-cabled dismantling did not drop exactly one machine: " + id + " / " + flow);
            var row = new com.google.gson.JsonObject(); row.addProperty("block", id); row.addProperty("hand", hand.name()); row.addProperty("flow", flow);
            row.addProperty("removed", true); row.addProperty("machine_drops", 1);
            if (squeezer) {
                check(drops.stream().filter(stack -> stack.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum() == 7,
                        "squeezer inventory lost or duplicated: " + hand + " / " + flow);
                ItemStack item = drops.stream().filter(stack -> stack.is(block.asItem())).findFirst().orElseThrow().copy();
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                block.setPlacedBy(level, pos, level.getBlockState(pos), player, item);
                Object restored = level.getBlockEntity(pos);
                check(((net.neoforged.neoforge.energy.IEnergyStorage) restored).getEnergyStored() == 12345,
                        "squeezer energy lost on replacement: " + hand + " / " + flow);
                var tank = (net.neoforged.neoforge.fluids.capability.IFluidHandler) restored.getClass().getMethod("getTank").invoke(restored);
                check(tank.getFluidInTank(0).getFluid() == net.minecraft.world.level.material.Fluids.WATER && tank.getFluidInTank(0).getAmount() == 750,
                        "squeezer fluid lost on replacement: " + hand + " / " + flow);
                row.addProperty("contents", 7); row.addProperty("restored_energy", 12345); row.addProperty("restored_water_mb", 750);
            }
            if (!(flow.equals("native") && hand == InteractionHand.MAIN_HAND)) {
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                try { deny = true; click(player, level, hit); } finally { deny = false; }
                check(!level.isEmptyBlock(pos), "GUI-cabled dismantling bypassed protection: " + id + " / " + hand + " / " + flow);
                row.addProperty("protected", true);
            }
            report.add(row);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        // Native offset mode still owns its own machine; extending offhand dismantle must not block configuration.
        for (InteractionHand hand : InteractionHand.values()) {
            player.getInventory().clearContent();
            ItemStack tool = preparedTool(player, "integrateddynamics:wrench");
            check(NativeModes.select(tool.getItem(), tool, player, "OFFSET"), "could not select native OFFSET mode");
            player.setItemInHand(hand, tool);
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("integrateddynamics:mechanical_squeezer")).defaultBlockState());
            player.gameMode.useItemOn(player, level, tool, hand, hit);
            check(!level.isEmptyBlock(pos) && NativeModes.current(tool.getItem(), tool).equals("OFFSET"), "native machine offset mode was overridden");
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); player.getInventory().clearContent();
        Files.writeString(Path.of("integrated-machine-verification.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        return "; Integrated Dynamics GUI machines: " + family.size() + " registered blocks, " + report.size()
                + " native/shared/simulated main/offhand cases, one machine drop, squeezer contents/12345 FE/750 mB preserved, protection, native OFFSET retained";
    }

    private static String verifyGlazedTerracottaRotation(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("supplementaries")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent(); player.setShiftKeyDown(false);
        player.setPose(net.minecraft.world.entity.Pose.STANDING);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(4, 98, 0);
        player.moveTo(4.5, 99, 2.5, 180, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(0.5, 1, 0.5), Direction.UP, pos, false);
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        BlockState before = Blocks.WHITE_GLAZED_TERRACOTTA.defaultBlockState().setValue(facing, Direction.NORTH);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        ItemStack original = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("supplementaries:wrench")));
        player.setItemInHand(InteractionHand.MAIN_HAND, original); level.setBlockAndUpdate(pos, before);
        player.gameMode.useItemOn(player, level, original, InteractionHand.MAIN_HAND, hit);
        check(level.getBlockState(pos).getValue(facing) != Direction.NORTH, "native Supplementaries wrench did not rotate glazed terracotta");
        ItemStack universal = new ItemStack(WrenchBridge.WRENCH.get());
        ItemStack source = new ItemStack(original.getItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, universal); player.getInventory().setItem(9, source);
        level.setBlockAndUpdate(pos, before);
        player.gameMode.useItemOn(player, level, universal, InteractionHand.MAIN_HAND, hit);
        check(level.getBlockState(pos).equals(before), "default dismantle mode unexpectedly rotated glazed terracotta");
        select(new ModePacket("supplementaries:wrench", ""), player);
        int damage = source.getDamageValue();
        long start = System.nanoTime();
        player.gameMode.useItemOn(player, level, universal, InteractionHand.MAIN_HAND, hit);
        check(level.getBlockState(pos).getValue(facing) != Direction.NORTH, "simulated Supplementaries wrench did not rotate glazed terracotta");
        check(player.getMainHandItem() == universal && player.getInventory().getItem(9) == source,
                "rotation simulation changed item identity or inventory location");
        check(source.getDamageValue() == damage + 1, "rotation did not consume the real wrench's durability");
        WrenchBridge.LOG.info("Glazed terracotta simulated rotation took {} ms", (System.nanoTime()-start)/1_000_000.0);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return "; glazed terracotta: default unchanged, native and simulated Supplementaries rotation, real durability";
    }

    private static String verifyExpandedMods(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("pneumaticcraft")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent(); player.setShiftKeyDown(true);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        BlockPos pos = new BlockPos(0, 95, 0);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        player.moveTo(0.5, 96, 0.5, 0, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(0.5, 1, 0.5), Direction.UP, pos, false);
        String[] targets = {"refinedstorage:controller", "enderio:alloy_smelter", "pneumaticcraft:air_compressor",
                "prettypipes:pipe", "powah:energy_cell_starter", "rftoolsutility:crafter1",
                "integrateddynamics:energy_battery", "moderndynamics:item_pipe",
                "minecraft:observer", "minecraft:chest", "minecraft:furnace", "minecraft:shulker_box"};
        String[] tools = {"refinedstorage:wrench", "enderio:yeta_wrench", "pneumaticcraft:pneumatic_wrench",
                "prettypipes:wrench", "powah:wrench", "rftoolsbase:smartwrench",
                "integrateddynamics:wrench", "moderndynamics:wrench", "pipez:wrench", "supplementaries:wrench"};
        java.util.List<String> failures = new java.util.ArrayList<>();
        com.google.gson.JsonArray report = new com.google.gson.JsonArray();
        int pairs = 0;
        for (String id : targets) {
            var key = ResourceLocation.parse(id);
            if (!BuiltInRegistries.BLOCK.containsKey(key)) { failures.add("missing target " + id); continue; }
            Block block = BuiltInRegistries.BLOCK.get(key);
            var row = new com.google.gson.JsonObject(); row.addProperty("target", id);
            row.addProperty("enabled", block.isEnabled(level.enabledFeatures()));
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            row.addProperty("class", block.getClass().getName());
            row.addProperty("protocol", dev.wrenchbridge.Targets.resolve(level.getBlockState(pos), level.getBlockEntity(pos)).name());
            String nativeTool = switch (key.getNamespace()) {
                case "refinedstorage" -> "refinedstorage:wrench";
                case "enderio" -> "enderio:yeta_wrench";
                case "pneumaticcraft" -> "pneumaticcraft:pneumatic_wrench";
                case "prettypipes" -> "prettypipes:wrench";
                case "powah" -> "powah:wrench";
                case "rftoolsutility" -> "rftoolsbase:smartwrench";
                case "integrateddynamics" -> "integrateddynamics:wrench";
                case "moderndynamics" -> "moderndynamics:wrench";
                default -> "anvilcraft:anvil_hammer";
            };
            var references = new java.util.LinkedHashSet<>(java.util.List.of("wrenchbridge:universal_wrench", "ae2:certus_quartz_wrench", "anvilcraft:anvil_hammer", nativeTool));
            for (String source : references) {
                player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(pos, block.defaultBlockState());
                block.setPlacedBy(level, pos, level.getBlockState(pos), player, new ItemStack(block.asItem()));
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(source)));
                if (source.equals("pneumaticcraft:pneumatic_wrench")) {
                    var air = (java.util.Optional<?>) Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities")
                            .getMethod("getAirHandler", ItemStack.class).invoke(null, stack);
                    Class.forName("me.desht.pneumaticcraft.api.tileentity.IAirHandler").getMethod("addAir", int.class).invoke(air.orElseThrow(), 5000);
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                try {
                    var action = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
                    boolean removed = level.isEmptyBlock(pos);
                    row.addProperty(source, removed);
                    row.addProperty(source + ":result", action.name());
                    if (!removed || player.getMainHandItem() != stack) failures.add(source + " -> " + id);
                    if (!(source.equals(nativeTool) && !key.getNamespace().equals("minecraft"))) {
                        deny = true;
                        level.setBlockAndUpdate(pos, block.defaultBlockState());
                        player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
                        if (level.isEmptyBlock(pos)) failures.add("protection: " + source + " -> " + id);
                    }
                    pairs++;
                } catch (Throwable error) {
                    row.addProperty(source, error.toString()); failures.add(source + " -> " + id + ": " + error);
                } finally { deny = false; }
            }
            if (id.equals("refinedstorage:controller") && row.has(nativeTool) && !row.get(nativeTool).getAsBoolean()) {
                // RS's original wrench also refuses this freshly placed test target through canBuild.
                // Check denial parity rather than bypassing the mod's permission decision.
                row.addProperty("reference", "native wrench also refused; denial parity only");
                for (String source : references) {
                    if (!row.get(source).getAsBoolean()) failures.remove(source + " -> " + id);
                    else failures.add("RS native denial was bypassed by " + source);
                }
            }
            report.add(row);
        }
        for (String id : tools) {
            var key = ResourceLocation.parse(id);
            if (!BuiltInRegistries.ITEM.containsKey(key)) { failures.add("missing tool " + id); continue; }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(key));
            if (id.equals("pneumaticcraft:pneumatic_wrench")) {
                var handler = (java.util.Optional<?>) Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities")
                        .getMethod("getAirHandler", ItemStack.class).invoke(null, stack);
                Class.forName("me.desht.pneumaticcraft.api.tileentity.IAirHandler")
                        .getMethod("addAir", int.class).invoke(handler.orElseThrow(), 5000);
            }
            java.util.List<String> modes = NativeModes.choices(stack.getItem(), stack, player);
            for (String preferred : new String[] {"WRENCH", "ALL", "DEFAULT", "NORMAL"}) {
                if (modes.contains(preferred)) { NativeModes.select(stack.getItem(), stack, player, preferred); break; }
            }
            var row = new com.google.gson.JsonObject(); row.addProperty("tool", id);
            row.addProperty("candidate", dev.wrenchbridge.Tools.candidate(stack));
            row.addProperty("active", dev.wrenchbridge.Tools.active(stack));
            row.addProperty("mode", NativeModes.current(stack.getItem(), stack));
            row.addProperty("modes", modes.toString());
            for (String target : new String[] {"ae2:interface", "minecraft:observer"}) {
                player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(target)).defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                try {
                    var action = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
                    boolean removed = level.isEmptyBlock(pos); row.addProperty(target, removed);
                    row.addProperty(target + ":result", action.name());
                    if (!removed || player.getMainHandItem() != stack) failures.add(id + " -> " + target);
                    pairs++;
                } catch (Throwable error) { row.addProperty(target, error.toString()); failures.add(id + " -> " + target + ": " + error); }
            }
            report.add(row);
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        ItemStack virtual = new ItemStack(WrenchBridge.WRENCH.get());
        ItemStack pneumatic = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("pneumaticcraft:pneumatic_wrench")));
        player.getInventory().clearContent(); player.setItemInHand(InteractionHand.MAIN_HAND, virtual); player.getInventory().setItem(9, pneumatic);
        var air = (java.util.Optional<?>) Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities")
                .getMethod("getAirHandler", ItemStack.class).invoke(null, pneumatic);
        Class<?> airApi = Class.forName("me.desht.pneumaticcraft.api.tileentity.IAirHandler");
        airApi.getMethod("addAir", int.class).invoke(air.orElseThrow(), 5000);
        select(new ModePacket("pneumaticcraft:pneumatic_wrench", ""), player);
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pneumaticcraft:air_compressor")).defaultBlockState());
        player.gameMode.useItemOn(player, level, virtual, InteractionHand.MAIN_HAND, hit);
        int remaining = ((Number) airApi.getMethod("getAir").invoke(air.orElseThrow())).intValue();
        check(level.isEmptyBlock(pos) && remaining == 4950 && player.getMainHandItem() == virtual
                && player.getInventory().getItem(9) == pneumatic, "pneumatic simulation did not preserve identity and consume real air");
        airApi.getMethod("addAir", int.class).invoke(air.orElseThrow(), -remaining);
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pneumaticcraft:air_compressor")).defaultBlockState());
        player.gameMode.useItemOn(player, level, virtual, InteractionHand.MAIN_HAND, hit);
        check(!level.isEmptyBlock(pos), "empty pneumatic simulation fell back to free dismantling");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        for (int area = 1; area <= 16; area++) check(level.getServer().getFunctions()
                .get(ResourceLocation.parse(String.format(java.util.Locale.ROOT, "wrenchbridge_lab:reset_%02d", area))).isPresent(), "lab reset function failed to parse: " + area);
        check(level.getServer().getFunctions().get(ResourceLocation.parse("wrenchbridge_lab:air_wrench")).isPresent(), "charged wrench command failed to parse");
        com.google.gson.JsonArray hammerCoverage = new com.google.gson.JsonArray();
        var hammerTag = TagKey.create(Registries.BLOCK, ResourceLocation.parse("anvilcraft:hammer_removable"));
        for (Block block : BuiltInRegistries.BLOCK) if (block.defaultBlockState().is(hammerTag)
                || dev.wrenchbridge.core.ClassProbe.is(block, "dev.dubhe.anvilcraft.api.hammer.IHammerRemovable"))
            hammerCoverage.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        Files.writeString(Path.of("anvil-hammer-coverage.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(hammerCoverage));
        Files.writeString(Path.of("expanded-verification.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        check(failures.isEmpty(), "expanded compatibility failures: " + failures);
        return "; expanded lab: " + pairs + " target/source cases (RS native-denial parity), cross-mod protection, vanilla hammer targets, real pneumatic air consumption and empty denial, 16 reset areas parsed";
    }

    private static String verifyModeUiContracts(ServerLevel level) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent();
        ItemStack wrench = new ItemStack(WrenchBridge.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        check(wrench.getItem().use(level, player, InteractionHand.MAIN_HAND).getResult()
                == net.minecraft.world.InteractionResult.PASS, "air use still consumes the mode-menu action");
        check(player.getMainHandItem() == wrench, "air use replaced the wrench");
        if (net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
            ItemStack tool = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:configurator")));
            String before = NativeModes.current(tool.getItem(), tool);
            var name = dev.wrenchbridge.NativeModeNames.mode(tool, "CONFIGURATE_ITEMS");
            check(name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translation
                    && translation.getKey().equals("configurator.mekanism.configurate"),
                    "mode label did not retain Mekanism's translatable component");
            var choice = dev.wrenchbridge.NativeModeNames.choice(tool, "CONFIGURATE_ITEMS");
            check(choice.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translation
                    && translation.getKey().equals("screen.wrenchbridge.entry")
                    && translation.getArgs()[1] instanceof net.minecraft.network.chat.Component,
                    "mode label was flattened instead of composing native translations");
            check(before.equals(NativeModes.current(tool.getItem(), tool)), "reading mode labels changed the tool mode");
        }
        return "; GUI contracts: air use passes, native translation components, unchanged source mode";
    }
    private static String verifyBackpack(ServerLevel level) throws ReflectiveOperationException {
        return verifyBackpackHand(level, InteractionHand.MAIN_HAND) + verifyBackpackHand(level, InteractionHand.OFF_HAND);
    }
    private static String verifyBackpackHand(ServerLevel level, InteractionHand hand) throws ReflectiveOperationException {
        if (!net.neoforged.fml.ModList.get().isLoaded("sophisticatedbackpacks")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent(); player.setShiftKeyDown(true);
        ItemStack backpack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
        Object wrapper = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper")
                .getMethod("fromStack", ItemStack.class).invoke(null, backpack);
        wrapper.getClass().getMethod("setContentsUuid", java.util.UUID.class).invoke(wrapper, java.util.UUID.randomUUID());
        var items = (net.neoforged.neoforge.items.IItemHandler) wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
        ItemStack nativeTool = new ItemStack(MODE_TOOL.get());
        nativeTool.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Preserve me"));
        check(items.insertItem(0, nativeTool, false).isEmpty(), "could not prepare backpack tool");
        player.getInventory().setItem(8, backpack);
        ItemStack wrench = new ItemStack(WrenchBridge.WRENCH.get()); player.setItemInHand(hand, wrench);
        check(StoredTools.catalog(player).contains("wrenchbridge:mode_wrench|WRENCH"), "backpack contents not discovered");
        select(new ModePacket("wrenchbridge:mode_wrench", "WRENCH", hand == InteractionHand.OFF_HAND), player);
        check(player.getItemInHand(hand) == wrench && items.getStackInSlot(0).is(MODE_TOOL.get()), "selection moved a backpack item");
        check(NativeModes.current(MODE_TOOL.get(), items.getStackInSlot(0)).equals("CONFIG"), "selection changed the backpack tool mode");
        BlockPos pos = new BlockPos(0, 90, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
        player.gameMode.useItemOn(player, level, wrench, hand, hit);
        check(level.isEmptyBlock(pos) && player.getItemInHand(hand) == wrench, "backpack-backed wrench simulation failed");
        check(items.getStackInSlot(0).getHoverName().getString().equals("Preserve me"), "simulation lost native components");
        check(NativeModes.current(MODE_TOOL.get(), items.getStackInSlot(0)).equals("CONFIG"), "simulation changed the native mode");
        for (int slot = 0; slot < 36; slot++) if (slot != 8 && !(hand == InteractionHand.MAIN_HAND && slot == player.getInventory().selected)) player.getInventory().setItem(slot, new ItemStack(Items.STONE));
        select(new ModePacket("wrenchbridge:mode_wrench", "CONFIG", hand == InteractionHand.OFF_HAND), player);
        level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
        int damage = items.getStackInSlot(0).getDamageValue();
        player.gameMode.useItemOn(player, level, wrench, hand, hit);
        check(player.getItemInHand(hand) == wrench && items.getStackInSlot(0).getDamageValue() == damage + 1, "full-inventory simulation did not charge the real source");
        check(!level.isEmptyBlock(pos), "configuration simulation dismantled a target");
        deny = true;
        player.gameMode.useItemOn(player, level, wrench, hand, hit);
        check(items.getStackInSlot(0).getDamageValue() == damage + 1, "denied simulation consumed the source");
        deny = false;
        ItemStack before = wrench.copy();
        select(new ModePacket("wrenchbridge:mode_wrench", "INVALID", hand == InteractionHand.OFF_HAND), player);
        check(ItemStack.isSameItemSameComponents(wrench, before), "invalid profile modified the wrench");
        items.extractItem(0, 1, false);
        int calls = simulatedCalls;
        player.gameMode.useItemOn(player, level, wrench, hand, hit);
        check(simulatedCalls == calls && player.getItemInHand(hand) == wrench, "missing source synthesized a native tool");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        return "; Sophisticated Backpacks " + hand + ": virtual selection, stable held item, native mode preservation, real durability, full inventory, missing source";
    }
    private static String verifyInstalledMods(ServerLevel level) throws ReflectiveOperationException {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) return " (optional mods absent)";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent(); player.setShiftKeyDown(true);
        BlockPos pos=new BlockPos(0,90,0);
        BlockHitResult hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        String[] targets={"ae2:interface","mekanism:basic_energy_cube","create:shaft","anvilcraft:royal_steel_block"};
        String[] tools={"wrenchbridge:foreign_wrench","wrenchbridge:universal_wrench","mekanism:configurator","ae2:certus_quartz_wrench","create:wrench","anvilcraft:anvil_hammer"};
        for(String tool:tools) for(String target:targets) {
            if(tool.split(":")[0].equals(target.split(":")[0])) continue;
            ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(tool)));
            if(tool.equals("mekanism:configurator")) NativeModes.select(stack.getItem(),stack,player,"WRENCH");
            player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse(target)).defaultBlockState());
            player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,hit);
            check(level.isEmptyBlock(pos)&&player.getMainHandItem()==stack,"shared pair failed: "+tool+" -> "+target);
            deny=true;level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse(target)).defaultBlockState());
            player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,hit);deny=false;
            check(!level.isEmptyBlock(pos),"protected shared pair removed: "+tool+" -> "+target);
        }
        ItemStack wrench=new ItemStack(WrenchBridge.WRENCH.get());player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
        for(int index=2;index<tools.length;index++) {
            String id=tools[index];ItemStack donor=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            player.getInventory().setItem(9,donor);String originalMode=NativeModes.current(donor.getItem(),donor);
            select(new ModePacket(id,id.equals("mekanism:configurator")?"WRENCH":""),player);
            for(String target:targets) {
                level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse(target)).defaultBlockState());
                player.gameMode.useItemOn(player,level,wrench,InteractionHand.MAIN_HAND,hit);
                check(level.isEmptyBlock(pos),"virtual pair failed: "+id+" -> "+target);
                check(player.getMainHandItem()==wrench&&player.getInventory().getItem(9)==donor,"virtual pair moved inventory");
                check(NativeModes.current(donor.getItem(),donor).equals(originalMode),"virtual pair changed native mode");
            }
        }
        ItemStack configurator=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:configurator")));
        player.getInventory().setItem(9,configurator);select(new ModePacket("mekanism:configurator","CONFIGURATE_ITEMS"),player);
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:interface")).defaultBlockState());
        player.gameMode.useItemOn(player,level,wrench,InteractionHand.MAIN_HAND,hit);
        check(!level.isEmptyBlock(pos),"simulated configuration dismantled AE2");
        BlockPos rotatePos=new BlockPos(3,90,0);
        level.setBlockAndUpdate(rotatePos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:enrichment_chamber")).defaultBlockState());
        select(new ModePacket("mekanism:configurator","ROTATE"),player);player.setShiftKeyDown(false);
        player.gameMode.useItemOn(player,level,wrench,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(rotatePos),Direction.EAST,rotatePos,false));
        BlockState state=level.getBlockState(rotatePos);
        Direction facing=state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)?state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING):state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        check(facing==Direction.EAST&&player.getMainHandItem()==wrench,"virtual Mekanism rotation failed");
        check(NativeModes.current(configurator.getItem(),configurator).equals("CONFIGURATE_ITEMS"),"rotation changed original mode");
        level.setBlockAndUpdate(rotatePos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        return "; real mods: 20 physical pairs with protection, 16 virtual pairs, Mekanism CONFIG/WRENCH/ROTATE";
    }
    private static void select(ModePacket packet, ServerPlayer player) throws ReflectiveOperationException {
        java.lang.reflect.Method method = ModePacket.class.getDeclaredMethod("select", ModePacket.class, ServerPlayer.class);
        method.setAccessible(true);
        method.invoke(null, packet, player);
    }
    private static void verify(ServerLevel level) throws ReflectiveOperationException {
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true); player.getInventory().clearContent();
        BlockPos pos = new BlockPos(0, 90, 0); player.moveTo(0.5, 91, 0.5, 0, 0);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack source = new ItemStack(TOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, source);
        level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
        player.gameMode.useItemOn(player, level, source, InteractionHand.MAIN_HAND, hit);
        check(level.isEmptyBlock(pos), "shared tool did not invoke the native target protocol");
        check(nativeCalls == 1, "native target ran more than once");
        check(player.getMainHandItem() == source, "shared dispatch did not restore the original stack");

        deny = true; nativeCalls = 0;
        level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
        player.gameMode.useItemOn(player, level, source, InteractionHand.MAIN_HAND, hit);
        check(!level.isEmptyBlock(pos) && nativeCalls == 0, "protected target changed");
        check(player.getMainHandItem() == source, "rejected dispatch changed inventory");
        deny = false;

        denyUse = true;
        player.gameMode.useItemOn(player, level, source, InteractionHand.MAIN_HAND, hit);
        check(!level.isEmptyBlock(pos) && nativeCalls == 0, "explicitly disabled block interaction was bypassed");
        denyUse = false;

        ItemStack mode = new ItemStack(MODE_TOOL.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, mode);
        level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
        player.gameMode.useItemOn(player, level, mode, InteractionHand.MAIN_HAND, hit);
        check(!level.isEmptyBlock(pos) && nativeCalls == 0, "configuration mode dismantled a target");
        check(NativeModes.choices(mode.getItem(), mode, player).size() == 2, "native modes were not detected");

        ItemStack wrench=new ItemStack(WrenchBridge.WRENCH.get());player.getInventory().setItem(9,wrench);
        select(new ModePacket("wrenchbridge:universal_wrench",""),player);
        check(player.getMainHandItem()==mode&&player.getInventory().getItem(9)==wrench,"native tool gained the universal menu");
        player.getInventory().setItem(9,mode);player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
        select(new ModePacket("wrenchbridge:mode_wrench","WRENCH"),player);
        check(player.getMainHandItem()==wrench&&player.getInventory().getItem(9)==mode,"selection swapped inventory");
        check(VirtualModes.mode(wrench).equals("WRENCH")&&NativeModes.current(MODE_TOOL.get(),mode).equals("CONFIG"),"selection changed native mode");
        player.gameMode.useItemOn(player,level,wrench,InteractionHand.MAIN_HAND,hit);
        check(level.isEmptyBlock(pos)&&player.getMainHandItem()==wrench&&NativeModes.current(MODE_TOOL.get(),mode).equals("CONFIG"),"virtual dismantling failed or changed identity");
        select(new ModePacket("wrenchbridge:mode_wrench","CONFIG"),player);level.setBlockAndUpdate(pos,MACHINE.get().defaultBlockState());
        player.gameMode.useItemOn(player,level,wrench,InteractionHand.MAIN_HAND,hit);
        check(!level.isEmptyBlock(pos)&&mode.getDamageValue()==1&&player.getMainHandItem()==wrench,"native simulation did not retain identity and real consumption");
        tryReplace=true;
        player.gameMode.useItemOn(player,level,wrench,InteractionHand.MAIN_HAND,hit);
        tryReplace=false;
        check(player.getMainHandItem()==wrench,"native callback replaced the universal wrench");
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++)check(!player.getInventory().getItem(slot).is(Items.DIAMOND),"native callback created a conversion output");
        ItemStack before=wrench.copy();select(new ModePacket("minecraft:diamond",""),player);select(new ModePacket("wrenchbridge:mode_wrench","INVALID"),player);
        check(ItemStack.isSameItemSameComponents(wrench,before),"invalid packet changed profile");
        select(new ModePacket("wrenchbridge:universal_wrench",""),player);select(new ModePacket("wrenchbridge:mode_wrench",""),player);
        check(VirtualModes.mode(wrench).equals("CONFIG"),"per-tool mode not remembered");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }
}
