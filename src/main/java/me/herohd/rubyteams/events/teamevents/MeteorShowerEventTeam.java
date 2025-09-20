package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class MeteorShowerEventTeam extends TeamEvent implements Listener {

    private final Set<Location> sphereBlocks = new HashSet<>();
    // --- MODIFICA: Liste separate per superficie e glowstone attuali ---
    private final List<Location> surfaceBlocks = new ArrayList<>();
    private final List<Location> currentGlowstoneBlocks = new ArrayList<>();

    private final Location center;
    private final int radius;

    private final int obsidianPoints;
    private final int glowstonePoints;

    private final int glowstoneInterval;
    private final int glowstoneAmount;
    private BukkitTask glowstoneTask;

    public MeteorShowerEventTeam(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);
        this.center = parseLocationFromString(config.getString("sphere.center"));
        this.radius = config.getInt("sphere.radius");
        this.obsidianPoints = config.getInt("points.obsidian");
        this.glowstonePoints = config.getInt("points.glowstone");
        this.glowstoneInterval = config.getInt("glowstone-mechanic.interval-seconds");
        // lifespan non è più usato direttamente nel task, ma lo teniamo per completezza
        this.glowstoneAmount = config.getInt("glowstone-mechanic.amount");
    }

    @Override
    public void start() {
        super.start();
        startTimeBasedFinishChecker();
        generateSphere();

        glowstoneTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateGlowstonePositions, 20L * 5, 20L * glowstoneInterval); // Parte dopo 5 secondi, poi ogni 3
    }

    @Override
    public void finishEvent() {
        if (glowstoneTask != null) glowstoneTask.cancel();
        clearSphere();
        super.finishEvent();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockMine(BlockBreakEvent event) {
        if (sphereBlocks.isEmpty() || !sphereBlocks.contains(event.getBlock().getLocation())) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (block.getType() == Material.GLOWSTONE) {
            updateProgress(player, glowstonePoints);
            block.setType(Material.OBSIDIAN);
            // Rimuoviamolo dalla lista dei glowstone attivi per evitare che venga ripristinato dal task
            currentGlowstoneBlocks.remove(block.getLocation());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        } else if (block.getType() == Material.OBSIDIAN) {
            updateProgress(player, obsidianPoints);
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 1f);
        }
    }

    private void generateSphere() {
        Bukkit.broadcastMessage("§5[EVENTO] §dLa Sfera d'Ossidiana si sta materializzando...");
        // Fase 1: Genera la sfera piena
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);
                    if (center.distance(loc) <= radius) {
                        loc.getBlock().setType(Material.OBSIDIAN);
                        sphereBlocks.add(loc);
                    }
                }
            }
        }

        // --- MODIFICA: Fase 2: Identifica i blocchi sulla superficie ---
        for (Location loc : sphereBlocks) {
            // Controlla i 6 blocchi adiacenti
            if (isAir(loc.clone().add(1, 0, 0)) || isAir(loc.clone().add(-1, 0, 0)) ||
                    isAir(loc.clone().add(0, 1, 0)) || isAir(loc.clone().add(0, -1, 0)) ||
                    isAir(loc.clone().add(0, 0, 1)) || isAir(loc.clone().add(0, 0, -1))) {
                surfaceBlocks.add(loc);
            }
        }
    }

    // Metodo helper per controllare se un blocco non è nella sfera (quindi è "aria" per noi)
    private boolean isAir(Location loc) {
        return !sphereBlocks.contains(loc);
    }

    // --- MODIFICA: Logica di spawn del Glowstone completamente riscritta ---
    private void updateGlowstonePositions() {
        // Fase 1: Ripristina i vecchi blocchi di glowstone a ossidiana
        for (Location loc : currentGlowstoneBlocks) {
            if(loc.getBlock().getType() == Material.GLOWSTONE) {
                loc.getBlock().setType(Material.OBSIDIAN);
            }
        }
        currentGlowstoneBlocks.clear();

        if (surfaceBlocks.isEmpty()) return;

        // Fase 2: Scegli N nuove posizioni casuali sulla superficie
        Collections.shuffle(surfaceBlocks);

        int count = 0;
        for (Location loc : surfaceBlocks) {
            if (count >= glowstoneAmount) break;

            Block block = loc.getBlock();
            if (block.getType() == Material.OBSIDIAN) {
                block.setType(Material.GLOWSTONE);
                currentGlowstoneBlocks.add(loc); // Aggiungi alla lista dei glowstone attuali
                count++;
            }
        }
    }

    private void clearSphere() {
        for (Location loc : sphereBlocks) {
            loc.getBlock().setType(Material.AIR);
        }
        sphereBlocks.clear();
        surfaceBlocks.clear();
        currentGlowstoneBlocks.clear();
    }

    // --- METODI STANDARD DELL'EVENTO ---

    @Override
    public void updateBossBarTitle() {
        if (bossBar == null) return;
        TeamManager teamManager = plugin.getTeamManager();
        String title = this.bossBarTitle
                .replace("%team_one_name%", teamManager.getTeamOneName())
                .replace("%team_two_name%", teamManager.getTeamTwoName())
                .replace("%amount_1%", Formatter.format(teamProgress.getOrDefault(teamManager.getTeamOneName(), 0L)))
                .replace("%amount_2%", Formatter.format(teamProgress.getOrDefault(teamManager.getTeamTwoName(), 0L)))
                .replace("%time%", getRemainingTime());
        bossBar.setTitle(title);

        double progress = (double) (duration * 60 - getSecondPassed()) / (duration * 60);
        bossBar.setProgress(Math.max(0.0, progress));
    }

    @Override
    public boolean isFinished() {
        return getMinutePassed() >= duration;
    }

    @Override
    public EventType getType() {
        return EventType.METEOR_SHOWER; // Assicurati che questo sia il tipo corretto
    }

    @Override
    public BarColor bossBarColor() {
        return BarColor.PURPLE;
    }

    @Override
    public void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void unregisterListener() {
        BlockBreakEvent.getHandlerList().unregister(this);
    }

    private Location parseLocationFromString(String locString) {
        try {
            String[] parts = locString.split(";");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                plugin.getLogger().severe("MONDO NON VALIDO: '" + parts[0] + "'");
                return null;
            }
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (Exception e) {
            plugin.getLogger().severe("ERRORE DI CONFIGURAZIONE: La location '" + locString + "' non è formattata bene.");
            return null;
        }
    }
}