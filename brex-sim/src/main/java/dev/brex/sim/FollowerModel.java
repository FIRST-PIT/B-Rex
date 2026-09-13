package dev.brex.sim;

/**
 * A kinematic holonomic path follower: reference velocity feed-forward plus proportional
 * correction toward the reference pose, limited in speed, acceleration and turn rate.
 *
 * @param maxSpeed translational speed limit (m/s)
 * @param maxAcceleration translational acceleration limit (m/s²)
 * @param maxTurnRate angular speed limit (rad/s)
 * @param positionGain proportional correction on position error (1/s)
 * @param headingGain proportional correction on heading error (1/s)
 */
public record FollowerModel(double maxSpeed, double maxAcceleration, double maxTurnRate, double positionGain,
        double headingGain) {

    /** Roughly a competitive mecanum robot. */
    public static final FollowerModel DEFAULT = new FollowerModel(1.8, 4.0, 2 * Math.PI, 5.0, 5.0);

    public FollowerModel {
        if (!(maxSpeed > 0 && maxAcceleration > 0 && maxTurnRate > 0 && positionGain >= 0 && headingGain >= 0)) {
            throw new IllegalArgumentException("Follower limits must be positive and gains non-negative");
        }
    }
}
