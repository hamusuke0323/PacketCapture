package com.hamusuke.packetcap.gui.components;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.clazz.field.ClassField;
import com.hamusuke.packetcap.clazz.field.MapField;
import com.hamusuke.packetcap.clazz.visitor.ArrayVisitor;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.gui.components.ClassFieldList.AbstractEntry;
import com.hamusuke.packetcap.gui.screen.PacketDetailsScreen;
import com.hamusuke.packetcap.gui.screen.VisitClassScreen;
import com.hamusuke.packetcap.invoker.ParentListAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Objects;

public class ClassFieldList extends AlwaysSelectedEntryListWidget<AbstractEntry> {
    public ClassFieldList(MinecraftClient minecraft, int width, int height, int top, int itemHeight, ClassVisitor visitor, PacketDetailsScreen packetDetailsScreen, Screen parent) {
        super(minecraft, width, height, top, itemHeight);

        var array = visitor instanceof ArrayVisitor;

        if (array) {
            this.addEntry(new TextEntry(Text.literal("[").styled(style -> style.withFont(PacketCapture.MONO_FONT)).asOrderedText()));
        }

        var fields = visitor.getFields().stream().filter(classField -> !(classField instanceof MapField)).toList();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            var last = i >= fields.size() - 1;
            this.addEntry(field, field.getVisitor(), field.getDescription() + (array && !last ? "," : ""), packetDetailsScreen, parent);
            this.addEntry(new TextEntry(Text.literal(" ").styled(style -> style.withFont(PacketCapture.MONO_FONT)).asOrderedText()));
        }

        if (array) {
            this.addEntry(new TextEntry(Text.literal("]").styled(style -> style.withFont(PacketCapture.MONO_FONT)).asOrderedText()));
        }
    }

    @Override
    protected void drawSelectionHighlight(DrawContext context, int y, int entryWidth, int entryHeight, int borderColor, int fillColor) {
    }

    protected void addEntry(ClassField field, ClassVisitor visitor, String desc, PacketDetailsScreen packetDetailsScreen, Screen parent) {
        var simple = visitor == null || visitor.isStringConvertibleClass();
        this.client.textRenderer.wrapLines(Text.literal(desc).styled(style -> style.withFont(PacketCapture.MONO_FONT)), this.width * 2 / 3).forEach(formattedCharSequence -> {
            this.addEntry(simple ? new MemberEntry(formattedCharSequence, field) : new VisitableClassEntry(formattedCharSequence, field, packetDetailsScreen, parent, visitor));
        });
    }

    @Override
    public boolean isMouseOver(double p_93479_, double p_93480_) {
        return p_93480_ >= (double) this.getY() && p_93480_ <= (double) this.getBottom();
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

    public int getHeaderHeight() {
        return this.headerHeight;
    }

    public int getItemHeight() {
        return this.itemHeight;
    }

    @Override
    protected int getScrollbarX() {
        return this.width - 6;
    }

    @Override
    public int getRowLeft() {
        return this.width / 9 - 10;
    }

    @Override
    public int getRowWidth() {
        return this.width * 2 / 3;
    }

    public abstract static class AbstractEntry extends Entry<AbstractEntry> {
        @Override
        public boolean isMouseOver(double p_93537_, double p_93538_) {
            var list = ParentListAccessor.parentList(this);
            int $$6 = MathHelper.floor(p_93538_ - (double) list.getY()) - ((ClassFieldList) list).getHeaderHeight() + (int) list.getScrollY() - 4;
            int $$7 = $$6 / ((ClassFieldList) list).getItemHeight();
            var e = p_93537_ >= list.getRowLeft() && $$7 >= 0 && $$6 >= 0 && $$7 < ((ClassFieldList) list).getEntryCount() ? list.children().get($$7) : null;
            return Objects.equals(e, this);
        }
    }

    protected class TextEntry extends AbstractEntry {
        private final OrderedText text;

        public TextEntry(OrderedText text) {
            this.text = text;
        }

        @Override
        public Text getNarration() {
            return ScreenTexts.EMPTY;
        }

        @Override
        public void render(DrawContext guiGraphics, int i, int top, int rowLeft, int width, int height, int mouseX, int mouseY, boolean isHovered, float v) {
            guiGraphics.drawTextWithShadow(ClassFieldList.this.client.textRenderer, this.text, rowLeft, top, 16777215);
        }

        @Override
        public boolean mouseClicked(double p_333902_, double p_331922_, int p_328634_) {
            return false;
        }
    }

    public interface HasClassField {
        ClassField getField();
    }

    public class MemberEntry extends TextEntry implements HasClassField {
        protected final ClassField field;

        public MemberEntry(OrderedText text, ClassField field) {
            super(text);
            this.field = field;
        }

        @Override
        public ClassField getField() {
            return this.field;
        }
    }

    public class VisitableClassEntry extends AbstractEntry implements HasClassField {
        protected final ClassField field;
        private final TextButton button;

        public VisitableClassEntry(OrderedText msg, ClassField field, PacketDetailsScreen screen, Screen parent, ClassVisitor visitor) {
            this.field = field;
            this.button = new TextButton(ClassFieldList.this.client.textRenderer, 0, 0, msg, p_93751_ -> {
                ClassFieldList.this.client.setScreen(new VisitClassScreen(screen, parent, visitor));
            });
        }

        @Override
        public Text getNarration() {
            return ScreenTexts.EMPTY;
        }

        @Override
        public void render(DrawContext guiGraphics, int i, int top, int rowLeft, int i3, int i4, int mouseX, int mouseY, boolean b, float v) {
            this.button.setX(rowLeft);
            this.button.setY(top);
            this.button.render(guiGraphics, mouseX, mouseY, v);
        }

        @Override
        public boolean mouseClicked(double p_94737_, double p_94738_, int p_94739_) {
            return this.button.mouseClicked(p_94737_, p_94738_, p_94739_);
        }

        @Override
        public ClassField getField() {
            return this.field;
        }
    }
}
