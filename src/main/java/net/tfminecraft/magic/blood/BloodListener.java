package net.tfminecraft.magic.blood;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

import net.tfminecraft.interactiblefurniture.events.FurnitureBreakEvent;
import net.tfminecraft.interactiblefurniture.events.FurnitureInteractEvent;
import net.tfminecraft.interactiblefurniture.events.FurniturePlaceEvent;
import net.tfminecraft.magic.Magic;
import net.tfminecraft.magic.Messages;
import net.tfminecraft.rpcharacters.chat.CharacterChatEvent;
import net.tfminecraft.tlibs.character.CharacterDeathEvent;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSessionEndEvent;
import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;

/** Altar placement, protection, looks on chunk load, dark deeds for the whisper meter, and deaths. */
public final class BloodListener implements Listener {

    /** Staff who may still change an altar's chamber. */
    static final String BYPASS = "magic.blood.bypass";

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        BloodAltar altar = BloodAltars.at(clicked);
        if (altar != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            ItemStack held = player.getInventory().getItemInMainHand();
            String character = AltarService.currentCharacter(player);
            if (character != null && character.equalsIgnoreCase(altar.getOwnerCharacter())
                    && BloodItems.is(held, BloodItemKind.BLOOD_KNIFE) && character.equalsIgnoreCase(BloodItems.owner(held))
                    && BloodChoice.canAnswer(player)) {
                QuestionService.start(player);
                return;
            }
            Player carried = CarryService.carried(player);
            if (carried != null && BloodItems.is(held, BloodItemKind.BLOOD_KNIFE)) {
                layDown(player, carried);
                return;
            }
            AltarService.touch(player, altar);
            return;
        }
        ItemStack hand = event.getItem();
        if (!BloodItems.is(hand, BloodItemKind.ALTAR)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        Block target = AltarPlacement.replaceable(clicked) ? clicked : clicked.getRelative(event.getBlockFace());
        event.getPlayer().sendMessage(Messages.get(AltarService.place(event.getPlayer(), hand, target),
                "size", String.valueOf(AltarRoom.size()), "height", String.valueOf(BloodConfig.roomHeight())));
    }

