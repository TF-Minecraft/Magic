package net.tfminecraft.magic.meditation;

import java.util.UUID;

import org.bukkit.Location;

public final class MeditationOrb {

    private Location location;
    private final Location spawnLocation;
    private Location returnFrom;
    private final UUID sourceId;
    private boolean flow;
    private final boolean starter;
    private double angle;
    private final double radius;
    private final double heightBias;
    private final double angleSpeed;
    private double bobPhase;
    private final int introMax;
    private int introRemaining;
    private final int lifeMax;
    private int lifeRemaining;
    private boolean returning;
    private int returnRemaining;
    private boolean consumed;

    public MeditationOrb(Location location, boolean flow, boolean starter, double angle) {
        this(location, flow, starter, angle, location, 0, 0.0, 0.0, 0.0, 0.0, 0, null);
    }

    public MeditationOrb(
            Location location,
            boolean flow,
            boolean starter,
            double angle,
            Location spawnLocation,
            int introTicks,
            double radius,
            double heightBias,
            double angleSpeed,
            double bobPhase,
            int lifetimeTicks,
            UUID sourceId) {
        this.location = location.clone();
        this.spawnLocation = spawnLocation.clone();
        this.sourceId = sourceId;
        this.flow = flow;
        this.starter = starter;
        this.angle = angle;
        this.radius = radius;
        this.heightBias = heightBias;
        this.angleSpeed = angleSpeed;
        this.bobPhase = bobPhase;
        this.introMax = Math.max(0, introTicks);
        this.introRemaining = this.introMax;
        this.lifeMax = starter ? 0 : Math.max(0, lifetimeTicks);
        this.lifeRemaining = this.lifeMax;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location.clone();
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public boolean isFlow() {
        return flow;
    }

    public void setFlow(boolean flow) {
        this.flow = flow;
    }

    public boolean isStarter() {
        return starter;
    }

    public boolean isReturning() {
        return returning;
    }

    public void beginReturn() {
        if (returning || consumed || starter) {
            return;
        }
        returning = true;
        returnFrom = location.clone();
        returnRemaining = Math.max(1, introMax > 0 ? introMax : MeditationCache.orbIntroTicks);
    }

    public Location getReturnFrom() {
        return returnFrom != null ? returnFrom : location;
    }

    public int getReturnRemaining() {
        return returnRemaining;
    }

    public int getReturnMax() {
        return Math.max(1, introMax > 0 ? introMax : MeditationCache.orbIntroTicks);
    }

    public boolean tickReturn() {
        if (!returning || consumed) {
            return false;
        }
        if (returnRemaining > 0) {
            returnRemaining--;
        }
        return returnRemaining <= 0;
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

    public boolean tickLife() {
        if (lifeMax <= 0 || consumed || returning) {
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
