package com.hamusuke.packetcap.event;

import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

import java.util.function.BooleanSupplier;

public final class AddLayersEvent extends Event implements IModBusEvent {
    private final LayeredDraw customLayers = new LayeredDraw();

    public void add(Layer layer) {
        this.customLayers.add(layer);
    }

    public void add(LayeredDraw layeredDraw, BooleanSupplier booleanSupplier) {
        this.customLayers.add(layeredDraw, booleanSupplier);
    }

    public LayeredDraw getCustomLayers() {
        return this.customLayers;
    }
}
