package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who keeps an animal, read from the husbandry records: BreedingBuddies when it is installed, else
 * Cooking (which kept them before BreedingBuddies took husbandry over). Reflection keeps both out of
 * Magic's build; any mismatch reads as "nobody owns it".
 */
public final class HusbandryOwners {

    /** One keeper: the account, and the character when Cooking recorded one. */
    public record Owner(UUID account, UUID character) {}

    static final List<String> SERVICES = List.of(
            "net.tfminecraft.breedingbuddies.husbandry.HusbandryOwnershipService",
            "net.tfminecraft.cooking.husbandry.HusbandryOwnershipService");
    static List<String> serviceClasses = SERVICES;

    private HusbandryOwners() {}

    public static List<Owner> of(UUID animal) {
        List<Owner> out = new ArrayList<>();
        Class<?> service = null;
        for (String name : serviceClasses) {
            try {
                service = Class.forName(name);
                break;
            } catch (ClassNotFoundException | LinkageError ex) {
                // not installed: try the next keeper of records
            }
        }
        if (service == null) {
            return List.of();
        }
        try {
            Object owners = service.getMethod("listOwners", UUID.class).invoke(null, animal);
            for (Object owner : (List<?>) owners) {
                UUID account = (UUID) owner.getClass().getMethod("playerUuid").invoke(owner);
                UUID character = (UUID) owner.getClass().getMethod("characterUuid").invoke(owner);
                if (account != null) {
                    out.add(new Owner(account, character));
                }
            }
        } catch (ReflectiveOperationException | ClassCastException | LinkageError ex) {
            return List.of();
        }
        return out;
    }
}
