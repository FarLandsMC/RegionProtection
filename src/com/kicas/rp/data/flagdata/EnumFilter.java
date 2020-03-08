package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.Utils;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.HashSet;
import java.util.Set;

/**
 * Can act as a blacklist or whitelist for any given enum.
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

    public void setFilter(boolean isWhitelist, Set<Integer> ordinalFilter) {
        this.isWhitelist = isWhitelist;
        filter.clear();
        E[] enumConstants = enumClass.getEnumConstants();
        ordinalFilter.stream().map(ordinal -> enumConstants[ordinal]).forEach(filter::add);
    }

    @Override
    protected E elementFromString(String s) {
        return Utils.valueOfFormattedName(s, enumClass);
    }

    @Override
    protected String elementToString(E e) {
        return Utils.formattedName(e);
    }

    public static class EntityFilter extends EnumFilter<EntityType> {
        public static final EntityFilter EMPTY_FILTER = new EntityFilter();

        public EntityFilter() {
            super(EntityType.class);
        }
    }

    public static class MaterialFilter extends EnumFilter<Material> {
        public static final MaterialFilter EMPTY_FILTER = new MaterialFilter();

        public MaterialFilter() {
            super(Material.class);
        }
    }
}
