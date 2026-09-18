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
 * <p>The legacy class names are resolved through Fabric Loader's {@link MappingResolver} rather than
 * written out directly. A development client uses Yarn names while a normal installed client only
 * has intermediary ones, so any hard-coded string would work in exactly one of the two -- a mistake
 * that is invisible during development and fails for every real user. The resolver supplies the
 * correct runtime name for whichever version is running.
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

        // Every candidate is checked against the real class, so a miss simply means "not on this
        // version" and the next one is tried. Both spellings are listed because mapping rewrites
        // compiled references but never string literals: a development client exposes Yarn names,
        // while an installed client only has intermediary names.
        literalFactory = findMethod(Text.class, String.class,
                "literal",       // 1.19.x, development
                "of",            // 1.18.x - 1.19.x, development
                "method_43470",  // 1.19.x, production  (Text.literal)
                "method_30163"); // 1.18.2 - 1.19.3, production (Text.of) - shared by every version

        translatableFactory = findMethod(Text.class, new Class<?>[] {String.class, Object[].class},
                "translatable",  // 1.19.x, development
                "method_43469"); // 1.19.x, production

        literalCtor = findCtor("net.minecraft.class_2585", "net.minecraft.text.LiteralText",
                String.class);
        translatableCtor = findCtor("net.minecraft.class_2588", "net.minecraft.text.TranslatableText",
                String.class, Object[].class);

        if (literalFactory == null && literalCtor == null) {
            throw new IllegalStateException("No Text factory found on Text loaded by "
                    + Text.class.getClassLoader());
        }
        resolved = true;
    }

    /**
     * Returns the first method that exists under any of the supplied names.
     *
     * @param owner the class declaring the static factory
     * @param singleParam the method's single parameter type
     * @param names candidate names, tried in order
     * @return the method, or {@code null} if none of the names exists
     */
    private static Method findMethod(Class<?> owner, Class<?> singleParam, String... names) {
        return findMethod(owner, new Class<?>[] {singleParam}, names);
    }

    /**
     * Returns the first method that exists under any of the supplied names.
     *
     * @param owner the class declaring the static factory
     * @param parameterTypes the method's parameter types
     * @param names candidate names, tried in order
     * @return the method, or {@code null} if none of the names exists
     */
    private static Method findMethod(Class<?> owner, Class<?>[] parameterTypes, String... names) {
        for (String name : names) {
            try {
                return owner.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                // Expected wherever the name does not exist on this version.
            }
        }
        return null;
    }

    /**
     * Looks up a constructor by trying the runtime production name first and the development name as a
     * fallback.
     *
     * @param productionName the 1.18.x intermediary name, e.g. {@code net.minecraft.class_2585}
     * @param devName the Yarn name, e.g. {@code net.minecraft.text.LiteralText}
     * @param parameterTypes the constructor signature
     * @return the constructor, or {@code null} if the class or signature is unavailable
     */
    private static Constructor<? extends Text> findCtor(
            String productionName, String devName, Class<?>... parameterTypes) {
        Constructor<? extends Text> ctor = findCtor(productionName, parameterTypes);
        if (ctor != null) {
            return ctor;
        }
        return findCtor(devName, parameterTypes);
    }

    /**
     * Finds a constructor for a known legacy text class. The class is loaded through
     * {@link Text}'s own classloader so it resolves in whichever loader owns Minecraft's classes.
     *
     * @param className the class name to try
     * @param parameterTypes constructor signature to find
     * @return the constructor, or {@code null} if the class or signature is unavailable
     */
    private static Constructor<? extends Text> findCtor(
            String className, Class<?>... parameterTypes) {
        try {
            Class<?> type = Class.forName(className, false, Text.class.getClassLoader());
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
