package net.tfminecraft.magic.blood;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who keeps an animal, read from Cooking's husbandry records when Cooking is installed.
 * Reflection keeps Cooking out of Magic's build; any mismatch reads as "nobody owns it".
 */
public final class HusbandryOwners {

    /** One keeper: the account, and the character when Cooking recorded one. */
    public record Owner(UUID account, UUID character) {}

    static String serviceClass = "net.tfminecraft.cooking.husbandry.HusbandryOwnershipService";

    private HusbandryOwners() {}

    public static List<Owner> of(UUID animal) {
        List<Owner> out = new ArrayList<>();
        try {
            Class<?> service = Class.forName(serviceClass);
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
