package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import static com.hamusuke.packetcap.PacketCapture.MOD_ID;

public class ConfigScreen extends Screen {
    private static final Text TITLE = Text.translatable(MOD_ID + ".config.title");
    private static final Text SHOW_PACKET_FLOW = Text.translatable(MOD_ID + ".config.packetFlow");
    private static final Text SHOW_PACKET_NAME_POSTFIX = Text.translatable(MOD_ID + ".config.packetNamePostfix");
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addDrawableChild(CyclingButtonWidget
                .onOffBuilder(Config.showPacketFlow)
                .build(this.width / 4, this.height / 2 - 20, this.width / 2, 20, SHOW_PACKET_FLOW, (a, b) -> Config.showPacketFlow = b));

        this.addDrawableChild(CyclingButtonWidget
                .onOffBuilder(Config.showPacketNamePostfix)
                .build(this.width / 4, this.height / 2, this.width / 2, 20, SHOW_PACKET_NAME_POSTFIX, (a, b) -> Config.showPacketNamePostfix = b));

        this.addDrawableChild(ButtonWidget
                .builder(ScreenTexts.BACK, button -> this.close())
                .dimensions(this.width / 4, this.height - 20, this.width / 2, 20)
                .build());
    }

    @Override
    public void render(DrawContext p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        super.render(p_281549_, p_281550_, p_282878_, p_282465_);
        p_281549_.drawCenteredTextWithShadow(this.textRenderer, TITLE, this.width / 2, 20, 16777215);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public void removed() {
        super.removed();
        Config.save();
    }
}
