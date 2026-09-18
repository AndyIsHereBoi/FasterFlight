package com.fasterflight.config;

import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Builds {@link Text} components without compiling against a factory that only exists on some
 * Minecraft versions.
 *
 * <p>Minecraft 1.19 renamed the text factories. 1.18.x exposes the concrete
 * {@code LiteralText}/{@code TranslatableText} types, while 1.19 removed those classes and added
 * the static {@code Text.literal(String)} and {@code Text.translatable(String, Object...)}
 * helpers. Both directions are breaking: code compiled against {@code new LiteralText(...)} throws
 * {@link NoClassDefFoundError} on 1.19 because the class no longer exists.
 *
 * <p>Referencing either API directly from a compiled constant pool therefore hard-binds the jar to
 * one side of the rename. To keep a single compiled jar loadable on both, every call here goes
 * through reflection:
 * <ul>
 *   <li>{@code Text.literal(String)} / {@code Text.translatable(String, Object...)} are tried
 *       first, which is the 1.19+ path and the officially supported API.</li>
 *   <li>If those are absent, the 1.18.x constructors are instantiated reflectively.</li>
 * </ul>
 *
 * <p>Reflection is used only for these two factory calls, which happen a handful of times when a
 * screen is built or a HUD frame is drawn; the cost is negligible next to rendering. Resolved
 * handles are cached in static fields so the lookup happens once per JVM rather than per call.
 */
public final class TextCompat {

    // 1.19+ static factories; null on 1.18.x.
    private static Method literalFactory;
    private static Method translatableFactory;

    // 1.18.x constructors; the classes are gone in 1.19+.
    private static Constructor<? extends Text> literalCtor;
    private static Constructor<? extends Text> translatableCtor;

    private static boolean resolved;

    private TextCompat() {
    }

    /**
     * Creates a literal (non-translated) text component.
     *
     * @param value the raw string to display
     * @return a text component containing {@code value} verbatim
     */
    public static Text literal(String value) {
        resolve();
        if (literalFactory != null) {
            return (Text) invoke(literalFactory, null, value);
        }
        if (literalCtor != null) {
            return newInstance(literalCtor, value);
        }
        throw new IllegalStateException("No Text.literal(String) or LiteralText(String) available");
    }

    /**
     * Creates a text component that is resolved through the active language file.
     *
     * @param key the translation key to look up
     * @return a translatable text component for {@code key}
     */
    public static Text translatable(String key) {
        resolve();
        if (translatableFactory != null) {
            return (Text) invoke(translatableFactory, null, key, new Object[0]);
        }
        if (translatableCtor != null) {
            return newInstance(translatableCtor, key, new Object[0]);
        }
        throw new IllegalStateException(
                "No Text.translatable(String,Object...) or TranslatableText(String,Object...) available");
    }

    /**
     * Locates the available factories once per JVM.
     *
     * <p>Each lookup is individually guarded: on 1.18.2 the 1.19 methods are missing and on 1.19
     * the legacy classes are missing, so a failed lookup is the expected fallback signal rather
     * than an error worth surfacing.
     */
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;

        try {
            literalFactory = Text.class.getMethod("literal", String.class);
        } catch (NoSuchMethodException ignored) {
            // 1.18.x: Text.literal does not exist.
        }
        try {
            translatableFactory = Text.class.getMethod("translatable", String.class, Object[].class);
        } catch (NoSuchMethodException ignored) {
            // 1.18.x: Text.translatable does not exist.
        }

        literalCtor = findCtor("net.minecraft.text.LiteralText", String.class);
        translatableCtor = findCtor("net.minecraft.text.TranslatableText", String.class, Object[].class);

        if (literalFactory == null && literalCtor == null) {
            throw new IllegalStateException("Unsupported Minecraft version: no text factory found");
        }
    }

    /**
     * Looks up a text constructor by class name, tolerating the class being absent.
     *
     * @param className fully-qualified legacy class name
     * @param parameterTypes constructor signature to find
     * @return the constructor, or {@code null} if the class or signature is unavailable
     */
    private static Constructor<? extends Text> findCtor(
            String className, Class<?>... parameterTypes) {
        try {
            Class<?> type = Class.forName(className);
            return type.asSubclass(Text.class).getConstructor(parameterTypes);
        } catch (ClassNotFoundException | NoSuchMethodException | ClassCastException e) {
            return null;
        }
    }

    /**
     * Invokes a cached static factory, unwrapping reflective failure into a runtime error.
     *
     * @param method the static factory to call
     * @param target always {@code null}; the factory is static
     * @param args the factory arguments
     * @return the created text component
     */
    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke " + method, e);
        }
    }

    /**
     * Instantiates a cached legacy constructor, unwrapping reflective failure into a runtime error.
     *
     * @param ctor the constructor to call
     * @param args the constructor arguments
     * @return the created text component
     */
    private static Text newInstance(Constructor<? extends Text> ctor, Object... args) {
        try {
            return ctor.newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to construct " + ctor, e);
        }
    }
}
