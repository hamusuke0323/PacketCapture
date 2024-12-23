package com.hamusuke.packetcap.clazz.visitor;

import com.google.common.primitives.Primitives;
import com.hamusuke.packetcap.event.RegisterClassVisitorsEvent;
import com.hamusuke.packetcap.utils.ObjectUtil;
import com.mojang.datafixers.util.Pair;
import net.minecraftforge.fml.ModLoader;
import org.apache.commons.compress.utils.Lists;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class ClassVisitors {
    private static final List<VisitorRegistry> REGISTRY = Lists.newArrayList();

    static {
        registerClassVisitor(o -> o.getClass().isArray(), o -> new ArrayVisitor(o.getClass(), ObjectUtil.toArray(o)));
        registerClassVisitor(o -> o instanceof Collection<?>, o -> (Collection<?>) o, o -> new CollectionVisitor(o.getClass(), o));
        registerClassVisitor(o -> o instanceof Map<?, ?>, o -> (Map<?, ?>) o, o -> new MapVisitor(o.getClass(), o));
        registerClassVisitor(o -> o.getClass().isEnum(), o -> (Enum<?>) o, e -> new EnumVisitor(e.getClass(), e));
        registerClassVisitor(o -> o instanceof String || Primitives.isWrapperType(o.getClass()), o -> new StringConvertibleClassVisitor(o.getClass(), o));
        registerClassVisitor(o -> o instanceof Pair<?, ?>, o -> (Pair<?, ?>) o, p -> new PairVisitor(p.getClass(), p));

        var e = ModLoader.get().postEventWithReturn(new RegisterClassVisitorsEvent());
        REGISTRY.addAll(e.getCustomVisitors());
    }

    private static void registerClassVisitor(Predicate<Object> finder, VisitorFactory<Object> factory) {
        registerClassVisitor(finder, o -> o, factory);
    }

    private static <T> void registerClassVisitor(Predicate<Object> finder, Function<Object, ? extends T> caster, VisitorFactory<? extends T> factory) {
        REGISTRY.add(new VisitorRegistry<>(finder, caster, factory));
    }

    public static List<VisitorRegistry> getRegistry() {
        return List.copyOf(REGISTRY);
    }

    public record VisitorRegistry<T>(Predicate<Object> finder, Function<Object, ? extends T> caster,
                                     VisitorFactory<? extends T> factory) {
    }

    public interface VisitorFactory<T> {
        ClassVisitor create(T obj);
    }
}
