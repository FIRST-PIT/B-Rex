package dev.brex.core.run;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/**
 * Generates run IDs such as {@code 20260913-175012-blue-left-3fa9}.
 *
 * <p>IDs sort chronologically, are safe file names, and stay readable in a terminal. The random
 * suffix prevents collisions when two runs of the same routine start in the same second.
 */
public final class RunIds {

    private RunIds() {
    }

    public static String generate(String name, long epochMillis, Random random) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String suffix = String.format(Locale.ROOT, "%04x", random.nextInt(0x10000));
        return format.format(new Date(epochMillis)) + "-" + slug(name) + "-" + suffix;
    }

    /** Lowercases and replaces anything other than letters, digits, '.', '_' and '-' with '-'. */
    public static String slug(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            boolean safe = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            sb.append(safe ? c : '-');
        }
        return sb.length() == 0 ? "run" : sb.toString();
    }
}
