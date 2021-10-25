package com.kicas.rp.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides utilities to interact with Java classes and Java's reflection API. All methods return null upon failure
 * rather than throwing exceptions. This class does not respect access modifiers.
 */
public final class ReflectionHelper {
    // Wrapper to primitive
    private static final Map<Class<?>, Class<?>> W2P = new HashMap<>();
    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = new HashMap<>();

    static {
        W2P.put(Integer.class, int.class);
        W2P.put(Long.class, long.class);
        W2P.put(Short.class, short.class);
        W2P.put(Double.class, double.class);
        W2P.put(Float.class, float.class);
        W2P.put(Boolean.class, boolean.class);
        W2P.put(Byte.class, byte.class);
        W2P.put(Character.class, char.class);
        W2P.put(Integer[].class, int[].class);
        W2P.put(Long[].class, long[].class);
        W2P.put(Short[].class, short[].class);
        W2P.put(Double[].class, double[].class);
        W2P.put(Float[].class, float[].class);
        W2P.put(Boolean[].class, boolean[].class);
        W2P.put(Byte[].class, byte[].class);
        W2P.put(Character[].class, char[].class);

        PRIMITIVE_DEFAULTS.put(int.class, 0);
        PRIMITIVE_DEFAULTS.put(long.class, 0L);
        PRIMITIVE_DEFAULTS.put(short.class, (short) 0);
        PRIMITIVE_DEFAULTS.put(double.class, 0.0D);
        PRIMITIVE_DEFAULTS.put(float.class, 0.0F);
        PRIMITIVE_DEFAULTS.put(boolean.class, false);
        PRIMITIVE_DEFAULTS.put(byte.class, (byte) 0);
        PRIMITIVE_DEFAULTS.put(char.class, (char) 0);
    }

    private ReflectionHelper() {
    }

    private static Object defaultValue(Class<?> type) {
        type = asPrimitive(type);
        if (PRIMITIVE_DEFAULTS.containsKey(type))
            return PRIMITIVE_DEFAULTS.get(type);
        return null;
    }

    /**
     * Return whether or not an object of the given type can be null.
     *
     * @param type the type.
     * @return true if the given type can be null, false otherwise.
     */
    public static boolean isNullable(Class<?> type) {
        return !type.isPrimitive();
    }

    /**
     * Casts the given number to the new type.
     *
     * @param newType the new type.
     * @param target  the number to cast.
     * @return the casted number, or null if the new type is invalid.
     */
    public static Number numericCast(Class<?> newType, Number target) {
        if (target.getClass().equals(newType))
            return target;
        if (Integer.class.equals(newType) || int.class.equals(newType))
            return target.intValue();
        else if (Double.class.equals(newType) || double.class.equals(newType))
            return target.doubleValue();
        else if (Long.class.equals(newType) || long.class.equals(newType))
            return target.longValue();
        else if (Short.class.equals(newType) || short.class.equals(newType))
            return target.shortValue();
        else if (Float.class.equals(newType) || float.class.equals(newType))
            return target.floatValue();
        else if (Byte.class.equals(newType) || byte.class.equals(newType))
            return target.byteValue();
        else
            return null;
    }

    /**
     * Safely casts the given object to the given type, and returning null if the cast could not be completed.
     *
     * @param newType the new type.
     * @param target  the target object.
     * @param <T>     the new type.
     * @return the casted object, or null if the cast could not be completed.
     */
    @SuppressWarnings("unchecked")
    public static <T> T safeCast(Class<T> newType, Object target) {
        if (target instanceof Number) {
            Number number = numericCast(newType, (Number) target);
            if (number != null)
                return (T) number;
        }

        if (newType.isAssignableFrom(target.getClass()))
            return newType.cast(target);
        else
            return null;
    }

    public static boolean isInvalidCast(Class<?> from, Class<?> to) {
        return !to.isAssignableFrom(from) && (!Number.class.isAssignableFrom(to) ||
                !Number.class.isAssignableFrom(from));
    }

    public static <T extends Annotation> T getAnnotation(Class<T> annotationClass, Object target) {
        // Check both declared annotations and regular annotations
        T a = target.getClass().getAnnotation(annotationClass);
        return a == null ? target.getClass().getDeclaredAnnotation(annotationClass) : a;
    }

    public static Method getMethod(String methodName, Class<?> clazz, Class<?>... parameterTypes) {
        return getByParameterTypesAndName(methodName, Arrays.asList(clazz.getMethods()), parameterTypes);
    }

    public static <T extends Annotation> List<Pair<Method, T>> getMethodsAnnotatedWith(Class<T> annotationType,
                                                                                       Class<?> clazz) {
        return Arrays.stream(clazz.getMethods()).filter(m -> m.isAnnotationPresent(annotationType))
                .map(m -> new Pair<>(m, m.getAnnotation(annotationType))).collect(Collectors.toList());
    }

    public static <T extends Annotation> Pair<Method, T> getAnnotatedMethod(Class<T> annotationType, Class<?> clazz) {
        List<Pair<Method, T>> methods = getMethodsAnnotatedWith(annotationType, clazz);
        return methods.isEmpty() ? null : methods.get(0);
    }

