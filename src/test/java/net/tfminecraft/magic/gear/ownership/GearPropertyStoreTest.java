package net.tfminecraft.magic.gear.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.tfminecraft.tlibs.character.CharacterRef;
import net.tfminecraft.tlibs.character.estate.EstateDecision;
import net.tfminecraft.tlibs.character.estate.Recipient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GearPropertyStoreTest {
    @TempDir Path root;
    GearIdentity identity; GearPropertyStore store;
    final GearIdentity.Owner from = new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID()), to = new GearIdentity.Owner(UUID.randomUUID(), UUID.randomUUID());
    final UUID death = UUID.randomUUID();
    @BeforeEach void setup() { identity = mock(GearIdentity.class); when(identity.heirsReady()).thenReturn(true); when(identity.heir(from.account())).thenReturn(Optional.of(from)); store = new GearPropertyStore(root, identity); }
    String decision(GearIdentity.Owner recipient, long revision) {
        var testator = new CharacterRef(from.account(), from.character()); var heir = new CharacterRef(recipient.account(), recipient.character());
        return new EstateDecision(death, testator, Optional.empty(), Instant.EPOCH, Optional.of(heir), Optional.empty(), Optional.empty(), Recipient.FROZEN, Recipient.character(heir), Recipient.WAITING, Recipient.SENDER, revision).toString();
    }
    String waiting(long revision) {return decision(to,revision).replace("property=Character[ref="+new CharacterRef(to.account(),to.character())+"]", "property=Waiting[]");}
    Map<Object,Object> row(UUID id, int x) {
        var row = new LinkedHashMap<Object,Object>(); row.put("world","world");row.put("x",x);row.put("y",64);row.put("z",20);row.put("owner",from.account().toString()); row.put("occupancy-id",id.toString());row.put("ownership",GearPropertyStore.ownership(from));row.put("effect-state","active");row.put("item-bytes",GearPropertyStore.b64(new byte[]{1,2,3}));
        var future=new LinkedHashMap<>();future.put("kept",null);future.put("binary",new byte[]{4,5});future.put("integer",1); row.put("future",future);return row;
    }
    void stations(Map<Object,Object> stations)throws Exception { var document=new LinkedHashMap<Object,Object>();document.put("root-future",null);document.put("stations",stations);GearFiles.write(root.resolve("gear-stations.yml"),GearYaml.bytes(document),true); }
    Path journal(){return root.resolve("gear-ownership/estates").resolve(death+".json");}
    JsonObject plan()throws Exception{return GearPropertyStore.entries(GearPropertyStore.read(journal())).getFirst();}
    Map<String,byte[]> snapshot()throws Exception{var result=new TreeMap<String,byte[]>();try(var files=Files.walk(root)){for(Path file:files.filter(Files::isRegularFile).toList())result.put(root.relativize(file).toString(),Files.readAllBytes(file));}return result;}
    void unchanged(Map<String,byte[]> before)throws Exception{var after=snapshot();assertEquals(before.keySet(),after.keySet());for(String key:before.keySet())assertArrayEquals(before.get(key),after.get(key),key);}
    void exportNative(String kind)throws Exception {
        String export=System.getProperty("magic.fixture.export");if(export==null)return;
        Path destination=Path.of(export).resolve(kind);try(var files=Files.walk(root)){for(Path file:files.filter(Files::isRegularFile).toList()){Path output=destination.resolve(root.relativize(file));Files.createDirectories(output.getParent());Files.copy(file,output,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}}
    }

    @Test void migrationUsesReviewedFixedHeirBeforeWorldOrItemLoadingAndRetainsRawNullBinaryKeys()throws Exception {
        UUID id=UUID.randomUUID();var raw=row(id,10);raw.remove("ownership");raw.remove("effect-state");raw.remove("occupancy-id");
        var rows=new LinkedHashMap<Object,Object>();rows.put(0,raw);rows.put("future","opaque");stations(rows);
        when(identity.heirsReady()).thenReturn(false);store.migrate();assertFalse(store.stations().getFirst().raw().containsKey("ownership"));
        when(identity.heirsReady()).thenReturn(true);when(identity.heir(from.account())).thenReturn(Optional.empty());store.migrate();assertFalse(store.stations().getFirst().raw().containsKey("ownership"));
        when(identity.heir(from.account())).thenReturn(Optional.of(new GearIdentity.Owner(to.account(),to.character())));store.migrate();assertFalse(store.stations().getFirst().raw().containsKey("ownership"));
        when(identity.heir(from.account())).thenReturn(Optional.of(from));store.migrate();var moved=store.stations().getFirst();assertEquals(0,moved.key());assertEquals(from,GearPropertyStore.owner(moved.raw()));
        var original=GearYaml.copy(moved.raw());original.remove("ownership");original.remove("occupancy-id");original.remove("effect-state");assertTrue(GearYaml.equal(raw,original));
        when(identity.heir(from.account())).thenReturn(Optional.of(new GearIdentity.Owner(from.account(),to.character())));store.migrate();assertEquals(from,GearPropertyStore.owner(store.stations().getFirst().raw()));
        exportNative("migration");
        new GearPropertyStore(root,identity).migrate();assertEquals(from,GearPropertyStore.owner(store.stations().getFirst().raw()));
        rows.put(0,raw);stations(rows);var before=snapshot();assertThrows(GearPropertyStore.Conflict.class,store::migrate);unchanged(before);
    }
    @Test void ownerlessMalformedAndUnknownRowsRemainDistinctAndRaw()throws Exception{
        var rows=new LinkedHashMap<Object,Object>();
        for(int i=0;i<4;i++){var raw=row(UUID.randomUUID(),i);raw.remove("ownership");raw.remove("occupancy-id");raw.remove("effect-state");if(i==0)raw.remove("owner");if(i==1)raw.put("owner",null);if(i==2)raw.put("owner","");if(i==3)raw.put("owner","malformed");rows.put(i,raw);}
        rows.put("scalar",null);stations(rows);store.migrate();var stations=store.stations();assertEquals("OWNERLESS",((Map<?,?>)stations.get(0).raw().get("ownership")).get("kind"));assertEquals("OWNERLESS",((Map<?,?>)stations.get(1).raw().get("ownership")).get("kind"));assertEquals("HELD",((Map<?,?>)stations.get(2).raw().get("ownership")).get("kind"));assertEquals("HELD",((Map<?,?>)stations.get(3).raw().get("ownership")).get("kind"));
        var raw=GearYaml.read(Files.readAllBytes(root.resolve("gear-stations.yml")));assertTrue(raw.containsKey("root-future"));assertTrue(((Map<?,?>)raw.get("stations")).containsKey("scalar"));
    }
    @Test void stationAndReturnFanoutPreservesPropertyBytesAndCumulativeReceiptsAcrossRevisions()throws Exception{
        UUID station=UUID.randomUUID(),returned=UUID.randomUUID();store.insert(row(station,10));store.createReturn(from,returned,List.of("AQID"),"pending");
        var queue=store.returns(from).getFirst();var extended=queue.deepCopy();extended.add("future",GearJson.parse("{\"kept\":null,\"n\":1}"));store.saveReturn(queue,extended);
        var before=store.station(station).raw();var moved=store.transfer(death,decision(to,1),from,to,()->true);assertEquals(2,moved.size());assertEquals(to,GearPropertyStore.owner(store.station(station).raw()));assertEquals(to,GearPropertyStore.returnOwner(store.returns(to).getFirst()));assertTrue(store.returns(from).isEmpty());
        var raw=store.station(station).raw();raw.put("ownership",before.get("ownership"));assertTrue(GearYaml.equal(before,raw));assertEquals(moved,store.transfer(death,decision(to,1),from,to,()->true));assertEquals(moved,store.retained(death,waiting(2),from,()->true));
        exportNative("estates");
        UUID late=UUID.randomUUID();store.insert(row(late,11));assertEquals(3,store.transfer(death,decision(to,3),from,to,()->true).size());assertEquals(3,new GearPropertyStore(root,identity).retained(death,waiting(4),from,()->true).size());
    }
    @Test void restoredCompletedSourceAndReceiptInventoryCorruptionBlockAllRevisionsWithoutEffects()throws Exception{
        UUID id=UUID.randomUUID();store.insert(row(id,10));var original=store.station(id);store.transfer(death,decision(to,1),from,to,()->true);
        var inherited=store.station(id);store.patch(inherited,original.raw());var before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.retained(death,waiting(2),from,()->true));assertThrows(GearPropertyStore.Conflict.class,()->store.transfer(death,decision(to,2),from,to,()->true));unchanged(before);
        store.patch(store.station(id),inherited.raw());JsonObject journal=GearPropertyStore.read(journal());var entries=journal.get("entries").deepCopy();journal.add("entries",new com.google.gson.JsonArray());GearPropertyStore.write(journal(),journal);before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.retained(death,waiting(2),from,()->true));unchanged(before);
        journal.add("entries",entries);GearPropertyStore.write(journal(),journal);Path receipt;try(var files=Files.list(root.resolve("gear-ownership/receipts").resolve(death.toString()))){receipt=files.findFirst().orElseThrow();}byte[] receiptBytes=Files.readAllBytes(receipt);Files.delete(receipt);before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.retained(death,waiting(2),from,()->true));unchanged(before);GearFiles.write(receipt,receiptBytes,false);
    }
    @Test void fixedPartialFanoutBlocksNewerDecisionAndIncomingWritersUntilCapturedReplayFinishes()throws Exception{
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();store.insert(row(a,10));store.createReturn(from,b,List.of("AQ=="),"pending");var guard=new AtomicInteger();
        assertThrows(IOException.class,()->store.transfer(death,decision(to,1),from,to,()->guard.incrementAndGet()<=3));
        assertThrows(GearPropertyStore.Conflict.class,()->store.requireUnlocked(from));assertThrows(GearPropertyStore.Conflict.class,()->store.requireUnlocked(to));var before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.transfer(death,decision(to,2),from,to,()->true));assertThrows(GearPropertyStore.Conflict.class,()->store.retained(death,waiting(2),from,()->true));unchanged(before);
        assertEquals(2,store.transfer(death,decision(to,1),from,to,()->true).size());store.requireUnlocked(from);store.requireUnlocked(to);
    }
    @Test void untouchedPlanCanBeReplacedOnlyWhenItsSourceStillMatchesAndSelfRecipientCreatesNoReceipt()throws Exception{
        UUID id=UUID.randomUUID();store.insert(row(id,10));assertThrows(IOException.class,()->store.transfer(death,decision(to,1),from,to,()->false));
        var changed = new GearIdentity.Owner(from.account(),UUID.randomUUID());
        assertEquals(1,store.transfer(death,decision(changed,2),from,changed,()->true).size());
        UUID later=UUID.randomUUID();store.insert(row(later,11));assertEquals(1,store.transfer(death,decision(from,3),from,from,()->true).size());assertEquals(from,GearPropertyStore.owner(store.station(later).raw()));
    }
    @Test void historicalCurrentDecisionRefsRevisionAndAuthorityOnlyPayloadsAreValidatedBeforeMutation()throws Exception{
        UUID id=UUID.randomUUID();store.insert(row(id,10));store.transfer(death,decision(to,1),from,to,()->true);byte[] original=Files.readAllBytes(journal());
        for(String field:List.of("death","from-account","from-character","to-account","to-character","decision","revision","source","target","type","id","property","phase","selector","before-hash","after-hash")){
            JsonObject journal=GearPropertyStore.read(journal());JsonObject entry=GearPropertyStore.entries(journal).getFirst();entry.addProperty(field,"bad");GearPropertyStore.write(journal(),journal);var before=snapshot();assertThrows(IOException.class,()->store.retained(death,waiting(2),from,()->true),field);unchanged(before);GearFiles.write(journal(),original,true);
        }
        for(String field:List.of("item-bytes","world","future")){
            JsonObject journal=GearPropertyStore.read(journal());JsonObject entry=GearPropertyStore.entries(journal).getFirst();var after=GearYaml.read(GearPropertyStore.bytes(GearPropertyStore.text(entry,"after")));after.put(field,"hash-consistent mutation");byte[] bytes=GearYaml.bytes(after);entry.addProperty("after",GearPropertyStore.b64(bytes));entry.addProperty("after-hash",GearPropertyStore.hash(bytes));GearPropertyStore.write(journal(),journal);var before=snapshot();assertThrows(IOException.class,()->store.retained(death,waiting(2),from,()->true),field);unchanged(before);GearFiles.write(journal(),original,true);
        }
        JsonObject journal=GearPropertyStore.read(journal());JsonObject entry=GearPropertyStore.entries(journal).getFirst();var after=GearYaml.read(GearPropertyStore.bytes(GearPropertyStore.text(entry,"after")));var authority=new LinkedHashMap<Object,Object>((Map<?,?>)after.get("ownership"));var changed=new GearIdentity.Owner(to.account(),UUID.randomUUID());authority.put("character",changed.character().toString());after.put("ownership",authority);byte[] bytes=GearYaml.bytes(after);entry.addProperty("after",GearPropertyStore.b64(bytes));entry.addProperty("after-hash",GearPropertyStore.hash(bytes));entry.addProperty("to-character",changed.character().toString());GearPropertyStore.write(journal(),journal);var before=snapshot();assertThrows(IOException.class,()->store.retained(death,waiting(2),from,()->true));unchanged(before);
    }
    @Test void hashConsistentReturnMetadataNumericTypeMutationIsRejected()throws Exception{
        UUID id=UUID.randomUUID();store.createReturn(from,id,List.of("AQ=="),"pending");var before=store.returns(from).getFirst();var raw=before.deepCopy();raw.addProperty("future-number",1);store.saveReturn(before,raw);store.transfer(death,decision(to,1),from,to,()->true);
        JsonObject journal=GearPropertyStore.read(journal());JsonObject entry=GearPropertyStore.entries(journal).getFirst();String bytes=new String(GearPropertyStore.bytes(GearPropertyStore.text(entry,"after")),java.nio.charset.StandardCharsets.UTF_8).replace("\"future-number\":1","\"future-number\":1.0");entry.addProperty("after",GearPropertyStore.b64(bytes.getBytes()));entry.addProperty("after-hash",GearPropertyStore.hash(bytes.getBytes()));GearPropertyStore.write(journal(),journal);var snapshot=snapshot();assertThrows(IOException.class,()->store.retained(death,waiting(2),from,()->true));unchanged(snapshot);
    }
    @Test void completedEmptyAndRetainedRevisionsBindFullDecisionsAndRejectLaterHoldingsUntilNewRevision()throws Exception{
        assertTrue(store.transfer(death,decision(to,1),from,to,()->true).isEmpty());var completion=GearPropertyStore.read(journal()).getAsJsonObject("completions").getAsJsonObject("1");assertEquals("CHARACTER",GearPropertyStore.text(completion,"recipient-kind"));assertEquals(0,completion.getAsJsonArray("moved").size());
        UUID id=UUID.randomUUID();store.insert(row(id,10));var before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.transfer(death,decision(to,1),from,to,()->true));assertThrows(GearPropertyStore.Conflict.class,()->store.retained(death,waiting(1),from,()->true));unchanged(before);
        assertTrue(store.retained(death,waiting(2),from,()->true).isEmpty());assertEquals(1,GearPropertyStore.read(journal()).getAsJsonObject("completions").getAsJsonObject("2").getAsJsonArray("retained").size());before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.transfer(death,decision(to,2),from,to,()->true));unchanged(before);
        assertEquals(1,store.transfer(death,decision(to,3),from,to,()->true).size());assertEquals(1,store.retained(death,waiting(4),from,()->true).size());
        JsonObject journal=GearPropertyStore.read(journal());journal.getAsJsonObject("completions").remove("1");GearPropertyStore.write(journal(),journal);before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.retained(death,waiting(5),from,()->true));unchanged(before);
    }
    @Test void completionEnvelopeCountsRecipientsAndHistoricalCumulativeIdsAreIndependentAndImmutable()throws Exception{
        UUID id=UUID.randomUUID();store.insert(row(id,10));store.transfer(death,decision(to,1),from,to,()->true);byte[] original=Files.readAllBytes(journal());
        for(String field:List.of("format","kind","death","from-account","from-character","to-account","to-character","recipient-kind","revision","decision","moved","retained","counts")){
            JsonObject journal=GearPropertyStore.read(journal());var record=journal.getAsJsonObject("completions").getAsJsonObject("1");record.addProperty(field,"bad");GearPropertyStore.write(journal(),journal);var before=snapshot();assertThrows(IOException.class,()->store.retained(death,waiting(2),from,()->true),field);unchanged(before);GearFiles.write(journal(),original,true);
        }
        JsonObject journal=GearPropertyStore.read(journal());journal.getAsJsonObject("completions").getAsJsonObject("1").add("moved",new com.google.gson.JsonArray());GearPropertyStore.write(journal(),journal);var before=snapshot();assertThrows(IOException.class,()->store.retained(death,waiting(2),from,()->true));unchanged(before);
    }
    @Test void duplicateGlobalReturnIdsIncludingDeliveredOrHeldEvidenceBlockBeforeCreatingOrMovingAnyNewRow()throws Exception{
        UUID id=UUID.randomUUID();store.createReturn(from,id,List.of("AQ=="),"delivered");var row=store.returns(from).getFirst();var changed=row.deepCopy();GearPropertyStore.setReturnOwner(changed,to);GearFiles.write(store.returnFile(to,id),GearJson.bytes(changed),false);var before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.returns(from));assertThrows(GearPropertyStore.Conflict.class,()->store.createReturn(from,UUID.randomUUID(),List.of("AQ=="),"pending"));assertThrows(GearPropertyStore.Conflict.class,()->store.transfer(death,decision(to,1),from,to,()->true));unchanged(before);
    }
    @Test void aSourceIntroducedDuringReceiptPublicationCannotBeAbsorbedByTheSameIncompleteRevision()throws Exception{
        UUID first=UUID.randomUUID(),late=UUID.randomUUID();store.insert(row(first,10));var calls=new AtomicInteger();
        assertThrows(GearPropertyStore.Conflict.class,()->store.transfer(death,decision(to,1),from,to,()->{if(calls.incrementAndGet()==2)try{store.insert(row(late,11));}catch(IOException ex){throw new RuntimeException(ex);}return true;}));
        assertEquals(from,GearPropertyStore.owner(store.station(late).raw()));var before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.transfer(death,decision(to,1),from,to,()->true));unchanged(before);
        assertEquals(2,store.transfer(death,decision(to,2),from,to,()->true).size());
    }
    @Test void historicalCompletionDecisionMustMatchItsCapturedEntriesEvenWhenBothCompletionCopiesAreRewritten()throws Exception{
        UUID id=UUID.randomUUID();store.insert(row(id,10));store.transfer(death,decision(to,1),from,to,()->true);
        JsonObject journal=GearPropertyStore.read(journal());JsonObject completion=journal.getAsJsonObject("completions").getAsJsonObject("1");var foreign=new GearIdentity.Owner(to.account(),UUID.randomUUID());completion.addProperty("decision",decision(foreign,1));completion.addProperty("to-character",foreign.character().toString());GearPropertyStore.write(journal(),journal);GearPropertyStore.write(root.resolve("gear-ownership/completions").resolve(death.toString()).resolve("1.json"),completion);
        var before=snapshot();assertThrows(IOException.class,()->store.retained(death,waiting(2),from,()->true));unchanged(before);
    }
    @Test void completionPropertySyntaxAndExactCountSchemaRejectHashConsistentUnsupportedRecords()throws Exception{
        store.retained(death,waiting(1),from,()->true);byte[] original=Files.readAllBytes(journal());
        for(String property:List.of("unknown:thing","return:bad","station:world@"+UUID.randomUUID(),"station:,1,2,3@"+UUID.randomUUID(),"station:world,01,2,3@"+UUID.randomUUID(),"station:world,2147483648,2,3@"+UUID.randomUUID())){
            JsonObject journal=GearPropertyStore.read(journal());var completion=journal.getAsJsonObject("completions").getAsJsonObject("1");var retained=new com.google.gson.JsonArray();retained.add(property);completion.add("retained",retained);GearPropertyStore.write(journal(),journal);var before=snapshot();assertThrows(IOException.class,()->store.retained(death,waiting(2),from,()->true));unchanged(before);GearFiles.write(journal(),original,true);
        }
        JsonObject journal=GearPropertyStore.read(journal());journal.getAsJsonObject("completions").getAsJsonObject("1").getAsJsonObject("counts").addProperty("extra",0);GearPropertyStore.write(journal(),journal);var before=snapshot();assertThrows(IOException.class,()->store.retained(death,waiting(2),from,()->true));unchanged(before);
    }
    @Test void missingIndependentReceiptHoldsOrdinaryWritersBeforeChargeReleaseOrReturnDelivery()throws Exception{
        UUID id=UUID.randomUUID();store.insert(row(id,10));store.transfer(death,decision(to,1),from,to,()->true);store.requireUnlocked(to);
        try(var files=Files.list(root.resolve("gear-ownership/receipts").resolve(death.toString()))){Files.delete(files.findFirst().orElseThrow());}
        var before=snapshot();assertThrows(GearPropertyStore.Conflict.class,()->store.requireUnlocked(to));assertThrows(GearPropertyStore.Conflict.class,()->store.requireUnlocked(from));unchanged(before);
    }
    @Test void interruptedInventoryAndWorldEffectsAreHeldRatherThanRepeatedAndUnknownNullsRemain()throws Exception{
        for(String state:List.of("depositing","charging","releasing")){UUID id=UUID.randomUUID();var row=row(id,store.stations().size());row.put("effect-state",state);store.insert(row);}
        UUID returned=UUID.randomUUID();store.createReturn(from,returned,List.of("AQ=="),"delivering");store.holdInterrupted();for(var station:store.stations())assertEquals("held",station.raw().get("effect-state"));assertEquals("held",GearPropertyStore.text(store.returns(from).getFirst(),"state"));assertThrows(GearPropertyStore.Conflict.class,()->store.requireReady(from));new GearPropertyStore(root,identity).holdInterrupted();
    }
    @Test void rawAndTypedMalformedBoundariesRefuseWritesWhileKeepingOriginalFiles()throws Exception{
        for(String yaml:List.of("null","[]","broken","stations: [broken","stations: 4","stations: {}\nfuture: 1\nfuture: 2","stations: {}\nfuture: [*missing]")){
            GearFiles.write(root.resolve("gear-stations.yml"),yaml.getBytes(),true);byte[] before=Files.readAllBytes(root.resolve("gear-stations.yml"));assertThrows(IOException.class,store::stations);assertArrayEquals(before,Files.readAllBytes(root.resolve("gear-stations.yml")));
        }
        Files.delete(root.resolve("gear-stations.yml"));var raw=row(UUID.randomUUID(),10);store.insert(raw);assertTrue(store.occupied("world,10,64,20"));assertFalse(store.occupied("world,11,64,20"));assertThrows(IOException.class,()->store.insert(raw));var expected=store.stations().getFirst();var after=GearYaml.copy(raw);after.put("future",null);store.patch(expected,after);assertThrows(IOException.class,()->store.patch(expected,after));assertThrows(IOException.class,()->store.station(UUID.randomUUID()));
        for(Object value:Arrays.asList(null,"bad","",UUID.randomUUID(),"A"+UUID.randomUUID().toString().substring(1)))assertThrows(IOException.class,()->GearPropertyStore.uuid(value));
        for(Object value:Arrays.asList(null,1L,1.0,"1",true)){var row=GearYaml.copy(raw);row.put("x",value);assertThrows(IOException.class,()->GearPropertyStore.location(row));}
        for(Object value:Arrays.asList(null,""," ","with,comma",1)){var row=GearYaml.copy(raw);row.put("world",value);assertThrows(IOException.class,()->GearPropertyStore.location(row));}
        for(Object value:Arrays.asList(null,"",1,"unknown")){var row=GearYaml.copy(raw);row.put("effect-state",value);assertThrows(IOException.class,()->GearPropertyStore.state(row));}
        for(Object value:Arrays.asList(null,"bad",Map.of("format",2,"kind","CHARACTER"),Map.of("format",1,"kind","HELD"))){var row=GearYaml.copy(raw);row.put("ownership",value);assertThrows(IOException.class,()->GearPropertyStore.owner(row));}
        assertThrows(IOException.class,()->GearPropertyStore.bytes("!"));assertThrows(IOException.class,()->GearPropertyStore.text(new JsonObject(),"missing"));
    }
}
