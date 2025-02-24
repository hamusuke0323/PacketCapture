package com.hamusuke.packetcap.gui.screen;

import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.filter.FilterType;
import com.hamusuke.packetcap.filter.PacketFilter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class AddPacketFilterScreen extends Screen {
    public static final Text TITLE = Text.translatable(PacketCapture.MOD_ID + ".add_filter");
    private static final Text FILTERED_BY = Text.translatable(PacketCapture.MOD_ID + ".filtered_by");
    @Nullable
    private Screen parent;
    private TextFieldWidget filteredBy;
    private FilterType curType = FilterType.EQUALS;
    private final Supplier<Text> translatableFactory = () -> Text.translatable(PacketCapture.MOD_ID + ".type", this.curType.toString());

    public AddPacketFilterScreen() {
        super(TITLE);
    }

    public AddPacketFilterScreen setParent(@Nullable Screen parent) {
        this.parent = parent;
        return this;
    }

    @Override
    protected void init() {
        super.init();

        this.filteredBy = this.addDrawableChild(new TextFieldWidget(this.textRenderer, this.width / 4, this.height / 2 - 31, this.width / 2, 20, this.filteredBy, FILTERED_BY));
        this.filteredBy.setRenderTextProvider((s, integer) -> OrderedText.styledForwardsVisitedString(s, Style.EMPTY.withFont(PacketCapture.MONO_FONT)));
        this.addDrawableChild(ButtonWidget.builder(this.translatableFactory.get(), p_93751_ -> {
            this.curType = this.curType.next();
            p_93751_.setMessage(this.translatableFactory.get());
        }).dimensions(this.width / 4, this.height / 2 - 10, this.width / 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, p_93751_ -> {
            var v = this.filteredBy.getText();
            if (StringUtils.isEmpty(v) || StringUtils.isBlank(v)) {
                return;
            }

            p_93751_.active = false;
            PacketCapture.getInstance().addFilter(new PacketFilter(v, this.curType));
            this.close();
        }).dimensions(this.width / 4, this.height / 2 + 10, this.width / 2, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, p_93751_ -> this.close()).dimensions(this.width / 4, this.height - 20, this.width / 2, 20).build());
    }

    @Override
    public void render(DrawContext p_281549_, int p_281550_, int p_282878_, float p_282465_) {
        super.render(p_281549_, p_281550_, p_282878_, p_282465_);
        p_281549_.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2, 20, 16777215);
        p_281549_.drawCenteredTextWithShadow(this.textRenderer, FILTERED_BY, this.width / 2, this.filteredBy.getY() - 13, 16777215);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
