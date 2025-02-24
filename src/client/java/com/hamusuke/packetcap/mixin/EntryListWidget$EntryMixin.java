package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.invoker.ParentListAccessor;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.gui.widget.EntryListWidget$Entry")
public abstract class EntryListWidget$EntryMixin implements ParentListAccessor {
    @Shadow
    @Deprecated
    EntryListWidget<?> parentList;

    @Override
    public EntryListWidget<?> getParentList() {
        return this.parentList;
    }
}
