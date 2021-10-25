package com.kicas.rp.data.flagdata;

/**
 * Defines an object which can augment or modify its internal data based on another object.
 *
 * @param <T> the type of object.
 */
public interface Augmentable<T> {
    /**
     * Takes the data from the given object and incorporates it into this object in some way.
     *
     * @param other the other object.
     */
    void augment(T other);

    /**
     * Takes the data from the given object and reduces the data in this object in some way.
     *
     * @param other the other object.
     */
    void reduce(T other);
}
