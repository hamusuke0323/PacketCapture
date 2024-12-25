package com.hamusuke.packetcap;

import com.google.common.collect.*;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.hamusuke.packetcap.event.AddLayersEvent;
import com.hamusuke.packetcap.event.CreateMapForHexDumpHighlightEvent;
import com.hamusuke.packetcap.filter.FilterType;
import com.hamusuke.packetcap.filter.PacketFilter;
import com.hamusuke.packetcap.gui.overlay.PacketCaptureOverlay;
import com.hamusuke.packetcap.gui.screen.ConfigScreen;
import com.hamusuke.packetcap.gui.screen.PacketListScreen;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf;
import com.hamusuke.packetcap.packet.DedicatedPacket;
import com.hamusuke.packetcap.packet.DedicatedServerPacketDetails;
import com.hamusuke.packetcap.packet.PacketDetails;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
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

@Mod(PacketCapture.MOD_ID)
public final class PacketCapture {
    public static final String MOD_ID = "packetcapture";
    public static final ResourceLocation MONO_FONT = new ResourceLocation(MOD_ID, "mono");
    private static final ExecutorService SENT_PACKET_DETAIL_RETRIEVER = Executors.newSingleThreadExecutor(r -> new Thread(r, "Sent-Packet-Detail-Retriever"));
    private static final ExecutorService RECEIVED_PACKET_DETAIL_RETRIEVER = Executors.newSingleThreadExecutor(r -> new Thread(r, "Received-Packet-Detail-Retriever"));
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_PACKET_SIZE = 16384;
    private static final KeyMapping RENDER_CAPTURE_OVERLAY = new KeyMapping(MOD_ID + ".key.render_cap", GLFW.GLFW_KEY_HOME, KeyMapping.CATEGORY_MISC);
    private static final KeyMapping OPEN_PACKET_LIST_SCREEN = new KeyMapping(MOD_ID + ".key.open.packetListScreen", GLFW.GLFW_KEY_END, KeyMapping.CATEGORY_MISC);
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
            new PacketFilter("MovePlayer", FilterType.CONTAINS),
            new PacketFilter("Sound", FilterType.CONTAINS),
            new PacketFilter("Swing", FilterType.CONTAINS),
            new PacketFilter("KeepAlive", FilterType.CONTAINS),
            new PacketFilter("Ping", FilterType.CONTAINS),
            new PacketFilter("Pong", FilterType.CONTAINS),
            new PacketFilter("Attributes", FilterType.CONTAINS),
            new PacketFilter("SetTime", FilterType.CONTAINS),
            new PacketFilter("TeleportEntity", FilterType.CONTAINS),
            new PacketFilter("EntityEvent", FilterType.CONTAINS)
    );
    private static PacketCapture instance;
    private final Minecraft mc;
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
        this.mc = Minecraft.getInstance();
        this.overlay = new PacketCaptureOverlay(this.mc, this);
        this.screen = new PacketListScreen(this);
        var configDir = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        this.filterConfig = configDir.resolve("packet_filter.json");
        this.fieldsCsv = configDir.resolve("fields.csv");
        this.deobfuscationEnabled = this.loadCsv();
        this.loadFilters();
        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PacketCapture::registerKeyBinding);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onAddLayers);

        ModLoadingContext.get().registerConfig(Type.CLIENT, Config.SPEC);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenFactory.class, () -> new ConfigScreenFactory(ConfigScreen::new));
    }

    private static void registerKeyBinding(RegisterKeyMappingsEvent event) {
        event.register(RENDER_CAPTURE_OVERLAY);
        event.register(OPEN_PACKET_LIST_SCREEN);
    }

    private void onAddLayers(final AddLayersEvent event) {
        event.add((guiGraphics, v) -> {
            if (this.showCapture) {
                this.overlay.render(guiGraphics);
            }
        });
    }

    public static PacketCapture getInstance() {
        return instance;
    }

    @SubscribeEvent
    public void onClientPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        this.sentBytes.set(0L);
        this.sentPacketNum.set(0L);
        this.receivedBytes.set(0L);
        this.receivedPacketNum.set(0L);
        this.clearPackets();
    }

    @SubscribeEvent
    public void onGameShuttingDown(GameShuttingDownEvent e) {
        shutdownExecutor(SENT_PACKET_DETAIL_RETRIEVER);
        shutdownExecutor(RECEIVED_PACKET_DETAIL_RETRIEVER);
    }

    @SubscribeEvent
    public void onCreateMap(CreateMapForHexDumpHighlightEvent e) {
        var className = e.getDetails().getVisitor().getClassName();
        var fields = e.getFields();
        var logs = e.getDetails().getWriteLog();
        if (fields.size() >= 6 && logs.size() == 6 && className.contains("ServerboundMovePlayerPacket$PosRot")) { // special case
            for (int i = 0; i < 6; i++) {
                e.registerHighlight(fields.get(i).getName(), logs.get(i));
            }
            return;
        }

        if (fields.size() >= 6 && logs.size() == 4 && className.contains("ServerboundMovePlayerPacket$Pos")) {
            for (int i = 0; i < 3; i++) {
                e.registerHighlight(fields.get(i).getName(), logs.get(i));
            }

            e.registerHighlight(fields.get(5).getName(), logs.get(3));
            return;
        }

        if (fields.size() >= 6 && logs.size() == 3 && className.contains("ServerboundMovePlayerPacket$Rot")) {
            for (int i = 3; i < 6; i++) {
                e.registerHighlight(fields.get(i).getName(), logs.get(i - 3));
            }

            return;
        }

        if (fields.size() >= 6 && logs.size() == 1 && className.contains("ServerboundMovePlayerPacket$StatusOnly")) {
            e.registerHighlight(fields.get(5).getName(), logs.getFirst());
            return;
        }

        if (className.contains("ClientboundMoveEntityPacket$PosRot") && fields.size() >= 7 && logs.size() == 7) {
            for (int i = 0; i < 7; i++) {
                e.registerHighlight(fields.get(i).getName(), logs.get(i));
            }

            return;
        }

        if (className.contains("ClientboundMoveEntityPacket$Pos") && fields.size() >= 7 && logs.size() == 5) {
            for (int i = 0; i < 4; i++) {
                e.registerHighlight(fields.get(i).getName(), logs.get(i));
            }

            e.registerHighlight(fields.get(6).getName(), logs.get(4));
            return;
        }

        if (className.contains("ClientboundMoveEntityPacket$Rot") && fields.size() >= 7 && logs.size() == 4) {
            e.registerHighlight(fields.getFirst().getName(), logs.getFirst());
            for (int i = 4; i < 7; i++) {
                e.registerHighlight(fields.get(i).getName(), logs.get(i - 3));
            }
        }
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

    public <T extends PacketListener> void onEncodingPacketInMultiplayer(ProtocolInfo<T> protocolInfo, Packet<T> packet, ByteBuf byteBuf) {
        if (Minecraft.getInstance().hasSingleplayerServer() || packet.type().flow() == PacketFlow.CLIENTBOUND || !this.isCapturing()) {
            return;
        }

        var copied = byteBuf.copy();

        CompletableFuture.supplyAsync(() -> {
                    var newBuf = new WrittenBytesLoggingByteBuf(Unpooled.buffer());
                    protocolInfo.codec().encode(newBuf, packet);
                    newBuf.onFinishWriting();
                    newBuf.release();

                    return new DedicatedServerPacketDetails(packet, copied, newBuf);
                }, SENT_PACKET_DETAIL_RETRIEVER)
                .whenComplete((dedicatedServerPacketDetails, throwable) -> {
                    this.addToSent(dedicatedServerPacketDetails);
                });
    }

    public <T extends PacketListener> void onDecodingPacketInMultiplayer(ProtocolInfo<T> protocolInfo, ByteBuf buf) {
        if (!this.isCapturing() || Minecraft.getInstance().hasSingleplayerServer()) {
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
                    if (packet.type().flow() == PacketFlow.SERVERBOUND || byteBuf.readableBytes() > 0) {
                        return null;
                    }

                    var newBuf = new WrittenBytesLoggingByteBuf(Unpooled.buffer());
                    protocolInfo.codec().encode(newBuf, packet);
                    newBuf.onFinishWriting();
                    newBuf.release();

                    return new DedicatedServerPacketDetails(packet, delivered, newBuf);
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
        if (details instanceof DedicatedPacket dedicatedPacket) {
            this.receivedBytes.addAndGet(dedicatedPacket.getSize());
        }

        if (this.trash(details.getPacketClassName())) {
            return;
        }

        this.receivedPackets.add(details);
        this.postAdd();
    }

    public void addToSent(PacketDetails details) {
        this.sentPacketNum.incrementAndGet();
        if (details instanceof DedicatedPacket dedicatedPacket) {
            this.sentBytes.addAndGet(dedicatedPacket.getSize());
        }

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

    @SubscribeEvent
    public void onTick(final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (RENDER_CAPTURE_OVERLAY.consumeClick()) {
                this.showCapture = !this.showCapture;
                if (this.mc.getDebugOverlay().showDebugScreen() && this.showCapture) {
                    this.mc.getDebugOverlay().toggleOverlay();
                }
            }

            if (OPEN_PACKET_LIST_SCREEN.consumeClick()) {
                this.mc.setScreen(this.screen.setParent(this.mc.screen));
            }
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
