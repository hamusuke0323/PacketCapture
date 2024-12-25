package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.filter.FilterType;
import com.hamusuke.packetcap.filter.PacketFilter;
import com.hamusuke.packetcap.gui.components.ClassFieldList;
import com.hamusuke.packetcap.gui.components.ClassFieldList.HasClassField;
import com.hamusuke.packetcap.network.WrittenBytesLoggingByteBuf.WriteLog;
import com.hamusuke.packetcap.packet.DedicatedPacket;
import com.hamusuke.packetcap.packet.PacketDetails;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector2i;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.stream.IntStream;

@OnlyIn(Dist.CLIENT)
public class PacketDetailsScreen extends Screen {
    private static final Component ADD_TO_FILTER = Component.translatable(PacketCapture.MOD_ID + ".add_to_filter");
    private static final Component DATA = Component.translatable(PacketCapture.MOD_ID + ".packetData");
    @Nullable
    private final Screen parent;
    private final PacketDetails details;
    private final Map<String, WriteLog> mapForHighlighting;
    private final boolean isMP;
    private Details packetFields;
    private HexDump hexDump;
    private float hexDumpPrefixWidth;
    private float hexLineWidth;
    private float oneByteWidth;
    private float oneCharWidth;

    public PacketDetailsScreen(@Nullable Screen parent, PacketDetails details) {
        super(Component.literal(details.getPacketClassName() + (details instanceof DedicatedPacket d ? " (" + d.getFriendlySize() + ")" : "")).withStyle(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.parent = parent;
        this.details = details;
        this.details.getVisitor().visit();
        this.isMP = this.details instanceof DedicatedPacket;
        if (this.details instanceof DedicatedPacket dedicatedPacket) {
            dedicatedPacket.createMap();
        }

        this.mapForHighlighting = this.details instanceof DedicatedPacket d ? d.getMapForHighlighting() : Map.of();
    }

    @Override
    protected void init() {
        super.init();

        double scroll = 0.0D;
        if (this.packetFields != null) {
            scroll = this.packetFields.getScrollAmount();
        }

        this.packetFields = new Details();
        this.packetFields.setScrollAmount(scroll);
        this.addWidget(this.packetFields);

        if (this.isMP) {
            scroll = 0.0D;
            if (this.hexDump != null) {
                scroll = this.hexDump.getScrollAmount();
            }

            this.hexDump = new HexDump();
            this.hexDump.setScrollAmount(scroll);
            this.addWidget(this.hexDump);
        }

        this.addRenderableWidget(Button.builder(ADD_TO_FILTER, p_93751_ -> PacketCapture.getInstance().addFilter(new PacketFilter(this.details.getPacketClassName(), FilterType.EQUALS))).bounds(0, this.height - 20, this.width / 2, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, p_93751_ -> this.onClose()).bounds(this.width / 2, this.height - 20, this.width / 2, 20).build());

        this.hexDumpPrefixWidth = this.font.getSplitter().stringWidth(Component.literal("|00000000| ").withStyle(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.hexLineWidth = this.font.getSplitter().stringWidth(Component.literal("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 |").withStyle(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.oneByteWidth = this.font.getSplitter().stringWidth(Component.literal("00 ").withStyle(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.oneCharWidth = this.font.getSplitter().stringWidth(Component.literal("0").withStyle(style -> style.withFont(PacketCapture.MONO_FONT)));
    }

    @Override
    public void render(GuiGraphics p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        super.render(p_281549_, p_281550_, p_282878_, p_282465_);

        this.packetFields.render(p_281549_, p_281550_, p_282878_, p_282465_);
        p_281549_.drawCenteredString(this.font, this.title, this.width / 2, 5, 16777215);

        if (this.isMP) {
            this.renderHexDump(p_281549_, p_281550_, p_282878_, p_282465_);
        }
    }

    private void renderHexDump(GuiGraphics gui, int mouseX, int mouseY, float v) {
        this.hexDump.render(gui, mouseX, mouseY, v);
        gui.drawCenteredString(this.font, DATA, this.width / 2, this.hexDump.getY() - 14, 16777215);

        if (this.packetFields.isMouseOver(mouseX, mouseY)) {
            var e = this.packetFields.getChildAt(mouseX, mouseY);
            e.filter(guiEventListener -> guiEventListener instanceof HasClassField).ifPresent(guiEventListener -> {
                var hasClassField = (HasClassField) guiEventListener;
                var name = hasClassField.getField().getName();
                var log = this.mapForHighlighting.get(name);
                if (log == null) {
                    return;
                }

                this.highlightHexAndChars(gui, log.startIndex(), log.endIndex());
            });
        }

        var i = this.getByteIndexAt(mouseX, mouseY);
        if (i < 0) {
            return;
        }

        for (var es : this.mapForHighlighting.entrySet()) {
            var log = es.getValue();
            if (IntStream.rangeClosed(log.startIndex(), log.endIndex()).boxed().toList().contains(i)) {
                this.highlightHexAndChars(gui, log.startIndex(), log.endIndex());
                gui.renderTooltip(this.font, this.font.split(Component.literal(es.getKey()).withStyle(style -> style.withFont(PacketCapture.MONO_FONT)), Math.max(gui.guiWidth() / 2, 200)), mouseX, mouseY);
                break;
            }
        }
    }

    private void highlightHexAndChars(GuiGraphics gui, int startIndex, int endIndex) {
        this.fillHexRect(gui, startIndex, endIndex);
        this.fillCharRect(gui, startIndex, endIndex);
    }

    private void fillHexRect(GuiGraphics gui, int startIndex, int endIndex) {
        int startRow = startIndex >>> 4;
        int endRow = endIndex >>> 4;
        int rows = endRow - startRow;

        if (rows == 0) {
            var start = this.getByteTopLeftFrom(startIndex);
            if (start.y < this.hexDump.getY() || start.y + 10 > this.hexDump.getBottom()) {
                return;
            }

            var end = this.getByteTopLeftFrom(endIndex).add(Mth.floor(this.oneByteWidth - this.oneCharWidth), 0);
            gui.fill(start.x, start.y, end.x, end.y + 10, ARGB32.color(64, 255, 255, 0));
            return;
        }

        this.fillHexRect(gui, startIndex, (startRow << 4) + 15);
        for (int i = startRow + 1; i < rows; i++) {
            this.fillHexRect(gui, i << 4, (i << 4) + 15);
        }
        this.fillHexRect(gui, endRow << 4, endIndex);
    }

    private void fillCharRect(GuiGraphics gui, int startIndex, int endIndex) {
        int startRow = startIndex >>> 4;
        int endRow = endIndex >>> 4;
        int rows = endRow - startRow;

        if (rows == 0) {
            var start = this.getCharTopLeftFrom(startIndex);
            if (start.y < this.hexDump.getY() || start.y + 10 > this.hexDump.getBottom()) {
                return;
            }

            var end = this.getCharTopLeftFrom(endIndex).add(Mth.floor(this.oneCharWidth), 0);
            gui.fill(start.x, start.y, end.x, end.y + 10, ARGB32.color(64, 255, 255, 0));
            return;
        }

        this.fillCharRect(gui, startIndex, (startRow << 4) + 15);
        for (int i = startRow + 1; i < rows; i++) {
            this.fillCharRect(gui, i << 4, (i << 4) + 15);
        }
        this.fillCharRect(gui, endRow << 4, endIndex);
    }

    private Vector2i getByteTopLeftFrom(int index) {
        if (!this.isMP) {
            return new Vector2i();
        }

        int row = index >>> 4;
        int column = index & 15;

        int top = this.hexDump.getRowTop(row + 3);
        int rowLeft = Mth.floor(this.hexDump.getRowLeft() + this.hexDumpPrefixWidth);

        return new Vector2i(Mth.floor(rowLeft + this.oneByteWidth * column), top);
    }

    private Vector2i getCharTopLeftFrom(int index) {
        if (!this.isMP) {
            return new Vector2i();
        }

        int row = index >>> 4;
        int column = index & 15;

        int top = this.hexDump.getRowTop(row + 3);
        int rowLeft = Mth.floor(this.hexDump.getRowLeft() + this.hexDumpPrefixWidth + this.hexLineWidth);

        return new Vector2i(Mth.floor(rowLeft + this.oneCharWidth * column), top);
    }

    private int getByteIndexAt(int mouseX, int mouseY) {
        if (!this.isMP || !this.hexDump.isMouseOver(mouseX, mouseY)) {
            return -1;
        }

        var rowLeft = Mth.floor(this.hexDump.getRowLeft() + this.hexDumpPrefixWidth);
        if (rowLeft > mouseX) {
            return -1;
        }

        int i = mouseY - this.hexDump.getY() - this.hexDump.getHeaderHeight() + (int) this.hexDump.getScrollAmount() - 4;
        int rowIndex = i / this.hexDump.getItemHeight();
        if (rowIndex < 3 || rowIndex >= this.hexDump.getItemCount() - 1) {
            return -1;
        }

        rowIndex -= 3;
        int offset = rowIndex << 4;
        int byteIndex = Mth.floor((double) (mouseX - rowLeft) / (double) this.oneByteWidth);
        if (byteIndex > 15) {
            return -1;
        }

        return offset + byteIndex;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private final class Details extends ClassFieldList {
        public Details() {
            super(PacketDetailsScreen.this.minecraft, PacketDetailsScreen.this.width, !PacketDetailsScreen.this.isMP ? PacketDetailsScreen.this.height - 40 : (PacketDetailsScreen.this.height - 60) / 2, 20, 10, PacketDetailsScreen.this.details.getVisitor(), PacketDetailsScreen.this, PacketDetailsScreen.this);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width - 6;
        }
    }

    private final class HexDump extends ClassFieldList {
        public HexDump() {
            super(PacketDetailsScreen.this.minecraft, PacketDetailsScreen.this.width, (PacketDetailsScreen.this.height - 60) / 2, 40 + (PacketDetailsScreen.this.height - 60) / 2, 10, ClassVisitor.EMPTY, PacketDetailsScreen.this, PacketDetailsScreen.this);

            if (PacketDetailsScreen.this.details instanceof DedicatedPacket hexLines) {
                hexLines.getHexLines().forEach(s -> this.addEntry(new TextEntry(Component.literal(s).withStyle(style -> style.withFont(PacketCapture.MONO_FONT)).getVisualOrderText())));
            }
        }

        @Override
        public int getItemCount() {
            return super.getItemCount();
        }

        @Override
        public int getRowTop(int p_93512_) {
            return super.getRowTop(p_93512_);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width - 6;
        }
    }
}
