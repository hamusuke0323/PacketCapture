package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import static com.hamusuke.packetcap.PacketCapture.MOD_ID;

public class ConfigScreen extends Screen {
    private static final Component TITLE = Component.translatable(MOD_ID + ".config.title");
    private static final Component SHOW_PACKET_FLOW = Component.translatable(MOD_ID + ".config.packetFlow");
    private static final Component SHOW_PACKET_NAME_POSTFIX = Component.translatable(MOD_ID + ".config.packetNamePostfix");
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(CycleButton
                .onOffBuilder(Config.showPacketFlow)
                .create(this.width / 4, this.height / 2 - 20, this.width / 2, 20, SHOW_PACKET_FLOW, (a, b) -> Config.showPacketFlow = b));

        this.addRenderableWidget(CycleButton
                .onOffBuilder(Config.showPacketNamePostfix)
                .create(this.width / 4, this.height / 2, this.width / 2, 20, SHOW_PACKET_NAME_POSTFIX, (a, b) -> Config.showPacketNamePostfix = b));

        this.addRenderableWidget(Button
                .builder(CommonComponents.GUI_BACK, button -> this.onClose())
                .bounds(this.width / 4, this.height - 20, this.width / 2, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        super.render(p_281549_, p_281550_, p_282878_, p_282465_);
        p_281549_.drawCenteredString(this.font, TITLE, this.width / 2, 20, 16777215);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        super.removed();
        Config.save();
    }
}
