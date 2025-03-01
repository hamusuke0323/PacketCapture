package com.hamusuke.packetcap.gui.components;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;

public class EntryListWidget<E extends AlwaysSelectedEntryListWidget.Entry<E>> extends AlwaysSelectedEntryListWidget<E> {
    public EntryListWidget(MinecraftClient minecraftClient, int i, int j, int k, int l) {
        super(minecraftClient, i, j, k, l);
    }

    public EntryListWidget(MinecraftClient minecraftClient, int i, int j, int k, int l, int m) {
        super(minecraftClient, i, j, k, l, m);
    }

    @Override
    public boolean isMouseOver(double p_93479_, double p_93480_) {
        return p_93480_ >= (double) this.getY() && p_93480_ <= (double) this.getBottom();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.checkScrollbarDragged(mouseX, mouseY, button);
        for (var e : this.children()) {
            if (e.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void setScrollY(double scrollY) {
        super.setScrollY(scrollY);
        this.onListScrolled();
    }

    protected void onListScrolled() {
        int size = this.getEntryCount();
        var children = this.children();

        for (int i = 0; i < size; ++i) {
            if (children.get(i) instanceof ShouldRefreshAfterScrolling casted) {
                casted.onListScrolled(this.getRowTop(i));
            }
        }
    }

    public interface ShouldRefreshAfterScrolling {
        void onListScrolled(int top);
    }
}
