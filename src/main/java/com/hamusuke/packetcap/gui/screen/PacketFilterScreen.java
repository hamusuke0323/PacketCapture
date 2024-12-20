package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.filter.PacketFilter;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Collection;

public class PacketFilterScreen extends Screen {
    public static final Component TITLE = Component.translatable(PacketCapture.MOD_ID + ".packet_filter");
    private static final Component RELOAD = Component.translatable(PacketCapture.MOD_ID + ".reload");
    private static final Component REMOVE_ALL = Component.translatable(PacketCapture.MOD_ID + ".remove_all");
    private static final Component RESTORE_DEFAULT = Component.translatable(PacketCapture.MOD_ID + ".restore_default");
    @Nullable
    private Screen parent;
    private PacketFilterList list;

    public PacketFilterScreen() {
        super(TITLE);
    }

    public PacketFilterScreen setParent(@Nullable Screen parent) {
        this.parent = parent;
        return this;
    }

    @Override
    protected void init() {
        super.init();

        this.list = new PacketFilterList(PacketCapture.getInstance().getFilters());
        this.addWidget(this.list);

        this.addRenderableWidget(Button.builder(REMOVE_ALL, p_93751_ -> {
            PacketCapture.getInstance().removeAllFilters();
            this.rebuildWidgets();
        }).bounds(0, this.height - 40, this.width / 3, 20).build());
        this.addRenderableWidget(Button.builder(RELOAD, p_93751_ -> {
            PacketCapture.getInstance().loadFilters();
            this.rebuildWidgets();
        }).bounds(this.width / 3, this.height - 40, this.width / 3, 20).build());
        this.addRenderableWidget(Button
                .builder(AddPacketFilterScreen.TITLE, p_93751_ -> this.minecraft.setScreen(new AddPacketFilterScreen().setParent(this)))
                .bounds(this.width * 2 / 3, this.height - 40, this.width / 3, 20)
                .build());
        this.addRenderableWidget(Button
                .builder(RESTORE_DEFAULT, button -> {
                    PacketCapture.getInstance().restoreDefaultFilters();
                    this.rebuildWidgets();
                })
                .bounds(0, this.height - 20, this.width / 2, 20)
                .build());
        this.addRenderableWidget(Button
                .builder(CommonComponents.GUI_BACK, p_93751_ -> this.onClose())
                .bounds(this.width / 2, this.height - 20, this.width / 2, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        super.render(p_281549_, p_281550_, p_282878_, p_282465_);
        this.list.render(p_281549_, p_281550_, p_282878_, p_282465_);
        p_281549_.drawCenteredString(this.font, this.title, this.width / 2, 5, 16777215);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private final class PacketFilterList extends ObjectSelectionList<PacketFilterList.Entry> {
        private static final Component LIST_TITLE = Component.translatable(PacketCapture.MOD_ID + ".cur_filtering");

        private PacketFilterList(Collection<PacketFilter> list) {
            super(PacketFilterScreen.this.minecraft, PacketFilterScreen.this.width, PacketFilterScreen.this.height - 70, 30, 10);

            for (var filter : list) {
                this.addEntry(new Entry(filter));
            }
        }

        @Override
        public boolean isMouseOver(double p_93479_, double p_93480_) {
            return p_93480_ >= (double) this.getY() && p_93480_ <= (double) this.getBottom();
        }

        @Override
        public boolean mouseClicked(double p_93420_, double p_93421_, int p_93422_) {
            this.updateScrollingState(p_93420_, p_93421_, p_93422_);

            if (!this.isMouseOver(p_93420_, p_93421_)) {
                return false;
            }

            for (var child : this.children()) {
                if (child.mouseClicked(p_93420_, p_93421_, p_93422_)) {
                    this.setFocused(child);
                    this.setDragging(true);
                    return true;
                }
            }

            if (p_93422_ == 0) {
                this.clickedHeader((int) (p_93420_ - (double) (this.getRowLeft() + this.width / 2 - this.getRowWidth() / 2)), (int) (p_93421_ - (double) this.getY()) + (int) this.getScrollAmount() - 4);
                return true;
            }

            return this.scrolling;
        }

        @Override
        protected void renderDecorations(GuiGraphics p_281477_, int p_93459_, int p_93460_) {
            p_281477_.drawCenteredString(this.minecraft.font, LIST_TITLE, this.width / 2, this.getY() - 10, 16777215);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getRight() - 6;
        }

        private final class Entry extends ObjectSelectionList.Entry<Entry> {
            private static final Component TEXT = Component.translatable(PacketCapture.MOD_ID + ".button.remove");
            private final PacketFilter packetFilter;
            private final Button remove;

            private Entry(PacketFilter filter) {
                this.packetFilter = filter;
                this.remove = Button.builder(TEXT, p_93751_ -> {
                    p_93751_.active = false;
                    PacketFilterList.this.removeEntry(this);
                    PacketCapture.getInstance().removeFilter(this.packetFilter);
                }).bounds(0, 0, 50, 10).build();
            }

            @Override
            public Component getNarration() {
                return GameNarrator.NO_TITLE;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int i, int top, int i2, int i3, int i4, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
                var text = Component.translatable(PacketCapture.MOD_ID + ".filter_detail", this.packetFilter.filteredBy(), this.packetFilter.filterType().toString());
                var fontWidth = PacketFilterScreen.this.font.width(text);
                var x = guiGraphics.drawString(PacketFilterScreen.this.font, text, (this.list.getRight() / 2 - (fontWidth + 50) / 2), top + 1, 16777215);
                x = Math.min(x, this.list.getRight() - 50);
                this.remove.setX(x);
                this.remove.setY(top);
                this.remove.render(guiGraphics, mouseX, mouseY, tickDelta);
            }

            @Override
            public boolean mouseClicked(double p_94737_, double p_94738_, int p_94739_) {
                return this.remove.mouseClicked(p_94737_, p_94738_, p_94739_);
            }
        }
    }
}
