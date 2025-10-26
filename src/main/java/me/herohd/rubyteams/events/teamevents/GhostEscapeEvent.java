package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.block.Block; // Import necessario
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie; // Usiamo gli Zombie
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent; // Per farli targhettare
import org.bukkit.event.player.PlayerMoveEvent; // Import per le trappole
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

import static me.herohd.rubyteams.utils.Utils.parseLocationFromString; // Importiamo il parser

public class GhostEscapeEvent extends TeamEvent implements Listener {

    // Set dei giocatori ancora "vivi"
    private final Set<UUID> playersAlive = new HashSet<>();
    // Lista dei fantasmi spawnati (per la pulizia)
    private final List<Entity> spawnedGhosts = new ArrayList<>();
    private BukkitTask scoreTask;
    private BukkitTask paranoiaTask; // <-- NUOVO TASK

    // Impostazioni caricate dalla config
    private final Location arenaSpawn;
    private final Location spectatorSpawn;
    private final List<Location> ghostSpawnPoints = new ArrayList<>();
    private final int ghostCount;
    private final int ghostSpeedAmplifier;
    private final int pointsPerInterval;
    private final int pointIntervalSeconds;

    // --- NUOVI CAMPI PER DIFFICOLTÀ ---
    private final boolean paranoiaEnabled;
    private final int paranoiaInterval;
    private final PotionEffect paranoiaEffect;

    private final boolean trapsEnabled;
    private final Material trapBlock;
    private final PotionEffect trapEffect;
    // --- FINE NUOVI CAMPI ---

    // Teschio per i fantasmi
    private final ItemStack ghostHead = new ItemStack(Material.JACK_O_LANTERN);

    public GhostEscapeEvent(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);

        // Carichiamo le location dalla .yml
        this.arenaSpawn = parseLocationFromString(config.getString("arena-spawn"));
        this.spectatorSpawn = parseLocationFromString(config.getString("spectator-spawn"));
        for (String locString : config.getStringList("ghost-spawn-points")) {
            this.ghostSpawnPoints.add(parseLocationFromString(locString));
        }

        this.ghostCount = config.getInt("ghost-count");
        this.ghostSpeedAmplifier = config.getInt("ghost-speed-amplifier");
        this.pointsPerInterval = config.getInt("points-per-interval");
        this.pointIntervalSeconds = config.getInt("point-interval-seconds");

        // --- CARICA IMPOSTAZIONI DIFFICOLTÀ ---
        this.paranoiaEnabled = config.getBoolean("paranoia.enabled");
        this.paranoiaInterval = config.getInt("paranoia.interval-seconds");
        this.paranoiaEffect = parsePotionEffect(config.getString("paranoia.effect"));

