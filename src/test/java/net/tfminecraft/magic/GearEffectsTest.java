package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.gui.*;
import net.tfminecraft.magic.gear.orb.*;
import net.tfminecraft.magic.gear.ownership.*;
import net.tfminecraft.tlibs.character.*;
import net.tfminecraft.tlibs.character.estate.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class GearEffectsTest extends GearStationCoverageSupport {
    GearIdentity.Token token(Player player){return GearStationStore.property().identity().current(player).orElseThrow();}
    GearPropertyStore spyStore()throws Exception{
        var store=spy(GearStationStore.property());setField("property",store);return store;
    }
    CharacterSession session(Player player){return characters.currentSession(player).orElseThrow();}
    void switchSession(Player player){var current=session(player);characters.sessions.put(player.getUniqueId(),new CharacterSession(current.ref(),current.sessionId()+1));}
    ItemStack[] withoutHand(Player player){var after=GearEffects.cloneContents(player.getInventory().getContents());after[player.getInventory().getHeldItemSlot()]=null;return after;}
    EstateDecision decision(CharacterRef from,CharacterRef to){return new EstateDecision(UUID.randomUUID(),from,Optional.empty(),Instant.EPOCH,Optional.of(to),Optional.empty(),Optional.empty(),Recipient.FROZEN,Recipient.character(to),Recipient.WAITING,Recipient.SENDER,1);}

    @Test void depositIntentPrecedesRemovalAndExactOutgoingCharacterPersistsAcrossViewClearAndRestart()throws Exception{
        var player=server.addPlayer();var token=token(player);var location=station();var weapon=item();player.getInventory().setItemInMainHand(weapon);
        var store=spyStore();doAnswer(call->{call.callRealMethod();assertEquals(weapon,player.getInventory().getItemInMainHand());var raw=(Map<?,?>)call.getArgument(0);assertEquals("depositing",raw.get("effect-state"));return null;}).when(store).insert(any());
        var occupancy=GearStationStore.deposit(player,token,location,weapon,Map.of(),true,withoutHand(player));assertNotNull(occupancy);assertTrue(player.getInventory().isEmpty());assertEquals(token.owner(),occupancy.characterOwner());
        checkpoint.verify(()->InventoryCheckpoint.save(player));var row=store.station(occupancy.generation()).raw();assertEquals("active",row.get("effect-state"));
        var extended=GearYaml.copy(row);extended.put("future",GearYaml.read("kept: null\nbinary: !!binary AQID\n".getBytes()));store.patch(store.station(occupancy.generation()),extended);GearStationStore.save();GearStationStore.clearAll();assertTrue(GearStationStore.isOccupied(location));GearStationStore.load();assertEquals(token.owner(),GearStationStore.get(location).characterOwner());assertTrue(((Map<?,?>)store.station(occupancy.generation()).raw().get("future")).containsKey("kept"));
    }
    @Test void staleDepositAndChangedInventoryCancelBeforeEffectsWhilePostEffectFailureIsHeld()throws Exception{
        var player=server.addPlayer();var first=token(player);var location=station();player.getInventory().setItemInMainHand(item());var before=GearEffects.frame(player.getInventory().getContents());
        switchSession(player);assertNull(GearStationStore.deposit(player,first,location,item(),Map.of(),true,withoutHand(player)));assertEquals(before,GearEffects.frame(player.getInventory().getContents()));
        var store=spyStore();var current=token(player);doAnswer(call->{call.callRealMethod();switchSession(player);return null;}).when(store).insert(any());
        assertNull(GearStationStore.deposit(player,current,location,item(),Map.of(),true,withoutHand(player)));assertEquals(before,GearEffects.frame(player.getInventory().getContents()));assertEquals("released",store.stations().getFirst().raw().get("effect-state"));
        doCallRealMethod().when(store).insert(any());checkpoint.when(()->InventoryCheckpoint.save(player)).thenThrow(new IllegalStateException("checkpoint interrupted"));
        assertNull(GearStationStore.deposit(player,token(player),location,item(),Map.of(),true,withoutHand(player)));assertTrue(player.getInventory().isEmpty());assertEquals("held",store.stations().getLast().raw().get("effect-state"));assertTrue(GearStationStore.isOccupied(location));GearStationStore.load();assertNull(GearStationStore.get(location));assertEquals("held",store.stations().getLast().raw().get("effect-state"));
    }
    @Test void itemCheckerMutationCannotOverwriteInterveningInventoryUsingAStalePlannedFrame()throws Exception{
        var player=server.addPlayer();var token=token(player);var location=station();player.getInventory().setItem(0,new ItemStack(Material.STONE,2));
        var before=GearEffects.frame(player.getInventory().getContents());
        var api=mock(net.tfminecraft.tlibs.objects.api.ItemAPI.class,RETURNS_DEEP_STUBS);libs.when(net.tfminecraft.tlibs.TLibs::getItemAPI).thenReturn(api);
        when(api.getChecker().checkItemWithPath(any(),any())).thenAnswer(call->{player.getInventory().setItem(2,new ItemStack(Material.DIAMOND));return true;});
        var part=new PartDef("paid","Paid","core",1,Set.of(GearType.STAFF),"v.STICK",Map.of("v.STONE",2),List.of(),Map.of(),Map.of(),List.of(),null,1,false);
        var after=GearCosts.plan(player,List.of(part));assertNotNull(after);assertNull(after[2]);
        assertNull(GearStationStore.deposit(player,token,location,item(),Map.of("v.STONE",2),false,after,before));assertEquals(2,player.getInventory().getItem(0).getAmount());assertEquals(Material.DIAMOND,player.getInventory().getItem(2).getType());assertTrue(GearStationStore.property().stations().isEmpty());
    }
    @Test void depositValidationAndStorageFailureLeaveTheInventoryIntact()throws Exception{
        var player=server.addPlayer();var token=token(player);var location=station();player.getInventory().setItemInMainHand(item());var before=GearEffects.frame(player.getInventory().getContents());
        assertNull(GearStationStore.deposit(player,token,null,item(),null,false,withoutHand(player)));assertNull(GearStationStore.deposit(player,token,new Location(null,1,2,3),item(),null,false,withoutHand(player)));assertNull(GearStationStore.deposit(player,token,location,null,null,false,withoutHand(player)));
        var store=spyStore();doThrow(new IOException("disk unavailable")).when(store).insert(any());assertNull(GearStationStore.deposit(player,token,location,item(),null,false,withoutHand(player)));assertEquals(before,GearEffects.frame(player.getInventory().getContents()));
        doAnswer(call->{call.callRealMethod();player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND));return null;}).when(store).insert(any());assertNull(GearStationStore.deposit(player,token,location,item(),null,false,withoutHand(player)));assertEquals(Material.DIAMOND,player.getInventory().getItemInMainHand().getType());
    }
    @Test void malformedPartialOrOverflowingPaidMapsCannotDiscardTheFreshWeaponForAnIncompleteRefund()throws Exception{
        var player=server.addPlayer();token(player);var location=station();var occupancy=seed(location,item(),player.getUniqueId(),Map.of("v.stone",2));
        var store=GearStationStore.property();var original=store.station(occupancy.generation());
        List<Object> malformed=List.of("bad",Map.of("amount",2),Map.of("path"," ","amount",2),Map.of("path","v.stone","amount","2"),Map.of("path","v.stone","amount",0),Map.of("path","v.stone","amount",-1));
        for(Object invalid:malformed){var changed=GearYaml.copy(original.raw());var costs=new LinkedHashMap<Object,Object>();costs.put("valid",Map.of("path","v.stone","amount",2));costs.put("invalid",invalid);changed.put("charged",costs);store.patch(store.station(occupancy.generation()),changed);GearStationStore.shutdown();GearStationStore.load();var loaded=GearStationStore.get(location);assertNull(loaded.getCharged());assertFalse(GearEffects.abortReturn(player,location,loaded,loaded.getCharged()));assertTrue(player.getInventory().isEmpty());assertTrue(GearStationStore.isOccupied(location));}
        var changed=GearYaml.copy(original.raw());changed.put("charged",Map.of("0",Map.of("path","v.stone","amount",Integer.MAX_VALUE),"1",Map.of("path","v.stone","amount",1)));store.patch(store.station(occupancy.generation()),changed);GearStationStore.shutdown();GearStationStore.load();assertNull(GearStationStore.get(location).getCharged());assertFalse(GearEffects.abortReturn(player,location,GearStationStore.get(location),null));assertTrue(GearStationStore.isOccupied(location));
    }
    @Test void freeSlotDeliveryRetainsRemainderAndNeverFollowsAnIncomingSession()throws Exception{
        var player=server.addPlayer();var token=token(player);var store=GearStationStore.property();UUID op=UUID.randomUUID();var item=new ItemStack(Material.DIAMOND,65);store.createReturn(token.owner(),op,List.of(GearPropertyStore.b64(GearItems.bytes(item))),"pending");
        for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));GearEffects.deliver(player,token);assertEquals("pending",GearPropertyStore.text(store.returns(token.owner()).getFirst(),"state"));assertEquals(65,GearItems.item(GearPropertyStore.bytes(store.returns(token.owner()).getFirst().getAsJsonArray("items").get(0).getAsString())).getAmount());
        player.getInventory().setItem(1,null);GearEffects.deliver(player,token);assertEquals(64,player.getInventory().getItem(1).getAmount());assertEquals("pending",GearPropertyStore.text(store.returns(token.owner()).getFirst(),"state"));
        switchSession(player);player.getInventory().setItem(2,null);GearEffects.deliver(player,token);assertNull(player.getInventory().getItem(2));GearEffects.deliver(player,token(player));assertEquals(1,player.getInventory().getItem(2).getAmount());assertEquals("delivered",GearPropertyStore.text(store.returns(token.owner()).getFirst(),"state"));
    }
    @Test void deliveryEvidencePrecedesInventoryAndInterruptedAcknowledgmentIsHeldWithoutRedelivery()throws Exception{
        var player=server.addPlayer();var token=token(player);var store=spyStore();UUID op=UUID.randomUUID();store.createReturn(token.owner(),op,List.of(GearPropertyStore.b64(GearItems.bytes(new ItemStack(Material.DIAMOND,2)))),"pending");
        doAnswer(call->{call.callRealMethod();JsonObject after=call.getArgument(1);if("delivering".equals(after.get("state").getAsString()))assertTrue(player.getInventory().isEmpty());return null;}).when(store).saveReturn(any(),any());checkpoint.when(()->InventoryCheckpoint.save(player)).thenThrow(new IllegalStateException("interrupted"));
        GearEffects.deliver(player,token);assertEquals(2,player.getInventory().getItem(0).getAmount());assertEquals("delivering",GearPropertyStore.text(store.returns(token.owner()).getFirst(),"state"));store.holdInterrupted();GearEffects.deliver(player,token);assertEquals(2,player.getInventory().getItem(0).getAmount());var held=store.returns(token.owner()).getFirst();assertEquals("held",GearPropertyStore.text(held,"state"));assertTrue(held.has("inventory-before"));assertTrue(held.has("inventory-after"));
    }
    @Test void deliverySwitchBetweenIntentAndEffectCancelsWithoutGivingAnythingToTheIncomingSession()throws Exception{
        var player=server.addPlayer();var token=token(player);var store=spyStore();UUID op=UUID.randomUUID();store.createReturn(token.owner(),op,List.of(GearPropertyStore.b64(GearItems.bytes(item()))),"pending");
        doAnswer(call->{call.callRealMethod();JsonObject after=call.getArgument(1);if("delivering".equals(after.get("state").getAsString()))switchSession(player);return null;}).when(store).saveReturn(any(),any());GearEffects.deliver(player,token);assertTrue(player.getInventory().isEmpty());assertEquals("pending",GearPropertyStore.text(store.returns(token.owner()).getFirst(),"state"));
    }
    @Test void publicChargingWaitsByDepositedOwnerAndOutgoingPaidRunSettlesItsCapturedTarget()throws Exception{
        var owner=server.addPlayer();var actor=server.addPlayer();var heir=server.addPlayer();var ownerToken=token(owner);var actorToken=token(actor);token(heir);var location=station();var occupancy=seed(location,item(),owner.getUniqueId(),Map.of("v.stone",2));var hand=new ItemStack(Material.PAPER,2);actor.getInventory().setItemInMainHand(hand);
        try(var sessions=mockConstruction(GearOrbSession.class,(run,context)->{
            Location target=(Location)context.arguments().get(0);CharacterSession identity=(CharacterSession)context.arguments().get(5);when(run.getStation()).thenReturn(target);when(run.getPlayerId()).thenReturn(actor.getUniqueId());when(run.occupancy()).thenReturn(occupancy);when(run.matches(any())).thenAnswer(call->identity.equals(call.getArgument(0)));when(run.isCurrent()).thenAnswer(call->Characters.isCurrent(identity));when(run.capturedFill()).thenReturn(Map.of("fire",3d));when(run.displayPoint()).thenReturn(location);
        });var estates=mockStatic(Estates.class)){
            assertTrue(GearEffects.charge(actor,actorToken,location,hand,Map.of("fire",5d),"fire",1));assertEquals(1,actor.getInventory().getItemInMainHand().getAmount());assertTrue(GearOrbService.touches(ownerToken.owner()));assertFalse(GearOrbService.touches(actorToken.owner()));
            estates.when(()->Estates.guardHolds(any())).thenReturn(true);var decision=decision(characters.ref(owner.getUniqueId()),characters.ref(heir.getUniqueId()));var participant=new CharacterGearEstate(Magic.plugin);assertEquals(Outcome.Kind.RETRY,participant.settle(decision).kind());assertEquals(ownerToken.owner(),GearPropertyStore.owner(GearStationStore.property().station(occupancy.generation()).raw()));
            var outgoing=session(actor);switchSession(actor);GearOrbService.finishForSession(actor.getUniqueId(),outgoing);assertFalse(GearOrbService.touches(ownerToken.owner()));assertEquals(3,WeaponRequirement.fromItem(GearStationStore.get(location).getItem()).aura().getFill("fire"));assertEquals(Outcome.Kind.DONE,participant.settle(decision).kind());assertEquals(token(heir).owner(),GearStationStore.get(location).characterOwner());
        }finally{GearOrbService.stop();}
    }
    @Test void oldOrbCompletionCannotTouchAReplacedOccupancyAndOldMenuCannotCommitForANewSession()throws Exception{
        var player=server.addPlayer();var location=station();var oldOccupancy=seed(location,item(),player.getUniqueId(),Map.of());var token=token(player);var hand=new ItemStack(Material.PAPER,1);player.getInventory().setItemInMainHand(hand);
        try(var runs=mockConstruction(GearOrbSession.class,(run,context)->{when(run.getStation()).thenReturn(location);when(run.getPlayerId()).thenReturn(player.getUniqueId());when(run.occupancy()).thenReturn(oldOccupancy);when(run.displayPoint()).thenReturn(location);})){
            assertTrue(GearEffects.charge(player,token,location,hand,Map.of("fire",5d),"fire",1));var run=runs.constructed().getFirst();var replacement=seed(location,new ItemStack(Material.BLAZE_ROD),player.getUniqueId(),Map.of());var bytes=GearYaml.bytes(GearStationStore.property().station(replacement.generation()).raw());invoke(GearOrbService.class,"complete",new Class[]{GearOrbSession.class,boolean.class},run,false);assertArrayEquals(bytes,GearYaml.bytes(GearStationStore.property().station(replacement.generation()).raw()));
            OpenStationManager.set(player,location);var oldHolder=new AssemblyHolder(player);switchSession(player);OpenStationManager.set(player,location);var newHolder=new AssemblyHolder(player);assertFalse(oldHolder.current(player));assertTrue(newHolder.current(player));OpenStationManager.clear(player,token);assertEquals(location,OpenStationManager.get(player));
        }finally{GearOrbService.stop();}
    }
    @Test void aSwitchDuringTheInventoryCheckpointCannotAttachThePaidChargeToAnIncomingRun()throws Exception{
        var player=server.addPlayer();var location=station();var occupancy=seed(location,item(),player.getUniqueId(),Map.of());var token=token(player);var hand=new ItemStack(Material.PAPER,2);player.getInventory().setItemInMainHand(hand);
        checkpoint.when(()->InventoryCheckpoint.save(player)).thenAnswer(call->{switchSession(player);return null;});
        assertFalse(GearEffects.charge(player,token,location,hand,Map.of("fire",5d),"fire",1));assertFalse(GearOrbService.isActive(location));assertEquals(1,player.getInventory().getItemInMainHand().getAmount());assertEquals("held",GearStationStore.property().station(occupancy.generation()).raw().get("effect-state"));
    }
    @Test void failedOrStaleChargeHoldsPaidInputsAndNeverMutatesAnIncomingOrReplacedStation()throws Exception{
        var player=server.addPlayer();var location=station();var occupancy=seed(location,item(),player.getUniqueId(),Map.of());var token=token(player);var hand=new ItemStack(Material.PAPER,2);player.getInventory().setItemInMainHand(hand);
        try(var orbs=mockStatic(GearOrbService.class)){
            assertFalse(GearEffects.charge(player,token,location,hand,Map.of("fire",1d),"fire",1));assertEquals(1,player.getInventory().getItemInMainHand().getAmount());assertEquals("held",GearStationStore.property().station(occupancy.generation()).raw().get("effect-state"));assertNull(GearStationStore.eject(location));
            var other=seed(location,item(),player.getUniqueId(),Map.of());var store=spyStore();doAnswer(call->{call.callRealMethod();switchSession(player);return null;}).when(store).patch(any(),any());assertFalse(GearEffects.charge(player,token,location,player.getInventory().getItemInMainHand(),Map.of("fire",1d),"fire",1));assertEquals(1,player.getInventory().getItemInMainHand().getAmount());assertEquals("active",store.station(other.generation()).raw().get("effect-state"));
        }
    }
    @Test void chargeGuardsInventoryGenerationAndBusyStateBeforeSpendingAndHoldsCheckpointFailure()throws Exception{
        var p=server.addPlayer();var token=token(p);var location=station();var hand=new ItemStack(Material.PAPER);p.getInventory().setItemInMainHand(hand);
        assertFalse(GearEffects.charge(p,token,location,hand,Map.of(),"fire",1));
        var occupancy=seed(location,item(),null,Map.of());assertFalse(GearEffects.charge(p,token,location,new ItemStack(Material.DIAMOND),Map.of(),"fire",1));
        var store=spyStore();var expected=store.station(occupancy.generation());var busy=GearYaml.copy(expected.raw());busy.put("effect-state","charging");store.patch(expected,busy);
        assertFalse(GearEffects.charge(p,token,location,hand,Map.of(),"fire",1));assertEquals(hand,p.getInventory().getItemInMainHand());
        occupancy=seed(location,item(),null,Map.of());UUID id=occupancy.generation();
        doAnswer(call->{call.callRealMethod();Map<?,?> row=call.getArgument(1);if("charging".equals(row.get("effect-state")))p.getInventory().setItem(3,new ItemStack(Material.DIAMOND));return null;}).when(store).patch(any(),any());
        assertFalse(GearEffects.charge(p,token,location,hand,Map.of(),"fire",1));assertEquals(hand,p.getInventory().getItemInMainHand());assertEquals(Material.DIAMOND,p.getInventory().getItem(3).getType());assertEquals("active",store.station(id).raw().get("effect-state"));
        doCallRealMethod().when(store).patch(any(),any());checkpoint.when(()->InventoryCheckpoint.save(p)).thenThrow(new IllegalStateException("save failed"));
        assertFalse(GearEffects.charge(p,token,location,hand,Map.of(),"fire",1));assertTrue(p.getInventory().getItemInMainHand().getType().isAir());assertEquals("held",store.station(id).raw().get("effect-state"));
        GearEffects.hold(id);GearEffects.hold(UUID.randomUUID());
    }
    @Test void abortRefundRefusesUnbuildableCostsAndLateSessionChangesAndRetainsHeldEvidenceOnFailure()throws Exception{
        var p=server.addPlayer();var other=server.addPlayer();var token=token(p);token(other);var location=station();var occupancy=seed(location,item(),other.getUniqueId(),Map.of());
        assertFalse(GearEffects.authorize(p,occupancy));assertFalse(GearEffects.abortReturn(p,location,occupancy,Map.of()));
        occupancy=seed(location,item(),p.getUniqueId(),Map.of("v.DIAMOND",65));
        try(var refs=mockStatic(net.tfminecraft.magic.util.ItemRef.class)){
            assertFalse(GearEffects.abortReturn(p,location,occupancy,Map.of("bad",2)));refs.when(()->net.tfminecraft.magic.util.ItemRef.build("air")).thenReturn(new ItemStack(Material.AIR));assertFalse(GearEffects.abortReturn(p,location,occupancy,Map.of("air",2)));
            refs.when(()->net.tfminecraft.magic.util.ItemRef.build("switch")).thenAnswer(call->{switchSession(p);return new ItemStack(Material.DIAMOND);});assertFalse(GearEffects.abortReturn(p,location,occupancy,Map.of("switch",2)));assertTrue(GearStationStore.isOccupied(location));
            refs.when(()->net.tfminecraft.magic.util.ItemRef.build("v.DIAMOND")).thenReturn(new ItemStack(Material.DIAMOND));
            var store=spyStore();doThrow(new IOException("return publish failed")).when(store).createReturn(any(),any(),any(),anyString());assertFalse(GearEffects.abortReturn(p,location,occupancy,Map.of("v.DIAMOND",65)));assertEquals("releasing",store.station(occupancy.generation()).raw().get("effect-state"));assertTrue(p.getInventory().isEmpty());
        }
    }
    @Test void returnPlanningDoesNotOverwriteInterveningItemsAndKeepsUndecodableBytes()throws Exception{
        var p=server.addPlayer();var token=token(p);var store=spyStore();var op=UUID.randomUUID();store.createReturn(token.owner(),op,List.of(GearPropertyStore.b64(GearItems.bytes(new ItemStack(Material.DIAMOND,2)))),"pending");
        p.getInventory().setItem(0,new ItemStack(Material.AIR));doAnswer(call->{call.callRealMethod();JsonObject row=call.getArgument(1);if("delivering".equals(row.get("state").getAsString()))p.getInventory().setItem(3,new ItemStack(Material.EMERALD));return null;}).when(store).saveReturn(any(),any());
        GearEffects.deliver(p,token);assertEquals(Material.EMERALD,p.getInventory().getItem(3).getType());assertTrue(p.getInventory().getItem(0)==null||p.getInventory().getItem(0).getType().isAir());assertEquals("pending",GearPropertyStore.text(store.returns(token.owner()).getFirst(),"state"));
        doThrow(new IOException("intent failed")).when(store).saveReturn(any(),any());var before=GearEffects.frame(p.getInventory().getContents());GearEffects.deliver(p,token);assertEquals(before,GearEffects.frame(p.getInventory().getContents()));
        doCallRealMethod().when(store).saveReturn(any(),any());var bad=UUID.randomUUID();store.createReturn(token.owner(),bad,List.of(GearPropertyStore.b64("unloadable".getBytes())),"pending");var raw=Files.readAllBytes(store.returnFile(token.owner(),bad));GearEffects.deliver(p,token);assertArrayEquals(raw,Files.readAllBytes(store.returnFile(token.owner(),bad)));
        assertNull(GearEffects.frame(new ItemStack[]{new ItemStack(Material.AIR)}).getFirst());
    }
    @Test void refundItemCallbackCannotReleaseAReplacementStationUsingAnOldOccupancy()throws Exception{
        var p=server.addPlayer();var token=token(p);var location=station();var old=seed(location,item(),p.getUniqueId(),Map.of("replace",2));var replaced=new ArrayList<GearStationStore.Occupancy>();
        try(var refs=mockStatic(net.tfminecraft.magic.util.ItemRef.class)){
            refs.when(()->net.tfminecraft.magic.util.ItemRef.build("replace")).thenAnswer(call->{replaced.add(seed(location,new ItemStack(Material.BLAZE_ROD),p.getUniqueId(),Map.of()));return new ItemStack(Material.DIAMOND);});
            assertFalse(GearEffects.abortReturn(p,location,old,old.getCharged()));
            assertSame(replaced.getFirst(),GearStationStore.get(location));assertEquals("active",GearStationStore.property().station(replaced.getFirst().generation()).raw().get("effect-state"));assertTrue(GearStationStore.property().returns(token.owner()).isEmpty());assertTrue(p.getInventory().isEmpty());
        }
    }
    @Test void depositRechecksAfterUnlockBeforeIntentAndRecordsExactPositivePaidCosts()throws Exception{
        var p=server.addPlayer();var location=station();p.getInventory().setItemInMainHand(new ItemStack(Material.STONE,2));var store=spyStore();var token=token(p);var after=withoutHand(p);
        doAnswer(call->{call.callRealMethod();switchSession(p);return null;}).when(store).requireUnlocked(any());assertNull(GearStationStore.deposit(p,token,location,item(),Map.of("v.STONE",2),false,after));assertTrue(store.stations().isEmpty());assertEquals(2,p.getInventory().getItemInMainHand().getAmount());
        doAnswer(call->{call.callRealMethod();p.getInventory().setItem(3,new ItemStack(Material.DIAMOND));return null;}).when(store).requireUnlocked(any());assertNull(GearStationStore.deposit(p,token(p),location,item(),Map.of("v.STONE",2),false,after));assertTrue(store.stations().isEmpty());assertEquals(Material.DIAMOND,p.getInventory().getItem(3).getType());
        doCallRealMethod().when(store).requireUnlocked(any());after=withoutHand(p);var occupied=GearStationStore.deposit(p,token(p),location,item(),Map.of("v.STONE",2),false,after);assertNotNull(occupied);assertEquals(Map.of("v.STONE",2),occupied.getCharged());assertEquals(Material.DIAMOND,p.getInventory().getItem(3).getType());var stale=token(p);switchSession(p);assertFalse(GearEffects.charge(p,stale,location,new ItemStack(Material.PAPER),Map.of(),"fire",1));
    }
    @Test void failedReleaseAcknowledgmentKeepsReturnHeldAndDoesNotDeliver()throws Exception{
        var p=server.addPlayer();var token=token(p);var location=station();var occupancy=seedRest(location,item(),p.getUniqueId());
        try(var stations=mockStatic(GearStationStore.class,CALLS_REAL_METHODS)){
            stations.when(()->GearStationStore.acknowledgeRelease(location,occupancy.generation())).thenReturn(false);assertFalse(GearEffects.abortReturn(p,location,occupancy,Map.of()));assertTrue(p.getInventory().isEmpty());assertEquals("held",GearPropertyStore.text(GearStationStore.property().returns(token.owner()).getFirst(),"state"));
        }
    }
    @Test void switchAfterFirstReturnAcknowledgmentLeavesTheNextOperationForItsCapturedOwner()throws Exception{
        var p=server.addPlayer();var token=token(p);var store=spyStore();var first=new UUID(0,1);var second=new UUID(0,2);var bytes=GearPropertyStore.b64(GearItems.bytes(new ItemStack(Material.DIAMOND)));
        store.createReturn(token.owner(),first,List.of(bytes),"pending");store.createReturn(token.owner(),second,List.of(bytes),"pending");
        doAnswer(call->{call.callRealMethod();JsonObject after=call.getArgument(1);if("delivered".equals(after.get("state").getAsString()))switchSession(p);return null;}).when(store).saveReturn(any(),any());
        GearEffects.deliver(p,token);assertEquals(1,p.getInventory().getItem(0).getAmount());assertNull(p.getInventory().getItem(1));assertEquals("pending",GearPropertyStore.text(GearPropertyStore.read(store.returnFile(token.owner(),second)),"state"));
    }
    @Test void airStorageSlotsAreCompatibleWithDeliveryAndRetainTheUnplacedRemainder()throws Exception{
        var original=server.addPlayer();var token=token(original);var store=spyStore();var p=mock(Player.class);var inventory=mock(org.bukkit.inventory.PlayerInventory.class);when(p.getInventory()).thenReturn(inventory);var bridge=mock(GearIdentity.class);when(store.identity()).thenReturn(bridge);when(bridge.current(token,p)).thenReturn(true);
        ItemStack[][] live={new ItemStack[]{new ItemStack(Material.AIR)}};when(inventory.getStorageContents()).thenAnswer(call->live[0]);doAnswer(call->{live[0]=call.getArgument(0);return null;}).when(inventory).setStorageContents(any());
        store.createReturn(token.owner(),UUID.randomUUID(),List.of(GearPropertyStore.b64(GearItems.bytes(new ItemStack(Material.DIAMOND,65)))),"pending");GearEffects.deliver(p,token);assertEquals(64,live[0][0].getAmount());var row=store.returns(token.owner()).getFirst();assertEquals("pending",GearPropertyStore.text(row,"state"));assertEquals(1,GearItems.item(GearPropertyStore.bytes(row.getAsJsonArray("items").get(0).getAsString())).getAmount());
    }
}
