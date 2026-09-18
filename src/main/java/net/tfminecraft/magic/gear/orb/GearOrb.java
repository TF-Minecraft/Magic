package net.tfminecraft.magic.gear.orb;

import org.bukkit.Location;

/**
 * One virtual orb circling a crafting station. Particle only, no entity, so it is never
 * hit by anything but the plugin's own hitscan.
 */
public final class GearOrb {

    private final Location anchor;
    private Location location;
    private final boolean good;
    private double angle;
    private final double radius;
    private final double heightBias;
    private final double angleSpeed;
    private double bobPhase;
    private final int introMax;
    private int introRemaining;
    private int lifeRemaining;
    private boolean consumed;

    public GearOrb(
            Location anchor,
            boolean good,
            double angle,
            double radius,
            double heightBias,
            double angleSpeed,
            double bobPhase,
            int introTicks,
            int lifetimeTicks) {
        this.anchor = anchor.clone();
        this.location = anchor.clone();
        this.good = good;
        this.angle = angle;
        this.radius = radius;
        this.heightBias = heightBias;
        this.angleSpeed = angleSpeed;
        this.bobPhase = bobPhase;
        this.introMax = Math.max(0, introTicks);
        this.introRemaining = this.introMax;
        this.lifeRemaining = Math.max(1, lifetimeTicks);
    }

    public Location getAnchor() {
        return anchor;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location.clone();
    }

    public boolean isGood() {
        return good;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    public double getRadius() {
        return radius;
    }

    public double getHeightBias() {
        return heightBias;
    }

    public double getAngleSpeed() {
        return angleSpeed;
    }

    public double getBobPhase() {
        return bobPhase;
    }

    public void addBobPhase(double delta) {
        this.bobPhase += delta;
    }

    public int getIntroMax() {
        return introMax;
    }

    public int getIntroRemaining() {
        return introRemaining;
    }

    public void tickIntro() {
        if (introRemaining > 0) {
            introRemaining--;
        }
    }

    /** @return true on the tick the orb runs out of time */
    public boolean tickLife() {
        if (consumed) {
            return false;
        }
        if (lifeRemaining > 0) {
            lifeRemaining--;
        }
        return lifeRemaining <= 0;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }
}
