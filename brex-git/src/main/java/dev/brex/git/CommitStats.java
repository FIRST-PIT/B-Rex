package dev.brex.git;

import dev.brex.analysis.IdleStates;
import dev.brex.analysis.Stats;
import dev.brex.analysis.score.AutonScorer;
import dev.brex.analysis.score.ScoringConfig;
import dev.brex.core.run.Run;
import java.util.ArrayList;
import java.util.List;

/**
 * How one routine performed at one commit, aggregated over every run recorded with that commit.
 *
 * @param routine routine name
 * @param commit the commit
 * @param runs number of runs recorded at this commit
 * @param meanDuration mean duration of completed runs, seconds (NaN when none completed)
 * @param reliability percentage of runs that completed without errors
 * @param meanScore mean autonomous score (NaN when it cannot be computed)
 */
public record CommitStats(String routine, Commit commit, int runs, double meanDuration, double reliability,
        double meanScore) {

    /** Runs of {@code routine} in {@code allRuns} whose recorded commit matches {@code commit}. */
    public static List<Run> runsAt(Commit commit, String routine, List<Run> allRuns) {
        return allRuns.stream()
                .filter(r -> r.name().equals(routine) && commit.matches(r.metadata().gitCommit()))
                .toList();
    }

    /**
     * Aggregates runs recorded at a commit. Scores use the runs at the same commit as history, so
     * consistency and reliability describe that version of the code.
     */
    public static CommitStats of(String routine, Commit commit, List<Run> runsAtCommit, Run baseline,
            ScoringConfig scoring, IdleStates idleStates) {
        if (runsAtCommit.isEmpty()) {
            return new CommitStats(routine, commit, 0, Double.NaN, Double.NaN, Double.NaN);
        }
        AutonScorer scorer = new AutonScorer(scoring, idleStates);
        List<Double> durations = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        int succeeded = 0;
        for (int i = 0; i < runsAtCommit.size(); i++) {
            Run run = runsAtCommit.get(i);
            if (run.succeeded()) {
                succeeded++;
                durations.add(run.duration());
            }
            double total = scorer.score(run, baseline, runsAtCommit.subList(0, i + 1)).total();
            if (!Double.isNaN(total)) {
                scores.add(total);
            }
        }
        return new CommitStats(routine, commit, runsAtCommit.size(), Stats.mean(durations),
                100.0 * succeeded / runsAtCommit.size(), Stats.mean(scores));
    }
}
