package dev.brex.cli;

/** Process entry point for the {@code brex} command. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int code = BrexCli.standard().run(args);
        System.out.flush();
        System.err.flush();
        System.exit(code);
    }
}
