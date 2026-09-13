package dev.brex.cli;

import java.util.Collection;
import java.util.Optional;

/** "Did you mean ...?" suggestions based on edit distance. */
public final class Suggestions {

    private Suggestions() {
    }

    /** The candidate closest to {@code input}, if it is close enough to be a plausible typo. */
    public static Optional<String> closest(String input, Collection<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = distance(input, candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        int allowed = Math.max(1, input.length() / 3);
        return best != null && bestDistance <= allowed ? Optional.of(best) : Optional.empty();
    }

    /**
     * Optimal string alignment distance: insertions, deletions, substitutions and transpositions of
     * adjacent characters each cost 1, so the common typo {@code tset} is one edit from {@code test}.
     */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
