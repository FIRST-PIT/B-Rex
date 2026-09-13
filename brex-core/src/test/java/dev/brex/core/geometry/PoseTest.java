package dev.brex.core.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PoseTest {

    private static final double EPS = 1e-9;

    @Test
    void normalizesHeading() {
        assertEquals(-Math.PI / 2, Pose.of(0, 0, 3 * Math.PI / 2).heading(), EPS);
        assertEquals(Math.PI, Pose.of(0, 0, -Math.PI).heading(), EPS);
    }

    @Test
    void convertsInchesAndDegrees() {
        Pose pose = Pose.ofInches(10, -20, 90);

        assertEquals(0.254, pose.x(), EPS);
        assertEquals(-0.508, pose.y(), EPS);
        assertEquals(Math.PI / 2, pose.heading(), EPS);
    }

    @Test
    void distanceIsEuclidean() {
        assertEquals(5.0, Pose.of(0, 0, 0).distanceTo(Pose.of(3, 4, 1)), EPS);
    }

    @Test
    void headingDifferenceTakesShortestArc() {
        Pose a = Pose.of(0, 0, Math.toRadians(170));
        Pose b = Pose.of(0, 0, Math.toRadians(-170));

        assertEquals(Math.toRadians(20), a.headingDifferenceTo(b), EPS);
        assertEquals(Math.toRadians(-20), b.headingDifferenceTo(a), EPS);
    }

    @Test
    void interpolatesAcrossHeadingWrap() {
        Pose a = Pose.of(0, 0, Math.toRadians(170));
        Pose b = Pose.of(2, 4, Math.toRadians(-170));

        Pose mid = a.interpolate(b, 0.5);

        assertEquals(1.0, mid.x(), EPS);
        assertEquals(2.0, mid.y(), EPS);
        assertEquals(Math.PI, Math.abs(mid.heading()), EPS);
    }

    @Test
    void valueEquality() {
        assertEquals(Pose.of(1, 2, 0.5), Pose.of(1, 2, 0.5));
        assertEquals(Pose.of(1, 2, 0.5).hashCode(), Pose.of(1, 2, 0.5).hashCode());
    }

    @Test
    void parsesDistanceUnits() {
        assertEquals(DistanceUnit.CENTIMETERS, DistanceUnit.parse("cm"));
        assertEquals(DistanceUnit.INCHES, DistanceUnit.parse("Inches"));
        assertThrows(IllegalArgumentException.class, () -> DistanceUnit.parse("furlong"));
        assertEquals(2.54, DistanceUnit.CENTIMETERS.fromMeters(DistanceUnit.INCHES.toMeters(1)), EPS);
    }
}
