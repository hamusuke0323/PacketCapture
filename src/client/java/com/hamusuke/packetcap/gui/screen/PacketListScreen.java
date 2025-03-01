package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.PacketDetails;
import com.hamusuke.packetcap.gui.components.EntryListWidget;
import com.hamusuke.packetcap.gui.components.ScalableCheckbox;
import com.hamusuke.packetcap.gui.screen.PacketListScreen.PacketList.Entry;
import com.hamusuke.packetcap.invoker.ParentListAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PacketListScreen extends Screen {
    private static final Text TITLE = Text.translatable(PacketCapture.MOD_ID + ".packetListScreen.title");
    private static final Text START_CAPTURING = Text.translatable(PacketCapture.MOD_ID + ".start_cap");
    private static final Text STOP_CAPTURING = Text.translatable(PacketCapture.MOD_ID + ".stop_cap");
    private static final Text CLEAR = Text.translatable(PacketCapture.MOD_ID + ".clear");
    private static final Text SENT = Text.translatable(PacketCapture.MOD_ID + ".sent");
    private static final Text RECEIVED = Text.translatable(PacketCapture.MOD_ID + ".received");
    private static final Text AUTO_SCROLL = Text.translatable(PacketCapture.MOD_ID + ".auto_scroll");
    private final PacketCapture capture;
    @Nullable
    private Screen parent;
    private PacketList sentPackets;
    private PacketList receivedPackets;
    private ScalableCheckbox autoTxScroll;
    private ScalableCheckbox autoRxScroll;

    public PacketListScreen(PacketCapture capture) {
        super(TITLE);
        this.capture = capture;
    }

    public PacketListScreen setParent(@Nullable Screen parent) {
        this.parent = parent;
        return this;
    }

    @Override
    protected void init() {
        super.init();

        double d = this.sentPackets != null ? this.sentPackets.getScrollY() : 0.0D;
        this.sentPackets = new PacketList(this.capture.getSentPackets(), 30, PacketListType.SENT);
        if (this.autoTxScroll == null) {
            this.autoTxScroll = new ScalableCheckbox(this.sentPackets.getRight() - 20, 20, 10, 10, AUTO_SCROLL, true);
            this.autoTxScroll.setTooltip(Tooltip.of(AUTO_SCROLL));
        }
        this.sentPackets.setScrollY(this.autoTxScroll.selected() ? this.sentPackets.getMaxScrollY() : d);
        this.addSelectableChild(this.sentPackets);
        this.addDrawableChild(this.autoTxScroll);

        double d1 = this.receivedPackets != null ? this.receivedPackets.getScrollY() : 0.0D;
        this.receivedPackets = new PacketList(this.capture.getReceivedPackets(), this.height / 2 + 10, PacketListType.RECEIVED);
        if (this.autoRxScroll == null) {
            this.autoRxScroll = new ScalableCheckbox(this.sentPackets.getRight() - 20, this.height / 2 + 10, 10, 10, AUTO_SCROLL, true);
            this.autoRxScroll.setTooltip(Tooltip.of(AUTO_SCROLL));
        }
        this.receivedPackets.setScrollY(this.autoRxScroll.selected() ? this.receivedPackets.getMaxScrollY() : d1);
        this.addSelectableChild(this.receivedPackets);
        this.addDrawableChild(this.autoRxScroll);

        this.addDrawableChild(ButtonWidget.builder(this.capture.isCapturing() ? STOP_CAPTURING : START_CAPTURING, p_93751_ -> {
            this.capture.toggle();
            p_93751_.setMessage(this.capture.isCapturing() ? STOP_CAPTURING : START_CAPTURING);
        }).dimensions(0, this.height - 20, this.width / 4, 20).build());

        this.addDrawableChild(ButtonWidget.builder(PacketFilterScreen.TITLE, p_93751_ -> this.client.setScreen(new PacketFilterScreen().setParent(this))).dimensions(this.width / 4, this.height - 20, this.width / 4, 20).build());

        this.addDrawableChild(ButtonWidget.builder(CLEAR, p_93751_ -> {
            this.capture.clearPackets();
            this.refreshWidgetPositions();
        }).dimensions(this.width / 2, this.height - 20, this.width / 4, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, p_93751_ -> this.close()).dimensions(this.width * 3 / 4, this.height - 20, this.width / 4, 20).build());
    }

    @Override
    public void render(DrawContext p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        super.render(p_281549_, p_281550_, p_282878_, p_282465_);
        this.receivedPackets.render(p_281549_, p_281550_, p_282878_, p_282465_);
        this.sentPackets.render(p_281549_, p_281550_, p_282878_, p_282465_);
        p_281549_.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 16777215);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    private enum PacketListType {
        SENT(PacketListScreen.SENT),
        RECEIVED(PacketListScreen.RECEIVED);

        private final Text text;

        PacketListType(Text text) {
            this.text = text;
        }
    }

    protected final class PacketList extends EntryListWidget<Entry> {
        private final PacketListType type;

        private PacketList(List<PacketDetails> list, int top, PacketListType type) {
            super(PacketListScreen.this.client, PacketListScreen.this.width, PacketListScreen.this.height / 2 - 50, top, 10);
            this.type = type;

            for (var details : list) {
                this.addEntry(new Entry(details));
            }
        }

        @Override
        protected void renderDecorations(DrawContext p_281477_, int p_93459_, int p_93460_) {
            var width = this.client.textRenderer.getWidth(this.type.text);
            var x = p_281477_.drawTextWithShadow(this.client.textRenderer, this.type.text, this.getWidth() / 2 - width / 2, this.getY() - 10, 16777215);
            x = Math.min(x, this.getRight() - 20);
            (switch (this.type) {
                case SENT -> PacketListScreen.this.autoTxScroll;
                case RECEIVED -> PacketListScreen.this.autoRxScroll;
            }).setPosition(x, this.getY() - 10);
        }

        @Override
        public void setScrollY(double scrollY) {
            super.setScrollY(scrollY);

            (switch (this.type) {
                case SENT -> PacketListScreen.this.autoTxScroll;
                case RECEIVED -> PacketListScreen.this.autoRxScroll;
            }).setSelected(this.getScrollY() >= this.getMaxScrollY());
        }

        @Override
        protected int getScrollbarX() {
            return this.getRight() - 6;
        }

        protected final class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> implements ShouldRefreshAfterScrolling {
            private static final Text TEXT = Text.translatable(PacketCapture.MOD_ID + ".button.show.details");
            private final PacketDetails packetDetails;
            private final ButtonWidget details;

            private Entry(PacketDetails details) {
                this.packetDetails = details;
                this.details = ButtonWidget.builder(TEXT, p_93751_ -> {
                    PacketListScreen.this.client.setScreen(new PacketDetailsScreen(PacketListScreen.this, this.packetDetails));
                }).dimensions(0, 0, 50, 10).build();
            }

            @Override
            public Text getNarration() {
                return ScreenTexts.EMPTY;
            }

            @Override
            public void render(DrawContext guiGraphics, int i, int top, int i2, int i3, int i4, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
                var text = Text.literal(this.packetDetails.getPacketClassName() + " (" + this.packetDetails.getFriendlySize() + ")");
                text.styled(style -> style.withFont(PacketCapture.MONO_FONT));
                var width = PacketListScreen.this.textRenderer.getWidth(text);
                var list = ParentListAccessor.parentList(this);
                var x = guiGraphics.drawTextWithShadow(PacketListScreen.this.textRenderer, text, (list.getRight() / 2 - (width + 50) / 2), top + 1, 16777215);
                x = Math.min(x, list.getRight() - 50);
                this.details.setX(x);
                this.details.setY(top);
                this.details.render(guiGraphics, mouseX, mouseY, tickDelta);
            }

            @Override
            public void onListScrolled(int top) {
                this.details.setY(top);
            }

            @Override
            public boolean mouseClicked(double p_94737_, double p_94738_, int p_94739_) {
                return this.details.mouseClicked(p_94737_, p_94738_, p_94739_);
            }
        }
    }
}
