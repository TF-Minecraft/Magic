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
import io.lumine.mythic.lib.api.item.NBTItem;
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
import net.tfminecraft.magic.gear.GearType;
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
 * <p><b>Refusal</b> is a locked door. The spell's {@code skills.yml} tier is the
 * floor: the weapon must hold that element at that band, and the caster's resonance
 * must meet the same band. A high-attuned staff does not block a lower-tier spell.
 * Foreign element or a failed floor cancels before {@code whenCast} and costs
 * nothing. It is never called a whiff.
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
        String skillId = SkillIdResolver.resolveSkillId(cast);
        String elementId = SkillElementRegistry.elementOf(skillId);
        if (elementId == null) {
            return;
        }
        int spellTier = SkillElementRegistry.tierOf(skillId);
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
        if (refuse(player, weapon, elementId, spellTier)) {
            event.setCancelled(true);
            return;
        }
        wearWeapon(player, slot, weapon, skillId, elementId);

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

    private static void wearWeapon(
            Player player, HeldSlot slot, ItemStack weapon, String skillId, String elementId) {
        DurabilityItem durability = new DurabilityItem(player, weapon);
        GearType archetype = GearProvenance.archetypeOf(weapon);
        String mmo = mmoTypeId(weapon);
        String who = player.getName();
        String gear = archetype == null ? "unknown" : archetype.name();
        String hand = slot == null ? "none" : slot.name();
        if (!durability.isValid()) {
            Magic.plugin.getLogger().info("[Magic][cast-wear] skip: DurabilityItem not valid"
                    + " player=" + who
                    + " skill=" + skillId
                    + " element=" + elementId
                    + " gear=" + gear
                    + " hand=" + hand
                    + " mmo=" + mmo
                    + " valid=false");
            return;
        }
        int before = durability.getDurability();
        int max = durability.getMaxDurability();
        DurabilityItem worn = durability.decreaseDurability(1);
        int after = worn.getDurability();
        GearHand.setHeld(player, slot, worn.toItem());
        Magic.plugin.getLogger().info("[Magic][cast-wear] applied"
                + " player=" + who
                + " skill=" + skillId
                + " element=" + elementId
                + " gear=" + gear
                + " hand=" + hand
                + " mmo=" + mmo
                + " valid=true"
                + " durability=" + before + "->" + after + "/" + max
                + " setHeld=true");
    }

    private static String mmoTypeId(ItemStack weapon) {
        if (weapon == null) {
            return "-";
        }
        try {
            NBTItem nbt = NBTItem.get(weapon);
            String type = nbt.hasType() ? nbt.getType() : "-";
            String id = nbt.getString("MMOITEMS_ITEM_ID");
            if (id == null || id.isBlank()) {
                return type;
            }
            return type + ":" + id;
        } catch (Exception ex) {
            return "-";
        }
    }

    /** @return true when this spell cannot fire on this weapon for this caster */
    private static boolean refuse(Player player, ItemStack weapon, String elementId, int spellTier) {
        double weaponFill = WeaponRequirement.fromItem(weapon).aura().getFill(elementId);
        ResonanceSession session = Magic.plugin.getResonanceGuiManager().getSessionManager().get(player);
        double playerFill = session == null ? 0.0 : session.getResonance(elementId);
        int weaponBand = TierBands.bandOf(elementId, weaponFill);
        int playerBand = TierBands.bandOf(elementId, playerFill);
        SpellTierGate.Refuse kind = SpellTierGate.refuse(weaponFill > 0, weaponBand, playerBand, spellTier);
        if (kind == SpellTierGate.Refuse.NONE) {
            return false;
        }
        String elementName = elementName(elementId);
        String need = numeralOrDash(spellTier);
        String weaponHave = numeralOrDash(weaponBand);
        String playerHave = numeralOrDash(playerBand);
        String subtitle;
        String chat;
        switch (kind) {
            case FOREIGN -> {
                subtitle = Messages.get("cast.refuse_sub_foreign", "element", elementName);
                chat = Messages.get("cast.refuse_chat_foreign", "element", elementName);
            }
            case WEAPON -> {
                subtitle = Messages.get("cast.refuse_sub_weapon");
                chat = Messages.get(
                        "cast.refuse_chat_weapon",
                        "element", elementName,
                        "have", weaponHave,
                        "need", need);
            }
            default -> {
                subtitle = Messages.get("cast.refuse_sub_spell");
                chat = Messages.get(
                        "cast.refuse_chat_spell",
                        "element", elementName,
                        "need", need,
                        "have", playerHave);
            }
        }
        player.sendTitle(Messages.get("cast.refuse_title"), subtitle, 0, 25, 10);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.7f, 1.4f);
        if (claimChat(player, weapon, elementId)) {
            player.sendMessage(chat);
        }
        return true;
    }

    private static String numeralOrDash(int band) {
        String numeral = TierBands.numeral(band);
        return numeral.isEmpty() ? "-" : numeral;
    }

    /**
     * A damaged weapon refuses everything, so nothing is spent here either. Shares the
     * refusal rate limiter so a player holding one is told once, not once per cast.
     */
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

    private static void whiff(Player player, int rift) {
        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendTitle(
                Messages.get("cast.whiff"),
                Messages.get("cast.whiff_sub", "rift", String.valueOf(rift)),
                0, 20, 10);
    }

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
        return element == null ? elementId : element.getColoredName();
    }

    public static void clearAll() {
        lastRefuseChat.clear();
    }
}
