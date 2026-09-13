package dev.brex.core.format;

/**
 * Constants of the B-rex run file format.
 *
 * <p>A run file is a JSON document (optionally gzip-compressed) whose root object declares
 * {@code "format": "brex-run"} and an integer {@code "formatVersion"}. The full specification is in
 * {@code docs/run-format.md}.
 *
 * <h2>Compatibility policy</h2>
 *
 * <ul>
 *   <li>Adding optional fields does not change the version; readers ignore unknown fields.</li>
 *   <li>Renaming, removing or changing the meaning of a field increments the version. The reader
 *       then upgrades older documents before parsing, so runs recorded with any earlier release of
 *       B-rex stay readable.</li>
 *   <li>A reader refuses documents with a newer version than it knows, with a message telling the
 *       user to upgrade.</li>
 * </ul>
 */
public final class RunFormat {

    public static final String FORMAT = "brex-run";
    public static final int CURRENT_VERSION = 1;

    /** Recommended file name suffix. */
    public static final String EXTENSION = ".run.json";
    /** Recommended suffix for compressed runs, useful for long match recordings. */
    public static final String COMPRESSED_EXTENSION = ".run.json.gz";

    private RunFormat() {
    }
}
