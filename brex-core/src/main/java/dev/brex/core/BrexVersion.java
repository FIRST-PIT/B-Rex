package dev.brex.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The version of the B-rex libraries on the classpath. */
public final class BrexVersion {

    private static final String VERSION = load();

    private BrexVersion() {
    }

    /** Returns the semantic version string, for example {@code 0.1.0}. */
    public static String get() {
        return VERSION;
    }

    private static String load() {
        try (InputStream in = BrexVersion.class.getResourceAsStream("brex-version.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }
}
