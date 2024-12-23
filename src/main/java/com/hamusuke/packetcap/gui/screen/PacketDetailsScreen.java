package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.filter.FilterType;
import com.hamusuke.packetcap.filter.PacketFilter;
import com.hamusuke.packetcap.gui.components.ClassFieldList;
import com.hamusuke.packetcap.packet.DedicatedPacket;
import com.hamusuke.packetcap.packet.PacketDetails;
import com.hamusuke.packetcap.utils.ByteConversion;
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
    @Nullable
    private final Screen parent;
    private final PacketDetails details;
    private Details list;

    public PacketDetailsScreen(@Nullable Screen parent, PacketDetails details) {
        super(Component.literal(details.getPacketClassName() + (details instanceof DedicatedPacket d ? " (" + d.getFriendlySize() + ")" : "")).withStyle(style -> style.withFont(PacketCapture.MONO_FONT)));
        this.parent = parent;
        this.details = details;
        this.details.getVisitor().visit();
    }

    @Override
    protected void init() {
        super.init();

        this.list = new Details();
        this.addWidget(this.list);

        this.addRenderableWidget(Button.builder(ADD_TO_FILTER, p_93751_ -> PacketCapture.getInstance().addFilter(new PacketFilter(this.details.getPacketClassName(), FilterType.EQUALS))).bounds(0, this.height - 20, this.width / 2, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, p_93751_ -> this.onClose()).bounds(this.width / 2, this.height - 20, this.width / 2, 20).build());
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

    private final class Details extends ClassFieldList {
        private static final Component DATA = Component.translatable(PacketCapture.MOD_ID + ".packetData");

        public Details() {
            super(PacketDetailsScreen.this.minecraft, PacketDetailsScreen.this.width, PacketDetailsScreen.this.height - 40, 20, 10, PacketDetailsScreen.this.details.getVisitor(), PacketDetailsScreen.this, PacketDetailsScreen.this);

            if (PacketDetailsScreen.this.details instanceof DedicatedPacket hexLines) {
                font.split(DATA, this.width * 2 / 3).forEach(formattedCharSequence -> this.addEntry(new CenteredDetailEntry(formattedCharSequence)));
                hexLines.getHexLines().forEach(s -> this.addEntry(new TextEntry(Component.literal(s).withStyle(style -> style.withFont(PacketCapture.MONO_FONT)).getVisualOrderText())));
            }
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width - 6;
        }

        private final class CenteredDetailEntry extends AbstractEntry {
            private final FormattedCharSequence text;

            private CenteredDetailEntry(FormattedCharSequence text) {
                this.text = text;
            }

            @Override
            public Component getNarration() {
                return GameNarrator.NO_TITLE;
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int bottom, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                guiGraphics.drawCenteredString(PacketDetailsScreen.this.font, this.text, PacketDetailsScreen.this.width / 2, top, 16777215);
            }

            @Override
            public boolean mouseClicked(double p_333902_, double p_331922_, int p_328634_) {
                return false;
            }
        }
    }
}
