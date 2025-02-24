package com.hamusuke.packetcap.gui.components;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class TextButton extends ButtonWidget {
    private final TextRenderer font;
    private final OrderedText message;

    public TextButton(TextRenderer font, int x, int y, OrderedText message, PressAction onPress) {
        super(x, y, 0, 0, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.font = font;
        this.message = message;
        this.setWidth(this.font.getWidth(this.message));
        this.setHeight(this.font.fontHeight);
    }

    @Override
    protected void renderWidget(DrawContext p_281670_, int p_282682_, int p_281714_, float p_282542_) {
        var color = this.isSelected() ? 5592575 : 16777215;
        p_281670_.drawTextWithShadow(this.font, this.message, this.getX(), this.getY(), color | MathHelper.ceil(this.alpha * 255.0F) << 24);
    }
}
