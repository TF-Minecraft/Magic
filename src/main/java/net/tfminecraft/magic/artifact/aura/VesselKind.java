package net.tfminecraft.magic.artifact.aura;

/** Which concrete type an {@link AuraVessel} is, for the few places behaviour differs. */
public enum VesselKind {
    /** Permanent shrine item. Meditation and care/muffle apply. */
    ARTIFACT,
    /** Crafting consumable spent at the mage station. No meditation, no care. */
    CHARGE
}
