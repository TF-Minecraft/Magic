package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.gui.*;
import net.tfminecraft.magic.gear.orb.*;
import net.tfminecraft.magic.gear.ownership.*;
import net.tfminecraft.magic.listener.MagicSessionListener;
import net.tfminecraft.magic.profile.MagicProfileService;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.*;
import org.junit.jupiter.api.Test;

class GearOwnershipRuntimeTest extends GearStationCoverageSupport {
  GearIdentity.Token token(org.bukkit.entity.Player p){return GearStationStore.property().identity().current(p).orElseThrow();}
  CharacterSession session(org.bukkit.entity.Player p){return characters.currentSession(p).orElseThrow();}
  void next(org.bukkit.entity.Player p){var s=session(p);characters.sessions.put(p.getUniqueId(),new CharacterSession(s.ref(),s.sessionId()+1));}

  @Test void capturedMenusAndContextsSurviveLateOutgoingEventsButCloseForTheirOwnEnd()throws Exception{
    var p=server.addPlayer();var location=station();var outgoing=session(p);var oldToken=token(p);
    OpenStationManager.set(p,location);var oldHolder=new AssemblyHolder(p);p.openInventory(server.createInventory(oldHolder,9));
    var profiles=mock(MagicProfileService.class);var gui=Magic.plugin.getResonanceGuiManager();var listener=new MagicSessionListener(profiles,gui);
    next(p);var incoming=session(p);var newToken=token(p);OpenStationManager.set(p,location);var incomingHolder=new AssemblyHolder(p);p.openInventory(server.createInventory(incomingHolder,9));
    listener.onSessionEnd(new CharacterSessionEndEvent(p,outgoing,CharacterSessionEndEvent.Reason.SWITCH));
    assertSame(incomingHolder,p.getOpenInventory().getTopInventory().getHolder());assertEquals(location,OpenStationManager.get(p));
    listener.onSessionStart(new CharacterSessionStartEvent(p,outgoing));assertSame(incomingHolder,p.getOpenInventory().getTopInventory().getHolder());
    listener.onSessionStart(new CharacterSessionStartEvent(p,incoming));assertSame(incomingHolder,p.getOpenInventory().getTopInventory().getHolder());
    OpenStationManager.clear(null,oldToken);assertFalse(OpenStationManager.matches(p,null));OpenStationManager.clear(p,oldToken);assertEquals(location,OpenStationManager.get(p));
    listener.onSessionEnd(new CharacterSessionEndEvent(p,incoming,CharacterSessionEndEvent.Reason.SWITCH));assertNull(OpenStationManager.get(p));assertNull(p.getOpenInventory().getTopInventory());
    OpenStationManager.set(p,location);p.openInventory(server.createInventory(new AssemblyHolder(p),9));next(p);
    listener.onSessionStart(new CharacterSessionStartEvent(p,session(p)));assertNull(OpenStationManager.get(p));assertNull(p.getOpenInventory().getTopInventory());
    p.disconnect();OpenStationManager.set(p,location);assertNull(OpenStationManager.get(p));OpenStationManager.clear(p,newToken);OpenStationManager.clear(p);
  }

