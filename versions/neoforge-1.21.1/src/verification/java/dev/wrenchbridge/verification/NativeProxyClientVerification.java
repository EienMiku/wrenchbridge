package dev.wrenchbridge.verification;

import com.google.gson.GsonBuilder;
import dev.wrenchbridge.*;
import dev.wrenchbridge.client.NativeClientViews;
import dev.wrenchbridge.client.NativeScreenBindings;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandler;

/** Exercises the real Create key handler, native screen and packet through an integrated connection. */
public final class NativeProxyClientVerification {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static int ticks, since, scenario, stage;
    private static boolean started, finished;
    private static IItemHandler bag;
    private static ItemStack expected;
    private static final Map<ClientOption, Boolean> OPTIONS = new EnumMap<>(ClientOption.class);
    private NativeProxyClientVerification() {}
    public static void register() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> tick());
    }
    private static void check(boolean ok, String text) { if (!ok) throw new AssertionError("native GUI: " + text); }
    private static InteractionHand hand() { return scenario == 1 || scenario == 3 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND; }
    private static boolean stored() { return scenario == 2 || scenario == 3; }
    private static ItemStack item(String id) { return ReviewVerification.item(id); }
    private static void server(Consumer<ServerPlayer> action) throws Exception {
        var game = Minecraft.getInstance(); var id = game.player.getUUID();
        game.getSingleplayerServer().submit(() -> action.accept(game.getSingleplayerServer().getPlayerList().getPlayer(id))).get(5, TimeUnit.SECONDS);
    }
    private static ItemStack source(ServerPlayer player) {
        return scenario == 6 ? player.getMainHandItem() : stored() ? bag.getStackInSlot(0) : player.getInventory().getItem(9);
    }
    private static void setup() throws Exception {
        var game = Minecraft.getInstance(); game.setScreen(null); bag = null;
        game.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
        SharingPreferences.setClientOption(ClientOption.SOURCE_DETAILS, scenario != 3);
        server(player -> {
            try {
                player.getInventory().clearContent(); player.getInventory().selected = 0;
                player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                player.setHealth(player.getMaxHealth()); player.setInvulnerable(true); player.fallDistance = 0;
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.teleport(0.5, 100, 3.5, 180, 0);
                var level = player.serverLevel();
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 4; z++)
                    level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(POS, Blocks.OAK_LOG.defaultBlockState());
                ItemStack donor = item("create:wrench");
                donor.set(DataComponents.CUSTOM_NAME, Component.literal("Proxy source " + scenario)); expected = donor.copy();
                if (scenario == 6) player.setItemInHand(InteractionHand.MAIN_HAND, donor);
                else {
                    if (stored()) {
                        ItemStack backpack = item("sophisticatedbackpacks:backpack");
                        Object wrapper = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper")
                                .getMethod("fromStack", ItemStack.class).invoke(null, backpack);
                        wrapper.getClass().getMethod("setContentsUuid", UUID.class).invoke(wrapper, UUID.randomUUID());
                        bag = (IItemHandler) wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
                        check(bag.insertItem(0, donor, false).isEmpty(), "bag seed"); player.getInventory().setItem(8, backpack);
                    } else player.getInventory().setItem(9, donor);
                    if (hand() == InteractionHand.OFF_HAND) player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                    player.setItemInHand(hand(), new ItemStack(WrenchBridge.WRENCH.get()));
                    check(VirtualModes.select(player, hand(), "create:wrench", ""), "select Create mode");
                }
                NativeBlockActions.forget(player.getUUID()); NativeProxyObserver.rotations = 0;
                SourceViewPacket.changed(player); player.inventoryMenu.broadcastChanges();
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        stage = 1; since = ticks;
    }
    private static void open() throws Exception {
        var game = Minecraft.getInstance();
        game.hitResult = new BlockHitResult(Vec3.atCenterOf(POS), Direction.SOUTH, POS, false);
        Object key = Class.forName("com.simibubi.create.AllKeys").getField("ROTATE_MENU").get(null);
        var mapping = (net.minecraft.client.KeyMapping) key.getClass().getMethod("getKeybind").invoke(key);
        // Create leaves this shortcut unbound by default. Bind it only in the isolated test profile.
        mapping.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_R));
        net.minecraft.client.KeyMapping.resetMapping();
        WrenchBridge.LOG.info("Native GUI input: screen={} target={} actual={} projected={} key={} match={} mode={} cooldown={}",
                game.screen == null ? "none" : game.screen.getClass().getName(), game.level.getBlockState(POS),
                Tools.id(game.player.getMainHandItem()), NativeClientViews.input(game.player, "create:wrench"), mapping.getKey(),
                key.getClass().getMethod("doesModifierAndCodeMatch", int.class).invoke(key, mapping.getKey().getValue()),
                game.gameMode.getPlayerMode(), Class.forName("com.simibubi.create.content.contraptions.wrench.RadialWrenchHandler").getField("COOLDOWN").getInt(null));
        Class.forName("com.simibubi.create.content.contraptions.wrench.RadialWrenchHandler")
                .getMethod("onKeyInput", int.class, boolean.class).invoke(null, mapping.getKey().getValue(), true);
    }
    private static void submit(Screen screen) throws Exception {
        var all = screen.getClass().getDeclaredField("allStates"); all.setAccessible(true);
        @SuppressWarnings("unchecked") List<BlockState> states = (List<BlockState>) all.get(screen);
        int selected = -1;
        for (int i = 0; i < states.size(); i++) if (states.get(i).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X) selected = i;
        check(selected >= 0, "native menu lacks X state");
        var index = screen.getClass().getDeclaredField("selectedStateIndex"); index.setAccessible(true); index.setInt(screen, selected);
        if (scenario == 7) check(screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_R, 0, 0), "native release shortcut not consumed");
        else screen.mouseClicked(0, 0, 0);
    }
    private static void tick() {
        if (finished) return;
        var game = Minecraft.getInstance();
        if (game.player == null || game.level == null || game.getSingleplayerServer() == null) return;
        ticks++;
        try {
            if (!started) {
                started = true;
                for (ClientOption option : ClientOption.values()) OPTIONS.put(option, SharingPreferences.enabled(option));
                setup(); return;
            }
            if (ticks - since > 240) throw new AssertionError("native GUI phase timed out: " + scenario + "/" + stage);
            if (stage == 1 && ticks - since >= 35) {
                if (game.screen != null || !game.level.getBlockState(POS).is(Blocks.OAK_LOG)) return;
                if (scenario != 6) {
                    check(game.player.getItemInHand(hand()).is(WrenchBridge.WRENCH.get()), "physical held item not synced");
                    ItemStack view = NativeClientViews.view(game.player, hand());
                    if (view.isEmpty() || !view.getHoverName().getString().equals("Proxy source " + scenario)) return;
                }
                open(); stage = 10; since = ticks;
            } else if (stage == 10) {
                if (game.screen == null || !game.screen.getClass().getName().equals(NativeInputRule.CREATE_ROTATION.screenClass())) {
                    check(ticks - since < 20, "deferred native wheel did not open"); return;
                }
                check(NativeScreenBindings.bound(game.screen) == (scenario != 6), "native/proxy screen routing");
                stage = 2; since = ticks;
            } else if (stage == 2 && ticks - since >= 10) {
                var screen = game.screen;
                check(screen != null, "wheel closed unexpectedly");
                if (scenario == 0) try (var image = net.minecraft.client.Screenshot.takeScreenshot(game.getMainRenderTarget())) {
                    image.writeToFile(Path.of("native-rotation-wheel.png"));
                }
                if (scenario == 4) server(player -> { player.getInventory().setItem(9, ItemStack.EMPTY); SourceViewPacket.changed(player); player.inventoryMenu.broadcastChanges(); });
                if (scenario == 5) server(player -> VirtualModes.select(player, hand(), "wrenchbridge:universal_wrench", ""));
                else submit(screen);
                stage = 3; since = ticks;
            } else if (stage == 3 && ticks - since >= 25) {
                boolean rotated = scenario != 4 && scenario != 5;
                server(player -> {
                    check(player.serverLevel().getBlockState(POS).getValue(RotatedPillarBlock.AXIS) == (rotated ? Direction.Axis.X : Direction.Axis.Y), "server state mismatch");
                    check(NativeProxyObserver.rotations == (rotated ? 1 : 0), "native callback duplicated or stale commit accepted");
                    if (scenario != 4) check(ItemStack.matches(source(player), expected), "real source changed");
                    if (scenario != 6) check(player.getItemInHand(hand()).is(WrenchBridge.WRENCH.get()), "server hand converted");
                });
                check(game.screen == null, "closed/invalid menu retained");
                if (scenario != 6) check(game.player.getItemInHand(hand()).is(WrenchBridge.WRENCH.get()), "client hand converted");
                RESULTS.add(Map.of("scenario", scenario, "hand", hand().name(), "backpack", stored(), "rotated", rotated,
                        "native_menu", true, "source_details_enabled", SharingPreferences.enabled(ClientOption.SOURCE_DETAILS),
                        "submit", scenario == 7 ? "native-key-release" : "native-click"));
                if (++scenario >= 8) finish("PASS"); else setup();
            }
        } catch (Throwable error) { WrenchBridge.LOG.error("Native GUI client check failed", error); finish("FAIL: " + error); }
    }
    private static void finish(String status) {
        finished = true;
        try { for (var option : OPTIONS.entrySet()) SharingPreferences.setClientOption(option.getKey(), option.getValue()); }
        catch (Exception error) { status = "FAIL restoring test settings: " + error; }
        try { Files.writeString(Path.of("native-proxy-client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status", status, "cases", RESULTS))); }
        catch (Exception error) { WrenchBridge.LOG.error("Could not save native GUI result", error); }
        WrenchBridge.LOG.info("Native GUI proxy client: {}", status); Minecraft.getInstance().stop();
    }
}
