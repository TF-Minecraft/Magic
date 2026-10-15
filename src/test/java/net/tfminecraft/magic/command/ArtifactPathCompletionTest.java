package net.tfminecraft.magic.command;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.path.ArtifactPathParser;
import org.junit.jupiter.api.*;

class ArtifactPathCompletionTest {
    @BeforeEach void setup() {
        ArtifactTypeRegistry.clear(); ArtifactRarityRegistry.clear();
        ArtifactTypeRegistry.register(new ArtifactTypeDef("fire", "names", "models", true, 1,
                Map.of("common", new CapRange(1, 2)), Map.of()));
        ArtifactRarityRegistry.register(new ArtifactRarityDef("common", "Common", "#ffffff", 1, 1, 1, new CapRange(1, 2)));
    }
    @AfterEach void cleanup() { ArtifactTypeRegistry.clear(); ArtifactRarityRegistry.clear(); }

    @Test void finiteKeysAndValuesCanBeContinuedOrClosedWithoutChangingExistingText() throws Exception {
        var ctor = ArtifactPathCompletion.class.getDeclaredConstructor(); ctor.setAccessible(true); ctor.newInstance();
        assertEquals(List.of("magic.(element="), ArtifactPathCompletion.complete("magic.(el"));
        assertEquals(List.of("magic.(fire)", "magic.(fire;", "magic.(fire="), ArtifactPathCompletion.complete("magic.(fi"));
        assertEquals(List.of("magic.(element=fire)", "magic.(element=fire;"), ArtifactPathCompletion.complete("magic.(element=f"));
        assertEquals(List.of("magic.(rarity=common)", "magic.(rarity=common;"), ArtifactPathCompletion.complete("magic.(rarity=c"));
        assertEquals(List.of("magic.(rarity=common;primary=fire)", "magic.(rarity=common;primary=fire;"),
                ArtifactPathCompletion.complete("magic.(rarity=common;primary=f"));
        assertFalse(ArtifactPathCompletion.complete("magic.(element=fire;").stream().anyMatch(s -> s.contains(";primary=")));
        for (String head : List.of("magic.(fire;", "magic.(fire=2;", "magic.(rarity=common;fire=2;")) {
            assertTrue(ArtifactPathCompletion.complete(head).stream().noneMatch(s -> s.startsWith(head + "fire")));
            for (String completed : ArtifactPathCompletion.complete(head + "primary=f")) {
                if (completed.endsWith(")")) assertNotNull(ArtifactPathParser.parse(completed));
            }
        }
        assertTrue(ArtifactPathCompletion.complete("magic.(primary=fire;rarity=c").stream().allMatch(s -> s.contains("common")));
        assertTrue(ArtifactPathCompletion.complete("magic.(primary=fire;rarity=r").isEmpty());
    }

    @Test void malformedOrManualInputDoesNotInventExecutableData() {
        for (String token : List.of("other", "magic.(unknown;", "magic.(primary;", "magic.(primary=unknown;",
                "magic.(rarity;", "magic.(rarity=unknown;", "magic.(fire=bad;", "magic.(fire=", "magic.(fire=20",
                "magic.(unknown=", "magic.(primary=fire;primary=", "magic.(primary=fire)", "magic.( primary=")) {
            assertTrue(ArtifactPathCompletion.complete(token).isEmpty(), token);
        }
    }
}
