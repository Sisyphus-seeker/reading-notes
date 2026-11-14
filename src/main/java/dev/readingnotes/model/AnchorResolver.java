package dev.readingnotes.model;

import java.util.List;

/** Reopens a saved anchor using nearby source text; ambiguous matches stay detached. */
public final class AnchorResolver {
    private AnchorResolver() {}

    public static int resolve(List<String> lines, int savedLine, String context, String before, String after) {
        if (lines.isEmpty()) return -1;
        int expected = savedLine - 1;
        if (expected >= 0 && expected < lines.size() && lines.get(expected).strip().equals(context)
            && (before.isEmpty() || expected > 0 && lines.get(expected - 1).strip().equals(before))
            && (after.isEmpty() || expected + 1 < lines.size() && lines.get(expected + 1).strip().equals(after))) return expected;
        int best = -1, score = -1;
        boolean ambiguous = false;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).strip().equals(context)) continue;
            int current = 0;
            if (!before.isEmpty() && i > 0 && lines.get(i - 1).strip().equals(before)) current++;
            if (!after.isEmpty() && i + 1 < lines.size() && lines.get(i + 1).strip().equals(after)) current++;
            if (current > score) { best = i; score = current; ambiguous = false; }
            else if (current == score) ambiguous = true;
        }
        return ambiguous ? -1 : best;
    }
}
