package net.tfminecraft.magic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.magic.command.MagicCommand;
import net.tfminecraft.magic.command.ResonanceCommand;
import net.tfminecraft.magic.integration.RpCharactersBridge;
import net.tfminecraft.magic.integration.SpellModifierApplyService;
import net.tfminecraft.magic.artifact.ArtifactAttuneScanHandler;
import net.tfminecraft.magic.artifact.ArtifactFrameCareTicker;
import net.tfminecraft.magic.artifact.config.ArtifactConfigLoader;
import net.tfminecraft.magic.artifact.fillchest.ArtifactFillChestService;
import net.tfminecraft.magic.artifact.path.ArtifactPathFactory;
import net.tfminecraft.magic.artifact.sacrifice.SacrificeRiteService;
import net.tfminecraft.magic.artifact.shrine.ShrineChargeService;
import net.tfminecraft.magic.attunement.ArtifactDisplayIndex;
import net.tfminecraft.magic.attunement.AuraLog;
import net.tfminecraft.magic.listener.ArtifactFillChestListener;
import net.tfminecraft.magic.listener.ArtifactListener;
import net.tfminecraft.magic.listener.CastDriftListener;
import net.tfminecraft.magic.listener.GearAlignmentListener;
import net.tfminecraft.magic.listener.GearRefreshListener;
import net.tfminecraft.magic.listener.MagicSessionListener;
import net.tfminecraft.magic.listener.ResonanceCastListener;
import net.tfminecraft.magic.listener.SacrificeListener;
import net.tfminecraft.magic.loader.ChargesLoader;
import net.tfminecraft.magic.loader.ConfigLoader;
import net.tfminecraft.magic.loader.ElementsLoader;
import net.tfminecraft.magic.loader.GearLoader;
import net.tfminecraft.magic.loader.GuiLoader;
import net.tfminecraft.magic.loader.SkillsLoader;
import net.tfminecraft.magic.gear.GearBrokenMarker;
import net.tfminecraft.magic.gear.GearStationListener;
import net.tfminecraft.magic.gear.orb.GearOrbService;
import net.tfminecraft.magic.gear.GearStationStore;
import net.tfminecraft.magic.modifier.DriftCache;
import net.tfminecraft.magic.model.ElementDef;
import net.tfminecraft.magic.manager.ArtifactCreateGuiManager;
import net.tfminecraft.magic.manager.MagicTickService;
import net.tfminecraft.magic.manager.ResonanceGuiManager;
import net.tfminecraft.magic.meditation.MeditationService;
import net.tfminecraft.magic.profile.MagicProfileService;
import net.tfminecraft.magic.profile.MagicProfileStore;
import net.tfminecraft.magic.registry.ElementRegistry;
import net.tfminecraft.magic.registry.SkillElementRegistry;
import net.tfminecraft.magic.service.EquilibriumService;
import net.tfminecraft.magic.service.ResonanceService;
import net.tfminecraft.magic.util.GridLayout;
import net.tfminecraft.magic.util.RevisionTracker;
import net.tfminecraft.tlibs.itemscan.ItemScanService;

/**
 * Elemental resonance and casting modes. See docs/ for batch plan.
 */
public class Magic extends JavaPlugin {

    public static Magic plugin;

    private static final RevisionTracker revisionTracker = new RevisionTracker();

    private final ConfigLoader configLoader = new ConfigLoader();
    private final GuiLoader guiLoader = new GuiLoader();
    private final ElementsLoader elementsLoader = new ElementsLoader();
    private final SkillsLoader skillsLoader = new SkillsLoader();
    private final ChargesLoader chargesLoader = new ChargesLoader();
    private final GearLoader gearLoader = new GearLoader();
    private final ResonanceCommand resonanceCommand = new ResonanceCommand();
    private final MagicCommand magicCommand = new MagicCommand();
    private final ResonanceGuiManager resonanceGuiManager = new ResonanceGuiManager();
    private final ArtifactCreateGuiManager artifactCreateGuiManager = new ArtifactCreateGuiManager();
    private MagicProfileService profileService;
    private MeditationService meditationService;
    private final ArtifactAttuneScanHandler attuneScanHandler = new ArtifactAttuneScanHandler();

