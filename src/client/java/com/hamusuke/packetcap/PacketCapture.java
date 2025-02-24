package com.hamusuke.packetcap;

import com.google.common.collect.*;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.hamusuke.packetcap.filter.FilterType;
import com.hamusuke.packetcap.filter.PacketFilter;
import com.hamusuke.packetcap.gui.overlay.PacketCaptureOverlay;
import com.hamusuke.packetcap.gui.screen.PacketListScreen;
import com.hamusuke.packetcap.highlight.DataHighlightInstruction;
import com.hamusuke.packetcap.highlight.DataHighlightInstructions;
import com.hamusuke.packetcap.highlight.Highlight;
import com.hamusuke.packetcap.invoker.PacketCodecDispatcherAccessor;
import fuzs.forgeconfigapiport.fabric.api.forge.v4.ForgeConfigRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
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
    private static final Identifier PACKET_CAPTURE_OVERLAY_LAYER_ID = Identifier.of(MOD_ID, "layer");
    private static final ExecutorService SENT_PACKET_DETAIL_RETRIEVER = Executors.newSingleThreadExecutor(r -> new Thread(r, "Sent-Packet-Detail-Retriever"));
    private static final ExecutorService RECEIVED_PACKET_DETAIL_RETRIEVER = Executors.newSingleThreadExecutor(r -> new Thread(r, "Received-Packet-Detail-Retriever"));
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_PACKET_SIZE = 16384;
    private static final KeyBinding RENDER_CAPTURE_OVERLAY = new KeyBinding(MOD_ID + ".key.render_cap", GLFW.GLFW_KEY_HOME, KeyBinding.MISC_CATEGORY);
    private static final KeyBinding OPEN_PACKET_LIST_SCREEN = new KeyBinding(MOD_ID + ".key.open.packetListScreen", GLFW.GLFW_KEY_END, KeyBinding.MISC_CATEGORY);
    private static final Gson GSON = new Gson();
    private static final Set<PacketFilter> DEFAULT_PACKET_FILTERS = Set.of(
            new PacketFilter("Chunk", FilterType.CONTAINS),
            new PacketFilter("Motion", FilterType.CONTAINS),
            new PacketFilter("BlockUpdate", FilterType.CONTAINS),
            new PacketFilter("MoveEntity", FilterType.CONTAINS),
            new PacketFilter("EntityData", FilterType.CONTAINS),
            new PacketFilter("RotateHead", FilterType.CONTAINS),
            new PacketFilter("RemoveEntities", FilterType.CONTAINS),
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
            new PacketFilter("SetTime", FilterType.CONTAINS),
            new PacketFilter("TeleportEntity", FilterType.CONTAINS),
            new PacketFilter("EntityEvent", FilterType.CONTAINS)
    );
    private static PacketCapture instance;
    private final MinecraftClient mc;
    private final PacketCaptureOverlay overlay;
    private final PacketListScreen screen;
    private final Path filterConfig;
    private final Path fieldsCsv;
    private final boolean deobfuscationEnabled;
    private final Map<String, String> deobMap = Maps.newHashMap();
    private final Set<PacketFilter> packetFilters = Collections.synchronizedSet(Sets.newHashSet());
    private final AtomicBoolean capturing = new AtomicBoolean(true);
    private final AtomicLong sentBytes = new AtomicLong();
    private final AtomicLong sentPacketNum = new AtomicLong();
    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong receivedPacketNum = new AtomicLong();
    private final List<PacketDetails> sentPackets = Collections.synchronizedList(Lists.newLinkedList());
    private final List<PacketDetails> receivedPackets = Collections.synchronizedList(Lists.newLinkedList());
    private boolean showCapture;

    public PacketCapture() {
        instance = this;

        this.mc = MinecraftClient.getInstance();
        this.overlay = new PacketCaptureOverlay(this.mc, this);
        this.screen = new PacketListScreen(this);
        var configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        this.filterConfig = configDir.resolve("packet_filter.json");
        this.fieldsCsv = configDir.resolve("fields.csv");
        this.deobfuscationEnabled = this.loadCsv();
        this.loadFilters();
    }

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(RENDER_CAPTURE_OVERLAY);
        KeyBindingHelper.registerKeyBinding(OPEN_PACKET_LIST_SCREEN);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (RENDER_CAPTURE_OVERLAY.wasPressed()) {
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
            w.addLayer(IdentifiedLayer.of(PACKET_CAPTURE_OVERLAY_LAYER_ID, (context, tickCounter) -> {
                if (this.showCapture) {
                    this.overlay.render(context);
                }
            }));
        });

        ForgeConfigRegistry.INSTANCE.register(MOD_ID, Type.CLIENT, Config.SPEC);
    }

    public static PacketCapture getInstance() {
        return instance;
    }

    private static void shutdownExecutor(ExecutorService e) {
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
    }

    private List<Highlight<?>> createHighlights(Packet<?> packet, int packetEndIndex) {
        List<Highlight<?>> highlights = Lists.newArrayList();
        DataHighlightInstruction inst = DataHighlightInstructions.getFrom(packet.getClass());
        if (inst != null) {
            DynamicRegistryManager manager = null;
            if (this.mc.getNetworkHandler() != null) {
                manager = this.mc.getNetworkHandler().getRegistryManager();
            }
            var reg = new RegistryByteBuf(Unpooled.buffer(), manager);
            try {
                highlights.addAll(inst.createHighlights(packetEndIndex + 1, reg, packet));
            } finally {
                reg.release();
            }
        }

        return highlights;
    }

    public <T extends PacketListener> void onEncodingPacketInMultiplayer(NetworkState<T> protocolInfo, Packet<T> packet, ByteBuf byteBuf) {
        if (packet.getPacketType().side() == NetworkSide.CLIENTBOUND || !this.isCapturing()) {
            return;
        }

        var copied = byteBuf.copy();

        CompletableFuture.supplyAsync(() -> {
                    int packetId = -1;
                    if (protocolInfo.codec() instanceof PacketCodecDispatcherAccessor codec) {
                        packetId = codec.getTypeToIndex().getOrDefault(codec.getPacketIdGetter().apply(packet), -1);
                    }

                    int end = VarInts.getSizeInBytes(packetId) - 1;
                    return new PacketDetails(packet, copied, packetId, end, this.createHighlights(packet, end));
                }, SENT_PACKET_DETAIL_RETRIEVER)
                .whenComplete((dedicatedServerPacketDetails, throwable) -> {
                    this.addToSent(dedicatedServerPacketDetails);
                });
    }

    public <T extends PacketListener> void onDecodingPacketInMultiplayer(NetworkState<T> protocolInfo, ByteBuf buf) {
        if (!this.isCapturing()) {
            return;
        }

        var byteBuf = buf.copy();
        var delivered = byteBuf.copy();

        CompletableFuture.supplyAsync(() -> {
                    int i = byteBuf.readableBytes();
                    if (i == 0) {
                        return null;
                    }

                    var packet = protocolInfo.codec().decode(byteBuf);
                    if (packet.getPacketType().side() == NetworkSide.SERVERBOUND || byteBuf.readableBytes() > 0) {
                        return null;
                    }

                    int packetId = -1;
                    if (protocolInfo.codec() instanceof PacketCodecDispatcherAccessor codec) {
                        packetId = codec.getTypeToIndex().getOrDefault(codec.getPacketIdGetter().apply(packet), -1);
                    }

                    int end = VarInts.getSizeInBytes(packetId) - 1;
                    return new PacketDetails(packet, delivered, packetId, end, this.createHighlights(packet, end));
                }, RECEIVED_PACKET_DETAIL_RETRIEVER)
                .whenComplete((details, throwable) -> {
                    ReferenceCountUtil.release(byteBuf);

                    if (details != null) {
                        this.addToReceived(details);
                    }
                });
    }

    private boolean loadCsv() {
        var file = this.fieldsCsv.toFile();
        if (!file.exists() || !file.isFile()) {
            return false;
        }

        try {
            this.deobMap.clear();
            for (var line : Files.readAllLines(this.fieldsCsv, StandardCharsets.UTF_8)) {
                var dataArray = line.split(",");
                this.deobMap.put(dataArray[0], dataArray[1]);
            }
            return true;
        } catch (Throwable e) {
            LOGGER.warn("Error occurred while loading csv file", e);
            return false;
        }
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

    public String deobfuscate(String obfuscated) {
        return this.deobfuscationEnabled ? this.deobMap.getOrDefault(obfuscated, obfuscated) : obfuscated;
    }
}
