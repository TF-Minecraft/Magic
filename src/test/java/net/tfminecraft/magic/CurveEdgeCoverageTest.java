package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.magic.modifier.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CurveEdgeCoverageTest {
  @Test
  void malformedNonFiniteKeyframesCannotPoisonInterpolation() {
    var cfg = new YamlConfiguration();
    cfg.set("0.damage", 0);
    cfg.set("NaN.damage", .5);
    cfg.set("Infinity.damage", .7);
    cfg.set(" .damage", .8);
    assertEquals(1, KeyframeCurve.fromSection(cfg).size());
    assertEquals(0, KeyframeCurve.fromSection(cfg).sample(25).damage());
    var keys = new TreeMap<Double, ModifierTriple>();
    keys.put(0., ModifierTriple.ZERO);
    keys.put(Double.NaN, new ModifierTriple(0, .5, 0));
    keys.put(Double.POSITIVE_INFINITY, new ModifierTriple(0, .7, 0));
    assertEquals(1, new KeyframeCurve(keys).size());
    assertEquals(0, new KeyframeCurve(keys).sample(25).damage());
    assertEquals(
        1, new KeyframeCurve(new TreeMap<>(Map.of(Double.NaN, ModifierTriple.ZERO))).size());
  }

  @Test
  void exactInteriorKeysAndNanAmountsResolveToStableValues() {
    var curve =
        new KeyframeCurve(
            new TreeMap<>(
                Map.of(
                    0.,
                    ModifierTriple.ZERO,
                    10.,
                    new ModifierTriple(0, .2, 0),
                    20.,
                    new ModifierTriple(0, .4, 0))));
    assertEquals(.2, curve.sample(10).damage());
    assertEquals(.1, curve.sample(5).damage(), 1e-12);
    assertEquals(0, curve.sample(Double.NaN).damage());
    assertEquals(0, curve.sample(Double.NEGATIVE_INFINITY).damage());
    assertEquals(.4, curve.sample(Double.POSITIVE_INFINITY).damage());
  }
}
