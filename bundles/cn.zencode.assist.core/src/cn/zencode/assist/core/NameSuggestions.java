package cn.zencode.assist.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ranks names only AFTER the caller has checked scope, type and signature. */
public final class NameSuggestions {
    private NameSuggestions() { }

    public static List<String> suggest(String unresolved, Collection<String> eligibleNames) {
        if (unresolved == null || unresolved.isEmpty() || unresolved.length() > 128) {
            return Collections.emptyList();
        }
        // A valid existing name must never be diagnosed as a typo.
        if (eligibleNames.contains(unresolved)) return Collections.emptyList();
        final int threshold = unresolved.length() <= 4 ? 1 : 2;
        Set<String> unique = new HashSet<String>();
        for (String candidate : eligibleNames) {
            if (candidate != null && !candidate.isEmpty() && candidate.length() <= 128
                    && Math.abs(candidate.length() - unresolved.length()) <= threshold
                    && distance(unresolved, candidate) <= threshold) unique.add(candidate);
        }
        final String token = unresolved;
        List<String> result = new ArrayList<String>(unique);
        Collections.sort(result, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int rank = Integer.compare(distance(token, a), distance(token, b));
                return rank != 0 ? rank : a.compareTo(b);
            }
        });
        return Collections.unmodifiableList(new ArrayList<String>(result.subList(0, Math.min(5, result.size()))));
    }

    /** Optimal string alignment distance: a neighbouring transposition costs one edit. */
    public static int distance(String left, String right) {
        int[][] d = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) d[i][0] = i;
        for (int j = 0; j <= right.length(); j++) d[0][j] = j;
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(d[i - 1][j] + 1,
                        Math.min(d[i][j - 1] + 1, d[i - 1][j - 1] + cost));
                if (i > 1 && j > 1 && left.charAt(i - 1) == right.charAt(j - 2)
                        && left.charAt(i - 2) == right.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[left.length()][right.length()];
    }
}
