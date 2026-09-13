package dev.brex.git;

/**
 * A Git commit.
 *
 * @param hash full 40-character hash
 * @param subject first line of the commit message
 * @param author author name
 * @param timestampMillis commit time in milliseconds since the epoch
 */
public record Commit(String hash, String subject, String author, long timestampMillis) {

    /** The conventional 7-character abbreviation. */
    public String shortHash() {
        return hash.length() > 7 ? hash.substring(0, 7) : hash;
    }

    /**
     * Whether {@code recorded} (a full or abbreviated hash stored with a run) refers to this
     * commit. Abbreviations shorter than 7 characters never match, to avoid false positives.
     */
    public boolean matches(String recorded) {
        if (recorded == null || recorded.length() < 7) {
            return false;
        }
        String a = recorded.toLowerCase(java.util.Locale.ROOT);
        return hash.startsWith(a) || a.startsWith(hash);
    }
}
