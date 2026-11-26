package dev.npclife;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCCreateEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.event.entity.EntityDeathEvent;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class NPCLifePlugin extends JavaPlugin implements Listener {
    private static NPCLifePlugin instance;
    private PersonalityManager personalities;
    private BrainManager brains;
    private SequenceManager sequences;
    private KitManager kits;
    private TeamManager teams;
    private NameManager names;
    private SequenceRecorder recorder;
    private TraitInfo traitInfo;

    public static NPCLifePlugin get() { return instance; }
    PersonalityManager personalities() { return personalities; }
    BrainManager brains() { return brains; }
    SequenceManager sequences() { return sequences; }
    KitManager kits() { return kits; }
    TeamManager teams() { return teams; }
    NameManager names() { return names; }
    SequenceRecorder recorder() { return recorder; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (getServer().getPluginManager().getPlugin("Citizens") == null) {
            getLogger().severe("Citizens is not installed - disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        personalities = new PersonalityManager(this);
        personalities.load();
        kits = new KitManager(this);
        kits.load();
        teams = new TeamManager(this);
        teams.load();
        names = new NameManager(this);
        names.load();
        sequences = new SequenceManager(this);
        sequences.load();
        recorder = new SequenceRecorder(this);
        brains = new BrainManager(this);

        traitInfo = TraitInfo.create(LifeTrait.class);
        CitizensAPI.getTraitFactory().registerTrait(traitInfo);
        getServer().getPluginManager().registerEvents(this, this);

        LifeCommand cmd = new LifeCommand(this);
        PluginCommand pc = getCommand("npclife");
        pc.setExecutor(cmd);
        pc.setTabCompleter(cmd);
        brains.start();
        // Existing NPCs: attach the trait once Citizens has loaded them (and again later as a safety net).
        getServer().getScheduler().runTaskLater(this, this::attachAll, 40L);
        getServer().getScheduler().runTaskLater(this, this::attachAll, 400L);
        getLogger().info("NPCLife enabled. Head control: " + HeadUtil.describe());
    }

    @Override
    public void onDisable() {
        if (recorder != null) recorder.stop();
        if (brains != null) brains.stop();
        try { if (traitInfo != null) CitizensAPI.getTraitFactory().deregisterTrait(traitInfo); } catch (Throwable ignored) { }
    }

    void reload() {
        reloadConfig();
        personalities.load();
        sequences.load();
        kits.load();
        teams.load();
        names.load();
    }

    private void attachAll() {
        for (NPC n : CitizensAPI.getNPCRegistry()) {
            LifeTrait lt = n.getOrAddTrait(LifeTrait.class);
            if (n.isSpawned()) { lt.ensureCenter(); brains.register(lt); }
        }
    }

    /** Gives every new NPC the (inert) trait so its spawn location becomes its wander centre. */
    @EventHandler
    public void onCreate(NPCCreateEvent e) {
        if (getConfig().getBoolean("auto-attach", true)) e.getNPC().getOrAddTrait(LifeTrait.class);
    }

    /** Covers NPCs that spawn later (chunk loads, /npc spawn): the trait records the spawn point as the home centre. */
    @EventHandler
    public void onNpcSpawn(NPCSpawnEvent e) {
        NPC n = e.getNPC();
        LifeTrait lt = n.getOrAddTrait(LifeTrait.class);
        getServer().getScheduler().runTask(this, () -> { if (n.isSpawned()) { lt.ensureCenter(); brains.register(lt); } });
    }

    /** Army NPCs don't drop their kit or XP when they die (they are brought back with /npcx army respawn). */
    @EventHandler
    public void onNpcDeath(EntityDeathEvent e) {
        if (!getConfig().getBoolean("army-clear-drops", true)) return;
        NPC n = CitizensAPI.getNPCRegistry().getNPC(e.getEntity());
        if (n != null && n.hasTrait(LifeTrait.class) && n.getOrAddTrait(LifeTrait.class).isArmy()) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }
}