        this.trapsEnabled = config.getBoolean("traps.enabled");
        this.trapBlock = Material.valueOf(config.getString("traps.trap-block").toUpperCase());
        this.trapEffect = parsePotionEffect(config.getString("traps.effect"));
        // --- FINE CARICAMENTO ---
    }

    @Override
    public void start() {
        super.start();
        playersAlive.clear();
        spawnedGhosts.clear();
        startTimeBasedFinishChecker(); // Avvia il timer di fine evento

        // 1. Teletrasporta tutti i giocatori e aggiungili ai "vivi"
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.teleport(arenaSpawn);
            playersAlive.add(player.getUniqueId());
            player.sendTitle("§c§lSOPRAVVIVI!", "§7Non farti prendere dai fantasmi!", 10, 60, 10);
        }

        // 2. Spawna i fantasmi
        spawnGhosts();

        // 3. Avvia il task che dà i punti
        this.scoreTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isFinished()) {
                    this.cancel();
                    return;
                }

                // Dai punti a tutti i giocatori ancora vivi
                for (UUID uuid : playersAlive) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        updateProgress(player, pointsPerInterval);
                    }
                }
                Bukkit.broadcastMessage("§a[EVENTO] §f+"+pointsPerInterval+" punti sopravvivenza ai giocatori rimasti!");
            }
        }.runTaskTimer(plugin, 20L * pointIntervalSeconds, 20L * pointIntervalSeconds);

        // 4. --- AVVIA TASK PARANOIA ---
        if (paranoiaEnabled && paranoiaEffect != null) {
            this.paranoiaTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (isFinished()) {
                        this.cancel();
                        return;
                    }
                    for (UUID uuid : playersAlive) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.addPotionEffect(paranoiaEffect);
                            p.playSound(p.getLocation(), Sound.ENTITY_GHAST_SCREAM, 0.7f, 1.2f);
                        }
                    }
                }
            }.runTaskTimer(plugin, 20L * paranoiaInterval, 20L * paranoiaInterval);
        }
    }

    private void spawnGhosts() {
        if (ghostSpawnPoints.isEmpty()) {
            plugin.getLogger().severe("Nessun punto di spawn per i fantasmi! L'evento non funzionerà.");
            return;
        }

        PotionEffect invis = new PotionEffect(PotionEffectType.INVISIBILITY, 999999, 1, false, false);
        PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, 999999, ghostSpeedAmplifier, false, false);

        for (int i = 0; i < ghostCount; i++) {
            Location spawnLoc = ghostSpawnPoints.get(i % ghostSpawnPoints.size()); // Cicla sui punti di spawn
            Zombie ghost = (Zombie) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);

            ghost.setCustomName("§cFantasma");
            ghost.setCustomNameVisible(false); // Nascosto
            ghost.setSilent(true);
            ghost.getEquipment().setHelmet(ghostHead); // Mette la zucca in testa
            ghost.getEquipment().setHelmetDropChance(0f); // Non la droppa
            ghost.addPotionEffect(invis);
            ghost.addPotionEffect(speed);

            spawnedGhosts.add(ghost);
        }
    }

    @Override
    public void finishEvent() {
        super.finishEvent(); // Gestisce ricompense, bossbar, ecc.

        // Ferma i task
        if (scoreTask != null) scoreTask.cancel();
        if (paranoiaTask != null) paranoiaTask.cancel(); // <-- FERMA NUOVO TASK
        scoreTask = null;
        paranoiaTask = null;

        // Rimuovi tutti i fantasmi
        for (Entity ghost : spawnedGhosts) {
            if (ghost != null && ghost.isValid()) {
                ghost.remove();
            }
        }
        spawnedGhosts.clear();

        // Teletrasporta tutti i giocatori (vivi o spettatori) in un posto sicuro
        for (UUID uuid : playersAlive) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.teleport(arenaSpawn);
        }
        playersAlive.clear();
    }

    // L'evento chiave: quando un giocatore viene COLPITO
    @EventHandler(priority = EventPriority.HIGH) // Priorità alta per annullare subito
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        // Se la vittima è un player E l'attaccante è un fantasma
        if (event.getEntity() instanceof Player && spawnedGhosts.contains(event.getDamager())) {
            Player player = (Player) event.getEntity();
            event.setCancelled(true); // Impedisce al giocatore di morire

            // Se il giocatore era ancora "vivo"
            if (playersAlive.contains(player.getUniqueId())) {
                playersAlive.remove(player.getUniqueId()); // Rimuovi dai vivi

                player.teleport(spectatorSpawn); // Manda agli spettatori
                player.sendTitle("§c§lSEI STATO PRESO!", "§7Guarda gli altri sopravvissuti...", 10, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_GHAST_DEATH, 1f, 1f);

                Bukkit.broadcastMessage("§c[EVENTO] §7Il giocatore §f" + player.getName() + " §7è stato preso!");
            }
        }
    }

    // --- NUOVO EVENT HANDLER PER LE TRAPPOLE ---
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!trapsEnabled || isFinished()) return;

        Player player = event.getPlayer();
        // Controlla solo se il giocatore è ancora vivo
        if (!playersAlive.contains(player.getUniqueId())) return;

        // Controlla se il blocco sotto il giocatore è la trappola
        Block blockUnder = event.getTo().clone().subtract(0, 0.1, 0).getBlock();
        if (blockUnder.getType() == trapBlock) {
            player.addPotionEffect(trapEffect);
        }
    }
    // --- FINE NUOVO HANDLER ---


    // Impedisce ai fantasmi di targhettare entità diverse dai player
    @EventHandler
    public void onGhostTarget(EntityTargetLivingEntityEvent event) {
        if (spawnedGhosts.contains(event.getEntity())) {
            if (!(event.getTarget() instanceof Player)) {
                event.setCancelled(true); // Non targhettare villager, golem, ecc.
            }
        }
    }

    // Gestisce i giocatori che quittano
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playersAlive.remove(event.getPlayer().getUniqueId());
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
        // L'evento finisce o quando scade il tempo, o quando tutti sono stati presi
        return getMinutePassed() >= duration || playersAlive.isEmpty();
    }

    @Override
    public EventType getType() {
        return EventType.GHOST_ESCAPE; // Il nuovo tipo
    }

    @Override
    public BarColor bossBarColor() {
        return BarColor.WHITE; // Un bel colore spettrale
    }

    @Override
    public void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void unregisterListener() {
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        EntityTargetLivingEntityEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);
        PlayerMoveEvent.getHandlerList().unregister(this); // <-- DEREGISTRA NUOVO LISTENER
    }

    // --- NUOVO METODO HELPER ---
    /**
     * Converte una stringa di configurazione (es. "SLOW;5;1") in un PotionEffect.
     */
    private PotionEffect parsePotionEffect(String configString) {
        if (configString == null || configString.isEmpty()) {
            return null;
        }
        try {
            String[] parts = configString.split(";");
            PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
            int duration = Integer.parseInt(parts[1]) * 20; // Converte secondi in tick
            int amplifier = Integer.parseInt(parts[2]);
            return new PotionEffect(type, duration, amplifier, true, false); // Ambient=true, Particles=false
        } catch (Exception e) {
            plugin.getLogger().warning("Errore nel parsing dell'effetto pozione: " + configString);
            return null;
        }
    }
}