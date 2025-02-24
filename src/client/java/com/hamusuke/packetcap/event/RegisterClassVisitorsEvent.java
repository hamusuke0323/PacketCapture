package com.hamusuke.packetcap.event;

import com.hamusuke.packetcap.clazz.visitor.ClassVisitors.VisitorFactory;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitors.VisitorRegistry;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public interface RegisterClassVisitorsEvent {
    Event<RegisterClassVisitorsEvent> EVENT = EventFactory.createArrayBacked(RegisterClassVisitorsEvent.class, listeners -> ctx -> {
        for (var l : listeners) {
            l.call(ctx);
        }
    });

    void call(Context ctx);

    class Context {
        private final List<VisitorRegistry<?>> customVisitors = Lists.newArrayList();

        public void register(Predicate<Object> finder, VisitorFactory<Object> factory) {
            this.register(finder, o -> o, factory);
        }

        public <T> void register(Predicate<Object> finder, Function<Object, T> caster, VisitorFactory<T> factory) {
            this.customVisitors.add(new VisitorRegistry<>(finder, caster, factory));
        }

        public List<VisitorRegistry<?>> getCustomVisitors() {
            return List.copyOf(this.customVisitors);
        }
    }
}
