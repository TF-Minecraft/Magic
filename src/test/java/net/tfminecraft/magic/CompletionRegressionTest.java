package net.tfminecraft.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import net.tfminecraft.magic.artifact.config.*;
import net.tfminecraft.magic.artifact.path.ArtifactPathParser;
import net.tfminecraft.magic.command.ResonanceCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.*;

class CompletionRegressionTest {
    CommandTest fixture = new CommandTest();
    @BeforeEach void setup() { fixture.setup(); ArtifactGeneratorCache.reset(); }
    @AfterEach void cleanup() { fixture.cleanup(); ArtifactGeneratorCache.reset(); }

    List<String> console(String... args) {
        var sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        return fixture.command.onTabComplete(sender, null, "magic", args);
    }

    @Test void consoleOnlySeesExecutableRoutesAtEveryDepth() {
        assertEquals(List.of("weapon", "reload", "artifact", "resonance"), console(""));
        assertEquals(List.of("reload", "resonance"), console("RE"));
        assertEquals(List.of("roll", "give", "path"), console("artifact", ""));
        for (String root : List.of("open", "refresh", "rune", "fillchest", "shrine")) {
            assertTrue(console(root, "").isEmpty());
            assertTrue(console(root, "fill", "").isEmpty());
        }
        for (String action : List.of("create", "setfill", "setmuffle", "unmuffle")) {
            assertTrue(console("artifact", action, "").isEmpty());
        }
        assertEquals(List.of("random", "fire"), console("artifact", "roll", ""));
        assertEquals(List.of("Mage"), console("artifact", "give", ""));
        assertEquals(List.of("magic.artifact"), console("artifact", "path", "magic.a"));
        assertEquals(List.of("give"), console("weapon", ""));
        assertEquals(List.of("get", "set", "add", "reset"), console("resonance", ""));
        assertTrue(fixture.tab("artifact", "").contains("create"));
    }

    @Test void raritiesFollowSelectedBracketsAndRandomPool() {
        ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("common", 1, 1, 1));
        ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("rare", 1, 1, 1));
        ArtifactTypeRegistry.register(new ArtifactTypeDef("ice", "names", "models", true, 1,
                Map.of("rare", new CapRange(5, 10)), Map.of()));
        ArtifactTypeRegistry.register(new ArtifactTypeDef("zero", "names", "models", true, 0,
                Map.of("rare", new CapRange(5, 10)), Map.of()));
        ArtifactTypeRegistry.register(new ArtifactTypeDef("infinite", "names", "models", true, Double.POSITIVE_INFINITY,
                Map.of("rare", new CapRange(5, 10)), Map.of()));
        assertEquals(List.of("roll", "common"), fixture.tab("artifact", "roll", "FiRe", ""));
        assertEquals(List.of("common"), fixture.tab("artifact", "give", "Mage", "fire", "CO"));
        assertTrue(fixture.tab("artifact", "roll", "water", "").isEmpty());
        assertTrue(fixture.tab("artifact", "roll", "missing", "").isEmpty());
        ArtifactGeneratorCache.setRandomPool(List.of("fire", "zero", "infinite"));
        assertEquals(List.of("roll", "common"), fixture.tab("artifact", "roll", "random", ""));
        ArtifactGeneratorCache.reset();
        assertEquals(List.of("roll", "common", "rare"), fixture.tab("artifact", "roll", "random", ""));
    }

    @Test void pathCompletesFiniteGrammarAndPreservesEarlierTokenText() {
        ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("common", 1, 1, 1));
        ArtifactRarityRegistry.register(ArtifactGenerationTest.rarity("rare", 1, 1, 1));
        assertEquals(List.of("magic.artifact", "magic.("), fixture.tab("artifact", "path", ""));
        assertEquals(List.of("magic.artifact"), fixture.tab("artifact", "path", "magic.a"));
        assertTrue(fixture.tab("artifact", "path", "Magic.(Pri").contains("Magic.(Primary="));
        String typed = "Magic.(primary=Fi";
        assertEquals(List.of(typed + "re)", typed + "re;"), fixture.tab("artifact", "path", typed));
        String withCap = "magic.(primary=fire;fire=12.5;rarity=CO";
        assertEquals(List.of(withCap + "mmon)", withCap + "mmon;"), fixture.tab("artifact", "path", withCap));
        for (String candidate : fixture.tab("artifact", "path", withCap)) {
            if (candidate.endsWith(")")) assertNotNull(ArtifactPathParser.parse(candidate));
        }
        assertTrue(fixture.tab("artifact", "path", "magic.(primary=fire;rarity=ra").isEmpty());
        assertTrue(fixture.tab("artifact", "path", "magic.(fire=").isEmpty());
        assertTrue(fixture.tab("artifact", "path", "magic.(fire=12").isEmpty());
        assertTrue(fixture.tab("artifact", "path", "magic.(primary=fire)").isEmpty());
        assertFalse(fixture.tab("artifact", "path", "magic.(primary=fire;").contains("magic.(primary=fire;element="));
        assertTrue(fixture.tab("artifact", "path", "magic.artifact", "").isEmpty());
        assertTrue(fixture.tab("artifact", "unknown", "").isEmpty());
    }

    @Test void resonanceExecutorSuppressesDefaultNamesIncludingAlias() throws Exception {
        var ctor = PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
        ctor.setAccessible(true);
        var registered = ctor.newInstance("resonance", Magic.plugin);
        registered.setExecutor(new ResonanceCommand());
        for (String alias : List.of("resonance", "res")) {
            assertTrue(registered.tabComplete(fixture.player, alias, new String[]{""}).isEmpty());
            assertTrue(registered.tabComplete(fixture.player, alias, new String[]{"Ma", ""}).isEmpty());
        }
    }
}
