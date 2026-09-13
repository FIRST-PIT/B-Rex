package dev.brex.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.brex.core.run.Run;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the base class the way a team would write a test. */
class AutonomousTestTest {

    static class BlueLeftTest extends AutonomousTest {
        int loads;

        @Override
        protected Run run() {
            loads++;
            return RunAssertionsTest.blueLeft("42").build();
        }

        @Override
        protected Run baseline() {
            return RunAssertionsTest.blueLeft("41").build();
        }

        @Override
        protected List<Run> history() {
            return List.of(RunAssertionsTest.blueLeft("39").build(), RunAssertionsTest.blueLeft("40").build());
        }

        void blueLeftScoresSix() {
            assertScores(6);
        }

        void blueLeftFinishesUnder25Seconds() {
            assertTimeBelow(25.0);
        }

        void depositsAfterAligning() {
            assertEventOccurred("alignment.complete");
            assertEventBefore("alignment.complete", "deposit.start");
            assertElapsedBetween("deposit.start", "deposit.complete", 1.5);
        }

        void matchesBaseline() {
            assertNoRegression();
            assertPathDeviationBelow(0.01);
            assertAutonScoreAtLeast(95);
            assertReliabilityAtLeast(100);
        }
    }

    @Test
    void readsNaturally() {
        BlueLeftTest test = new BlueLeftTest();

        test.blueLeftScoresSix();
        test.blueLeftFinishesUnder25Seconds();
        test.depositsAfterAligning();
        test.matchesBaseline();

        assertEquals(1, test.loads, "run() is loaded once per test instance");
    }

    @Test
    void baselineAssertionsExplainMissingBaseline() {
        AutonomousTest test = new AutonomousTest() {
            @Override
            protected Run run() {
                return RunAssertionsTest.blueLeft("42").build();
            }
        };

        BrexAssertionError error = assertThrows(BrexAssertionError.class, test::assertNoRegression);

        assertEquals("assertNoRegression needs a baseline: override baseline() in ", error.getMessage());
    }
}
