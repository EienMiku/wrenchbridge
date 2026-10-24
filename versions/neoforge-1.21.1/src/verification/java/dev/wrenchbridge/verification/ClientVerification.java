package dev.wrenchbridge.verification;

import dev.wrenchbridge.ConfiguratorViews;
import dev.wrenchbridge.VirtualModes;
import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.client.NativeRenderTools;
import dev.wrenchbridge.core.NativeModes;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Isolated, offline, bounded-heap render integration check. Never included in a release JAR. */
public final class ClientVerification {
    private static final String[] BASE_CASES = {"native-main", "simulated-main", "simulated-offhand", "simulated-energy", "simulated-backpack", "missing-source", "shared", "shared-ordinary-use", "shared-sneak-use", "ae2-native-network", "ae2-simulated-network", "ae2-offhand-network", "ae2-air-storage", "ae2-non-network-storage", "ae2-backpack-network", "ae2-backpack-offhand-network", "ae2-backpack-storage", "ae2-backpack-detach", "ae2-backpack-offhand-storage", "ae2-native-sound", "ae2-simulated-sound", "ae2-offhand-sound", "ae2-backpack-sound", "ae2-backpack-offhand-sound", "ae2-shared-sound", "ae2-shared-offhand-sound", "ae2-denied-sound", "modern-native-feedback", "modern-main-feedback", "modern-off-feedback", "powah-native-feedback", "powah-main-feedback", "powah-off-feedback", "supplementaries-native-feedback", "supplementaries-main-feedback", "supplementaries-off-feedback", "pneumatic-native-feedback", "pneumatic-main-feedback", "pneumatic-off-feedback", "create-native-feedback", "create-main-feedback", "create-off-feedback", "pretty-native-feedback", "pretty-main-feedback", "pretty-off-feedback", "pneumatic-shared-main-feedback", "pneumatic-shared-off-feedback"};
    private static final String[] CASES = java.util.stream.Stream.concat(java.util.Arrays.stream(BASE_CASES), java.util.stream.Stream.of("ender-native-feedback", "ender-main-feedback", "ender-off-feedback", "anvil-native-feedback", "anvil-main-feedback", "anvil-off-feedback")).toArray(String[]::new);
    private static final BlockPos POS = new BlockPos(0, 65, 0);
    private static final com.google.gson.JsonArray results = new com.google.gson.JsonArray();
    private static int ticks, phase = -1, started, highlights, overlays;
    private static volatile boolean configured;
    private static boolean finished;
    private static boolean clickSent, useCheckQueued;
    private static int clickedAt;
    private static volatile Boolean useVerified;
    private static volatile boolean menuOpened;
    private static volatile net.minecraft.world.level.block.state.BlockState beforeUse;
    private static volatile ItemStack sharedHeld;
    private static volatile ItemStack networkDonor, networkHeld;
    private static volatile int networkNodes, sourceSlot;
    private static volatile ItemStack networkBag;
    private static volatile net.neoforged.neoforge.items.IItemHandler networkItems;
    private static volatile Boolean cardsVerified;
    private static int cardPhase, cardStarted;
    private static volatile int actorSounds, serverSounds, excludedActors;
    private static volatile boolean denySoundPickup, nativeSoundParameters;
    private static final int[] FEEDBACK_SAMPLES = {12, 9, 14, 5, 3, 6};
    private static final java.util.Map<String, Integer> feedbackSounds = new java.util.TreeMap<>();
    private static final java.util.Map<Integer, java.util.Map<String, Integer>> nativeFeedback = new java.util.HashMap<>();
    private static final java.util.Map<Integer, String> nativeFeedbackState = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Integer> nativeFeedbackSwings = new java.util.HashMap<>();
    private static volatile java.util.Set<String> feedbackSoundIds = java.util.Set.of();
    private static volatile String feedbackState;
    private static boolean feedbackSwing;
    private static int feedbackSwings;
    private ClientVerification() {}

