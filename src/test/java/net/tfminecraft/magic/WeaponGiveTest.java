package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.tfminecraft.magic.command.*;
import net.tfminecraft.magic.gear.*;
import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.model.ElementVisibility;
import net.tfminecraft.magic.registry.ElementRegistry;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class WeaponGiveTest {
    ServerMock server;
    PlayerMock target;
    CommandSender sender;
    MagicCommand command = new MagicCommand();
    String[] valid = {"weapon", "give", "Mage", "staff", "fire", "10", "core"};

    @BeforeEach void setup() {
        server = MockBukkit.mock(); target = server.addPlayer("Mage");
        Magic.plugin = mock(Magic.class); when(Magic.plugin.namespace()).thenReturn("magic");
        sender = mock(CommandSender.class);
        Cache.givePermission = "custom.staff";
        when(sender.hasPermission("custom.staff")).thenReturn(true);
        ArchetypeRegistry.clear(); PartRegistry.clear(); ElementRegistry.clear(); TierBands.clear();
        ArchetypeRegistry.register(new ArchetypeDef(GearType.STAFF, "Staff", "v.STICK", "v.STICK", false, List.of("core"), Map.of()));
        PartRegistry.register(part("core", "core", false, GearType.STAFF));
        ElementRegistry.register(DomainTest.element("fire")); TierBands.register("fire", 1, 5);
    }
    PartDef part(String id, String category, boolean disabled, GearType type) {
        return new PartDef(id, id, category, 1, Set.of(type), "v.STICK", Map.of(), List.of(), Map.of(), Map.of(), List.of(), "", 1, disabled);
    }
    @AfterEach void cleanup() {
        Cache.givePermission = "magic.weapon.give";
        ArchetypeRegistry.clear(); PartRegistry.clear(); ElementRegistry.clear(); TierBands.clear();
        Magic.plugin = null; MockBukkit.unmock();
    }
    void run(String... args) { assertTrue(command.onCommand(sender, null, "magic", args)); }
    void reject(int index, String value, String message) {
        clearInvocations(sender); String[] args=valid.clone(); args[index]=value; run(args);
        verify(sender).sendMessage(contains(message)); assertEquals(-1, target.getInventory().first(Material.STICK));
    }
    @Test void validatesBeforeGivingAndPermissionDoesNotRequireAdmin() throws Exception {
        var ctor=WeaponGiveCommand.class.getDeclaredConstructor(); ctor.setAccessible(true); ctor.newInstance();
        Cache.givePermission=""; run(valid); assertTrue(command.onTabComplete(sender,null,"magic",valid).isEmpty());
        Cache.givePermission="custom.staff"; when(sender.hasPermission("custom.staff")).thenReturn(false); run(valid);
        when(sender.hasPermission("custom.staff")).thenReturn(true);
        run("weapon"); run("weapon","bad","Mage","staff","fire","10","core");
        reject(2,"Offline","online"); reject(3,"bad","archetype"); reject(3,"wand","archetype");
        reject(4,"missing","element");
        try(var visibility=mockStatic(ElementVisibility.class)) { reject(4,"fire","element"); }
        for(String amount:List.of("bad","NaN","Infinity","0","-1","1")) reject(5,amount,"Aura");
        reject(6,"unknown","part");
        PartRegistry.register(part("disabled","core",true,GearType.STAFF)); reject(6,"disabled","part");
        PartRegistry.register(part("wand","core",false,GearType.WAND)); reject(6,"wand","part");
        run("weapon","give","Mage","staff","fire","10","core","core");
        PartRegistry.register(part("extra","handle",false,GearType.STAFF)); reject(6,"extra","categories");
        for(int i=0;i<36;i++) target.getInventory().setItem(i,new ItemStack(Material.STONE));
        clearInvocations(sender); run(valid); verify(sender).sendMessage(contains("empty inventory")); target.getInventory().clear(); target.getInventory().setItem(0,new ItemStack(Material.AIR));
        ItemStack airSlot=mock(ItemStack.class); when(airSlot.getType()).thenReturn(Material.AIR);
        try(var bukkit=mockStatic(org.bukkit.Bukkit.class); var builder=mockStatic(GearItemBuilder.class)) {
            var airTarget=mock(org.bukkit.entity.Player.class); var inventory=mock(org.bukkit.inventory.PlayerInventory.class);
            when(airTarget.getInventory()).thenReturn(inventory);
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{airSlot});
            bukkit.when(()->org.bukkit.Bukkit.getPlayerExact("Mage")).thenReturn(airTarget);
            run(valid);
        }
        try(var builder=mockStatic(GearItemBuilder.class)) {
            run(valid);
            builder.when(()->GearItemBuilder.prepare(eq(GearType.STAFF),anyCollection())).thenReturn(new ItemStack(Material.AIR)); run(valid);
            builder.when(()->GearItemBuilder.prepare(eq(GearType.STAFF),anyCollection())).thenReturn(new ItemStack(Material.BARRIER)); run(valid);
            ItemStack item=new ItemStack(Material.STICK); GearProvenance.stamp(item,GearType.STAFF,List.of(PartRegistry.get("core")));
            builder.when(()->GearItemBuilder.prepare(eq(GearType.STAFF),anyCollection())).thenReturn(item);
            builder.when(()->GearItemBuilder.rewriteSockets(item,1)).thenAnswer(inv->{GearProvenance.lockSockets(item); return item;});
            run(valid);
            ItemStack given=target.getInventory().getItem(target.getInventory().first(Material.STICK));
            assertEquals(10,WeaponRequirement.fromItem(given).aura().getFill("fire"));
            assertEquals(Map.of(),GearProvenance.readInputs(given)); assertTrue(GearProvenance.socketsLocked(given));
            verify(sender).sendMessage(contains("Gave completed Staff"));
        }
    }
    List<String> tab(String... args) { return command.onTabComplete(sender,null,"magic",args); }
    @Test void completionRespectsPermissionAndPrefixes() {
        run(); verify(sender).sendMessage(contains("Usage: /magic weapon give"));
        assertTrue(tab("weapon").isEmpty());
        assertEquals(List.of("weapon"),tab("we"));
        assertEquals(List.of("give"),tab("weapon","g"));
        assertTrue(tab("weapon","bad","").isEmpty());
        assertEquals(List.of("Mage"),tab("weapon","give","M"));
        assertEquals(List.of("staff"),tab("weapon","give","Mage","s"));
        assertEquals(List.of("fire"),tab("weapon","give","Mage","staff","f"));
        assertTrue(tab("weapon","give","Mage","staff","fire","").isEmpty());
        assertEquals(List.of("core"),tab("weapon","give","Mage","staff","fire","10","c"));
        when(sender.hasPermission("custom.staff")).thenReturn(false);
        assertTrue(tab("weapon","give","").isEmpty()); assertTrue(tab("").isEmpty());
    }
}
