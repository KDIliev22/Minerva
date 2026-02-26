package com.minerva;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public class WeldBootstrap {
    private static Weld weld;
    private static WeldContainer container;

    public static void start() {
        if (weld == null) {
            weld = new Weld();
            container = weld.initialize();
        }
    }

    public static void shutdown() {
        if (weld != null) {
            weld.shutdown();
            weld = null;
            container = null;
        }
    }

    /**
     * Retrieve a bean by its type and optional qualifiers.
     */
    public static <T> T getBean(Class<T> type, Annotation... qualifiers) {
        if (container == null) {
            throw new IllegalStateException("Weld container not started");
        }
        Instance<T> instance = container.select(type, qualifiers);
        if (instance.isUnsatisfied()) {
            throw new IllegalArgumentException("No bean found for type " + type);
        }
        return instance.get();
    }

    /**
     * Retrieve a bean by its generic type (Type) and optional qualifiers.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(Type type, Annotation... qualifiers) {
        if (container == null) {
            throw new IllegalStateException("Weld container not started");
        }
        Instance<T> instance = (Instance<T>) container.select(type, qualifiers);
        if (instance.isUnsatisfied()) {
            throw new IllegalArgumentException("No bean found for type " + type);
        }
        return instance.get();
    }

    public static BeanManager getBeanManager() {
        if (container == null) return null;
        return container.getBeanManager();
    }
}