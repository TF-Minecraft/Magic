package net.tfminecraft.magic.listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import io.lumine.mythic.lib.api.event.skill.PlayerCastSkillEvent;
import io.lumine.mythic.lib.skill.Skill;
import net.Indyuce.mmoitems.api.interaction.util.DurabilityItem;
import net.tfminecraft.magic.Cache;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.gear.GearBrokenMarker;
import net.tfminecraft.magic.gear.GearHand;
import net.tfminecraft.magic.gear.GearHand.HeldSlot;
import net.tfminecraft.magic.gear.GearProvenance;
import net.tfminecraft.magic.gear.WeaponRequirement;
import net.tfminecraft.magic.gear.WeaponRift;
import net.tfminecraft.magic.integration.SkillIdResolver;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.registry.SkillElementRegistry;
import net.tfminecraft.magic.session.ResonanceSession;

/**
 * The ways a mage weapon stops or taxes a spell.
 *
 * <p><b>Refusal</b> is a locked door. The weapon demands more of the spell's element
 * than the caster carries, or was never attuned to that element at all, so the cast is
 * cancelled before {@code whenCast} and costs nothing. It is never called a whiff.
 *
 * <p><b>Overload</b> is carrying more than one staff. After refusal passes, the cast
 * always fumbles: mana and cooldown are spent, but the spell does not fire.
 *
 * <p><b>Rift</b> is a fumble. A flat percentage burnt into the weapon by bad orbs. It
 * is rolled only once refusal and overload have passed, and a failed roll spends mana
 * and cooldown.
 *
 * <p>Every spent cast (success, overload whiff, or rift whiff) also costs one MMOItems
 * durability on the held weapon.
 */
public final class ResonanceCastListener implements Listener {

    private static final double EPSILON = 0.0001;
    private static final int CHAT_KEYS_MAX = 512;

    private static final Map<String, Long> lastRefuseChat = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCastSkill(PlayerCastSkillEvent event) {
        if (Magic.plugin == null) {
            return;
        }
        Skill cast = event.getCast();
        if (!SkillIdResolver.isActiveCast(cast)) {
            return;
        }
        String elementId = SkillElementRegistry.elementOf(SkillIdResolver.resolveSkillId(cast));
        if (elementId == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        HeldSlot slot = GearHand.heldSlot(player);
        if (slot == null) {
            return;
        }
        ItemStack weapon = GearHand.held(player);
        if (weapon == null) {
            return;
        }
        if (GearBrokenMarker.isBroken(weapon)) {
            event.setCancelled(true);
            broken(player, weapon, elementId);
            return;
        }
        if (refuse(player, weapon, elementId)) {
            event.setCancelled(true);
            return;
        }
        wearWeapon(player, slot, weapon);

        boolean overload = GearHand.staffCount(player) > 1;
        int rift = WeaponRift.get(weapon);
        boolean riftWhiff = !overload
                && rift > 0
                && ThreadLocalRandom.current().nextDouble() * 100.0 < rift;
        if (overload || riftWhiff) {
            cast.whenCast(event.getMetadata());
            event.setCancelled(true);
            if (overload) {
                staffOverloadWhiff(player);
            } else {
                whiff(player, rift);
            }
        }
    }

    private static void wearWeapon(Player player, HeldSlot slot, ItemStack weapon) {
        DurabilityItem durability = new DurabilityItem(player, weapon);
        if (!durability.isValid()) {
            return;
        }
        GearHand.setHeld(player, slot, durability.decreaseDurability(1).toItem());
    }

    /** @return true when the weapon will not carry this element for this caster */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static boolean refuse(Player player, ItemStack weapon, String elementId) {
        double required = WeaponRequirement.fromItem(weapon).aura().getFill(elementId);
        ResonanceSession session = Magic.plugin.getResonanceGuiManager().getSessionManager().get(player);
        double actual = session == null ? 0.0 : session.getResonance(elementId);
        boolean foreign = required <= 0;
        if (!foreign && actual + EPSILON >= required) {
            return false;
        }
        String elementName = elementName(elementId);
        player.sendTitle(
                Messages.get("cast.refuse_title"),
                foreign
                        ? Messages.get("cast.refuse_sub_foreign", "element", elementName)
                        : Messages.get("cast.refuse_sub"),
                0, 25, 10);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.4f);
        if (claimChat(player, weapon, elementId)) {
            if (foreign) {
                player.sendMessage(Messages.get("cast.refuse_chat_foreign", "element", elementName));
            } else {
                String have = TierBands.numeralFor(elementId, actual);
                player.sendMessage(Messages.get(
                        "cast.refuse_chat",
                        "element", elementName,
                        "need", TierBands.numeralFor(elementId, required),
                        "have", have.isEmpty() ? "-" : have));
            }
        }
        return true;
    }

    /**
     * A damaged weapon refuses everything, so nothing is spent here either. Shares the
     * refusal rate limiter so a player holding one is told once, not once per cast.
     */
    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static void broken(Player player, ItemStack weapon, String elementId) {
        player.sendTitle(
                Messages.get("cast.broken_title"),
                Messages.get("cast.broken_sub"),
                0, 25, 10);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.7f, 0.6f);
        if (claimChat(player, weapon, elementId)) {
            player.sendMessage(Messages.get("cast.broken_chat"));
        }
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static void whiff(Player player, int rift) {
        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendTitle(
                Messages.get("cast.whiff"),
                Messages.get("cast.whiff_sub", "rift", String.valueOf(rift)),
                0, 20, 10);
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static void staffOverloadWhiff(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendTitle(
                Messages.get("cast.whiff"),
                Messages.get("cast.whiff_sub_staffs"),
                0, 20, 10);
    }

    /**
     * One chat line per player, weapon and element per window. The title still fires on
     * every refusal, so spamming a locked spell is loud without flooding chat.
     */
    private static boolean claimChat(Player player, ItemStack weapon, String elementId) {
        if (Cache.refuseChatMillis <= 0) {
            return true;
        }
        String key = player.getUniqueId() + "|" + elementId + "|" + GearProvenance.partsRaw(weapon);
        long now = System.currentTimeMillis();
        Long previous = lastRefuseChat.get(key);
        if (previous != null && now - previous < Cache.refuseChatMillis) {
            return false;
        }
        if (lastRefuseChat.size() > CHAT_KEYS_MAX) {
            lastRefuseChat.entrySet().removeIf(entry -> now - entry.getValue() > Cache.refuseChatMillis);
        }
        lastRefuseChat.put(key, now);
        return true;
    }

    private static String elementName(String elementId) {
        ElementDef element = ElementRegistry.getById(elementId);
        return element == null ? elementId : element.getName();
    }

    public static void clearAll() {
        lastRefuseChat.clear();
    }
}
