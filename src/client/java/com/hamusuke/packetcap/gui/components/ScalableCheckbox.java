package com.hamusuke.packetcap.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class ScalableCheckbox extends PressableWidget {
    private static final Identifier CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE = Identifier.of("widget/checkbox_selected_highlighted");
    private static final Identifier CHECKBOX_SELECTED_SPRITE = Identifier.of("widget/checkbox_selected");
    private static final Identifier CHECKBOX_HIGHLIGHTED_SPRITE = Identifier.of("widget/checkbox_highlighted");
    private static final Identifier CHECKBOX_SPRITE = Identifier.of("widget/checkbox");
    protected static final int TEXT_COLOR = 14737632;
    protected final boolean showLabel;
    protected boolean selected;

    public ScalableCheckbox(int x, int y, int width, int height, Text component, boolean selected) {
        this(x, y, width, height, component, selected, true);
    }

    public ScalableCheckbox(int x, int y, int width, int height, Text component, boolean selected, boolean showLabel) {
        super(x, y, width, height, component);
        this.selected = selected;
        this.showLabel = showLabel;
    }

    @Override
    public void onPress() {
        this.selected = !this.selected;
    }

    public boolean selected() {
        return this.selected;
    }

    public void setSelected(boolean flag) {
        this.selected = flag;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, this.getNarrationMessage());
        if (this.active) {
            if (this.isFocused()) {
                builder.put(NarrationPart.USAGE, Text.translatable("narration.checkbox.usage.focused"));
            } else {
                builder.put(NarrationPart.USAGE, Text.translatable("narration.checkbox.usage.hovered"));
            }
        }
    }

    @Override
    protected void renderWidget(DrawContext p_281670_, int p_282682_, int p_281714_, float p_282542_) {
        RenderSystem.enableDepthTest();
        var font = MinecraftClient.getInstance().textRenderer;
        var scale = this.height / 20.0F;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
        RenderSystem.enableBlend();
        p_281670_.getMatrices().push();
        p_281670_.getMatrices().scale(scale, scale, 0.0F);
        p_281670_.getMatrices().translate(this.getX(), this.getY(), 0.0F);

        Identifier texture;
        if (this.selected) {
            texture = this.isFocused() ? CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE : CHECKBOX_SELECTED_SPRITE;
        } else {
            texture = this.isFocused() ? CHECKBOX_HIGHLIGHTED_SPRITE : CHECKBOX_SPRITE;
        }

        p_281670_.drawGuiTexture(RenderLayer::getGuiTextured, texture, this.getX(), this.getY(), 16, 16);
        p_281670_.getMatrices().pop();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.showLabel) {
            p_281670_.drawTextWithShadow(font, this.getMessage(), this.getX() + (int) (24 * scale), this.getY() + (this.height - 8) / 2, TEXT_COLOR | MathHelper.ceil(this.alpha * 255.0F) << 24);
        }
    }
}