    /** The ritual knife binds a living animal onto its owner's altar. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (event.getRightClicked() instanceof Player victim && BloodItems.is(held, BloodItemKind.BLOOD_KNIFE)) {
            event.setCancelled(true);
            Player mage = event.getPlayer();
            if (victim.equals(CarryService.carried(mage))) {
                if (mage.isSneaking()) {
                    CarryService.release(mage, true);
                } else {
                    layDown(mage, victim);
                }
                return;
            }
            if (mage.isSneaking() && HuntConfig.carryEnabled()) {
                CharacterRiteService.Bind start = CarryService.start(mage, victim);
                if (start != CharacterRiteService.Bind.OK) {
                    WhisperService.tell(mage, "blood.rite.bind." + start.name().toLowerCase(Locale.ROOT));
                }
                return;
            }
            layDown(mage, victim);
            return;
        }
        if (!(event.getRightClicked() instanceof LivingEntity animal) || animal instanceof Player
                || !BloodItems.is(held, BloodItemKind.RITUAL_KNIFE)) {
            return;
        }
        event.setCancelled(true);
        AnimalRiteService.Bind result = AnimalRiteService.bind(event.getPlayer(), animal);
        WhisperService.tell(event.getPlayer(), "blood.animal.bind." + result.name().toLowerCase(Locale.ROOT));
    }

    private static void layDown(Player mage, Player victim) {
        CharacterRiteService.Bind result = CharacterRiteService.bind(mage, victim);
        WhisperService.tell(mage, "blood.rite.bind." + result.name().toLowerCase(Locale.ROOT));
    }

    /** The altar's own pedestals cannot be picked up, broken or carried off; purifying it removes them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureBreak(FurnitureBreakEvent event) {
        if (event.hasPlayer() && AltarPedestals.bound(event.getFurniture())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.get("blood.altar.pedestal_bound"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureInteract(FurnitureInteractEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && player.getInventory().getItemInMainHand().getType().isAir()
                && AltarPedestals.bound(event.getFurniture())) {
            event.setCancelled(true);
            player.sendMessage(Messages.get("blood.altar.pedestal_bound"));
        }
    }

    /** Sitting on the altar with /sit is how its owner meditates there; the stone answers. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            AltarService.sat(player, event.getMount());
        }
    }

    /** The answer to the god's question is spoken aloud, in roleplay chat. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCharacterChat(CharacterChatEvent event) {
        QuestionService.onChat(event.getSender(), event.getChannel(), event.getMessage());
    }

    /** A skin may have been put on a blood piece in a menu: keep ItemsAdder off it. */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            BloodArmour.protectAll(player);
        }
    }

    /** Blood armour stays on its own character only; checked a tick after any armour change. */
    @EventHandler
    public void onArmourChange(PlayerArmorChangeEvent event) {
        if (BloodArmour.is(event.getNewItem())) {
            Player player = event.getPlayer();
            Magic.plugin.getServer().getScheduler().runTask(Magic.plugin, () -> BloodArmour.enforce(player));
        }
    }

    /** A dead owner's blood armour crumbles when someone tries to pick it up. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (BloodArmour.crumbles(event.getItem().getItemStack())) {
            event.setCancelled(true);
            BloodFx.play("armour_crumbles", event.getItem().getLocation());
            event.getItem().remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        StolenClues.leashed(event.getEntity());
    }

    /** The altar item is never set down as an ordinary block. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (BloodItems.kind(event.getItemInHand()) != null) {
            event.setCancelled(true);
        } else if (locked(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** The altar's chamber is the stone's: nobody but staff places, breaks or hangs anything in it. */
    static boolean locked(Player player, Block block) {
        if (AltarRoom.lockedBy(block) == null || (player != null && player.hasPermission(BYPASS))) {
            return false;
        }
        if (player != null) {
            player.sendMessage(Messages.get("blood.altar.room_locked"));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (locked(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (locked(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHang(HangingPlaceEvent event) {
        if (locked(event.getPlayer(), event.getEntity().getLocation().getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (locked(event.getPlayer(), event.getEntity().getLocation().getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFurniturePlace(FurniturePlaceEvent event) {
        if (locked(event.getPlayer(), event.getFurniture().getLoc().getBlock())) {
            event.setCancelled(true);
        }
    }

    /** The altar, and every block it has changed around it, stand against tools. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (BloodAltars.at(block) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.get("blood.altar.unbreakable"));
        } else if (AltarCorruption.isCorrupted(block)) {
            event.setCancelled(true);
            WhisperService.tell(event.getPlayer(), "blood.corruption.unbreakable");
            BloodFx.play("corruption_unbreakable", block.getLocation().add(0.5, 0.5, 0.5), event.getPlayer());
        } else if (locked(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (touchesAltar(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (touchesAltar(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(BloodListener::guarded);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(BloodListener::guarded);
    }

    /** The altar, a block it has changed, or anything in its locked chamber. */
    private static boolean guarded(Block block) {
        return BloodAltars.at(block) != null || AltarCorruption.isCorrupted(block) || AltarRoom.lockedBy(block) != null;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        AltarDisplays.removeStrays(event.getEntities());
        AnimalRiteService.wakeLoaded(event.getEntities());
        StolenClues.removeExpired(event.getEntities());
        AltarPedestals.sweep();
        for (BloodAltar altar : BloodAltars.inChunk(event.getWorld().getName(),
                event.getChunk().getX(), event.getChunk().getZ())) {
            AltarService.refresh(altar);
        }
    }

    @EventHandler
    public void onCharacterDeath(CharacterDeathEvent event) {
        String dead = event.getCharacter().characterId().toString();
        AltarService.onCharacterDeath(dead);
        if (!CharacterRiteService.isBeingSacrificed(dead)) {
            FinalService.onDeath(dead);
        }
        CharacterRef killer = event.getKillerCharacter();
        if (killer == null || killer.characterId().equals(event.getCharacter().characterId())) {
            return;
        }
        String character = killer.characterId().toString();
        Player online = Bukkit.getPlayer(killer.ownerId());
        Player current = online != null && character.equalsIgnoreCase(AltarService.currentCharacter(online))
                ? online : null;
        WhisperService.addTo(character, killer.ownerId().toString(), WhisperConfig.characterKill(), current);
    }

    /** HIGHEST: after other damage plugins, so the ritual knife's stroke stays lethal on a bound animal. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnimalHit(EntityDamageByEntityEvent event) {
        AnimalRiteService.onHit(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnimalDeath(EntityDeathEvent event) {
        AnimalRiteService.onDeath(event);
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            WhisperService.add(killer, WhisperService.animalKillWeight(killer, event.getEntity()));
        }
    }

    /** LOW: before RPCharacters closes the character session on quit. */
    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        CharacterRiteService.onQuit(event.getPlayer());
        WhisperService.forget(event.getPlayer().getUniqueId());
        AnimalRiteService.releaseFor(event.getPlayer().getUniqueId());
        QuestionService.forget(event.getPlayer().getUniqueId());
        CarryService.drop(event.getPlayer().getUniqueId());
        BloodPerms.clear(event.getPlayer());
    }

    @EventHandler
    public void onSessionStart(CharacterSessionStartEvent event) {
        WhisperService.forget(event.getPlayer().getUniqueId());
        BloodNotes.deliver(event.getPlayer(), event.getSession().ref().characterId().toString(),
                System.currentTimeMillis(), true);
        BloodArmour.enforce(event.getPlayer());
        String character = event.getSession().ref().characterId().toString();
        BondService.load(character);
        BloodBoons.apply(event.getPlayer());
        ClassRewards.applySoon(event.getPlayer());
        BloodPerms.refresh(event.getPlayer());
        FinalService.check(event.getPlayer());
    }

    @EventHandler
    public void onSessionEnd(CharacterSessionEndEvent event) {
        BondService.unload(event.getSession().ref().characterId().toString());
        BloodBoons.clear(event.getPlayer());
        BloodPerms.clear(event.getPlayer());
        AnimalRiteService.releaseFor(event.getPlayer().getUniqueId());
    }

    private static boolean touchesAltar(List<Block> blocks) {
        for (Block block : blocks) {
            if (guarded(block)) {
                return true;
            }
        }
        return false;
    }
}
