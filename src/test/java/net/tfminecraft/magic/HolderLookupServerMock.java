package net.tfminecraft.magic;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;

/** MockBukkit leaves Paper's non-copying getHolder(boolean) unimplemented; the plugin uses it on hot paths. */
public class HolderLookupServerMock extends ServerMock {

  /** A spy of {@code inventory} whose getHolder(boolean) answers like getHolder(). */
  @SuppressWarnings("unchecked")
  public static <T extends Inventory> T withHolderLookup(T inventory) {
    if (mockingDetails(inventory).isMock()) return inventory;
    T spied = spy(inventory);
    doAnswer(call -> ((Inventory) call.getMock()).getHolder()).when(spied).getHolder(anyBoolean());
    return spied;
  }

  @Override
  public InventoryMock createInventory(InventoryHolder owner, InventoryType type, String title, int size) {
    return withHolderLookup(super.createInventory(owner, type, title, size));
  }

  @Override
  public InventoryMock createInventory(InventoryHolder owner, InventoryType type) {
    return withHolderLookup(super.createInventory(owner, type));
  }

  @Override
  public InventoryMock createInventory(InventoryHolder owner, InventoryType type, Component title) {
    return withHolderLookup(super.createInventory(owner, type, title));
  }

  @Override
  public InventoryMock createInventory(InventoryHolder owner, InventoryType type, String title) {
    return withHolderLookup(super.createInventory(owner, type, title));
  }

  @Override
  public InventoryMock createInventory(InventoryHolder owner, int size) {
    return withHolderLookup(super.createInventory(owner, size));
  }

  @Override
  public InventoryMock createInventory(InventoryHolder owner, int size, Component title) {
    return withHolderLookup(super.createInventory(owner, size, title));
  }

  @Override
  public InventoryMock createInventory(InventoryHolder owner, int size, String title) {
    return withHolderLookup(super.createInventory(owner, size, title));
  }
}
