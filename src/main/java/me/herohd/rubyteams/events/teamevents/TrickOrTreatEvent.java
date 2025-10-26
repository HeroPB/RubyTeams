package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import me.herohd.rubyteams.utils.ProbabilityUtils;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType; // Import per EntityType
import org.bukkit.entity.Player;
import org.bukkit.entity.Witch; // Import per la Strega
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot; // Import per il controllo della mano
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

// Non importiamo più 'Slime'

public class TrickOrTreatEvent extends TeamEvent implements Listener {

    // Traccia gli NPC attivi (UUID) e la location (dalla config) che occupano
    private final Map<UUID, Location> activeNpcs = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private final List<Location> spawnLocations = new ArrayList<>(); // Lista di tutte le possibili location
    private final String npcName;
    private final int trickPoints;
    private final double trickChance;
    private final int cooldownSeconds;
    private final int maxActiveNpcs; // Quanti NPC attivi

    public TrickOrTreatEvent(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);

        // Carichiamo i dati dalla .yml
        this.npcName = ChatColor.translateAlternateColorCodes('&', config.getString("npc-name"));
        this.trickPoints = config.getInt("trick-points");
        this.trickChance = config.getDouble("trick-chance");
        this.cooldownSeconds = config.getInt("cooldown-seconds");
        this.maxActiveNpcs = config.getInt("active-npcs");

