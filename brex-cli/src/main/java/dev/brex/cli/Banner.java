package dev.brex.cli;

import java.io.PrintStream;

/** The B-rex startup banner. */
public final class Banner {

    private static final String[] DINO = {
        "                               boing         boing         boing              \n" +
                "             e-e           . - .         . - .         . - .          \n" +
                "           (\\_/)\\       '       `.   ,'       `.   ,'       .        \n" +
                "            `-'\\ `--.___,         . .           . .          .       \n" +
                "               '\\( ,_.-'                                             \n" +
                "                  \\\\               \"             \"            a:f    \n" +
                "                  ^'                                                 \n"
    };

    private static final String[] WORDMARK = {
        "",
        "██████╗        ██████╗ ███████╗██╗  ██╗",
        "██╔══██╗       ██╔══██╗██╔════╝╚██╗██╔╝",
        "██████╔╝ █████╗██████╔╝█████╗   ╚███╔╝",
        "██╔══██╗ ╚════╝██╔══██╗██╔══╝   ██╔██╗",
        "██████╔╝       ██║  ██║███████╗██╔╝ ██╗",
        "╚═════╝        ╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝",
        "",
        "",
    };

    private static final int DINO_WIDTH = 21; // DINO :o

    private Banner() {
    }

    /** Prints the art, then the wordmark, then the tagline. */
    public static void print(PrintStream out, Style style, String version) {
        out.println();
        for (String art : DINO) {
            for (String line : art.split("\n")) {
                out.println(style.green(line.stripTrailing()));
            }
        }
        for (String line : WORDMARK) {
            if (!line.isEmpty()) {
                out.println(style.bold(pad(line, DINO_WIDTH)));
            }
        }
        out.println("Autonomous Testing Framework " + style.dim("v" + version));
        out.println();
    }

    private static String pad(String text, int width) {
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
