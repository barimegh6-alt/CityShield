package com.cityshield.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny helpers for building JSON-shaped responses (ordered maps). */
public final class Json {
    private Json() {
    }

    /** Builds an ordered map from alternating key/value arguments. */
    public static Map<String, Object> obj(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("key/value pairs required");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Rounds to the given number of decimals. */
    public static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }
}
