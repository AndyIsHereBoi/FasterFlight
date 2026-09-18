package com.fasterflight.config;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * Creates a {@link ButtonWidget} without depending on any Minecraft member name.
 *
 * <p>1.19.3 made ButtonWidget's constructor protected and moved callers onto
 * {@code builder(Text, PressAction)}, which does not exist on 1.18.2/1.19.2. Neither route compiles
 * against the other version's target, so the constructor is invoked reflectively.
 *
 * <p>The constructor's parameter list is identical on every supported version, which makes it a
 * reliable anchor: the press-action interface and its method are both discovered from that signature
 * rather than named in source.
 *
 * <p>Names must not be used anywhere in this class. Minecraft member names are remapped in a normal
 * installed client -- {@code PressAction#onPress} is {@code method_25306}, {@code Builder#build} is
 * {@code method_46431} -- so a lookup by source name succeeds in development and fails for every
 * real user. That failure mode is silent when the call is guarded, which is how it hides.
 */
final class ButtonCompat {

    private static Constructor<?> buttonCtor;
    private static Class<?> pressActionType;

    /** Runtime name of the press-action method, resolved from the interface itself. */
    private static String pressMethodName;

    private static boolean resolved;

    private ButtonCompat() {
    }

    /**
     * Builds a button with the given label and press handler.
     *
     * @param x left edge
     * @param y top edge
     * @param width button width
     * @param height button height
     * @param label the button text
     * @param onPress the action to run when pressed
     * @return a configured button widget
     */
    static ButtonWidget create(int x, int y, int width, int height, Text label, Runnable onPress) {
        resolve();
        try {
            return (ButtonWidget) buttonCtor.newInstance(
                    x, y, width, height, label, pressAction(onPress));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to construct ButtonWidget", e);
        }
    }

    /**
     * Resolves the constructor and press-action details once.
     *
     * <p>The six-argument constructor is used because its shape is fixed: position, size, label,
     * press action, narration supplier. The press-action type is read straight out of that signature,
     * so nothing is looked up by name.
     */
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }

        buttonCtor = findConstructor();
        if (buttonCtor == null) {
            throw new IllegalStateException(
                    "No usable ButtonWidget constructor found on " + ButtonWidget.class.getName());
        }

        // Parameter 5 of the six-argument constructor is the press action.
        pressActionType = buttonCtor.getParameterTypes()[5];
        pressMethodName = findPressMethodName();
        resolved = true;
    }

    /**
     * Wraps a {@link Runnable} in the version's press-action interface.
     *
     * <p>The interface method is matched by its resolved runtime name. Comparing against the source
     * name {@code onPress} would never match in a production client, making the proxy a no-op and
     * leaving every button wired through it silently dead.
     *
     * @param onPress the action to run
     * @return a proxy implementing the press-action interface
     */
    private static Object pressAction(Runnable onPress) {
        return Proxy.newProxyInstance(
                ButtonCompat.class.getClassLoader(),
                new Class<?>[]{pressActionType},
                (proxy, method, args) -> {
                    if (method.getName().equals(pressMethodName)) {
                        onPress.run();
                        return null;
                    }
                    return switch (method.getName()) {
                        case "equals" -> args != null && args.length == 1 && proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "FasterFlightButtonAction";
                        default -> null;
                    };
                });
    }

    /**
     * Finds the press-action method's runtime name from the interface itself.
     *
     * <p>On 1.18.2 the real contract is {@code onPress(ButtonWidget)}, with a single argument of the
     * button instance. The interfaces in later versions are similar but may use a different method
     * name at runtime; matching the one abstract void method with a single ButtonWidget parameter is
     * therefore the robust rule and avoids hard-coded names.
     *
     * @return the runtime name of the press method
     */
    private static String findPressMethodName() {
        for (var method : pressActionType.getMethods()) {
            if (method.getParameterCount() == 1
                    && method.getReturnType() == void.class
                    && ButtonWidget.class.isAssignableFrom(method.getParameterTypes()[0])
                    && Modifier.isAbstract(method.getModifiers())) {
                return method.getName();
            }
        }
        // Some implementations add a default method; relax the filter rather than fail.
        for (var method : pressActionType.getMethods()) {
            if (method.getParameterCount() == 1
                    && method.getReturnType() == void.class
                    && ButtonWidget.class.isAssignableFrom(method.getParameterTypes()[0])) {
                return method.getName();
            }
        }
        throw new IllegalStateException("No press method found on " + pressActionType.getName());
    }

    /**
     * Finds the six-argument constructor, which shares its shape across all supported versions.
     *
     * <p>It is protected on 1.19.3, so {@code setAccessible} is required even though the class is
     * public.
     *
     * @return the constructor, or {@code null} if the expected signature is absent
     */
    private static Constructor<?> findConstructor() {
        for (Constructor<?> ctor : ButtonWidget.class.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 6 && params[0] == int.class && params[1] == int.class
                    && params[2] == int.class && params[3] == int.class
                    && params[4] == Text.class) {
                ctor.setAccessible(true);
                return ctor;
            }
        }
        return null;
    }
}
