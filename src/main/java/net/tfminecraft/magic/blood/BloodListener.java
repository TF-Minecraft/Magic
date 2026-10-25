package net.tfminecraft.magic.blood;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.magic.Messages;
import net.tfminecraft.tlibs.character.CharacterDeathEvent;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.CharacterSessionStartEvent;

/** Altar placement, protection, looks on chunk load, dark deeds for the whisper meter, and deaths. */
public final class BloodListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        BloodAltar altar = BloodAltars.at(clicked);
        if (altar != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(AltarService.touch(event.getPlayer(), altar));
            return;
        }
        ItemStack hand = event.getItem();
        if (!BloodItems.is(hand, BloodItemKind.ALTAR)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        Block target = AltarPlacement.replaceable(clicked) ? clicked : clicked.getRelative(event.getBlockFace());
        event.getPlayer().sendMessage(Messages.get(AltarService.place(event.getPlayer(), hand, target)));
    }

    /** The altar item is never set down as an ordinary block. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (BloodItems.kind(event.getItemInHand()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (BloodAltars.at(event.getBlock()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.get("blood.altar.unbreakable"));
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
        event.blockList().removeIf(block -> BloodAltars.at(block) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> BloodAltars.at(block) != null);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        AltarDisplays.removeStrays(event.getEntities());
        for (BloodAltar altar : BloodAltars.inChunk(event.getWorld().getName(),
                event.getChunk().getX(), event.getChunk().getZ())) {
            AltarService.refresh(altar);
        }
    }

    @EventHandler
    public void onCharacterDeath(CharacterDeathEvent event) {
        AltarService.onCharacterDeath(event.getCharacter().characterId().toString());
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

    @EventHandler(ignoreCancelled = true)
    public void onAnimalDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            WhisperService.add(killer, WhisperService.animalKillWeight(killer, event.getEntity()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        WhisperService.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSessionStart(CharacterSessionStartEvent event) {
        WhisperService.forget(event.getPlayer().getUniqueId());
    }

    private static boolean touchesAltar(List<Block> blocks) {
        for (Block block : blocks) {
            if (BloodAltars.at(block) != null) {
                return true;
            }
        }
        return false;
    }
}
