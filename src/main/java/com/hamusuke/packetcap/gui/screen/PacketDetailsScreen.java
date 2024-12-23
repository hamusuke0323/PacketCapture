package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitor;
import com.hamusuke.packetcap.filter.FilterType;
import com.hamusuke.packetcap.filter.PacketFilter;
import com.hamusuke.packetcap.gui.components.ClassFieldList;
import com.hamusuke.packetcap.packet.DedicatedPacket;
import com.hamusuke.packetcap.packet.PacketDetails;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public class PacketDetailsScreen extends Screen {
    private static final Component ADD_TO_FILTER = Component.translatable(PacketCapture.MOD_ID + ".add_to_filter");
    private static final Component DATA = Component.translatable(PacketCapture.MOD_ID + ".packetData");
    @Nullable
    private final Screen parent;
    private final PacketDetails details;
    private Details packetFields;
    private HexDump hexDump;

    public PacketDetailsScreen(@Nullable Screen parent, PacketDetails details) {
        super(Component.literal(details.getPacketClassName() + (details instanceof DedicatedPacket d ? " (" + d.getFriendlySize() + ")" : "")).withStyle(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.parent = parent;
        this.details = details;
        this.details.getVisitor().visit();

        if (this.details instanceof DedicatedPacket d) {
            System.out.println("PacketId: " + d.getPacketId() + ", " + d.getWriteLog());
        }
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

        scroll = 0.0D;
        if (this.hexDump != null) {
            scroll = this.hexDump.getScrollAmount();
        }

        this.hexDump = new HexDump();
        this.hexDump.setScrollAmount(scroll);
        this.addWidget(this.hexDump);

        this.addRenderableWidget(Button.builder(ADD_TO_FILTER, p_93751_ -> PacketCapture.getInstance().addFilter(new PacketFilter(this.details.getPacketClassName(), FilterType.EQUALS))).bounds(0, this.height - 20, this.width / 2, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, p_93751_ -> this.onClose()).bounds(this.width / 2, this.height - 20, this.width / 2, 20).build());
    }

    @Override
    public void render(GuiGraphics p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        super.render(p_281549_, p_281550_, p_282878_, p_282465_);
        this.packetFields.render(p_281549_, p_281550_, p_282878_, p_282465_);
        this.hexDump.render(p_281549_, p_281550_, p_282878_, p_282465_);
        p_281549_.drawCenteredString(this.font, this.title, this.width / 2, 5, 16777215);
        p_281549_.drawCenteredString(this.font, DATA, this.width / 2, this.hexDump.getY() - 14, 16777215);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private final class Details extends ClassFieldList {
        public Details() {
            super(PacketDetailsScreen.this.minecraft, PacketDetailsScreen.this.width, (PacketDetailsScreen.this.height - 60) / 2, 20, 10, PacketDetailsScreen.this.details.getVisitor(), PacketDetailsScreen.this, PacketDetailsScreen.this);
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
        protected void renderItem(GuiGraphics p_282205_, int mouseX, int mouseY, float p_238968_, int p_238969_, int p_238970_, int p_238971_, int p_238972_, int p_238973_) {
            super.renderItem(p_282205_, mouseX, mouseY, p_238968_, p_238969_, p_238970_, p_238971_, p_238972_, p_238973_);
            var e = this.getEntry(p_238969_);
            if (e instanceof TextEntry t) {
                var bb = t.getTextBB();
                if (bb.contains(mouseX, mouseY)) {
                    p_282205_.fill(bb.x, bb.y, bb.x + bb.width, bb.y + bb.height, -1873784752);
                }
            }
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width - 6;
        }
    }
}
