package com.hamusuke.packetcap.event;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hamusuke.packetcap.clazz.field.ClassField;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf.WriteLog;
import com.hamusuke.packetcap.packet.DedicatedServerPacketDetails;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;
import java.util.Map;

public final class CreateMapForHexDumpHighlightEvent extends Event {
    private final Map<String, WriteLog> map = Maps.newHashMap();
    private final DedicatedServerPacketDetails details;
    private final List<ClassField> fields;

    public CreateMapForHexDumpHighlightEvent(DedicatedServerPacketDetails details, List<ClassField> fields) {
        this.details = details;
        this.fields = fields;
    }

    public void registerHighlight(String fieldName, WriteLog writeLog) {
        this.map.put(fieldName, writeLog);
    }

    public DedicatedServerPacketDetails getDetails() {
        return this.details;
    }

    public List<ClassField> getFields() {
        return this.fields;
    }

    public Map<String, WriteLog> getMap() {
        return ImmutableMap.copyOf(this.map);
    }
}