    public static void register(IEventBus bus) {
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() ->
                GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow())));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) -> {
            var id = event.getOriginalSound().getLocation();
            if (phase >= 19 && phase < 27 && isPickupSound(id)) actorSounds++;
            if (phase >= 27 && clickSent && feedbackSoundIds.contains(id.toString())) feedbackSounds.merge(id.toString(), 1, Integer::sum);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class, event -> {
            if (denySoundPickup && event.getPos().equals(POS)) event.setCanceled(true);
        });
        SoundObserver.listener = (excluded, packet) -> {
            if (phase >= 19 && packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound && isPickupSound(sound.getSound().value().getLocation())) {
                serverSounds++; if (excluded != null) excludedActors++;
                nativeSoundParameters &= sound.getSource() == net.minecraft.sounds.SoundSource.BLOCKS && Math.abs(sound.getVolume() - 0.7F) < 0.00001F && sound.getPitch() == 1;
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderHighlightEvent.Block.class, event -> {
            if (phase < 0 || !configured || ticks - started < 40 || !POS.equals(event.getTarget().getBlockPos())) return;
            highlights++;
        });
    }

    public static void overlayDrawn(RenderHighlightEvent.Block event) {
        if (configured && phase >= 0 && ticks - started >= 40 && POS.equals(event.getTarget().getBlockPos())) overlays++;
    }

    private static void tick() {
        if (finished) return;
        ticks++;
        var game = Minecraft.getInstance();
        if (ticks > 8000) { finish("FAIL: render check timed out"); return; }
        if (game.player == null || game.level == null || game.getSingleplayerServer() == null) return;
        if (phase < 0) {
            if (Boolean.getBoolean("wrenchbridge.verifyLevelOnly")) phase = 41;
            else if (Boolean.getBoolean("wrenchbridge.verifySoundOnly")) phase = 18;
            next(); return;
        }
        if (!configured || ticks - started < 70) return;
        try {
            if (phase >= 27) { checkModFeedback(); return; }
            if (phase >= 19) { checkPickupSound(); return; }
            if (phase >= 9) { checkNetworkMenu(); return; }
            if (phase >= 7 && !checkSharedUse()) return;
            boolean expected = phase < 5;
            if (highlights < 2 || (overlays > 0) != expected) throw new AssertionError(CASES[phase] + ": highlights=" + highlights + ", overlays=" + overlays);
            InteractionHand hand = phase == 2 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack held = game.player.getItemInHand(hand);
            if (phase > 0 && !held.is(WrenchBridge.WRENCH.get())) throw new AssertionError("render check replaced a held item");
            ItemStack view = NativeRenderTools.itemInHand(game.player, hand);
            if (phase > 0 && expected && !ConfiguratorViews.ITEM.equals(dev.wrenchbridge.Tools.id(view))) throw new AssertionError("renderer did not receive a configurator view");
            if (phase > 0 && phase < 4 && !NativeModes.current(game.player.getInventory().getItem(9).getItem(), game.player.getInventory().getItem(9)).equals("WRENCH"))
                throw new AssertionError("rendering changed native donor mode");
            var row = new com.google.gson.JsonObject(); row.addProperty("case", CASES[phase]);
            row.addProperty("highlights", highlights); row.addProperty("native_overlay_frames", overlays); row.addProperty("held_item", dev.wrenchbridge.Tools.id(held));
            row.addProperty("view_mode", NativeModes.current(view.getItem(), view)); results.add(row);
            if (phase == 0) row.addProperty("mixed_ingot_rod_recipe", true);
            if (phase == 7) { row.addProperty("ordinary_use_opened_menu", menuOpened); row.addProperty("unintended_rotation", false); }
            if (phase == 8) row.addProperty("shared_sneak_pickup", true);
            Screenshot.grab(game.gameDirectory, "wrenchbridge-" + CASES[phase] + ".png", game.getMainRenderTarget(), message -> {});
            next();
        } catch (Throwable error) { WrenchBridge.LOG.error("Client render verification failed", error); finish("FAIL: " + error); }
    }

    public static void handSwung(InteractionHand hand) {
        if (phase >= 27 && clickSent) {
            feedbackSwings++;
        }
    }
    private static int feedbackSampleIndex(int selected) { return selected >= 50 ? 4 : selected >= 47 ? 8 : selected >= 45 ? 5 : FEEDBACK_SAMPLES[(selected - 27) / 3]; }
    private static int feedbackVariant(int selected) { return selected >= 47 ? (selected - 47) % 3 : selected >= 45 ? selected - 42 : (selected - 27) % 3; }
    private static void checkModFeedback() throws Exception {
        var game = Minecraft.getInstance(); int sampleIndex = feedbackSampleIndex(phase), variant = feedbackVariant(phase);
        var sample = FeedbackVerification.SAMPLES.get(sampleIndex);
        InteractionHand hand = variant == 2 || variant == 4 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!clickSent) {
            clickSent = true; clickedAt = ticks;
            if (sample.sneak() && !game.player.isCrouching())
                throw new AssertionError("feedback fixture is not crouching: " + sample.tool() + ", flying=" + game.player.getAbilities().flying
                        + ", shift=" + game.player.input.shiftKeyDown + ", paused=" + game.isPaused()
                        + ", swimming=" + game.player.isSwimming() + ", passenger=" + game.player.isPassenger()
                        + ", position=" + game.player.position() + ", pose=" + game.player.getPose()
                        + ", fits=" + game.level.noCollision(game.player, game.player.getDimensions(net.minecraft.world.entity.Pose.CROUCHING).makeBoundingBox(game.player.position()).deflate(1.0E-7)));
            if (hand == InteractionHand.OFF_HAND && !game.player.getMainHandItem().isEmpty())
                throw new AssertionError("feedback fixture expected an empty main hand, got " + dev.wrenchbridge.Tools.id(game.player.getMainHandItem()));
            game.player.input.shiftKeyDown = sample.sneak(); game.player.setShiftKeyDown(sample.sneak());
            game.player.setPose(sample.sneak() ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
            game.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(game.player, sample.sneak()
                    ? net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                    : net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            game.options.keyShift.setDown(sample.sneak());
            game.hitResult = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS), sample.tool().startsWith("supplementaries:") ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.EAST, POS, false);
            var use = Minecraft.class.getDeclaredMethod("startUseItem"); use.setAccessible(true); use.invoke(game);
            return;
        }
        if (ticks - clickedAt < 30) return;
        if (!useCheckQueued) {
            useCheckQueued = true;
            game.getSingleplayerServer().execute(() -> {
                var player = game.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                feedbackState = player.serverLevel().getBlockState(POS).toString();
                useVerified = player.getItemInHand(hand) == networkHeld && !dev.wrenchbridge.WrenchContext.serverOnly() && dev.wrenchbridge.WrenchContext.hand() == null;
            });
            return;
        }
        if (useVerified == null) return;
        var actual = new java.util.TreeMap<>(feedbackSounds); feedbackSwing = feedbackSwings > 0;
        var row = new com.google.gson.JsonObject(); row.addProperty("case", CASES[phase]); row.addProperty("tool", sample.tool()); row.addProperty("hand", hand.name());
        row.add("actor_sounds", new com.google.gson.Gson().toJsonTree(actual)); row.addProperty("state", feedbackState); row.addProperty("client_swing", feedbackSwing);
        row.addProperty("swing_calls", feedbackSwings);
        row.addProperty("held_item_preserved", useVerified); results.add(row);
        if (!useVerified) throw new AssertionError("mod feedback changed hands or leaked context: " + sample.tool());
        if (variant == 0) {
            if (actual.isEmpty() || !sample.tool().startsWith("supplementaries:") && !feedbackState.equals("Block{minecraft:air}"))
                throw new AssertionError("native feedback fixture did not perform the expected action: " + sample.tool());
            nativeFeedback.put(sampleIndex, actual); nativeFeedbackState.put(sampleIndex, feedbackState); nativeFeedbackSwings.put(sampleIndex, feedbackSwings);
        }
        else if (!actual.equals(nativeFeedback.get(sampleIndex)) || !feedbackState.equals(nativeFeedbackState.get(sampleIndex))
                || nativeFeedbackSwings.get(sampleIndex) != feedbackSwings)
            throw new AssertionError("native/simulated client feedback differs for " + sample.tool() + ": native=" + nativeFeedback.get(sampleIndex) + ", actual=" + actual
                    + ", swings=" + nativeFeedbackSwings.get(sampleIndex) + "/" + feedbackSwings);
        WrenchBridge.LOG.info("Client feedback passed: {} {}", CASES[phase], actual); next();
    }

    private static void setupModFeedback(net.minecraft.server.level.ServerPlayer player, int selected) throws Exception {
        int sampleIndex = feedbackSampleIndex(selected), variant = feedbackVariant(selected);
        var sample = FeedbackVerification.SAMPLES.get(sampleIndex); var level = player.serverLevel();
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(sample.block()));
        level.setBlockAndUpdate(POS, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(POS, block.defaultBlockState());
        block.setPlacedBy(level, POS, level.getBlockState(POS), player, new ItemStack(block.asItem()));
        for (int x = 2; x <= 4; x++) for (int z = -1; z <= 1; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 64, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for (int y = 65; y <= 68; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        player.getAbilities().flying = false; player.onUpdateAbilities();
        // Keep both sides in the same held-input state across consecutive scenarios.
        // Otherwise the client need not resend PRESS_SHIFT after the server fixture resets it.
        player.setShiftKeyDown(sample.sneak());
        player.setPose(sample.sneak() ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
        player.connection.teleport(3.5, 65, 0.5, 90, 20.47F);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        var state = level.getBlockState(POS); var sound = state.getSoundType(level, POS, player);
        var ids = new java.util.HashSet<String>(); ids.add(sound.getBreakSound().getLocation().toString()); ids.add(sound.getPlaceSound().getLocation().toString());
        ids.add("minecraft:entity.item_frame.remove_item"); ids.add("minecraft:entity.item_frame.rotate_item");
        String namespace = ResourceLocation.parse(sample.tool()).getNamespace();
        for (var id : BuiltInRegistries.SOUND_EVENT.keySet()) if (id.getNamespace().equals(namespace)) ids.add(id.toString());
        feedbackSoundIds = java.util.Set.copyOf(ids);
        ItemStack source = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(sample.tool())));
        if (!sample.mode().isEmpty() && !NativeModes.select(source.getItem(), source, player, sample.mode())) throw new AssertionError("feedback mode unavailable");
        if (namespace.equals("pneumaticcraft")) {
            Object optional = Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities").getMethod("getAirHandler", ItemStack.class).invoke(null, source);
            Class.forName("me.desht.pneumaticcraft.api.tileentity.IAirHandler").getMethod("addAir", int.class).invoke(((java.util.Optional<?>) optional).orElseThrow(), 5000);
        }
        player.getCooldowns().removeCooldown(source.getItem()); player.getCooldowns().removeCooldown(WrenchBridge.WRENCH.get());
        InteractionHand hand = variant == 2 || variant == 4 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        networkHeld = variant == 0 ? source : new ItemStack(WrenchBridge.WRENCH.get()); player.setItemInHand(hand, networkHeld);
        if (variant == 1 || variant == 2) {
            player.getInventory().setItem(9, source);
            if (!VirtualModes.select(player, hand, sample.tool(), sample.mode())) throw new AssertionError("could not select feedback profile");
        }
    }

    private static boolean isPickupSound(ResourceLocation id) { return id.toString().equals("minecraft:entity.item_frame.remove_item"); }

    private static void checkPickupSound() {
        var game = Minecraft.getInstance();
        InteractionHand hand = phase == 21 || phase == 23 || phase == 25 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (!clickSent) {
            clickSent = true; clickedAt = ticks;
            game.player.input.shiftKeyDown = true;
            game.player.setShiftKeyDown(true); game.player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            game.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(game.player,
                    net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
            game.gameMode.useItemOn(game.player, hand, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS), net.minecraft.core.Direction.EAST, POS, false));
            return;
        }
        if (ticks - clickedAt < 30) return;
        if (!useCheckQueued) {
            useCheckQueued = true;
            game.getSingleplayerServer().execute(() -> {
                var player = game.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                useVerified = player.serverLevel().isEmptyBlock(POS) == (phase != 26) && player.getItemInHand(hand) == networkHeld
                        && dev.wrenchbridge.WrenchContext.hand() == null;
            });
            return;
        }
        if (useVerified == null) return;
        var row = new com.google.gson.JsonObject(); row.addProperty("case", CASES[phase]); row.addProperty("hand", hand.name());
        row.addProperty("actor_sound_events", actorSounds); row.addProperty("server_sound_broadcasts", serverSounds);
        row.addProperty("server_excluded_actor", excludedActors); row.addProperty("native_sound_parameters", nativeSoundParameters);
        row.addProperty("pickup_or_denial_correct", useVerified); row.addProperty("held_item", dev.wrenchbridge.Tools.id(game.player.getItemInHand(hand))); results.add(row);
        int expected = phase == 26 ? 0 : 1;
        int excluded = phase == 19 || phase == 24 || phase == 25 ? 1 : 0;
        if (!useVerified || actorSounds != expected || serverSounds != expected || excludedActors != excluded || !nativeSoundParameters)
            throw new AssertionError(CASES[phase] + ": actor=" + actorSounds + ", server=" + serverSounds + ", excluded=" + excludedActors + ", pickup=" + useVerified);
        next();
    }

    private static void setupPickupSound(net.minecraft.server.level.ServerPlayer player, int selected) throws Exception {
        var level = player.serverLevel();
        level.setBlockAndUpdate(POS, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:interface"));
        level.setBlockAndUpdate(POS, block.defaultBlockState());
        block.setPlacedBy(level, POS, level.getBlockState(POS), player, new ItemStack(block.asItem()));
        player.connection.teleport(3.5, 65, 0.5, 90, 20.47F);
        InteractionHand hand = selected == 21 || selected == 23 || selected == 25 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        networkDonor = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:network_tool")));
        networkHeld = selected == 19 ? networkDonor : new ItemStack(WrenchBridge.WRENCH.get());
        player.setItemInHand(hand, networkHeld);
        if (selected >= 20 && selected <= 23 || selected == 26) {
            if (selected == 22 || selected == 23) {
                ItemStack bag = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
                Object wrapper = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper").getMethod("fromStack", ItemStack.class).invoke(null, bag);
                wrapper.getClass().getMethod("setContentsUuid", java.util.UUID.class).invoke(wrapper, java.util.UUID.randomUUID());
                var contents = (net.neoforged.neoforge.items.IItemHandler) wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
                contents.insertItem(0, networkDonor, false); player.getInventory().setItem(8, bag);
            } else player.getInventory().setItem(9, networkDonor);
            if (!VirtualModes.select(player, hand, "ae2:network_tool", "")) throw new AssertionError("could not select network sound profile");
        }
        denySoundPickup = selected == 26;
    }

    private static void checkNetworkMenu() throws Exception {
        var game = Minecraft.getInstance();
        InteractionHand hand = networkHand(phase);
        boolean stored = phase >= 14;
        boolean statusPage = phase < 12 || phase == 14 || phase == 15 || phase == 17;
        if (!clickSent) {
            clickSent = true; clickedAt = ticks;
            game.player.input.shiftKeyDown = false;
            game.player.setShiftKeyDown(false); game.player.setPose(net.minecraft.world.entity.Pose.STANDING);
            game.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(game.player,
                    net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            if (phase == 12 || phase == 16 || phase == 18) game.gameMode.useItem(game.player, hand);
            else game.gameMode.useItemOn(game.player, hand,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS), net.minecraft.core.Direction.EAST, POS, false));
            return;
        }
        if (ticks - clickedAt < 40) return;
        if (!useCheckQueued) {
            useCheckQueued = true;
            game.getSingleplayerServer().execute(() -> {
                try {
                    var player = game.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    Object menu = player.containerMenu;
                    String expected = statusPage ? "appeng.menu.me.networktool.NetworkStatusMenu" : "appeng.menu.me.networktool.NetworkToolMenu";
                    if (!menu.getClass().getName().equals(expected)) throw new AssertionError("wrong AE2 page: " + menu.getClass().getName());
                    Class<?> base = Class.forName("appeng.menu.AEBaseMenu");
                    Object locator = base.getMethod("getLocator").invoke(menu);
                    Class<?> locatorType = Class.forName("appeng.menu.locator.ItemMenuHostLocator");
                    Integer slot = (Integer) locatorType.getMethod("getPlayerInventorySlot").invoke(locator); sourceSlot = slot == null ? -1 : slot;
                    Object host = base.getMethod("getTarget").invoke(menu);
                    Object bound = Class.forName("appeng.api.implementations.menuobjects.ItemMenuHost").getMethod("getItemStack").invoke(host);
                    if (stored) {
                        if (sourceSlot != -1 || !ItemStack.matches((ItemStack) bound, networkItems.getStackInSlot(0))) throw new AssertionError("network menu lost its backpack source");
                        for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).isEmpty()) throw new AssertionError("stored menu test unexpectedly has a spare player slot");
                    } else if (bound != networkDonor || sourceSlot != (phase == 9 ? player.getInventory().selected : 9)) throw new AssertionError("network menu lost its real source slot");
                    if (statusPage) {
                        var field = menu.getClass().getDeclaredField("grid"); field.setAccessible(true);
                        Object grid = field.get(menu);
                        if (grid == null) throw new AssertionError("network status menu has no live grid");
                        networkNodes = (Integer) Class.forName("appeng.api.networking.IGrid").getMethod("size").invoke(grid);
                        var hit = (net.minecraft.world.phys.BlockHitResult) locatorType.getMethod("hitResult").invoke(locator);
                        if (networkNodes < 2 || hit == null || !hit.getBlockPos().equals(POS)) throw new AssertionError("network source was not bound to the connected target");
                    }
                    useVerified = player.getItemInHand(hand) == networkHeld && (stored ? !networkItems.getStackInSlot(0).isEmpty() : player.getInventory().getItem(sourceSlot) == networkDonor)
                            && dev.wrenchbridge.NativeMenuContext.inventorySlot(player, hand) == null;
                } catch (Throwable error) { game.execute(() -> finish("FAIL: AE2 server menu " + error)); useVerified = false; }
            });
            return;
        }
        if (useVerified == null) return;
        if (!useVerified) throw new AssertionError("AE2 menu changed a held item or leaked its source binding");
        if (phase == 17 && cardPhase > 0) {
            if (ticks - cardStarted < 20 || cardsVerified == null) return;
            if (!cardsVerified || game.screen != null) throw new AssertionError("removing the backpack did not close its native menu safely");
            var row = new com.google.gson.JsonObject(); row.addProperty("case", CASES[phase]); row.addProperty("detached_bag_closed_menu", true);
            row.addProperty("real_tool_and_cards_preserved", true); results.add(row); next(); return;
        }
        String screen = statusPage ? "appeng.client.gui.me.networktool.NetworkStatusScreen" : "appeng.client.gui.me.networktool.NetworkToolScreen";
        if (game.screen == null || !game.screen.getClass().getName().equals(screen)) throw new AssertionError("wrong AE2 client screen");
        int groups = 0;
        if (statusPage) {
            var field = game.screen.getClass().getDeclaredField("status"); field.setAccessible(true);
            Object status = field.get(game.screen);
            if (status != null) groups = ((java.util.List<?>) status.getClass().getMethod("getGroupedMachines").invoke(status)).size();
            if (groups == 0 && ticks - clickedAt < 120) return;
            if (groups == 0) throw new AssertionError("network screen received no machine statistics");
        }
        if (phase == 17) {
            cardPhase = 1; cardStarted = ticks;
            game.getSingleplayerServer().execute(() -> {
                var player = game.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(8, ItemStack.EMPTY);
                game.getSingleplayerServer().execute(() -> cardsVerified = !networkItems.getStackInSlot(0).isEmpty() && storedCards() == 2 && player.getItemInHand(hand) == networkHeld);
            });
            return;
        }
        if (stored && !statusPage && !checkStoredCards()) return;
        var row = new com.google.gson.JsonObject(); row.addProperty("case", CASES[phase]); row.addProperty("source_slot", sourceSlot);
        row.addProperty("grid_nodes", networkNodes); row.addProperty("machine_groups", groups); row.addProperty("network_status_page", statusPage);
        row.addProperty("held_item", dev.wrenchbridge.Tools.id(game.player.getItemInHand(hand))); row.addProperty("real_source_preserved", true);
        if (stored) { row.addProperty("source_in_backpack", true); row.addProperty("full_player_inventory", true); }
        if (stored && !statusPage) row.addProperty("native_card_packets_saved_to_backpack", true);
        results.add(row);
        Screenshot.grab(game.gameDirectory, "wrenchbridge-" + CASES[phase] + ".png", game.getMainRenderTarget(), message -> {});
        next();
    }

    private static InteractionHand networkHand(int selected) { return selected == 11 || selected == 15 || selected == 18 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND; }
    private static int storedCards() { return networkItems.getStackInSlot(0).getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY).stream().mapToInt(ItemStack::getCount).sum(); }
    private static boolean checkStoredCards() {
        var game = Minecraft.getInstance();
        if (cardPhase == 0 || cardPhase == 2) {
            if (cardPhase == 2 && cardsVerified == null) return false;
            if (cardPhase == 2 && !cardsVerified) throw new AssertionError("card extraction did not persist in the backpack");
            game.gameMode.handleInventoryMouseClick(game.player.containerMenu.containerId, 0, 0, net.minecraft.world.inventory.ClickType.PICKUP, game.player);
            cardPhase++; cardStarted = ticks; cardsVerified = null; return false;
        }
        if (cardPhase == 1 || cardPhase == 3) {
            if (ticks - cardStarted < 12) return false;
            int expected = cardPhase == 1 ? 0 : 2;
            if (game.player.containerMenu.getSlot(0).getItem().getCount() != expected
                    || game.player.containerMenu.getCarried().getCount() != 2 - expected) throw new AssertionError("native card click did not synchronize");
            game.getSingleplayerServer().execute(() -> cardsVerified = storedCards() == expected && networkItems.getStackInSlot(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
            cardPhase++; return false;
        }
        if (cardsVerified == null) return false;
        if (!cardsVerified) throw new AssertionError("card insertion did not persist in the backpack");
        return true;
    }

    private static void setupNetworkMenu(net.minecraft.server.level.ServerPlayer player, int selected) throws Exception {
        var level = player.serverLevel();
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(selected == 13 ? "minecraft:stone" : "ae2:interface"));
        level.setBlockAndUpdate(POS, block.defaultBlockState());
        block.setPlacedBy(level, POS, level.getBlockState(POS), player, new ItemStack(block.asItem()));
        var cell = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell"));
        level.setBlockAndUpdate(POS.west(), cell.defaultBlockState());
        cell.setPlacedBy(level, POS.west(), level.getBlockState(POS.west()), player, new ItemStack(cell.asItem()));
        player.connection.teleport(3.5, 65, 0.5, 90, 20.47F);
        networkDonor = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:network_tool")));
        InteractionHand hand = networkHand(selected);
        if (selected >= 14) {
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STICK, 64));
            networkDonor.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Stored network tool"));
            networkDonor.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(
                    new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:speed_card")), 2))));
        }
        networkHeld = selected == 9 ? networkDonor : new ItemStack(WrenchBridge.WRENCH.get());
        player.setItemInHand(hand, networkHeld);
        if (selected != 9) {
            if (selected >= 14) {
                networkBag = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
                Object wrapper = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper").getMethod("fromStack", ItemStack.class).invoke(null, networkBag);
                wrapper.getClass().getMethod("setContentsUuid", java.util.UUID.class).invoke(wrapper, java.util.UUID.randomUUID());
                networkItems = (net.neoforged.neoforge.items.IItemHandler) wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
                if (!networkItems.insertItem(0, networkDonor, false).isEmpty()) throw new AssertionError("could not store the network tool");
                player.getInventory().setItem(8, networkBag);
            } else player.getInventory().setItem(9, networkDonor);
            if (!VirtualModes.select(player, hand, "ae2:network_tool", "")) throw new AssertionError("could not select simulated network tool");
        }
    }

    private static boolean checkSharedUse() {
        var game = Minecraft.getInstance();
        if (!clickSent) {
            clickSent = true; clickedAt = ticks;
            boolean sneak = phase == 8;
            game.player.input.shiftKeyDown = sneak;
            game.player.setShiftKeyDown(sneak);
            game.player.setPose(sneak ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
            game.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(game.player,
                    sneak ? net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                          : net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            game.gameMode.useItemOn(game.player, InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS), net.minecraft.core.Direction.EAST, POS, false));
            if (dev.wrenchbridge.WrenchContext.hand() != null || dev.wrenchbridge.WrenchContext.forced(game.player.getMainHandItem()))
                throw new AssertionError("client interaction leaked wrench traits");
            return false;
        }
        if (ticks - clickedAt < 20) return false;
        if (!useCheckQueued) {
            useCheckQueued = true;
            boolean sneak = phase == 8;
            game.getSingleplayerServer().execute(() -> {
                var player = game.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var state = player.serverLevel().getBlockState(POS);
                menuOpened = player.containerMenu != player.inventoryMenu;
                useVerified = (sneak ? state.isAir() : state.equals(beforeUse) && menuOpened)
                        && player.getMainHandItem() == sharedHeld && dev.wrenchbridge.WrenchContext.hand() == null
                        && !dev.wrenchbridge.WrenchContext.forced(sharedHeld);
            });
            return false;
        }
        if (useVerified == null) return false;
        if (!useVerified || phase == 7 && !(game.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>))
            throw new AssertionError("shared client use failed: " + CASES[phase] + ", menu=" + menuOpened);
        return true;
    }

    private static void next() {
        if (++phase == CASES.length) { finish("PASS: native and simulated Mekanism face overlays in main/offhand, selected transmission, backpack source, missing-source/shared rejection after catalog refresh, real held item and donor mode preserved, mixed ingot/rod crafting recipe, ordinary shared use opens the machine menu without rotation, shared sneak pickup and client/server scope cleanup; AE2 native/simulated main/offhand live network status and received machine statistics, air/non-network storage pages, real source-slot ownership; carried Sophisticated Backpack main/offhand network and storage menus with full player inventory, real card click packets persist removals and insertions, detached bag closes menu without tool/card loss; AE2 pickup sound reaches actor exactly once in native, simulated main/offhand, backpack and shared modes, native volume/pitch/category and nearby-player broadcasts preserved, protection denial is silent; Modern Dynamics, Powah, Supplementaries, PneumaticCraft, Create, Pretty Pipes, Ender IO and AnvilCraft native/simulated main/offhand client sound and swing-count parity, action preserved, pneumatic shared pickup sound restored"); return; }
        configured = false; highlights = overlays = 0; started = ticks;
        clickSent = useCheckQueued = menuOpened = false; useVerified = null;
        networkNodes = sourceSlot = 0;
        cardPhase = 0; cardsVerified = null;
        feedbackSounds.clear(); feedbackState = null; feedbackSwing = false; feedbackSwings = 0;
        actorSounds = serverSounds = excludedActors = 0; nativeSoundParameters = true; denySoundPickup = false;
        var game = Minecraft.getInstance();
        int selected = phase;
        game.getSingleplayerServer().execute(() -> {
            try {
                var server = game.getSingleplayerServer();
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.serverLevel();
                player.closeContainer();
                player.gameMode.changeGameModeForPlayer(GameType.CREATIVE); player.setShiftKeyDown(false);
                player.getInventory().clearContent();
                if (selected >= 9) {
                    if (selected >= 27) setupModFeedback(player, selected);
                    else if (selected >= 19) setupPickupSound(player, selected); else setupNetworkMenu(player, selected);
                    player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
                    game.execute(() -> {
                        game.setScreen(null);
                        // Hold the input before clicking so LocalPlayer's crouching field and server pose settle.
                        boolean sneak = selected >= 27 ? FeedbackVerification.SAMPLES.get(feedbackSampleIndex(selected)).sneak() : selected >= 19;
                        if (selected >= 27) {
                            game.player.getAbilities().flying = false;
                            // The hidden test window does not own keyboard focus. Feed vanilla's input
                            // state directly and let its normal player tick establish crouching.
                            game.player.input = new net.minecraft.client.player.Input();
                        }
                        game.options.keyShift.setDown(sneak); game.player.input.shiftKeyDown = sneak;
                        started = ticks; configured = true;
                    });
                    return;
                }
                level.setBlockAndUpdate(POS, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:enrichment_chamber")).defaultBlockState());
                beforeUse = level.getBlockState(POS);
                Object tile = level.getBlockEntity(POS);
                Object config = tile.getClass().getMethod("getConfig").invoke(tile);
                Class transmission = Class.forName("mekanism.common.lib.transmitter.TransmissionType");
                Class dataType = Class.forName("mekanism.common.tile.component.config.DataType");
                Class sideType = Class.forName("mekanism.api.RelativeSide");
                Object side = Enum.valueOf(sideType, "RIGHT"), input = Enum.valueOf(dataType, "INPUT");
                for (String type : new String[] {"ITEM", "ENERGY"}) {
                    Object channel = Enum.valueOf(transmission, type);
                    Object info = config.getClass().getMethod("getConfig", transmission).invoke(config, channel);
                    info.getClass().getMethod("setDataType", dataType, sideType).invoke(info, input, side);
                    config.getClass().getMethod("sideChanged", transmission, sideType).invoke(config, channel, side);
                }
                if (selected == 0) {
                    var recipe = (net.minecraft.world.item.crafting.ShapedRecipe) server.getRecipeManager()
                            .byKey(ResourceLocation.parse("wrenchbridge:universal_wrench")).orElseThrow().value();
                    var ingredients = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, java.util.List.of(
                            ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY,
                            ItemStack.EMPTY, new ItemStack(Items.STICK), new ItemStack(Items.IRON_INGOT),
                            new ItemStack(Items.BLAZE_ROD), ItemStack.EMPTY, ItemStack.EMPTY));
                    if (!recipe.matches(ingredients, level) || !recipe.assemble(ingredients, level.registryAccess()).is(WrenchBridge.WRENCH.get()))
                        throw new AssertionError("mixed ingot/rod crafting recipe did not match");
                }
                player.connection.teleport(3.5, 65, 0.5, 90, 20.47F);
                var tool = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ConfiguratorViews.ITEM));
                ItemStack donor = new ItemStack(tool);
                NativeModes.select(tool, donor, player, selected == 0 ? "CONFIGURATE_ITEMS" : "WRENCH");
                InteractionHand hand = selected == 2 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                if (selected == 0) player.setItemInHand(hand, donor);
                else {
                    player.setItemInHand(hand, new ItemStack(WrenchBridge.WRENCH.get()));
                    player.getInventory().setItem(9, donor);
                    if (selected == 4) {
                        ItemStack bag = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
                        Object wrapper = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper").getMethod("fromStack", ItemStack.class).invoke(null, bag);
                        wrapper.getClass().getMethod("setContentsUuid", java.util.UUID.class).invoke(wrapper, java.util.UUID.randomUUID());
                        var inventory = (net.neoforged.neoforge.items.IItemHandler) wrapper.getClass().getMethod("getInventoryHandler").invoke(wrapper);
                        inventory.insertItem(0, donor, false); player.getInventory().setItem(9, ItemStack.EMPTY); player.getInventory().setItem(8, bag);
                    }
                    if (selected < 6) {
                        if (!VirtualModes.select(player, hand, ConfiguratorViews.ITEM, selected == 3 ? "CONFIGURATE_ENERGY" : "CONFIGURATE_ITEMS")) throw new AssertionError("client render setup could not select profile");
                    }
                    if (selected == 5) player.getInventory().setItem(9, ItemStack.EMPTY);
                }
                sharedHeld = player.getMainHandItem();
                player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
                game.execute(() -> { game.setScreen(null); started = ticks; configured = true; });
            } catch (Throwable error) { game.execute(() -> finish("FAIL: setup " + error)); }
        });
    }

    private static void finish(String result) {
        if (finished) return;
        finished = true;
        try {
            Files.writeString(Path.of("client-verification-result.txt"), result + "\n");
            Files.writeString(Path.of("client-render-verification.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
        } catch (java.io.IOException error) { WrenchBridge.LOG.error("Could not save client verification", error); }
        WrenchBridge.LOG.info("{}", result);
        Minecraft.getInstance().stop();
    }
}
