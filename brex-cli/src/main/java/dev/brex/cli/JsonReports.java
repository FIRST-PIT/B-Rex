package dev.brex.cli;

import dev.brex.analysis.regression.RegressionCheck;
import dev.brex.analysis.regression.RegressionReport;
import dev.brex.analysis.score.AutonScore;
import dev.brex.analysis.score.MetricScore;
import dev.brex.core.BrexVersion;
import dev.brex.core.geometry.Pose;
import dev.brex.core.json.Json;
import dev.brex.core.run.Run;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builders for machine-readable output. Values are SI units (seconds, meters, radians); missing
 * values are {@code null}. Field names are part of the CLI contract.
 */
public final class JsonReports {

    private JsonReports() {
    }

    /** The common root object: B-rex version and command name. */
    public static Map<String, Object> envelope(String command) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("brexVersion", BrexVersion.get());
        root.put("command", command);
        return root;
    }

    public static String write(Object tree) {
        return Json.write(tree, true);
    }

    public static Map<String, Object> run(Run run) {
        if (run == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", run.id());
        m.put("routine", run.name());
        m.put("kind", run.metadata().kind().name());
        m.put("source", run.metadata().source().name());
        m.put("status", run.status().name());
        m.put("startedAt", run.metadata().startedAtEpochMillis());
        m.put("duration", run.duration());
        m.put("gitCommit", run.metadata().gitCommit());
        m.put("robot", run.metadata().robot());
        m.put("softwareVersion", run.metadata().softwareVersion());
        m.put("startPose", pose(run.startPose()));
        m.put("endPose", pose(run.endPose()));
        m.put("events", run.events().size());
        m.put("warnings", run.warnings().size());
        m.put("errors", run.errors().size());
        m.put("pointsScored", run.pointsScored());
        return m;
    }

    public static Map<String, Object> pose(Pose pose) {
        if (pose == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", pose.x());
        m.put("y", pose.y());
        m.put("heading", pose.heading());
        return m;
    }

    public static List<Object> checks(RegressionReport report) {
        List<Object> checks = new ArrayList<>();
        if (report == null) {
            return checks;
        }
        for (RegressionCheck check : report.checks()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", check.id());
            m.put("title", check.title());
            m.put("status", check.status().name());
            m.put("baseline", check.baseline());
            m.put("current", check.current());
            m.put("delta", check.delta());
            m.put("threshold", check.threshold());
            m.put("unit", check.unit().name());
            m.put("message", check.message());
            checks.add(m);
        }
        return checks;
    }

    public static Map<String, Object> score(AutonScore score) {
        if (score == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", score.total());
        List<Object> metrics = new ArrayList<>();
        for (MetricScore metric : score.metrics()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", metric.name());
            entry.put("score", metric.score());
            entry.put("weight", metric.weight());
            entry.put("detail", metric.detail());
            metrics.add(entry);
        }
        m.put("metrics", metrics);
        return m;
    }
}
