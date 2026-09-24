package net.tfminecraft.magic.gear;

import java.util.List;

import net.tfminecraft.magic.charge.TierBands;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.model.ElementVisibility;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.util.MagicText;

public final class WeaponResonanceDisplay {

    private WeaponResonanceDisplay() {}

    /**
     * One formatted line per imbued element. Caller supplies title / unattuned messaging.
     */
    public static void appendElementLines(List<String> out, WeaponRequirement requirement, String linePrefix) {
        if (requirement == null || !requirement.hasStored()) {
            return;
        }
        String prefix = linePrefix != null ? linePrefix : "";
        for (ElementDef element : ElementRegistry.getAll()) {
            String elementId = element.getId();
            if (!ElementVisibility.shownOnCharge(elementId) || !includeElement(requirement, elementId)) {
                continue;
            }
            double fill = requirement.aura().getFill(elementId);
            String numeral = TierBands.numeralFor(elementId, fill);
            out.add(MagicText.format(prefix) + MagicText.elementName(element)
                    + MagicText.format(" {color:label_muted}" + (numeral.isEmpty() ? "-" : numeral)));
        }
    }

    static boolean includeElement(WeaponRequirement requirement, String elementId) {
        if (requirement == null || elementId == null) {
            return false;
        }
        return includeAuraEntry(
                requirement.aura().getCap(elementId),
                requirement.aura().getFill(elementId));
    }

    static boolean includeAuraEntry(double cap, double fill) {
        return cap > 0 || fill > 0;
    }
}
