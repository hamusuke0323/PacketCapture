package com.hamusuke.packetcap.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class TextButton extends Button {
    private final Font font;
    private final FormattedCharSequence message;

    public TextButton(Font font, int x, int y, FormattedCharSequence message, OnPress onPress) {
        super(x, y, 0, 0, Component.empty(), onPress, DEFAULT_NARRATION);
        this.font = font;
        this.message = message;
        this.setWidth(this.font.width(this.message));
        this.setHeight(this.font.lineHeight);
    }

    @Override
    protected void renderWidget(GuiGraphics p_281670_, int p_282682_, int p_281714_, float p_282542_) {
        var color = this.isHoveredOrFocused() ? 5592575 : 16777215;
        p_281670_.drawString(this.font, this.message, this.getX(), this.getY(), color | Mth.ceil(this.alpha * 255.0F) << 24);
    }
}