    @Override
    public void onEnable() {
        plugin = this;
        createFolders();
        createConfigs();
        revisionTracker.load(getDataFolder());
        if (!loadConfigs()) {
            getLogger().warning("Magic loaded with config errors.");
        }
        revisionTracker.flush();
        TLibs.getItemAPI().registerPathHandler("magic", ArtifactPathFactory.INSTANCE);

        var resonanceCmd = getCommand("resonance");
        if (resonanceCmd != null) {
            resonanceCmd.setExecutor(resonanceCommand);
        } else {
            getLogger().severe("Command 'resonance' missing from plugin.yml");
        }

        var magicCmd = getCommand("magic");
        if (magicCmd != null) {
            magicCmd.setExecutor(magicCommand);
            magicCmd.setTabCompleter(magicCommand);
        } else {
            getLogger().severe("Command 'magic' missing from plugin.yml");
        }

        getServer().getPluginManager().registerEvents(resonanceGuiManager, this);
        getServer().getPluginManager().registerEvents(artifactCreateGuiManager, this);
        getServer().getPluginManager().registerEvents(new ArtifactListener(), this);
        getServer().getPluginManager().registerEvents(new ArtifactFillChestListener(), this);
        getServer().getPluginManager().registerEvents(new SacrificeListener(), this);
        GearStationListener gearStationListener = new GearStationListener();
        getServer().getPluginManager().registerEvents(gearStationListener, this);
        getServer().getPluginManager().registerEvents(gearStationListener.inventory(), this);
        getServer().getPluginManager().registerEvents(new GearOrbService(), this);
        getServer().getPluginManager().registerEvents(new GearRefreshListener(), this);
        if (isMmoStackPresent()) {
            getServer().getPluginManager().registerEvents(new ResonanceCastListener(), this);
            getServer().getPluginManager().registerEvents(new CastDriftListener(), this);
            getServer().getPluginManager().registerEvents(new GearAlignmentListener(), this);
        }

        MagicProfileStore profileStore = new MagicProfileStore(
                new File(getDataFolder(), "data/characters"));
        profileService = new MagicProfileService(profileStore, resonanceGuiManager.getSessionManager());
        getServer().getPluginManager().registerEvents(
                new MagicSessionListener(profileService, resonanceGuiManager), this);
        if (RpCharactersBridge.isAvailable()) {
            profileService.loadOnlineCharacters();
            if (isMmoStackPresent()) {
                SpellModifierApplyService.syncOnline(resonanceGuiManager.getSessionManager());
            }
        }

        MeditationService meditationService = new MeditationService(
                resonanceGuiManager.getSessionManager(), profileService);
        getServer().getPluginManager().registerEvents(meditationService, this);
        meditationService.start();
        this.meditationService = meditationService;

        MagicTickService.register(ctx -> EquilibriumService.tickOnlineSessions(
                resonanceGuiManager.getSessionManager()));
        MagicTickService.register(ctx -> ResonanceService.tickOnlineSessions(
                resonanceGuiManager.getSessionManager()));
        MagicTickService.register(ctx -> resonanceGuiManager.refreshOpenInventories());
        MagicTickService.register(ShrineChargeService::tick);
        ArtifactFrameCareTicker frameCareTicker = new ArtifactFrameCareTicker();
        MagicTickService.register(frameCareTicker);
        getServer().getPluginManager().registerEvents(frameCareTicker, this);
        if (isMmoStackPresent()) {
            MagicTickService.register(ctx -> SpellModifierApplyService.syncChangedOnline(
                    resonanceGuiManager.getSessionManager()));
        }
        MagicTickService.start();
        getServer().getScheduler().runTask(this, ArtifactDisplayIndex::rebuildFromSaved);
        getServer().getScheduler().runTask(this, GearStationStore::load);
        GearOrbService.start();
        if (ItemScanService.get() != null) {
            ItemScanService.get().subscribe(attuneScanHandler);
        }

        getLogger().info("Magic enabled.");
    }

    @Override
    public void onDisable() {
        if (meditationService != null) {
            meditationService.stop();
        }
        if (profileService != null) {
            profileService.saveAllOnline();
        }
        if (ItemScanService.get() != null) {
            ItemScanService.get().unsubscribe(attuneScanHandler);
        }
        GearOrbService.stop();
        GearStationStore.shutdown();
        MagicTickService.stop();
        MagicTickService.clearHandlers();
        TLibs.getItemAPI().unregisterPathHandler("magic");
        ArtifactFillChestService.clearAll();
        ShrineChargeService.clearAll();
        SacrificeRiteService.clearAll();
        if (isMmoStackPresent()) {
            SpellModifierApplyService.clearAll();
        }
        ResonanceCastListener.clearAll();
        GearBrokenMarker.clearWarningSession();
        revisionTracker.flush();
        getLogger().info("Magic disabled.");
    }

    public static RevisionTracker getRevisionTracker() {
        return revisionTracker;
    }

