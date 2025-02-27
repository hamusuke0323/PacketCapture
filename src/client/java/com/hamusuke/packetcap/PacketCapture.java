package com.hamusuke.packetcap;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.filter.FilterType;
import com.hamusuke.packetcap.filter.PacketFilter;
import com.hamusuke.packetcap.gui.hud.PacketCaptureHud;
import com.hamusuke.packetcap.gui.screen.PacketListScreen;
import com.hamusuke.packetcap.highlight.DataHighlightInstruction;
import com.hamusuke.packetcap.highlight.DataHighlightInstructions;
import com.hamusuke.packetcap.highlight.Highlight;
import com.hamusuke.packetcap.invoker.PacketCodecDispatcherAccessor;
import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeConfigRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.encoding.VarInts;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.neoforged.fml.config.ModConfig.Type;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PacketCapture implements ClientModInitializer {
    public static final String MOD_ID = "packetcapture";
    public static final Identifier MONO_FONT = Identifier.of(MOD_ID, "mono");
    private static final Identifier PACKET_CAPTURE_HUD_LAYER_ID = Identifier.of(MOD_ID, "layer");
    private static final ExecutorService SENT_PACKET_DETAIL_RETRIEVER = Executors.newSingleThreadExecutor(r -> new Thread(r, "Sent-Packet-Detail-Retriever"));
    private static final ExecutorService RECEIVED_PACKET_DETAIL_RETRIEVER = Executors.newSingleThreadExecutor(r -> new Thread(r, "Received-Packet-Detail-Retriever"));
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_PACKET_SIZE = 16384;
    private static final KeyBinding RENDER_CAPTURE_HUD = new KeyBinding(MOD_ID + ".key.render_cap", GLFW.GLFW_KEY_HOME, KeyBinding.MISC_CATEGORY);
    private static final KeyBinding OPEN_PACKET_LIST_SCREEN = new KeyBinding(MOD_ID + ".key.open.packetListScreen", GLFW.GLFW_KEY_END, KeyBinding.MISC_CATEGORY);
    private static final Gson GSON = new Gson();
    private static final Set<PacketFilter> DEFAULT_PACKET_FILTERS = Set.of(
            new PacketFilter("Chunk", FilterType.CONTAINS),
            new PacketFilter("EntityVelocity", FilterType.CONTAINS),
            new PacketFilter("BlockUpdate", FilterType.CONTAINS),
            new PacketFilter("MoveRelative", FilterType.CONTAINS),
            new PacketFilter("EntityPosition", FilterType.CONTAINS),
            new PacketFilter("Rotate", FilterType.CONTAINS),
            new PacketFilter("EntityTrackerUpdate", FilterType.CONTAINS),
            new PacketFilter("SetHeadYaw", FilterType.CONTAINS),
            new PacketFilter("EntitiesDestroy", FilterType.CONTAINS),
            new PacketFilter("SectionBlocksUpdate", FilterType.CONTAINS),
            new PacketFilter("Bundle", FilterType.CONTAINS),
            new PacketFilter("PlayerMove", FilterType.CONTAINS),
            new PacketFilter("Sound", FilterType.CONTAINS),
            new PacketFilter("Swing", FilterType.CONTAINS),
            new PacketFilter("KeepAlive", FilterType.CONTAINS),
            new PacketFilter("Ping", FilterType.CONTAINS),
            new PacketFilter("Pong", FilterType.CONTAINS),
            new PacketFilter("ClientTickEnd", FilterType.CONTAINS),
            new PacketFilter("Attributes", FilterType.CONTAINS),
            new PacketFilter("WorldTimeUpdate", FilterType.CONTAINS),
            new PacketFilter("TeleportEntity", FilterType.CONTAINS),
            new PacketFilter("EntityEvent", FilterType.CONTAINS)
    );
    private static PacketCapture instance;
    private final MinecraftClient mc;
    private final PacketCaptureHud hud;
    private final PacketListScreen screen;
    private final Path filterConfig;
    public final Deobfuscation classNameDeobfuscater;
    public final Deobfuscation fieldNameDeobfuscater;
    private final Set<PacketFilter> packetFilters = Collections.synchronizedSet(Sets.newHashSet());
    private final AtomicBoolean capturing = new AtomicBoolean(true);
    private final AtomicLong sentBytes = new AtomicLong();
    private final AtomicLong sentPacketNum = new AtomicLong();
    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong receivedPacketNum = new AtomicLong();
    private final List<PacketDetails> sentPackets = Collections.synchronizedList(Lists.newLinkedList());
    private final List<PacketDetails> receivedPackets = Collections.synchronizedList(Lists.newLinkedList());
    private boolean showCapture;
    private final List<PacketCaptureApi> apis = Lists.newArrayList();

    public PacketCapture() {
        instance = this;

        this.mc = MinecraftClient.getInstance();
        this.hud = new PacketCaptureHud(this.mc, this);
        this.screen = new PacketListScreen(this);
        var configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        this.filterConfig = configDir.resolve("packet_filter.json");
        var path = configDir.resolve("fields");
        this.classNameDeobfuscater = new Deobfuscation(path, s -> s.startsWith("c") && s.split(" ").length == 4, text -> {
            var split = text.split(" ");
            var named = split[1].split("/");
            var intermediary = split[3].split("/");

            return Pair.of(intermediary[intermediary.length - 1], named[named.length - 1]);
        });
        this.fieldNameDeobfuscater = new Deobfuscation(path, s -> s.startsWith("f") && s.split(" ").length == 5, text -> {
            var split = text.split(" ");
            var named = split[2];
            var intermediary = split[4];

            return Pair.of(intermediary, named);
        });
        this.loadFilters();
    }

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(RENDER_CAPTURE_HUD);
        KeyBindingHelper.registerKeyBinding(OPEN_PACKET_LIST_SCREEN);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (RENDER_CAPTURE_HUD.wasPressed()) {
                this.showCapture = !this.showCapture;
                if (this.mc.getDebugHud().shouldShowDebugHud() && this.showCapture) {
                    this.mc.getDebugHud().toggleDebugHud();
                }
            }

            if (OPEN_PACKET_LIST_SCREEN.wasPressed()) {
                this.mc.setScreen(this.screen.setParent(this.mc.currentScreen));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((clientPlayNetworkHandler, minecraftClient) -> {
            this.sentBytes.set(0L);
            this.sentPacketNum.set(0L);
            this.receivedBytes.set(0L);
            this.receivedPacketNum.set(0L);
            this.clearPackets();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            shutdownExecutor(SENT_PACKET_DETAIL_RETRIEVER);
            shutdownExecutor(RECEIVED_PACKET_DETAIL_RETRIEVER);
        });

        HudLayerRegistrationCallback.EVENT.register(w -> {
            w.addLayer(IdentifiedLayer.of(PACKET_CAPTURE_HUD_LAYER_ID, (context, tickCounter) -> {
                if (this.showCapture) {
                    this.hud.render(context);
                }
            }));
        });

        ForgeConfigRegistry.INSTANCE.register(MOD_ID, Type.CLIENT, Config.SPEC);

        FabricLoader.getInstance().getEntrypointContainers(MOD_ID, PacketCaptureApi.class).forEach(entrypoint -> {
            var metadata = entrypoint.getProvider().getMetadata();
            var modId = metadata.getId();
            try {
                this.apis.add(entrypoint.getEntrypoint());
            } catch (Throwable e) {
                LOGGER.error("Mod {} has a broken impl of PacketCaptureApi", modId, e);
            }
        });
    }

    public static PacketCapture getInstance() {
        return instance;
    }

    private static void shutdownExecutor(ExecutorService e) {
        var stopwatch = Stopwatch.createStarted();
        LOGGER.info("Shutting down the executor...");
        e.shutdown();

        boolean f;
        try {
            f = e.awaitTermination(3L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            f = false;
        }

        if (!f) {
            e.shutdownNow();
        }

        LOGGER.info("Shutdown completed in {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        stopwatch.stop();
    }

    private <B extends ByteBuf, P extends Packet<?>> List<Highlight<?>> createHighlights(@Nullable B received, P packet, int packetEndIndex) {
        List<Highlight<?>> highlights = Lists.newArrayList();

        try {
            DataHighlightInstruction<B, P> inst = (DataHighlightInstruction<B, P>) DataHighlightInstructions.getFrom(packet.getClass());
            if (inst != null) {
                DynamicRegistryManager manager;
                while (this.mc.player == null) {
                    Thread.yield();
                }
                manager = this.mc.player.getRegistryManager();
                var reg = new RegistryByteBuf(Unpooled.buffer(), manager); // Free buf
                try {
                    highlights.addAll(inst.write(packetEndIndex + 1, received == null ? null : (B) new RegistryByteBuf(received, manager), (B) reg, packet));
                } catch (Throwable t) {
                    LOGGER.warn("Failed to create highlights for " + packet.getClass(), t);
                } finally {
                    reg.release();
                }
            }
        } catch (Throwable e) {
            LOGGER.warn("Failed to create highlights for " + packet.getClass(), e);
        }

        return highlights;
    }

    public <T extends PacketListener> void onEncodingPacketInMultiplayer(NetworkState<T> protocolInfo, Packet<T> packet, ByteBuf byteBuf) {
        if (packet.getPacketType().side() == NetworkSide.CLIENTBOUND || !this.isCapturing()) {
            return;
        }

        var copied = byteBuf.copy();
        if (this.isPacketTrash(packet)) {
            this.sentPacketNum.incrementAndGet();
            this.sentBytes.addAndGet(copied.readableBytes());
            copied.release();
            return;
        }

        CompletableFuture.supplyAsync(() -> {
                    int packetId = -1;
                    if (protocolInfo.codec() instanceof PacketCodecDispatcherAccessor codec) {
                        packetId = codec.getTypeToIndex().getOrDefault(codec.getPacketIdGetter().apply(packet), -1);
                    }

                    int end = VarInts.getSizeInBytes(packetId) - 1;
                    return new PacketDetails(packet, copied, packetId, end, this.createHighlights(null, packet, end));
                }, SENT_PACKET_DETAIL_RETRIEVER)
                .whenComplete((dedicatedServerPacketDetails, throwable) -> {
                    copied.release();

                    if (throwable != null) {
                        LOGGER.warn("Error occurred while creating packet details", throwable);
                    }

                    this.addToSent(dedicatedServerPacketDetails);
                });
    }

    public <T extends PacketListener> void onDecodingPacketInMultiplayer(NetworkState<T> protocolInfo, ByteBuf buf) {
        if (!this.isCapturing()) {
            return;
        }

        var byteBuf = buf.copy();
        var delivered = buf.copy();

        CompletableFuture.supplyAsync(() -> {
                    int i = byteBuf.readableBytes();
                    if (i == 0) {
                        return null;
                    }

                    var packet = protocolInfo.codec().decode(byteBuf);
                    if (packet.getPacketType().side() == NetworkSide.SERVERBOUND || byteBuf.readableBytes() > 0) {
                        return null;
                    }

                    if (this.isPacketTrash(packet)) {
                        this.receivedPacketNum.incrementAndGet();
                        this.receivedBytes.addAndGet(delivered.readableBytes());
                        return null;
                    }

                    int packetId = -1;
                    if (protocolInfo.codec() instanceof PacketCodecDispatcherAccessor codec) {
                        packetId = codec.getTypeToIndex().getOrDefault(codec.getPacketIdGetter().apply(packet), -1);
                    }

                    int end = VarInts.getSizeInBytes(packetId) - 1;
                    VarInts.read(byteBuf.resetReaderIndex()); // only read packet id.
                    return new PacketDetails(packet, delivered, packetId, end, this.createHighlights(byteBuf, packet, end));
                }, RECEIVED_PACKET_DETAIL_RETRIEVER)
                .whenComplete((details, throwable) -> {
                    byteBuf.release();
                    delivered.release();

                    if (throwable != null) {
                        LOGGER.warn("Error occurred while creating packet details", throwable);
                    }

                    if (details != null) {
                        this.addToReceived(details);
                    }
                });
    }

    private boolean isPacketTrash(Packet<?> packet) {
        var clazz = ClassVisitor.getClassName(packet.getClass());
        return this.trash(this.classNameDeobfuscater.deobfuscate(clazz));
    }

    public synchronized void loadFilters() {
        var file = this.filterConfig.toFile();
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (!file.exists()) {
            this.packetFilters.clear();
            this.packetFilters.addAll(DEFAULT_PACKET_FILTERS);
            this.saveFilters();
            return;
        }

        try (var r = Files.newBufferedReader(this.filterConfig)) {
            var list = (List<Map<String, String>>) GSON.fromJson(r, List.class);
            this.packetFilters.clear();
            list.forEach(m -> this.packetFilters.add(new PacketFilter(m.get("filteredBy"), m.get("filterType"))));
        } catch (Throwable e) {
            LOGGER.warn("Failed to load packet filters", e);
            this.packetFilters.clear();
            this.packetFilters.addAll(DEFAULT_PACKET_FILTERS);
            this.saveFilters();
        }
    }

    private synchronized void saveFilters() {
        this.filterConfig.toFile().delete();

        try (var w = Files.newBufferedWriter(this.filterConfig, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
             var jsonW = new JsonWriter(w)
        ) {
            jsonW.setIndent("  ");
            GSON.toJson(this.packetFilters, List.class, jsonW);
            jsonW.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to save packet filters", e);
        }
    }

    public void addFilter(PacketFilter filter) {
        this.packetFilters.add(filter);
        this.saveFilters();
    }

    public void removeFilter(PacketFilter target) {
        this.packetFilters.remove(target);
        this.saveFilters();
    }

    public void removeAllFilters() {
        this.packetFilters.clear();
        this.saveFilters();
    }

    public void restoreDefaultFilters() {
        this.packetFilters.clear();
        this.packetFilters.addAll(DEFAULT_PACKET_FILTERS);
        this.saveFilters();
    }

    public ImmutableSet<PacketFilter> getFilters() {
        return ImmutableSet.copyOf(this.packetFilters);
    }

    public void addToReceived(PacketDetails details) {
        this.receivedPacketNum.incrementAndGet();
        this.receivedBytes.addAndGet(details.getSize());

        if (this.trash(details.getPacketClassName())) {
            return;
        }

        this.receivedPackets.add(details);
        this.postAdd();
    }

    public void addToSent(PacketDetails details) {
        this.sentPacketNum.incrementAndGet();
        this.sentBytes.addAndGet(details.getSize());

        if (this.trash(details.getPacketClassName())) {
            return;
        }

        this.sentPackets.add(details);
        this.postAdd();
    }

    private boolean trash(String packetName) {
        synchronized (this.packetFilters) {
            for (var packetFilter : this.packetFilters) {
                if (packetFilter.isPacketTrash(packetName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void postAdd() {
        if (this.sentPackets.size() > MAX_PACKET_SIZE) {
            this.sentPackets.removeFirst();
        }

        if (this.receivedPackets.size() > MAX_PACKET_SIZE) {
            this.receivedPackets.removeFirst();
        }
    }

    public ImmutableList<PacketDetails> getSentPackets() {
        return ImmutableList.copyOf(this.sentPackets);
    }

    public ImmutableList<PacketDetails> getReceivedPackets() {
        return ImmutableList.copyOf(this.receivedPackets);
    }

    public void clearPackets() {
        this.sentPackets.clear();
        this.receivedPackets.clear();
    }

    public void toggle() {
        this.capturing.set(!this.isCapturing());
    }

    public boolean isCapturing() {
        return this.capturing.get();
    }

    public long getSentBytes() {
        return this.sentBytes.get();
    }

    public long getSentPacketNum() {
        return this.sentPacketNum.get();
    }

    public long getReceivedBytes() {
        return this.receivedBytes.get();
    }

    public long getReceivedPacketNum() {
        return this.receivedPacketNum.get();
    }

    public List<PacketCaptureApi> getApis() {
        return ImmutableList.copyOf(this.apis);
    }
}