        // Carica e valida tutte le location
        this.spawnLocations.clear();
        for (String locString : config.getStringList("npc-locations")) {
            Location loc = parseLocationFromString(locString); // Usa il metodo helper
            if (loc != null && loc.getWorld() != null) {
                this.spawnLocations.add(loc);
            } else {
                plugin.getLogger().warning("Posizione NPC non valida in trick_or_treat.yml: " + locString);
            }
        }
    }

    @Override
    public void start() {
        super.start();
        cooldowns.clear();
        activeNpcs.clear();
        startTimeBasedFinishChecker(); // Avvia il timer di fine evento

        if (spawnLocations.isEmpty()) {
            plugin.getLogger().severe("Nessuna location valida per gli NPC in trick_or_treat.yml! Evento interrotto.");
            finishEvent();
            return;
        }

        // Spawna il set iniziale di NPC
        List<Location> startingSpawns = new ArrayList<>(this.spawnLocations);
        Collections.shuffle(startingSpawns);

        int count = Math.min(maxActiveNpcs, startingSpawns.size());
        for (int i = 0; i < count; i++) {
            spawnWitch(startingSpawns.get(i));
        }
    }

    /**
     * Helper method per spawnare una strega configurata
     */
    private void spawnWitch(Location loc) {
        if (loc == null || loc.getWorld() == null) return;

        Location spawnLoc = loc.clone().add(0.5, 0, 0.5); // Centra la strega
        spawnLoc.getChunk().load();

        // Spawna una Strega
        Witch witch = (Witch) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.WITCH);

        witch.setInvulnerable(true); // API Spigot 1.12.2
        witch.setCustomName(npcName);
        witch.setCustomNameVisible(true);
        witch.setAI(false); // Fondamentale: le impedisce di muoversi, attaccare o bere pozioni
        witch.setSilent(true); // Non fa la risatina
        witch.setCollidable(false); // I giocatori non ci sbattono contro

        activeNpcs.put(witch.getUniqueId(), loc); // Traccia l'NPC attivo e la sua location
    }


    @Override
    public void finishEvent() {
        // Rimuovi tutti gli NPC spawnati
        for (UUID npcUuid : activeNpcs.keySet()) {
            Entity npc = Bukkit.getEntity(npcUuid); // Prende l'entità dal suo UUID
            if (npc != null && npc.isValid()) {
                npc.remove();
            }
        }
        activeNpcs.clear();
        cooldowns.clear();
        super.finishEvent(); // Esegue la logica base (ricompense, bossbar, ecc.)
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractAtEntityEvent event) {
        // 1. --- CONTROLLO MANO (per fix doppio click) ---
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Entity clicked = event.getRightClicked();

        // 2. --- CONTROLLO NPC (usa la mappa) ---
        if (activeNpcs.containsKey(clicked.getUniqueId())) {
            event.setCancelled(true);

            // 3. Controlla se il giocatore è in un team
            String team = plugin.getTeamManager().getTeam(player);
            if (team == null) {
                player.sendMessage("§cDevi essere in un team per partecipare!");
                return;
            }

            // 4. Controlla il cooldown
            if (cooldowns.containsKey(player.getUniqueId()) && cooldowns.get(player.getUniqueId()) > System.currentTimeMillis()) {
                long timeLeft = (cooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
                player.sendMessage("§cDevi attendere altri " + (timeLeft + 1) + " secondi!");
                return;
            }

            // 5. Applica la logica Dolcetto/Scherzetto
            if (ProbabilityUtils.checkChance(this.trickChance)) {
                // --- DOLCETTO ---
                updateProgress(player, this.trickPoints);
                player.sendMessage("§aDOLCETTO! §fHai guadagnato §e" + this.trickPoints + " punto §fper il tuo team!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                // --- SCHERZETTO ---
                player.sendMessage("§8SCHERZETTO! §7Una fattura ti ha colpito!");
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_HURT, 1f, 0.8f);

                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 10, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 5, 1));
            }

            // 6. Applica il cooldown
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000));

            // 7. --- LOGICA DI RESPAWN ---
            Location oldLocation = activeNpcs.remove(clicked.getUniqueId());
            clicked.remove();

            // Trova una location non attualmente usata
            List<Location> availableLocations = new ArrayList<>(this.spawnLocations);
            availableLocations.removeAll(activeNpcs.values()); // Rimuove tutte le location già occupate

            Location newLocation;
            if (availableLocations.isEmpty()) {
                // Fallback: se (per assurdo) tutte le location sono piene, la riusa
                newLocation = oldLocation;
            } else {
                // Altrimenti, ne prende una a caso tra quelle libere
                Collections.shuffle(availableLocations);
                newLocation = availableLocations.get(0);
            }

            // Spawna la nuova strega con un tick di ritardo per sicurezza
            new BukkitRunnable() {
                @Override
                public void run() {
                    spawnWitch(newLocation);
                }
            }.runTaskLater(plugin, 1L);
        }
    }


    // --- Metodi standard dell'evento ---

    @Override
    public void updateBossBarTitle() {
        TeamManager tm = plugin.getTeamManager();
        bossBar.setTitle(this.bossBarTitle
                .replace("%team_one_name%", tm.getTeamOneName())
                .replace("%team_two_name%", tm.getTeamTwoName())
                .replace("%amount_1%", Formatter.format(teamProgress.getOrDefault(tm.getTeamOneName(), 0L)))
                .replace("%amount_2%", Formatter.format(teamProgress.getOrDefault(tm.getTeamTwoName(), 0L)))
                .replace("%time%", getRemainingTime())
        );

        double progress = (double) (duration * 60 - getSecondPassed()) / (duration * 60);
        bossBar.setProgress(Math.max(0, progress));
    }

    @Override
    public boolean isFinished() {
        return getMinutePassed() >= duration;
    }

    @Override
    public EventType getType() {
        return EventType.TRICK_OR_TREAT; // Il tipo che avevamo già registrato
    }

    @Override
    public BarColor bossBarColor() {
        return BarColor.RED;
    }

    @Override
    public void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void unregisterListener() {
        PlayerInteractAtEntityEvent.getHandlerList().unregister(this);
    }

    // Metodo helper per parsare le location (già presente in SpookyMaze)
    private Location parseLocationFromString(String locString) {
        try {
            String[] parts = locString.split(";");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                plugin.getLogger().severe("MONDO NON VALIDO: '" + parts[0] + "' in TrickOrTreatEvent");
                return null;
            }
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = (parts.length > 4) ? Float.parseFloat(parts[4]) : 0f;
            float pitch = (parts.length > 5) ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            plugin.getLogger().severe("ERRORE DI CONFIGURAZIONE: La location '" + locString + "' non è formattata bene.");
            return null;
        }
    }
}