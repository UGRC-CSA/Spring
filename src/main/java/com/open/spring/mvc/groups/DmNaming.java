package com.open.spring.mvc.groups;

import java.util.Locale;

public final class DmNaming {
    private DmNaming() {}

    public static String forPair(long first, long second) {
        if (first <= 0 || second <= 0 || first == second) {
            throw new IllegalArgumentException("Choose another user to message");
        }
        return "dm-" + Math.min(first, second) + "-" + Math.max(first, second);
    }

    public static boolean reserved(String name) {
        return name != null && name.strip().toLowerCase(Locale.ROOT).startsWith("dm-");
    }

    public static boolean isDirect(Groups group) {
        return group.getDmKey() != null || reserved(group.getName());
    }
}
