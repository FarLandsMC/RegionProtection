package com.kicas.rp.util;

/**
 * Represents a function which consumes a given value and may throw an exception.
 * @param <T> the value type.
 * @param <E> the exception type.
 */
@FunctionalInterface
public interface UnsafeConsumer<T, E extends Throwable> {
    /**
     * Accepts a given value.
     * @param t the value.
     * @throws E if an exception occurs.
     * @see java.util.function.Consumer#accept(Object)
     */
    void accept(T t) throws E;
}

