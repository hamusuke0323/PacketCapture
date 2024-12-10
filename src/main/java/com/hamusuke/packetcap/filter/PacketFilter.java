package com.hamusuke.packetcap.filter;

import java.util.Objects;

public record PacketFilter(String filteredBy, FilterType filterType) {
    public PacketFilter(String filteredBy, String filterType) {
        this(filteredBy, FilterType.valueOf(filterType));
    }

    public boolean isPacketTrash(String packetName) {
        return this.filterType.isTrash(packetName, this.filteredBy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PacketFilter that = (PacketFilter) o;
        return Objects.equals(filteredBy, that.filteredBy) && filterType == that.filterType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(filteredBy, filterType);
    }
}