    public static Class<?> asWrapper(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            for (Map.Entry<Class<?>, Class<?>> entry : W2P.entrySet()) {
                if (entry.getValue().equals(clazz))
                    return entry.getKey();
            }
        }
        return clazz;
    }

    public static Class<?> asPrimitive(Class<?> clazz) {
        if (clazz.isPrimitive() || String.class.equals(clazz))
            return clazz;
        Class<?> nativeClass = W2P.get(clazz);
        return nativeClass == null ? clazz : nativeClass;
    }

    public static <T> Constructor<T> getConstructor(Class<T> clazz, Class<?>... parameterTypes) {
        return getByParameterTypes(getConstructors(clazz), parameterTypes);
    }

    @SuppressWarnings("unchecked")
    public static <T> Constructor<T> getLowestParamCountConstructor(Class<T> clazz) {
        return Stream.of(clazz.getConstructors()).map(c -> (Constructor<T>) c)
                .min(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<Constructor<T>> getConstructors(Class<T> clazz) {
        return Stream.of(clazz.getConstructors()).map(c -> (Constructor<T>) c).collect(Collectors.toList());
    }

    public static <T> T instantiate(Class<T> clazz, Object... parameters) {
        Class<?>[] parameterTypes = new Class<?>[parameters.length];
        for (int i = 0; i < parameters.length; ++i)
            parameterTypes[i] = parameters[i].getClass();
        Constructor<T> c = getConstructor(clazz, parameterTypes);
        if (c == null)
            return null;
        c.setAccessible(true);
        try {
            return c.newInstance(matchParameterTypes(parameters, c.getParameterTypes()));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    public static <T> T instantiate(Class<T> clazz, Function<Class<?>, Object> paramSupplier) {
        Constructor<T> c = getLowestParamCountConstructor(clazz);
        if (c == null)
            return null;
        Object[] params = new Object[c.getParameterCount()];
        Class<?>[] paramTypes = c.getParameterTypes();
        for (int i = 0; i < params.length; ++i)
            params[i] = paramSupplier.apply(paramTypes[i]);
        c.setAccessible(true);
        try {
            return c.newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    public static <T> T instantiateWithDefaultParams(Class<T> clazz) {
        return instantiate(clazz, ReflectionHelper::defaultValue);
    }

    public static Object invoke(String methodName, Class<?> clazz, Object target, Object... parameters) {
        Class<?>[] parameterTypes = new Class<?>[parameters.length];
        for (int i = 0; i < parameters.length; ++i)
            parameterTypes[i] = parameters[i].getClass();
        Method m = getMethod(methodName, clazz, parameterTypes);
        if (m == null)
            return null;
        m.setAccessible(true);
        try {
            return m.invoke(target, matchParameterTypes(parameters, m.getParameterTypes()));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    public static Object invoke(Method method, Object target, Object... parameters) {
        method.setAccessible(true);
        try {
            return method.invoke(target, matchParameterTypes(parameters, method.getParameterTypes()));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }


    public static Field getFieldObject(String fieldName, Class<?> clazz) {
        try {
            return clazz.getField(fieldName);
        } catch (NoSuchFieldException ex) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex0) {
                return null;
            }
        }
    }

    public static Object getFieldValue(String fieldName, Class<?> clazz, Object target) {
        return getFieldValue(getFieldObject(fieldName, clazz), target);
    }

    public static Object getFieldValue(Field field, Object target) {
        field.setAccessible(true);
        try {
            return field.get(target);
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    public static boolean setNonFinalFieldValue(String fieldName, Class<?> clazz, Object target, Object value) {
        return setNonFinalFieldValue(getFieldObject(fieldName, clazz), target, value);
    }

    public static boolean setNonFinalFieldValue(Field field, Object target, Object value) {
        field.setAccessible(true);
        try {
            field.set(target, value);
        } catch (IllegalAccessException ex) {
            return false;
        }
        return true;
    }

    private static <T extends Executable> T getByParameterTypesAndName(String name, List<T> options,
                                                                       Class<?>[] parameterTypes) {
        outer:
        for (T option : options) {
            if (!Objects.equals(option.getName(), name))
                continue;
            Class<?>[] opts = option.getParameterTypes(); // OPTS = option parameter types
            if (opts.length == parameterTypes.length) {
                for (int i = 0; i < opts.length; ++i) {
                    if (isInvalidCast(parameterTypes[i], opts[i]))
                        continue outer;
                }
            } else
                continue;
            return option;
        }
        return null;
    }

    private static <T extends Executable> T getByParameterTypes(List<T> options, Class<?>[] parameterTypes) {
        outer:
        for (T option : options) {
            Class<?>[] opts = option.getParameterTypes(); // OPTS = option parameter types
            if (opts.length == parameterTypes.length) {
                for (int i = 0; i < opts.length; ++i) {
                    if (isInvalidCast(parameterTypes[i], opts[i]))
                        continue outer;
                }
            } else
                continue;
            return option;
        }
        return null;
    }

    private static Object[] matchParameterTypes(Object[] parameters, Class<?>[] parameterTypes) {
        Object[] matchedParams = new Object[parameters.length];
        for (int i = 0; i < parameters.length; ++i)
            matchedParams[i] = safeCast(parameterTypes[i], parameters[i]);
        return matchedParams;
    }
}
