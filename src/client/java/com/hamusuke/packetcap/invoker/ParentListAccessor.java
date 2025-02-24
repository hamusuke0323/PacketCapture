package com.hamusuke.packetcap.invoker;

import com.hamusuke.packetcap.gui.components.ClassFieldList.AbstractEntry;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.EntryListWidget;

public interface ParentListAccessor {
    static EntryListWidget<?> parentList(AlwaysSelectedEntryListWidget.Entry<?> widget) {
        return ((ParentListAccessor) widget).getParentList();
    }

    EntryListWidget<?> getParentList();
}
