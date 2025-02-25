package com.hamusuke.packetcap;

import com.hamusuke.packetcap.clazz.visitor.ClassVisitors.VisitorFactory;
import com.hamusuke.packetcap.clazz.visitor.ClassVisitors.VisitorRegistry;
import org.apache.commons.compress.utils.Lists;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public interface PacketCaptureApi {
    default void onRegisterClassVisitors(CustomVisitorRegistry registry) {
    }

    default void onRegisterHighlightInstructions() {
    }

    class CustomVisitorRegistry {
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
