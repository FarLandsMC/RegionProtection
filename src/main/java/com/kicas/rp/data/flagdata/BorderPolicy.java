package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.Utils;

/**
 * Defines how a region border entrance/exit restriction should be enforced. A hard border type will simply cancel the
 * move event while a soft border type will apply impulse forces on the player to push them out of the region, however
 * teleport events will still receive a hard cancel. If the policy is none, then players can move freely throughout the
 * region.
 */
public class BorderPolicy extends FlagMeta {
    private Policy policy;

    public static final BorderPolicy NONE = new BorderPolicy(Policy.NONE);

    public BorderPolicy(Policy policy) {
        this.policy = policy;
    }

    public BorderPolicy() {
        this(Policy.NONE);
    }

    public Policy getPolicy() {
        return policy;
    }

    /**
     * Updates the type of this border (hard or soft) based off the given string, which should be one of the literals
     * "hard", "soft", or "none".
     *
     * @param metaString "hard", "soft", or "none" to denote this border's type.
     */
    public void readMetaString(String metaString) {
        Policy policy = Utils.valueOfFormattedName(metaString, Policy.class);
        if (policy == null)
            throw new IllegalArgumentException("Invalid border policy: \"" + metaString + "\"");
        this.policy = policy;
    }

    /**
     * @return the formatted name of this border's policy.
     */
    public String toMetaString() {
        return Utils.formattedName(policy);
    }

    public enum Policy {
        HARD, SOFT, NONE;

        public static final Policy[] VALUES = values();
    }
}
