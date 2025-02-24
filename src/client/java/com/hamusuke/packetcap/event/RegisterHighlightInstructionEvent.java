package com.hamusuke.packetcap.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface RegisterHighlightInstructionEvent {
    Event<RegisterHighlightInstructionEvent> EVENT = EventFactory.createArrayBacked(RegisterHighlightInstructionEvent.class, listeners -> () -> {
        for (var listener : listeners) {
            listener.onRegister();
        }
    });

    void onRegister();
}
