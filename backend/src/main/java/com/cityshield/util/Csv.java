package com.cityshield.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free CSV reader used by the seeder. */
public final class Csv {
    private Csv() {
    }

    /** Minimal RFC-4180 parser: quoted fields, embedded commas/newlines and doubled quotes. */
    public static List<List<String>> parse(BufferedReader r) throws IOException {
        List<List<String>> out = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        int ch;
        boolean any = false;
        while ((ch = r.read()) != -1) {
            char c = (char) ch;
            if (quoted) {
                if (c == '"') {
                    r.mark(1);
                    int next = r.read();
                    if (next == '"') {
                        cur.append('"');
                    } else {
                        quoted = false;
                        if (next != -1) {
                            r.reset();
                        }
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
                any = true;
            } else if (c == ',') {
                row.add(cur.toString());
                cur.setLength(0);
                any = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r') {
                    r.mark(1);
                    int next = r.read();
                    if (next != '\n' && next != -1) {
                        r.reset();
                    }
                }
                if (any || cur.length() > 0) {
                    row.add(cur.toString());
                    out.add(row);
                }
                row = new ArrayList<>();
                cur.setLength(0);
                any = false;
            } else {
                cur.append(c);
                any = true;
            }
        }
        if (any || cur.length() > 0) {
            row.add(cur.toString());
            out.add(row);
        }
        return out;
    }
}
