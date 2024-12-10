package com.hamusuke.packetcap.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class ScalableCheckbox extends AbstractButton {
    private static final ResourceLocation CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE = new ResourceLocation("widget/checkbox_selected_highlighted");
    private static final ResourceLocation CHECKBOX_SELECTED_SPRITE = new ResourceLocation("widget/checkbox_selected");
    private static final ResourceLocation CHECKBOX_HIGHLIGHTED_SPRITE = new ResourceLocation("widget/checkbox_highlighted");
    private static final ResourceLocation CHECKBOX_SPRITE = new ResourceLocation("widget/checkbox");
    protected static final int TEXT_COLOR = 14737632;
    protected final boolean showLabel;
    protected boolean selected;

    public ScalableCheckbox(int x, int y, int width, int height, Component component, boolean selected) {
        this(x, y, width, height, component, selected, true);
    }

    public ScalableCheckbox(int x, int y, int width, int height, Component component, boolean selected, boolean showLabel) {
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
    public void updateWidgetNarration(NarrationElementOutput p_259858_) {
        p_259858_.add(NarratedElementType.TITLE, this.createNarrationMessage());
        if (this.active) {
            if (this.isFocused()) {
                p_259858_.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.focused"));
            } else {
                p_259858_.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.hovered"));
            }
        }
    }

    @Override
    protected void renderWidget(GuiGraphics p_281670_, int p_282682_, int p_281714_, float p_282542_) {
        RenderSystem.enableDepthTest();
        var font = Minecraft.getInstance().font;
        var scale = this.height / 20.0F;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, this.alpha);
        RenderSystem.enableBlend();
        p_281670_.pose().pushPose();
        p_281670_.pose().scale(scale, scale, 0.0F);
        p_281670_.pose().translate(this.getX(), this.getY(), 0.0F);

        ResourceLocation texture;
        if (this.selected) {
            texture = this.isFocused() ? CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE : CHECKBOX_SELECTED_SPRITE;
        } else {
            texture = this.isFocused() ? CHECKBOX_HIGHLIGHTED_SPRITE : CHECKBOX_SPRITE;
        }

        p_281670_.blitSprite(texture, this.getX(), this.getY(), 16, 16);
        p_281670_.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.showLabel) {
            p_281670_.drawString(font, this.getMessage(), this.getX() + (int) (24 * scale), this.getY() + (this.height - 8) / 2, TEXT_COLOR | Mth.ceil(this.alpha * 255.0F) << 24);
        }
    }
}
