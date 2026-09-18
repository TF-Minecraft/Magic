package net.tfminecraft.magic.artifact.shrine;

import org.bukkit.Particle;
import org.bukkit.Sound;

public final class ShrineFxDef {

    private final Sound ambientSound;
    private final float ambientVolume;
    private final float ambientPitch;
    private final int ambientEveryTicks;
    private final Sound startSound;
    private final float startVolume;
    private final float startPitch;
    private final Sound completeSound;
    private final float completeVolume;
    private final float completePitch;
    private final Sound completeSound2;
    private final float completeVolume2;
    private final float completePitch2;
    private final Particle trailParticle;
    private final Particle emitterParticle;
    private final Particle burstParticle;

    public ShrineFxDef(
            Sound ambientSound,
            float ambientVolume,
            float ambientPitch,
            int ambientEveryTicks,
            Sound startSound,
            float startVolume,
            float startPitch,
            Sound completeSound,
            float completeVolume,
            float completePitch,
            Sound completeSound2,
            float completeVolume2,
            float completePitch2,
            Particle trailParticle,
            Particle emitterParticle,
            Particle burstParticle) {
        this.ambientSound = ambientSound;
        this.ambientVolume = Math.max(1.0f, ambientVolume);
        this.ambientPitch = ambientPitch;
        this.ambientEveryTicks = Math.max(8, ambientEveryTicks);
        this.startSound = startSound;
        this.startVolume = Math.max(1.0f, startVolume);
        this.startPitch = startPitch;
        this.completeSound = completeSound;
        this.completeVolume = Math.max(1.0f, completeVolume);
        this.completePitch = completePitch;
        this.completeSound2 = completeSound2;
        this.completeVolume2 = Math.max(1.0f, completeVolume2);
        this.completePitch2 = completePitch2;
        this.trailParticle = trailParticle;
        this.emitterParticle = emitterParticle;
        this.burstParticle = burstParticle;
    }

    public static ShrineFxDef fallback() {
        return new ShrineFxDef(
                Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f, 24,
                Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f,
                Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.15f, 1.15f,
                Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.3f,
                Particle.ENCHANT, Particle.ENCHANT, Particle.FIREWORK);
    }

    public Sound getAmbientSound() {
        return ambientSound;
    }

    public float getAmbientVolume() {
        return ambientVolume;
    }

    public float getAmbientPitch() {
        return ambientPitch;
    }

    public int getAmbientEveryTicks() {
        return ambientEveryTicks;
    }

    public Sound getStartSound() {
        return startSound;
    }

    public float getStartVolume() {
        return startVolume;
    }

    public float getStartPitch() {
        return startPitch;
    }

    public Sound getCompleteSound() {
        return completeSound;
    }

    public float getCompleteVolume() {
        return completeVolume;
    }

    public float getCompletePitch() {
        return completePitch;
    }

    public Sound getCompleteSound2() {
        return completeSound2;
    }

    public float getCompleteVolume2() {
        return completeVolume2;
    }

    public float getCompletePitch2() {
        return completePitch2;
    }

    public Particle getTrailParticle() {
        return trailParticle;
    }

    public Particle getEmitterParticle() {
        return emitterParticle;
    }

    public Particle getBurstParticle() {
        return burstParticle;
    }
}
