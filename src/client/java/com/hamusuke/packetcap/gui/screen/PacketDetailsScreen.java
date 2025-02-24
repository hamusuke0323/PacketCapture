package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.PacketDetails;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.filter.FilterType;
import com.hamusuke.packetcap.filter.PacketFilter;
import com.hamusuke.packetcap.gui.components.ClassFieldList;
import com.hamusuke.packetcap.gui.components.ClassFieldList.HasClassField;
import com.hamusuke.packetcap.highlight.Highlight;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;

import java.util.Map;
import java.util.stream.IntStream;

public class PacketDetailsScreen extends Screen {
    private static final Text ADD_TO_FILTER = Text.translatable(PacketCapture.MOD_ID + ".add_to_filter");
    private static final Text DATA = Text.translatable(PacketCapture.MOD_ID + ".packetData");
    @Nullable
    private final Screen parent;
    private final PacketDetails details;
    private final Map<String, Highlight<?>> mapForHighlighting;
    private Details packetFields;
    private HexDump hexDump;
    private float hexDumpPrefixWidth;
    private float hexLineWidth;
    private float oneByteWidth;
    private float oneCharWidth;

    public PacketDetailsScreen(@Nullable Screen parent, PacketDetails details) {
        super(Text.literal(details.getPacketClassName() + " (" + details.getFriendlySize() + ")").styled(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.parent = parent;
        this.details = details;
        this.details.getVisitor().visit();
        details.createMap();
        this.mapForHighlighting = this.details.getMapForHighlighting();
    }

    @Override
    protected void init() {
        super.init();

        double scroll = 0.0D;
        if (this.packetFields != null) {
            scroll = this.packetFields.getScrollY();
        }

        this.packetFields = new Details();
        this.packetFields.setScrollY(scroll);
        this.addSelectableChild(this.packetFields);

        scroll = 0.0D;
        if (this.hexDump != null) {
            scroll = this.hexDump.getScrollY();
        }

        this.hexDump = new HexDump();
        this.hexDump.setScrollY(scroll);
        this.addSelectableChild(this.hexDump);

        this.addDrawableChild(ButtonWidget.builder(ADD_TO_FILTER, p_93751_ -> PacketCapture.getInstance().addFilter(new PacketFilter(this.details.getPacketClassName(), FilterType.EQUALS))).dimensions(0, this.height - 20, this.width / 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, p_93751_ -> this.close()).dimensions(this.width / 2, this.height - 20, this.width / 2, 20).build());

        this.hexDumpPrefixWidth = this.textRenderer.getTextHandler().getWidth(Text.literal("|00000000| ").styled(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.hexLineWidth = this.textRenderer.getTextHandler().getWidth(Text.literal("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 |").styled(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.oneByteWidth = this.textRenderer.getTextHandler().getWidth(Text.literal("00 ").styled(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.oneCharWidth = this.textRenderer.getTextHandler().getWidth(Text.literal("0").styled(style -> style.withFont(PacketCapture.MONO_FONT)));
    }

    @Override
    public void render(DrawContext p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        super.render(p_281549_, p_281550_, p_282878_, p_282465_);

        this.packetFields.render(p_281549_, p_281550_, p_282878_, p_282465_);
        p_281549_.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 16777215);

        this.renderHexDump(p_281549_, p_281550_, p_282878_, p_282465_);
    }

    private void renderHexDump(DrawContext gui, int mouseX, int mouseY, float v) {
        this.hexDump.render(gui, mouseX, mouseY, v);
        gui.drawCenteredTextWithShadow(this.textRenderer, DATA, this.width / 2, this.hexDump.getY() - 14, 16777215);

        if (this.packetFields.isMouseOver(mouseX, mouseY)) {
            var e = this.packetFields.hoveredElement(mouseX, mouseY);
            e.filter(guiEventListener -> guiEventListener instanceof HasClassField).ifPresent(guiEventListener -> {
                var hasClassField = (HasClassField) guiEventListener;
                var name = hasClassField.getField().getName();
                var h = this.mapForHighlighting.get(name);
                if (h == null) {
                    return;
                }

                this.highlightHexAndChars(gui, h.range().startInclusive(), h.range().endInclusive());
            });
        }

        var i = this.getByteIndexAt(mouseX, mouseY);
        if (i < 0) {
            return;
        }

        for (var es : this.mapForHighlighting.entrySet()) {
            var h = es.getValue();
            if (IntStream.rangeClosed(h.range().startInclusive(), h.range().endInclusive()).boxed().toList().contains(i)) {
                this.highlightHexAndChars(gui, h.range().startInclusive(), h.range().endInclusive());
                var additional = getToolTipTextRecursively(h, i);
                if (additional.endsWith("\n")) {
                    additional = additional.substring(0, additional.length() - 1);
                }

                gui.drawTooltip(this.textRenderer, this.textRenderer.wrapLines(Text.literal(es.getKey() + (additional.isEmpty() ? "" : "\n" + additional)).styled(style -> style.withFont(PacketCapture.MONO_FONT)), Math.max(gui.getScaledWindowWidth() / 2, 200)), (screenWidth, screenHeight, x, y, width1, height1) -> new Vector2i(mouseX + 10, mouseY + 10), mouseX, mouseY);
                break;
            }
        }
    }

    private static String getToolTipTextRecursively(Highlight<?> highlight, int curBytePos) {
        var builder = new StringBuilder();
        if (highlight.range().contains(curBytePos) && !highlight.description().isEmpty()) {
            builder.append("  ").append(highlight.description()).append('\n');
        }

        for (var sub : highlight.sub()) {
            var str = getToolTipTextRecursively(sub, curBytePos);
            if (str.isEmpty()) {
                continue;
            }

            builder.append(str);
        }

        return builder.toString();
    }

    private void highlightHexAndChars(DrawContext gui, int startIndex, int endIndex) {
        this.fillHexRect(gui, startIndex, endIndex);
        this.fillCharRect(gui, startIndex, endIndex);
    }

    private void fillHexRect(DrawContext gui, int startIndex, int endIndex) {
        int startRow = startIndex >>> 4;
        int endRow = endIndex >>> 4;
        int rows = endRow - startRow;

        if (rows == 0) {
            var start = this.getByteTopLeftFrom(startIndex);
            if (start.y < this.hexDump.getY() || start.y + 10 > this.hexDump.getBottom()) {
                return;
            }

            var end = this.getByteTopLeftFrom(endIndex).add(MathHelper.floor(this.oneByteWidth - this.oneCharWidth), 0);
            gui.fill(start.x, start.y, end.x, end.y + 10, ColorHelper.getArgb(64, 255, 255, 0));
            return;
        }

        this.fillHexRect(gui, startIndex, (startRow << 4) + 15);
        for (int i = startRow + 1; i < rows; i++) {
            this.fillHexRect(gui, i << 4, (i << 4) + 15);
        }
        this.fillHexRect(gui, endRow << 4, endIndex);
    }

    private void fillCharRect(DrawContext gui, int startIndex, int endIndex) {
        int startRow = startIndex >>> 4;
        int endRow = endIndex >>> 4;
        int rows = endRow - startRow;

        if (rows == 0) {
            var start = this.getCharTopLeftFrom(startIndex);
            if (start.y < this.hexDump.getY() || start.y + 10 > this.hexDump.getBottom()) {
                return;
            }

            var end = this.getCharTopLeftFrom(endIndex).add(MathHelper.floor(this.oneCharWidth), 0);
            gui.fill(start.x, start.y, end.x, end.y + 10, ColorHelper.getArgb(64, 255, 255, 0));
            return;
        }

        this.fillCharRect(gui, startIndex, (startRow << 4) + 15);
        for (int i = startRow + 1; i < rows; i++) {
            this.fillCharRect(gui, i << 4, (i << 4) + 15);
        }
        this.fillCharRect(gui, endRow << 4, endIndex);
    }

    private Vector2i getByteTopLeftFrom(int index) {
        int row = index >>> 4;
        int column = index & 15;

        int top = this.hexDump.getRowTop(row + 3);
        int rowLeft = MathHelper.floor(this.hexDump.getRowLeft() + this.hexDumpPrefixWidth);

        return new Vector2i(MathHelper.floor(rowLeft + this.oneByteWidth * column), top);
    }

    private Vector2i getCharTopLeftFrom(int index) {
        int row = index >>> 4;
        int column = index & 15;

        int top = this.hexDump.getRowTop(row + 3);
        int rowLeft = MathHelper.floor(this.hexDump.getRowLeft() + this.hexDumpPrefixWidth + this.hexLineWidth);

        return new Vector2i(MathHelper.floor(rowLeft + this.oneCharWidth * column), top);
    }

    private int getByteIndexAt(int mouseX, int mouseY) {
        if (!this.hexDump.isMouseOver(mouseX, mouseY)) {
            return -1;
        }

        var rowLeft = MathHelper.floor(this.hexDump.getRowLeft() + this.hexDumpPrefixWidth);
        if (rowLeft > mouseX) {
            return -1;
        }

        int i = mouseY - this.hexDump.getY() - this.hexDump.getHeaderHeight() + (int) this.hexDump.getScrollY() - 4;
        int rowIndex = i / this.hexDump.getItemHeight();
        if (rowIndex < 3 || rowIndex >= this.hexDump.getEntryCount() - 1) {
            return -1;
        }

        rowIndex -= 3;
        int offset = rowIndex << 4;
        int byteIndex = MathHelper.floor((double) (mouseX - rowLeft) / (double) this.oneByteWidth);
        if (byteIndex > 15) {
            return -1;
        }

        return offset + byteIndex;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    private final class Details extends ClassFieldList {
        public Details() {
            super(PacketDetailsScreen.this.client, PacketDetailsScreen.this.width, (PacketDetailsScreen.this.height - 60) / 2, 20, 10, PacketDetailsScreen.this.details.getVisitor(), PacketDetailsScreen.this, PacketDetailsScreen.this);
        }

        @Override
        protected int getScrollbarX() {
            return this.width - 6;
        }
    }

    private final class HexDump extends ClassFieldList {
        public HexDump() {
            super(PacketDetailsScreen.this.client, PacketDetailsScreen.this.width, (PacketDetailsScreen.this.height - 60) / 2, 40 + (PacketDetailsScreen.this.height - 60) / 2, 10, ClassVisitor.EMPTY, PacketDetailsScreen.this, PacketDetailsScreen.this);

            PacketDetailsScreen.this.details.getHexLines()
                    .forEach(s -> this.addEntry(new TextEntry(Text.literal(s)
                            .styled(style -> style.withFont(PacketCapture.MONO_FONT))
                            .asOrderedText())));
        }

        @Override
        public int getEntryCount() {
            return super.getEntryCount();
        }

        @Override
        public int getRowTop(int p_93512_) {
            return super.getRowTop(p_93512_);
        }

        @Override
        protected int getScrollbarX() {
            return this.width - 6;
        }
    }
}
