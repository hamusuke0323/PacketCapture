package com.hamusuke.packetcap.gui.components;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.clazz.field.ClassField;
import com.hamusuke.packetcap.clazz.field.MapField;
import com.hamusuke.packetcap.clazz.visitor.ArrayVisitor;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.gui.screen.PacketDetailsScreen;
import com.hamusuke.packetcap.gui.screen.VisitClassScreen;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.Objects;

public class ClassFieldList extends ObjectSelectionList<ClassFieldList.AbstractEntry> {
    public ClassFieldList(Minecraft minecraft, int width, int height, int top, int itemHeight, ClassVisitor visitor, PacketDetailsScreen packetDetailsScreen, Screen parent) {
        super(minecraft, width, height, top, itemHeight);

        var array = visitor instanceof ArrayVisitor;

        if (array) {
            this.addEntry(new TextEntry(Component.literal("[").withStyle(style -> style.withFont(PacketCapture.MONO_FONT)).getVisualOrderText()));
        }

        var fields = visitor.getFields().stream().filter(classField -> !(classField instanceof MapField)).toList();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            var last = i >= fields.size() - 1;
            this.addEntry(field, field.getVisitor(), field.getDescription() + (array && !last ? "," : ""), packetDetailsScreen, parent);
            this.addEntry(new TextEntry(Component.literal(" ").withStyle(style -> style.withFont(PacketCapture.MONO_FONT)).getVisualOrderText()));
        }

        if (array) {
            this.addEntry(new TextEntry(Component.literal("]").withStyle(style -> style.withFont(PacketCapture.MONO_FONT)).getVisualOrderText()));
        }
    }

    @Override
    protected void renderSelection(GuiGraphics p_283589_, int p_240142_, int p_240143_, int p_240144_, int p_240145_, int p_240146_) {
    }

    protected void addEntry(ClassField field, ClassVisitor visitor, String desc, PacketDetailsScreen packetDetailsScreen, Screen parent) {
        var simple = visitor == null || visitor.isStringConvertibleClass();
        minecraft.font.split(Component.literal(desc).withStyle(style -> style.withFont(PacketCapture.MONO_FONT)), this.width * 2 / 3).forEach(formattedCharSequence -> {
            this.addEntry(simple ? new MemberEntry(formattedCharSequence, field) : new VisitableClassEntry(formattedCharSequence, field, packetDetailsScreen, parent, visitor));
        });
    }

    @Override
    public boolean isMouseOver(double p_93479_, double p_93480_) {
        return p_93480_ >= (double) this.getY() && p_93480_ <= (double) this.getBottom();
    }

    public int getHeaderHeight() {
        return this.headerHeight;
    }

    public int getItemHeight() {
        return this.itemHeight;
    }

    @Override
    public boolean mouseClicked(double p_93420_, double p_93421_, int p_93422_) {
        this.updateScrollingState(p_93420_, p_93421_, p_93422_);

        if (!this.isMouseOver(p_93420_, p_93421_)) {
            return false;
        }

        for (var child : this.children()) {
            if (child.mouseClicked(p_93420_, p_93421_, p_93422_)) {
                var focused = this.getFocused();
                if (focused != child && focused instanceof ContainerEventHandler containereventhandler) {
                    containereventhandler.setFocused(null);
                }

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
    protected int getScrollbarPosition() {
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
            int $$6 = Mth.floor(p_93538_ - (double) this.list.getY()) - ((ClassFieldList) this.list).getHeaderHeight() + (int) this.list.getScrollAmount() - 4;
            int $$7 = $$6 / ((ClassFieldList) this.list).getItemHeight();
            var e = p_93537_ >= this.list.getRowLeft() && $$7 >= 0 && $$6 >= 0 && $$7 < ((ClassFieldList) this.list).getItemCount() ? this.list.children().get($$7) : null;
            return Objects.equals(e, this);
        }
    }

    protected class TextEntry extends AbstractEntry {
        private final FormattedCharSequence text;

        public TextEntry(FormattedCharSequence text) {
            this.text = text;
        }

        @Override
        public Component getNarration() {
            return GameNarrator.NO_TITLE;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int i, int top, int rowLeft, int width, int height, int mouseX, int mouseY, boolean isHovered, float v) {
            guiGraphics.drawString(ClassFieldList.this.minecraft.font, this.text, rowLeft, top, 16777215);
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

        public MemberEntry(FormattedCharSequence text, ClassField field) {
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

        public VisitableClassEntry(FormattedCharSequence msg, ClassField field, PacketDetailsScreen screen, Screen parent, ClassVisitor visitor) {
            this.field = field;
            this.button = new TextButton(ClassFieldList.this.minecraft.font, 0, 0, msg, p_93751_ -> {
                ClassFieldList.this.minecraft.setScreen(new VisitClassScreen(screen, parent, visitor));
            });
        }

        @Override
        public Component getNarration() {
            return GameNarrator.NO_TITLE;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int i, int top, int rowLeft, int i3, int i4, int mouseX, int mouseY, boolean b, float v) {
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