    public boolean reloadAll() {
        SacrificeRiteService.clearAll();
        boolean ok = loadConfigs();
        revisionTracker.flush();
        GearBrokenMarker.clearWarningSession();
        if (isMmoStackPresent()) {
            SpellModifierApplyService.syncOnline(resonanceGuiManager.getSessionManager());
        }
        if (MagicTickService.isRunning()) {
            MagicTickService.stop();
            MagicTickService.start();
        }
        return ok;
    }

    public MagicProfileService getProfileService() {
        return profileService;
    }

    public ResonanceGuiManager getResonanceGuiManager() {
        return resonanceGuiManager;
    }

    public ArtifactCreateGuiManager getArtifactCreateGuiManager() {
        return artifactCreateGuiManager;
    }

    public void syncSpellModifiers(org.bukkit.entity.Player player,
            net.tfminecraft.magic.session.ResonanceSession session) {
        if (!isMmoStackPresent()) {
            return;
        }
        SpellModifierApplyService.sync(player, session);
    }

    public void clearSpellModifiers(org.bukkit.entity.Player player) {
        if (!isMmoStackPresent()) {
            return;
        }
        SpellModifierApplyService.clear(player);
    }

    private static boolean isMmoStackPresent() {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled("MythicLib")
                && org.bukkit.Bukkit.getPluginManager().isPluginEnabled("MMOCore");
    }

    private boolean loadConfigs() {
        boolean ok = true;
        ok &= configLoader.loadSafe(new File(getDataFolder(), "config.yml"));
        Messages.load(new File(getDataFolder(), "messages.yml"));
        ok &= guiLoader.loadSafe(new File(getDataFolder(), "gui.yml"));
        ok &= elementsLoader.loadFolder(new File(getDataFolder(), "elements"));
        ok &= skillsLoader.loadSafe(new File(getDataFolder(), "skills.yml"));
        ok &= chargesLoader.loadSafe(new File(getDataFolder(), "charges.yml"));
        ok &= gearLoader.loadFolder(new File(getDataFolder(), "gear"));
        ok &= ArtifactConfigLoader.loadFolder(new File(getDataFolder(), "artifacts"));
        AuraLog.configure(Cache.loggingEnabled, Cache.wipeLog, getDataFolder());
        if (ok) {
            getLogger().info("[Magic] Configs loaded.");
        }
        if (Cache.debug) {
            getLogger().info("[Magic] Debug: elements=" + ElementRegistry.getAllIds());
            getLogger().info("[Magic] Debug: element_slots=" + GridLayout.elementSlots());
            getLogger().info("[Magic] Debug: character_head_slot=" + GridLayout.characterHeadSlot());
            getLogger().info("[Magic] Debug: skill_bindings=" + SkillElementRegistry.size());
            getLogger().info("[Magic] Debug: surge_keyframes=" + DriftCache.surge.size()
                    + " tranquility_keyframes=" + DriftCache.tranquility.size());
            for (ElementDef element : ElementRegistry.getAll()) {
                getLogger().info("[Magic] Debug: resonance_keyframes." + element.getId()
                        + "=" + element.getResonanceCurve().size());
            }
        }
        return ok;
    }

    private void createFolders() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        mkdir("elements");
        mkdir("artifacts");
        mkdir("gear");
        mkdir("data/characters");
    }

    private void mkdir(String relativePath) {
        File folder = new File(getDataFolder(), relativePath);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    private void createConfigs() {
        String[] defaultFiles = {
            "config.yml",
            "messages.yml",
            "gui.yml",
            "skills.yml",
            "charges.yml",
            "gear/archetypes.yml",
            "gear/part-types.yml",
            "gear/parts.yml",
            "gear/socket-colours.yml",
            "gear/orbs.yml",
            "elements/elements.yml",
            "artifacts/generator.yml",
            "artifacts/model-schemes.yml",
            "artifacts/naming-schemes.yml",
            "artifacts/adjectives.yml",
            "artifacts/shrines.yml",
            "artifacts/sacrifice.yml"
        };
        for (String path : defaultFiles) {
            copyResourceIfMissing(path);
        }
    }

    private void copyResourceIfMissing(String relativePath) {
        File target = new File(getDataFolder(), relativePath);
        if (target.exists()) {
            return;
        }
        target.getParentFile().mkdirs();
        try (InputStream in = getResource(relativePath)) {
            if (in == null) {
                getLogger().warning("Missing bundled resource: " + relativePath);
                return;
            }
            Files.copy(in, target.toPath());
        } catch (IOException ex) {
            getLogger().severe("Failed to copy default resource " + relativePath + ": " + ex.getMessage());
        }
    }
}
