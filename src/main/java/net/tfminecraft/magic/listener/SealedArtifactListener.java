package net.tfminecraft.magic.listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import net.tfminecraft.magic.artifact.sealed.SealedArtifactDef;
import net.tfminecraft.magic.artifact.sealed.SealedArtifactRegistry;
import net.tfminecraft.magic.artifact.sealed.SealedArtifactService;

/**
 * Right-clicking a sealed artifact breaks the seal. Clicks on chests, doors and other usable
 * blocks still use the block.
 */
public final class SealedArtifactListener implements Listener {

    private final Supplier<SealedArtifactService> services;
    private final Set<UUID> openedThisTick = new HashSet<>();
    private int openTick;

    public SealedArtifactListener() {
        this(SealedArtifactService::new);
    }

    SealedArtifactListener(Supplier<SealedArtifactService> services) {
        this.services = services;
    }

    // Right-clicks on air arrive already cancelled, so cancelled events are not ignored here.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY || usesBlock(event)) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        SealedArtifactDef def = SealedArtifactRegistry.match(event.getItem());
        if (hand == null || def == null) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        // One click can fire several interact events (block then air, main then off hand),
        // all in the same tick. Only each player's first one breaks a seal.
        int tick = Bukkit.getCurrentTick();
        if (tick != openTick) {
            openTick = tick;
            openedThisTick.clear();
        }
        Player player = event.getPlayer();
        if (!openedThisTick.add(player.getUniqueId())) {
            return;
        }
        services.get().open(player, hand, def);
    }

    private static boolean usesBlock(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        return block != null
                && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && block.getType().isInteractable()
                && !event.getPlayer().isSneaking();
    }
}
