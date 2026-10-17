package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.gear.ownership.GearItems;
import net.tfminecraft.tlibs.character.CharacterSession;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Paper can serialize a freshly built item differently from the copy it reads back (the same
 * components in another order). The station cache must hold the re-read copy, or every charge
 * and abort is refused as "mid-attunement" until the next restart.
 */
class GearStationRereadBytesTest extends GearStationCoverageSupport {

  @BeforeEach
  void rereadCopiesSerializeDifferently() {
    codec
        .when(() -> GearItems.item(any()))
        .thenAnswer(
            call -> {
              var stored = snapshots.get(new String((byte[]) call.getArgument(0), StandardCharsets.UTF_8));
              if (stored == null) throw new IllegalArgumentException("Unknown fixture bytes");
              var copy = stored.clone();
              var meta = copy.getItemMeta();
              if (meta.getLore() == null) meta.setLore(List.of("re-read"));
              copy.setItemMeta(meta);
              return copy;
            });
  }

  @Test
  void freshDepositAndChargeResultStayUsableBeforeARestart() throws Exception {
    var location = station();
    var player = server.addPlayer();
    var account = player.getUniqueId();
    characters.sessions.put(account, new CharacterSession(characters.ref(account), 1));
    var fresh = item();
    assertFalse(java.util.Arrays.equals(GearItems.bytes(fresh), GearItems.bytes(GearItems.item(GearItems.bytes(fresh)))));

    var placed = GearStationStore.occupy(location, fresh, account, Map.of());
    assertNotNull(placed);
    assertDoesNotThrow(() -> GearStationStore.expected(location, placed));
    assertEquals(List.of("re-read"), placed.getItem().getItemMeta().getLore());

    assertTrue(GearStationStore.update(location, placed, attuned()));
    assertTrue(GearStationStore.isAttuned(placed.getItem()));
    assertDoesNotThrow(() -> GearStationStore.expected(location, placed));

    ItemStack released = GearStationStore.takeForAbort(location);
    assertNotNull(released);
    assertTrue(GearStationStore.acknowledgeRelease(location, placed.generation()));
  }
}
