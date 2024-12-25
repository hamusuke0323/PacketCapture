package com.hamusuke.packetcap.packet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hamusuke.packetcap.clazz.field.SimpleClassField;
import com.hamusuke.packetcap.event.CreateMapForHexDumpHighlightEvent;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf.WriteLog;
import com.hamusuke.packetcap.utils.ByteConversion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class DedicatedServerPacketDetails extends PacketDetails implements DedicatedPacket {
    private final ImmutableList<String> hex;
    private final int size;
    private final String fSize;
    private final int packetId;
    private final int packetIdEndIndex;
    private final List<WriteLog> writeLog;
    private final Map<String, WriteLog> mapForHighlighting = Maps.newHashMap();

    public DedicatedServerPacketDetails(Packet<?> packet, ByteBuf data, WrittenBytesLoggingByteBuf buf) {
        super(packet);
        this.hex = ImmutableList.copyOf(ByteBufUtil.prettyHexDump(data).lines().toList());
        this.size = data.readableBytes();
        this.fSize = ByteConversion.convertBytes(this.size);
        data.release();

        this.packetId = buf.getPacketId();
        this.packetIdEndIndex = buf.getPacketIdEndIndex();
        this.writeLog = buf.getLogs();
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public String getFriendlySize() {
        return this.fSize;
    }

    @Override
    public ImmutableList<String> getHexLines() {
        return this.hex;
    }

    @Override
    public int getPacketId() {
        return this.packetId;
    }

    @Override
    public int getPacketIdEndIndex() {
        return this.packetIdEndIndex;
    }

    @Override
    public List<WriteLog> getWriteLog() {
        return ImmutableList.copyOf(this.writeLog);
    }

    @Override
    public synchronized void createMap() {
        if (!this.mapForHighlighting.isEmpty()) {
            return;
        }

        // common: fields
        this.getVisitor().visit();
        var fields = this.getVisitor().getFields().stream()
                .filter(f -> f instanceof SimpleClassField && !f.isStatic())
                .toList();

        if (fields.size() == this.getWriteLog().size()) {
            for (int i = 0; i < fields.size(); i++) {
                this.mapForHighlighting.put(fields.get(i).getName(), this.getWriteLog().get(i));
            }
        }

        var e = new CreateMapForHexDumpHighlightEvent(this, fields);
        MinecraftForge.EVENT_BUS.post(e);
        this.mapForHighlighting.putAll(e.getMap());

        // special: packet id
        this.mapForHighlighting.put("Packet Id: " + this.getPacketId(), new WriteLog(0, this.getPacketIdEndIndex()));
    }

    @Override
    public Map<String, WriteLog> getMapForHighlighting() {
        return ImmutableMap.copyOf(this.mapForHighlighting);
    }
}
