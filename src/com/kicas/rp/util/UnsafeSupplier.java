package com.kicas.rp.util;

@FunctionalInterface
public interface UnsafeSupplier<T, E extends Throwable> {
    T get() throws E;
}