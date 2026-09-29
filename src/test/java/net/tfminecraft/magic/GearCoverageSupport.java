package net.tfminecraft.magic;

import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.gear.orb.OrbCache;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

abstract class GearCoverageSupport {
  @TempDir Path temp;
  ServerMock server;
  Map<java.lang.reflect.Field, Object> orbConfig = new HashMap<>();

  @BeforeEach
  void setupGear() throws Exception {
    server = MockBukkit.mock();
    Magic.plugin = mock(Magic.class, RETURNS_DEEP_STUBS);
    when(Magic.plugin.getName()).thenReturn("Magic");
    when(Magic.plugin.namespace()).thenReturn("magic");
    when(Magic.plugin.getServer()).thenReturn(server);
    when(Magic.plugin.isEnabled()).thenReturn(true);
    when(Magic.plugin.getLogger()).thenReturn(Logger.getLogger("GearCoverage"));
    when(Magic.plugin.getDataFolder()).thenReturn(temp.toFile());
    for (var f : OrbCache.class.getFields())
      if (!java.lang.reflect.Modifier.isFinal(f.getModifiers())) orbConfig.put(f, f.get(null));
    ArchetypeRegistry.clear();
    PartRegistry.clear();
  }

  @AfterEach
  void cleanupGear() throws Exception {
    try {
      MockBukkit.unmock();
    } finally {
      Magic.plugin = null;
      ArchetypeRegistry.clear();
      PartRegistry.clear();
      for (var e : orbConfig.entrySet()) e.getKey().set(null, e.getValue());
    }
  }

  static ItemStack item() {
    var item = new ItemStack(Material.STICK);
    var meta = item.getItemMeta();
    meta.setDisplayName("Gear");
    item.setItemMeta(meta);
    return item;
  }

  static <T, Z> void tag(
      ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
    var meta = item.getItemMeta();
    if (value == null) meta.getPersistentDataContainer().remove(key);
    else meta.getPersistentDataContainer().set(key, type, value);
    item.setItemMeta(meta);
  }

  static Object invoke(Object target, String name, Class<?>[] params, Object... args)
      throws Exception {
    Class<?> type = target instanceof Class<?> c ? c : target.getClass();
    var method = type.getDeclaredMethod(name, params);
    method.setAccessible(true);
    return method.invoke(target instanceof Class<?> ? null : target, args);
  }

  static PartDef part(String id, int tier) {
    return GearDefinitionTest.part(id, tier, List.of(), Map.of("spell", 1));
  }
}
