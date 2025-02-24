package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.clazz.field.MapField;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.clazz.visitor.MapVisitor;
import com.hamusuke.packetcap.gui.components.ClassFieldList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public class VisitClassScreen extends Screen {
    private static final Text BACK_TO_DETAILS = Text.translatable(PacketCapture.MOD_ID + ".back_to_details");
    private final PacketDetailsScreen packetDetailsScreen;
    @Nullable
    private final Screen parent;
    private final ClassVisitor visitor;
    private Fields list;

    public VisitClassScreen(PacketDetailsScreen screen, @Nullable Screen parent, ClassVisitor visitor) {
        super(Text.literal(visitor.getFullClassName()).styled(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.packetDetailsScreen = screen;
        this.parent = parent;
        this.visitor = visitor;
        this.visitor.visit();
    }

    @Override
    protected void init() {
        super.init();

        this.list = new Fields();
        this.addSelectableChild(this.list);

        this.addDrawableChild(ButtonWidget.builder(BACK_TO_DETAILS, p_93751_ -> this.client.setScreen(this.packetDetailsScreen)).dimensions(0, this.height - 20, this.width / 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, p_93751_ -> this.close()).dimensions(this.width / 2, this.height - 20, this.width / 2, 20).build());
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

    private final class Fields extends ClassFieldList {
        public Fields() {
            super(VisitClassScreen.this.client, VisitClassScreen.this.width, VisitClassScreen.this.height - 40, 20, 10, VisitClassScreen.this.visitor, VisitClassScreen.this.packetDetailsScreen, VisitClassScreen.this);

            var visitor = VisitClassScreen.this.visitor;
            var map = visitor instanceof MapVisitor;

            if (map) {
                this.addEntry(new TextEntry(Text.literal("[").styled(style -> style.withFont(PacketCapture.MONO_FONT)).asOrderedText()));
            }

            var mapFields = visitor.getFields().stream().filter(classField -> classField instanceof MapField).map(classField -> (MapField) classField).toList();
            for (int i = 0; i < mapFields.size(); i++) {
                var mapField = mapFields.get(i);
                var last = i >= mapFields.size() - 1;
                var key = mapField.getKeyVisitor();
                var value = mapField.getVisitor();

                this.addEntry(new TextEntry(Text.literal("{").styled(style -> style.withFont(PacketCapture.MONO_FONT)).asOrderedText()));
                this.addEntry(mapField, key, key.toString(), VisitClassScreen.this.packetDetailsScreen, VisitClassScreen.this);
                this.addEntry(new TextEntry(Text.literal("").asOrderedText()));
                this.addEntry(new TextEntry(Text.literal("->").styled(style -> style.withFont(PacketCapture.MONO_FONT)).asOrderedText()));
                this.addEntry(new TextEntry(Text.literal("").asOrderedText()));
                this.addEntry(mapField, value, value == null ? "null" : value.toString(), VisitClassScreen.this.packetDetailsScreen, VisitClassScreen.this);
                this.addEntry(new TextEntry(Text.literal("}" + (last ? "" : ",")).styled(style -> style.withFont(PacketCapture.MONO_FONT)).asOrderedText()));
                this.addEntry(new TextEntry(Text.literal("").asOrderedText()));
                this.addEntry(new TextEntry(Text.literal("").asOrderedText()));
            }

            if (map) {
                this.addEntry(new TextEntry(Text.literal("]").styled(style -> style.withFont(PacketCapture.MONO_FONT)).asOrderedText()));
            }
        }

        @Override
        protected int getScrollbarX() {
            return this.width - 6;
        }
    }
}
