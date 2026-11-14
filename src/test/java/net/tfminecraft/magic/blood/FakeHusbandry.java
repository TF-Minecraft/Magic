package net.tfminecraft.magic.blood;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stands in for Cooking's HusbandryOwnershipService, which Magic reaches by reflection. */
public final class FakeHusbandry {
  public static final Map<UUID, List<Owner>> OWNERS = new HashMap<>();

  private FakeHusbandry() {}

  public static List<Owner> listOwners(UUID animal) {
    return OWNERS.getOrDefault(animal, List.of());
  }

  /** Same accessors as Cooking's HusbandryOwner. */
  public static final class Owner {
    private final UUID account;
    private final UUID character;

    public Owner(UUID account, UUID character) {
      this.account = account;
      this.character = character;
    }

    public UUID playerUuid() {
      return account;
    }

    public UUID characterUuid() {
      return character;
    }
  }

  /** A service whose answer is not a list. */
  public static final class Broken {
    private Broken() {}

    public static Object listOwners(UUID animal) {
      return "nope";
    }
  }

  static void use() {
    HusbandryOwners.serviceClass = FakeHusbandry.class.getName();
  }

  static void restore() {
    HusbandryOwners.serviceClass = "net.tfminecraft.cooking.husbandry.HusbandryOwnershipService";
    OWNERS.clear();
  }
}
