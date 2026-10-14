package net.tfminecraft.magic.gear.ownership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GearFilesTest {
  @TempDir Path root;
  @Test void immutableCreatesRefuseRacesAndUnsupportedLinksWhileReplacementsRequireAtomicMoves()throws Exception{
    Path file=root.resolve("new/sub/item");byte[] rival="rival".getBytes();
    try(var disk=mockStatic(Files.class,CALLS_REAL_METHODS)){
      disk.when(()->Files.createLink(eq(file),any())).thenAnswer(call->{Files.write(file,rival);return call.callRealMethod();});
      assertThrows(FileAlreadyExistsException.class,()->GearFiles.write(file,"planned".getBytes(),false));
    }assertArrayEquals(rival,Files.readAllBytes(file));
    try(var disk=mockStatic(Files.class,CALLS_REAL_METHODS)){
      disk.when(()->Files.createLink(any(),any())).thenThrow(new UnsupportedOperationException("links unsupported"));assertThrows(IOException.class,()->GearFiles.write(root.resolve("unsupported"),rival,false));
      disk.when(()->Files.move(any(Path.class),any(Path.class),eq(StandardCopyOption.ATOMIC_MOVE),eq(StandardCopyOption.REPLACE_EXISTING))).thenThrow(new AtomicMoveNotSupportedException("a","b","unsupported"));assertThrows(IOException.class,()->GearFiles.write(file,"new".getBytes(),true));
    }assertArrayEquals(rival,Files.readAllBytes(file));GearFiles.delete(file);GearFiles.delete(file);
  }
  @Test void publicationForcesEveryDirectoryAncestorAndRawParserRefusesAmbiguousDocuments()throws Exception{
    Path file=root.resolve("new/owner/item.json");var observed=new ArrayList<Path>();
    try(var disk=mockStatic(GearFiles.class,CALLS_REAL_METHODS)){
      disk.when(()->GearFiles.force(any())).thenAnswer(call->{observed.add(call.getArgument(0));return call.callRealMethod();});GearFiles.write(file,"bytes".getBytes(),false);
      var expected=new ArrayList<Path>();for(Path p=file.toAbsolutePath().getParent();p!=null;p=p.getParent())expected.add(p);assertEquals(expected,observed);
    }
    for(String source:List.of("null","[]","broken","{\"a\":1,\"a\":2}","{\"a\":[]} {}"))assertThrows(IOException.class,()->GearJson.parse(source));
    assertTrue(GearJson.parse("{\"a\":[{\"kept\":null}]} ").getAsJsonArray("a").get(0).getAsJsonObject().get("kept").isJsonNull());
  }
  @Test void paperInventoryCheckpointRequestsSaveDataBeforeItsCallerCanAcknowledgeTheEffect(){var player=mock(org.bukkit.entity.Player.class);InventoryCheckpoint.save(player);verify(player).saveData();}
  @Test void itemCodecDelegatesExactPaperBytesWithoutChangingThePayload(){
    byte[] bytes={0,1,2,3};var item=mock(org.bukkit.inventory.ItemStack.class);
    when(item.serializeAsBytes()).thenReturn(bytes);assertArrayEquals(bytes,GearItems.bytes(item));verify(item).serializeAsBytes();
    try(var paper=mockStatic(org.bukkit.inventory.ItemStack.class)){
      paper.when(()->org.bukkit.inventory.ItemStack.deserializeBytes(bytes)).thenReturn(item);
      assertSame(item,GearItems.item(bytes));paper.verify(()->org.bukkit.inventory.ItemStack.deserializeBytes(bytes));
    }
  }
  @Test void missingMandatoryDigestAlgorithmFailsClosed() throws Exception {
    try(var algorithms=mockStatic(java.security.MessageDigest.class)){
      algorithms.when(()->java.security.MessageDigest.getInstance("SHA-256")).thenThrow(new java.security.NoSuchAlgorithmException("missing"));
      assertThrows(AssertionError.class,()->GearPropertyStore.hash(new byte[0]));
    }
  }
}