  @Test void quitClosesCurrentGearAndLostViewContextCannotInventAStation()throws Exception{
    var p=server.addPlayer();var location=station();OpenStationManager.set(p,location);var field=OpenStationManager.class.getDeclaredField("OPEN");field.setAccessible(true);((Map<?,?>)field.get(null)).remove(p.getUniqueId());assertNull(OpenStationManager.get(p));
    p.openInventory(server.createInventory(new AssemblyHolder(p),9));var listener=new MagicSessionListener(mock(MagicProfileService.class),Magic.plugin.getResonanceGuiManager());listener.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(p,"quit"));assertNull(p.getOpenInventory().getTopInventory());
    var occupied=seedRest(location,item(),null);p.disconnect();invoke(GearStationListener.class,"eject",new Class[]{org.bukkit.entity.Player.class,Location.class},p,location);assertSame(occupied,GearStationStore.get(location));
  }

  @Test void realRunRetainsOriginalOccupancyActorAndSessionAcrossReplacementAndSwitch()throws Exception{
    var p=server.addPlayer();var q=server.addPlayer();var location=station();var original=seed(location,item(),p.getUniqueId(),Map.of());var outgoing=session(p);var token=token(p);
    var run=new GearOrbSession(location,p.getUniqueId(),Map.of(),"fire",OrbCache.tier(1),outgoing);
    assertSame(original,run.occupancy());assertEquals(token,run.actor());assertTrue(run.matches(outgoing));assertFalse(run.matches(session(q)));assertTrue(run.isCurrent());
    seed(location,item(),q.getUniqueId(),Map.of());assertSame(original,run.occupancy());next(p);assertFalse(run.isCurrent());
    var wrong=new GearOrbSession(location,p.getUniqueId(),Map.of(),"fire",OrbCache.tier(1),session(q));assertFalse(wrong.isCurrent());
    var unknown=new GearOrbSession(location,p.getUniqueId(),Map.of(),"fire",OrbCache.tier(1),null);assertNull(unknown.actor());assertFalse(unknown.isCurrent());
  }

  @Test void publicOwnerlessAbortIsBoundToTheCurrentChargingActorAndStaffOverride()throws Exception{
    var p=server.addPlayer();var q=server.addPlayer();var location=station();var occupied=seedRest(location,item(),null);var actor=token(p);token(q);
    try(var orbs=mockStatic(GearOrbService.class)){
      assertTrue(GearEffects.authorize(q,location,occupied));orbs.when(()->GearOrbService.actor(location)).thenReturn(actor);
      assertFalse(GearEffects.authorize(q,location,occupied));assertTrue(GearEffects.authorize(p,location,occupied));
      q.addAttachment(Magic.plugin,"magic.admin",true);assertTrue(GearEffects.authorize(q,location,occupied));
      p.disconnect();assertFalse(GearEffects.authorize(p,occupied));assertFalse(GearEffects.abortReturn(p,location,occupied,Map.of()));
    }
  }
  @Test void restingUsesTheExactHeldFrameAndDoesNotRemoveItemsWhenPublicationFails()throws Exception{
    var p=server.addPlayer();var location=station();var held=item();var before=GearEffects.frame(p.getInventory().getContents());
    invoke(GearStationListener.class,"rest",new Class[]{org.bukkit.entity.Player.class,Location.class,org.bukkit.inventory.ItemStack.class},p,location,held);assertEquals(before,GearEffects.frame(p.getInventory().getContents()));
    p.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.DIAMOND));before=GearEffects.frame(p.getInventory().getContents());invoke(GearStationListener.class,"rest",new Class[]{org.bukkit.entity.Player.class,Location.class,org.bukkit.inventory.ItemStack.class},p,location,held);assertEquals(before,GearEffects.frame(p.getInventory().getContents()));
    p.getInventory().setItemInMainHand(held);
    try(var stores=mockStatic(GearStationStore.class,CALLS_REAL_METHODS)){
      stores.when(()->GearStationStore.deposit(eq(p),any(),eq(location),any(),any(),eq(true),any(),any())).thenReturn(null);
      invoke(GearStationListener.class,"rest",new Class[]{org.bukkit.entity.Player.class,Location.class,org.bukkit.inventory.ItemStack.class},p,location,held);assertEquals(held,p.getInventory().getItemInMainHand());
    }
    p.disconnect();invoke(GearStationListener.class,"rest",new Class[]{org.bukkit.entity.Player.class,Location.class,org.bukkit.inventory.ItemStack.class},p,location,held);
    var event=mock(org.bukkit.event.player.PlayerInteractEvent.class);when(event.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);when(event.getHand()).thenReturn(org.bukkit.inventory.EquipmentSlot.HAND);when(event.getClickedBlock()).thenReturn(location.getBlock());when(event.getPlayer()).thenReturn(p);new GearStationListener().onInteract(event);verify(event).setCancelled(true);assertTrue(GearStationStore.property().stations().isEmpty());
  }
}
