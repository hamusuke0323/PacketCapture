package com.hamusuke.packetcap.mixin;

import com.hamusuke.packetcap.event.AddLayersEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraftforge.fml.ModLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow
    @Final
    private LayeredDraw layers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void constructor(Minecraft p_330021_, CallbackInfo ci) {
        var e = ModLoader.get().postEventWithReturn(new AddLayersEvent());
        this.layers.add((guiGraphics, v) -> e.getCustomLayers().render(guiGraphics, v));
    }
}
