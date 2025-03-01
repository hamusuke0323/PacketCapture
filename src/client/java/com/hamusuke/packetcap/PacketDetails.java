package com.hamusuke.packetcap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hamusuke.packetcap.clazz.field.SimpleClassField;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.highlight.DataHighlightInstruction;
import com.hamusuke.packetcap.highlight.DataHighlightInstructions;
import com.hamusuke.packetcap.highlight.Highlight;
import com.hamusuke.packetcap.highlight.Highlight.HighlightRange;
import com.hamusuke.packetcap.utils.ByteConversion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.Packet;
import org.apache.commons.compress.utils.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

public class PacketDetails {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ClassVisitor visitor;
    private final ImmutableList<String> hex;
    private final int size;
    private final String fSize;
    private final int packetId;
    private final int packetIdEndIndex;
    private final List<Highlight<?>> highlights;
    private final Map<String, Highlight<?>> mapForHighlighting = Maps.newHashMap();

    public PacketDetails(Packet<?> packet, ByteBuf data, int packetId, int packetIdEndIndex, List<Highlight<?>> highlights) {
        this.visitor = new ClassVisitor(packet.getClass(), packet);
        this.hex = ImmutableList.copyOf(ByteBufUtil.prettyHexDump(data).lines().toList());
        this.size = data.readableBytes();
        this.fSize = ByteConversion.convertBytes(this.size);

        this.packetId = packetId;
        this.packetIdEndIndex = packetIdEndIndex;
        this.highlights = ImmutableList.copyOf(highlights);
    }

    public String getPacketClassName() {
        return this.visitor.getClassName();
    }

    public ClassVisitor getVisitor() {
        return this.visitor;
    }

    public int getSize() {
        return this.size;
    }

    public String getFriendlySize() {
        return this.fSize;
    }

    public ImmutableList<String> getHexLines() {
        return this.hex;
    }

    public int getPacketId() {
        return this.packetId;
    }

    public int getPacketIdEndIndex() {
        return this.packetIdEndIndex;
    }

    public synchronized void createMap() {
        if (!this.mapForHighlighting.isEmpty()) {
            return;
        }

        // common: fields
        this.getVisitor().visit();
        var highlighter = DataHighlightInstructions.getFrom(this.getVisitor().getClazz());
        var fields = this.getVisitor().getFields().stream()
                .filter(f -> f instanceof SimpleClassField && !f.isStatic())
                .toList();

        if (highlighter == null && fields.size() == 1) {
            List<Highlight<?>> subHighlights = Lists.newArrayList();
            var visitor = fields.getFirst().getVisitor();
            if (visitor != null && visitor.getInstance() != null) {
                DataHighlightInstruction fieldHighlighter = DataHighlightInstructions.getFrom(visitor.getClazz());
                if (fieldHighlighter != null) {
                    var mc = MinecraftClient.getInstance();
                    var buf = mc.player != null ? new RegistryByteBuf(Unpooled.buffer(), mc.player.getRegistryManager()) : new PacketByteBuf(Unpooled.buffer());
                    try {
                        subHighlights.addAll(fieldHighlighter.write(this.getPacketIdEndIndex() + 1, null, buf, visitor.getInstance()));
                    } catch (Throwable e) {
                        LOGGER.warn("Error occurred while creating highlight map", e);
                    } finally {
                        buf.release();
                    }
                }
            }

            this.mapForHighlighting.put(fields.getFirst().getName(), new Highlight<>(new HighlightRange(this.getPacketIdEndIndex() + 1, this.getSize() - 1), visitor == null ? null : visitor.getInstance(), "", subHighlights));
        } else if (fields.size() >= this.highlights.size()) {
            for (int i = 0; i < this.highlights.size(); i++) {
                this.mapForHighlighting.put(fields.get(i).getName(), this.highlights.get(i));
            }
        }

        // special: packet id
        this.mapForHighlighting.put("Packet Id: " + this.getPacketId(), new Highlight<>(new HighlightRange(0, this.getPacketIdEndIndex()), null, "", List.of()));
    }

    public Map<String, Highlight<?>> getMapForHighlighting() {
        return ImmutableMap.copyOf(this.mapForHighlighting);
    }
}
