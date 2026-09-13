package dev.brex.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brex.core.BrexVersion;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrexCliTest {

    /** A command that echoes its arguments, used to test dispatch and parsing. */
    private static final class EchoCommand implements Command {
        @Override
        public String name() {
            return "test";
        }

        @Override
        public String summary() {
            return "Echo arguments";
        }

        @Override
        public String usage() {
            return "brex test [routine] [options]";
        }

        @Override
        public List<OptionSpec> options() {
            return List.of(OptionSpec.value("runs", "N", "Number of runs"), OptionSpec.flag("strict", "Be strict"));
        }

        @Override
        public int execute(CommandContext context, Arguments arguments) {
            if (arguments.positionals().contains("boom")) {
                throw new CliException("Baseline 'boom' not found. Run 'brex baseline list' to see baselines.");
            }
            if (arguments.positionals().contains("fail")) {
                return ExitCode.FAILED;
            }
            context.info("quiet=" + context.quiet());
            context.out().println("positionals=" + arguments.positionals() + " runs=" + arguments.intValue("runs", 0)
                    + " strict=" + arguments.flag("strict") + " format=" + context.format());
            return ExitCode.OK;
        }
    }

    private final CliHarness cli = new CliHarness(List.of(new EchoCommand()), Path.of("."));

    @Test
    void printsBannerByDefault() {
        CliHarness.Result result = cli.run("test");

        assertTrue(result.out().contains("██████╗"), result.out());
        assertTrue(result.out().contains("Autonomous Testing Framework v" + BrexVersion.get()));
    }

    @Test
    void bannerCanBeDisabled() {
        assertFalse(cli.run("--no-banner", "test").out().contains("██"));
        assertFalse(cli.run("test", "--no-banner").out().contains("██"));
        assertFalse(cli.run("test", "--quiet").out().contains("██"));
        assertFalse(cli.run("test", "--format", "json").out().contains("██"));
        assertFalse(cli.run("test", "--ci").out().contains("██"));
        assertFalse(new CliHarness(List.of(new EchoCommand()), Path.of(".")).env("BREX_NO_BANNER", "1")
                .run("test").out().contains("██"));
    }

    @Test
    void quietSuppressesInfoOutput() {
        CliHarness.Result result = cli.run("test", "-q");

        assertEquals("positionals=[] runs=0 strict=false format=TEXT\n", result.out());
    }

    @Test
    void printsVersion() {
        CliHarness.Result result = cli.run("--version");

        assertEquals("brex " + BrexVersion.get() + "\n", result.out());
        assertEquals(ExitCode.OK, result.exitCode());
    }

    @Test
    void helpListsCommandsAndGlobalOptions() {
        CliHarness.Result result = cli.run("--no-banner", "--help");

        assertTrue(result.out().contains("  test  Echo arguments"), result.out());
        assertTrue(result.out().contains("--no-banner"));
        assertEquals(ExitCode.OK, result.exitCode());
    }

    @Test
    void commandHelp() {
        CliHarness.Result viaFlag = cli.run("test", "--help");
        CliHarness.Result viaHelp = cli.run("help", "test");

        assertTrue(viaFlag.out().startsWith("Usage: brex test [routine] [options]"), viaFlag.out());
        assertTrue(viaFlag.out().contains("--runs <N>"));
        assertEquals(viaFlag.out(), viaHelp.out());
    }

    @Test
    void parsesCommandOptionsAnywhere() {
        CliHarness.Result result = cli.run("--no-banner", "test", "blue-left", "--runs=1000", "--strict", "-q");

        assertEquals("positionals=[blue-left] runs=1000 strict=true format=TEXT\n", result.out());
    }

    @Test
    void unknownCommandSuggestsClosest() {
        CliHarness.Result result = cli.run("tset");

        assertEquals(ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Unknown command 'tset'. Did you mean 'test'?"), result.err());
    }

    @Test
    void unknownOptionSuggestsClosest() {
        CliHarness.Result result = cli.run("test", "--rusn", "5");

        assertEquals(ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Unknown option '--rusn'. Did you mean --runs?"), result.err());
    }

    @Test
    void invalidNumberIsUsageError() {
        CliHarness.Result result = cli.run("test", "--runs", "many", "-q");

        assertEquals(ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Option --runs expects a whole number, got 'many'"), result.err());
    }

    @Test
    void userErrorsMapToErrorExitCode() {
        CliHarness.Result result = cli.run("test", "boom", "-q");

        assertEquals(ExitCode.ERROR, result.exitCode());
        assertTrue(result.err().startsWith("error: Baseline 'boom' not found."), result.err());
    }

    @Test
    void commandExitCodeIsPropagated() {
        assertEquals(ExitCode.FAILED, cli.run("test", "fail", "-q").exitCode());
    }

    @Test
    void negativeNumbersArePositionals() {
        Arguments arguments = Arguments.parse(List.of("-1.5", "--runs", "3"),
                List.of(OptionSpec.value("runs", "N", "")));

        assertEquals(List.of("-1.5"), arguments.positionals());
    }

    @Test
    void missingOptionValueIsReported() {
        CliException error = assertThrows(CliException.class,
                () -> Arguments.parse(List.of("--runs"), List.of(OptionSpec.value("runs", "N", ""))));

        assertEquals("Option --runs requires a value <N>", error.getMessage());
    }

    @Test
    void suggestionsIgnoreDistantTypos() {
        assertEquals("baseline", Suggestions.closest("baselin", List.of("baseline", "benchmark")).orElseThrow());
        assertTrue(Suggestions.closest("xyz", List.of("baseline", "benchmark")).isEmpty());
    }
}
