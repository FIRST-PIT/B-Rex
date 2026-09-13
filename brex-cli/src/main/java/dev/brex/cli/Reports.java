package dev.brex.cli;

import dev.brex.analysis.regression.RegressionCheck;
import dev.brex.analysis.score.AutonScore;
import dev.brex.analysis.score.MetricScore;
import dev.brex.core.geometry.DistanceUnit;
import java.io.PrintStream;
import java.util.stream.Collectors;

/** Human-readable blocks shared by several commands. */
public final class Reports {

    private Reports() {
    }

    /** One line per regression check: mark, title, explanation, and the limit for failures. */
    public static void printCheck(PrintStream out, Style style, RegressionCheck check, DistanceUnit unit) {
        String mark = switch (check.status()) {
            case PASS -> style.green("✓");
            case FAIL -> style.red("✗");
            case SKIP -> "-";
        };
        String line = "  " + mark + " " + Fmt.pad(check.title(), 18) + " " + check.message();
        if (check.failed() && !Double.isNaN(check.threshold())) {
            line += style.dim("  (limit " + Fmt.quantity(check.threshold(), check.unit(), unit) + ")");
        }
        out.println(check.status() == RegressionCheck.Status.SKIP ? style.dim(line) : line);
    }

    /** The AUTON SCORE block. */
    public static void printScore(PrintStream out, Style style, AutonScore score) {
        out.println(style.bold("AUTON SCORE"));
        out.println(Fmt.rule(34));
        for (MetricScore metric : score.metrics()) {
            out.println(Fmt.pad(metric.name(), 14) + Fmt.padLeft(Fmt.score(metric.score()), 6) + "   "
                    + style.dim(metric.detail()));
        }
        out.println();
        out.println(style.bold(Fmt.pad("TOTAL", 14) + Fmt.padLeft(Fmt.score(score.total()), 6)));
        if (!score.unavailable().isEmpty()) {
            out.println(style.dim("n/a metrics are excluded from the total: "
                    + score.unavailable().stream().map(MetricScore::name).collect(Collectors.joining(", "))));
        }
    }

    /** A single-line score summary. */
    public static String scoreLine(AutonScore score) {
        return "Score " + Fmt.score(score.total()) + "  (" + score.metrics().stream()
                .map(m -> m.name() + " " + Fmt.score(m.score())).collect(Collectors.joining(" · ")) + ")";
    }
}
