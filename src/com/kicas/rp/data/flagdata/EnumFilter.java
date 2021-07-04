package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.Utils;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Provides common functionality for filters of enums.
 */
public abstract class EnumFilter<E extends Enum<E>> extends AbstractFilter<E> {
    protected final Class<E> enumClass;

    public EnumFilter(boolean isWhitelist, Set<E> filter, Class<E> enumClass) {
        super(isWhitelist, filter);
        this.enumClass = enumClass;
    }

    public EnumFilter(boolean isWhitelist, Class<E> enumClass) {
        this(isWhitelist, new HashSet<>(), enumClass);
    }

    public EnumFilter(Class<E> enumClass) {
        this(false, enumClass);
    }

    /**
     * Updates this filter based on the given whitelist flag and ordinal values.
     *
     * @param isWhitelist whether or not this filter is a whitelist.
     * @param ordinalFilter the ordinals of the enum elements to add to the filter.
     * @deprecated This method is inherently unsafe as ordinals may refer to different elements between sessions. The
     * name filter method should be used instead.
     */
    @Deprecated
    public void setFilter(boolean isWhitelist, Set<Integer> ordinalFilter) {
        this.isWhitelist = isWhitelist;
        filter.clear();
        E[] enumConstants = enumClass.getEnumConstants();
        ordinalFilter.stream().map(ordinal -> enumConstants[ordinal]).forEach(filter::add);
    }

    /**
     * Updates this filter based on the given whitelist flag and enum constant names.
     *
     * @param isWhitelist whether or not this filter is a whitelist.
     * @param nameFilter the enum constants to add to the filter.
     */
    public void setNameFilter(boolean isWhitelist, Set<String> nameFilter)  {
        this.isWhitelist = isWhitelist;
        filter.clear();

        final Method valueOf;
        try {
            valueOf = enumClass.getMethod("valueOf", String.class);
        } catch (NoSuchMethodException ex) {
            throw new InternalError(ex);
        }

        nameFilter.stream().map(name -> {
            try {
                return enumClass.cast(valueOf.invoke(null, name));
            } catch (ClassCastException | IllegalAccessException | InvocationTargetException ex) {
                return null;
            }
        }).filter(Objects::nonNull).forEach(filter::add);
    }

    /**
     * Converts the given string (a formatted enum constant name) into an enum constant.
     *
     * @param string the string form of an element.
     * @return the enum constant with the given name, or null if such a constant does not exist.
     */
    @Override
    protected E elementFromString(String string) {
        return Utils.valueOfFormattedName(string, enumClass);
    }

    /**
     * @param element the element.
     * @return the formatted name of the given enum constant.
     */
    @Override
    protected String elementToString(E element) {
        return Utils.formattedName(element);
    }

    /**
     * Implementation of a filter for the EntityType enum.
     */
    public static class EntityFilter extends EnumFilter<EntityType> {
        public static final EntityFilter EMPTY_FILTER = new EntityFilter();

        public EntityFilter() {
            super(EntityType.class);
        }
    }

    /**
     * Implementation of a filter for the Material enum.
     */
    public static class MaterialFilter extends EnumFilter<Material> {
        public static final MaterialFilter EMPTY_FILTER = new MaterialFilter();

        public MaterialFilter() {
            super(Material.class);
        }
    }
}
