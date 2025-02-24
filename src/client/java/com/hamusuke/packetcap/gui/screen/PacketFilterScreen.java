package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.filter.PacketFilter;
import com.hamusuke.packetcap.gui.screen.PacketFilterScreen.PacketFilterList.Entry;
import com.hamusuke.packetcap.invoker.ParentListAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class PacketFilterScreen extends Screen {
    public static final Text TITLE = Text.translatable(PacketCapture.MOD_ID + ".packet_filter");
    private static final Text RELOAD = Text.translatable(PacketCapture.MOD_ID + ".reload");
    private static final Text REMOVE_ALL = Text.translatable(PacketCapture.MOD_ID + ".remove_all");
    private static final Text RESTORE_DEFAULT = Text.translatable(PacketCapture.MOD_ID + ".restore_default");
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
        this.addSelectableChild(this.list);

        this.addDrawableChild(ButtonWidget.builder(REMOVE_ALL, p_93751_ -> {
            PacketCapture.getInstance().removeAllFilters();
            this.refreshWidgetPositions();
        }).dimensions(0, this.height - 40, this.width / 3, 20).build());
        this.addDrawableChild(ButtonWidget.builder(RELOAD, p_93751_ -> {
            PacketCapture.getInstance().loadFilters();
            this.refreshWidgetPositions();
        }).dimensions(this.width / 3, this.height - 40, this.width / 3, 20).build());
        this.addDrawableChild(ButtonWidget
                .builder(AddPacketFilterScreen.TITLE, p_93751_ -> this.client.setScreen(new AddPacketFilterScreen().setParent(this)))
                .dimensions(this.width * 2 / 3, this.height - 40, this.width / 3, 20)
                .build());
        this.addDrawableChild(ButtonWidget
                .builder(RESTORE_DEFAULT, button -> {
                    PacketCapture.getInstance().restoreDefaultFilters();
                    this.refreshWidgetPositions();
                })
                .dimensions(0, this.height - 20, this.width / 2, 20)
                .build());
        this.addDrawableChild(ButtonWidget
                .builder(ScreenTexts.BACK, p_93751_ -> this.close())
                .dimensions(this.width / 2, this.height - 20, this.width / 2, 20)
                .build());
    }

    @Override
    public void render(DrawContext p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        super.render(p_281549_, p_281550_, p_282878_, p_282465_);
        this.list.render(p_281549_, p_281550_, p_282878_, p_282465_);
        p_281549_.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 16777215);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    protected final class PacketFilterList extends AlwaysSelectedEntryListWidget<Entry> {
        private static final Text LIST_TITLE = Text.translatable(PacketCapture.MOD_ID + ".cur_filtering");

        private PacketFilterList(Collection<PacketFilter> list) {
            super(PacketFilterScreen.this.client, PacketFilterScreen.this.width, PacketFilterScreen.this.height - 70, 30, 10);

            for (var filter : list) {
                this.addEntry(new Entry(filter));
            }
        }

        @Override
        public boolean isMouseOver(double p_93479_, double p_93480_) {
            return p_93480_ >= (double) this.getY() && p_93480_ <= (double) this.getBottom();
        }

        @Override
        protected void renderDecorations(DrawContext p_281477_, int p_93459_, int p_93460_) {
            p_281477_.drawCenteredTextWithShadow(this.client.textRenderer, LIST_TITLE, this.width / 2, this.getY() - 10, 16777215);
        }

        @Override
        protected int getScrollbarX() {
            return this.getRight() - 6;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (var e : this.children()) {
                if (e.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        protected final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private static final Text TEXT = Text.translatable(PacketCapture.MOD_ID + ".button.remove");
            private final PacketFilter packetFilter;
            private final ButtonWidget remove;

            private Entry(PacketFilter filter) {
                this.packetFilter = filter;
                this.remove = ButtonWidget.builder(TEXT, p_93751_ -> {
                    p_93751_.active = false;
                    PacketFilterList.this.removeEntry(this);
                    PacketCapture.getInstance().removeFilter(this.packetFilter);
                }).dimensions(0, 0, 50, 10).build();
            }

            @Override
            public Text getNarration() {
                return ScreenTexts.EMPTY;
            }

            @Override
            public void render(DrawContext guiGraphics, int i, int top, int i2, int i3, int i4, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
                var text = Text.translatable(PacketCapture.MOD_ID + ".filter_detail", this.packetFilter.filteredBy(), this.packetFilter.filterType().toString());
                var fontWidth = PacketFilterScreen.this.textRenderer.getWidth(text);
                var list = ParentListAccessor.parentList(this);
                var x = guiGraphics.drawTextWithShadow(PacketFilterScreen.this.textRenderer, text, (list.getRight() / 2 - (fontWidth + 50) / 2), top + 1, 16777215);
                x = Math.min(x, list.getRight() - 50);
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
