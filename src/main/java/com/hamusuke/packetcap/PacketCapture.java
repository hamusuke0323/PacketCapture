package com.hamusuke.packetcap;

import com.google.common.collect.*;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.hamusuke.packetcap.clazz.visitor.PairVisitor;
import com.hamusuke.packetcap.event.AddLayersEvent;
import com.hamusuke.packetcap.event.RegisterClassVisitorsEvent;
import com.hamusuke.packetcap.filter.FilterType;
import com.hamusuke.packetcap.filter.PacketFilter;
import com.hamusuke.packetcap.gui.overlay.PacketCaptureOverlay;
import com.hamusuke.packetcap.gui.screen.PacketListScreen;
import com.hamusuke.packetcap.packet.DedicatedPacket;
import com.hamusuke.packetcap.packet.PacketDetails;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Mod(PacketCapture.MOD_ID)
public final class PacketCapture {
    public static final String MOD_ID = "packetcapture";
    public static final ResourceLocation MONO_FONT = new ResourceLocation(MOD_ID, "mono");
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
            this.sentPackets.remove(0);
        }

        if (this.receivedPackets.size() > MAX_PACKET_SIZE) {
            this.receivedPackets.remove(0);
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
