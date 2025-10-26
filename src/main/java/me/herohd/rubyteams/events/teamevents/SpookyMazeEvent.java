package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import me.herohd.rubyteams.utils.MazeGenerator;
// --- NUOVE IMPORTAZIONI ---
import me.herohd.rubyteams.utils.MazeSolver;
import me.herohd.rubyteams.utils.MazeSolver.Point;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent; // <-- IMPORT AGGIUNTO
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors; // Nuova importazione

public class SpookyMazeEvent extends TeamEvent implements Listener {

    // Campi del labirinto
    private final Location corner;
    private final World mazeWorld;
    private final int mazeWidth, mazeHeight, wallHeight;
    private final Material wallMaterial, floorMaterial, endMaterial;

    // --- NUOVO CAMPO GUIDA ---
    private final Material guideBlockMaterial;

    // Campi dell'NPC
    private Entity entryNpc;
    private final Location npcLocation;
    private final String npcName;

    // Campi dello Scoreboard
    private Scoreboard mazeScoreboard;
    private Team mazeTeam;
    private final String teamName = "maze_event_team";

    // Tetto e Spawn
    private final boolean roofEnabled;
    private final Material roofMaterial;
    private final int spawnPlatformSize;
    private final Material spawnPlatformMaterial;
    private final Location startTeleportLocation;

    // Campi di stato
    private Location endBlockLocation;
    private final List<Location> mazeBlocks = new ArrayList<>();
    private final Set<UUID> playersFinished = new HashSet<>();
    private boolean isBuilding = false;

    // --- NUOVO CAMPO PER INVISIBILITÀ ---
    private final Set<UUID> playersInMaze = new HashSet<>();

    public SpookyMazeEvent(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);

        String[] cornerParts = config.getString("maze.corner-location").split(";");
        this.mazeWorld = Bukkit.getWorld(cornerParts[0]);
        this.corner = new Location(mazeWorld, Double.parseDouble(cornerParts[1]), Double.parseDouble(cornerParts[2]), Double.parseDouble(cornerParts[3]));
        this.mazeWidth = config.getInt("maze.width");
        this.mazeHeight = config.getInt("maze.height");
        this.wallHeight = config.getInt("maze.wall-height");
        this.wallMaterial = Material.valueOf(config.getString("maze.wall-material"));
        this.floorMaterial = Material.valueOf(config.getString("maze.floor-material"));
        this.endMaterial = Material.valueOf(config.getString("maze.end-block-material"));

        // --- CARICA MATERIALE GUIDA ---
        this.guideBlockMaterial = Material.valueOf(config.getString("maze.guide-block-material"));

        this.npcName = ChatColor.translateAlternateColorCodes('&', config.getString("maze.entry-npc.name"));
        this.npcLocation = parseFullLocationFromString(config.getString("maze.entry-npc.location"));

        this.roofEnabled = config.getBoolean("maze.roof.enabled");
        this.roofMaterial = Material.valueOf(config.getString("maze.roof.material"));
        this.spawnPlatformSize = config.getInt("maze.spawn-platform.size");
        this.spawnPlatformMaterial = Material.valueOf(config.getString("maze.spawn-platform.material"));

        double platformCenter = (spawnPlatformSize - 1) / 2.0;
        this.startTeleportLocation = corner.clone().add(platformCenter, 1, platformCenter);
    }

    @Override
    public void start() {
        super.start();
        playersFinished.clear();
        mazeBlocks.clear();
        isBuilding = true;
        playersInMaze.clear(); // <-- AGGIUNTO: Pulisce il set all'avvio

        this.mazeScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.mazeTeam = mazeScoreboard.registerNewTeam(teamName);
        this.mazeTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER); // <-- Errore di sintassi corretto

        Bukkit.broadcastMessage("§8[EVENTO] §7Il labirinto maledetto si sta componendo in un'altra dimensione...");

        buildMazeAsynchronously();
    }

    private void buildMazeAsynchronously() {
        // --- LOGICA ASINCRONA AGGIORNATA ---
        CompletableFuture.supplyAsync(() -> {
                    // 1. Genera il labirinto
                    MazeGenerator generator = new MazeGenerator(mazeWidth, mazeHeight);
                    int[][] mazeData = generator.generate();

                    // 2. Definisci Inizio e Fine
                    Point startPoint = new Point((spawnPlatformSize - 1) / 2, spawnPlatformSize); // Uscita dallo spawn
                    Point endPoint = new Point(mazeWidth - 2, mazeHeight - 2); // Punto premio

                    // 3. Risolvi il labirinto
                    List<Point> solutionPath = MazeSolver.solve(mazeData, startPoint, endPoint);

                    // 4. Ritorna entrambi i risultati
                    return new AbstractMap.SimpleEntry<>(mazeData, solutionPath);

                }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable))
                .thenAccept(result -> {
                    // --- LOGICA SINCRONA AGGIORNATA ---

                    int[][] mazeData = result.getKey();
                    List<Point> solutionPath = result.getValue();

                    // Converti la lista del percorso in un Set di stringhe "x:z" per ricerche veloci
                    Set<String> solutionSet = solutionPath.stream()
                            .map(p -> p.x + ":" + p.z)
                            .collect(Collectors.toSet());

                    new BukkitRunnable() {
                        private int z = 0;
                        private int x = 0;
                        private final int blocksPerTick = 1000;

                        @Override
                        public void run() {
                            for (int i = 0; i < blocksPerTick; i++) {
                                if (z >= mazeData.length) {
                                    onMazeBuilt(mazeData); // Passa mazeData
                                    this.cancel();
                                    return;
                                }

                                Location baseLoc = corner.clone().add(x, 0, z);
                                if (!baseLoc.getChunk().isLoaded()) {
                                    baseLoc.getChunk().load();
                                }

                                // 1. Piattaforma di Spawn
                                if (x < spawnPlatformSize && z < spawnPlatformSize) {
                                    Block floorBlock = baseLoc.getBlock();
                                    floorBlock.setType(spawnPlatformMaterial);
                                    mazeBlocks.add(floorBlock.getLocation());
                                    // Pulisci l'area sopra
                                    for (int y = 1; y <= wallHeight; y++) {
                                        baseLoc.clone().add(0, y, 0).getBlock().setType(Material.AIR);
                                    }
                                }
                                // 2. Labirinto e Muri
                                else {
                                    if (mazeData[z][x] == 1) { // Muro
                                        for (int y = 0; y < wallHeight; y++) {
                                            Location wallBlockLoc = baseLoc.clone().add(0, y, 0);
                                            wallBlockLoc.getBlock().setType(wallMaterial);
                                            mazeBlocks.add(wallBlockLoc);
                                        }
                                    } else { // Sentiero
                                        Block floorBlock = baseLoc.getBlock();
                                        floorBlock.setType(floorMaterial);
                                        mazeBlocks.add(floorBlock.getLocation());

                                        // --- METTI I BLOCCHI GUIDA ---
                                        if (solutionSet.contains(x + ":" + z)) {
                                            Location guideBlockLoc = baseLoc.clone().subtract(0, 1, 0);
                                            guideBlockLoc.getBlock().setType(guideBlockMaterial);
                                            mazeBlocks.add(guideBlockLoc); // Aggiungi alla pulizia
                                        }

                                        // Pulisci l'area sopra
                                        for (int y = 1; y < wallHeight; y++) {
                                            baseLoc.clone().add(0, y, 0).getBlock().setType(Material.AIR);
                                        }
                                    }
                                }

                                // 3. Tetto
                                if (roofEnabled) {
                                    Location roofBlockLoc = baseLoc.clone().add(0, wallHeight, 0);
                                    if (roofBlockLoc.getBlock().getType() == Material.AIR) {
                                        roofBlockLoc.getBlock().setType(roofMaterial);
                                        mazeBlocks.add(roofBlockLoc);
                                    }
                                }

                                x++;
                                if (x >= mazeData[z].length) {
                                    x = 0;
                                    z++;
                                }
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                });
    }

    // Metodo per perforare un muro (pulire il pavimento e l'aria)
    private void carvePath(Location loc) {
        Block floor = loc.getBlock();
        floor.setType(floorMaterial);
        mazeBlocks.add(floor.getLocation());

        for (int y = 1; y < wallHeight; y++) {
            Location airLoc = loc.clone().add(0, y, 0);
            airLoc.getBlock().setType(Material.AIR);
            mazeBlocks.add(airLoc);
        }
    }

    private void onMazeBuilt(int[][] mazeData) {
        isBuilding = false;

        // 1. Definiamo la posizione del blocco premio (Diamante)
        this.endBlockLocation = corner.clone().add(mazeWidth - 2, 0, mazeHeight - 2);

        // 2. Piazziamo il blocco premio (Diamante) sul pavimento
        carvePath(endBlockLocation); // Scava il sentiero
        endBlockLocation.getBlock().setType(endMaterial); // Piazza il diamante sul pavimento

        // 3. Creiamo un'entrata per collegare lo spawn al labirinto
        int entranceZ = (spawnPlatformSize - 1) / 2; // Centro della parete
        carvePath(corner.clone().add(spawnPlatformSize, 0, entranceZ)); // Muro a X=size, Z=centro

        // 4. Creiamo un'uscita nel muro perimetrale
        carvePath(corner.clone().add(mazeWidth - 1, 0, mazeHeight - 2)); // Muro a X=max, Z=premio

        // Spawna l'NPC
        if (npcLocation != null) {
            Location spawnLoc = npcLocation.clone().add(0.5, 0, 0.5);
            Slime slime = (Slime) spawnLoc.getWorld().spawn(spawnLoc, Slime.class);
            slime.setSize(3);
            slime.setInvulnerable(true);
            slime.setCustomName(npcName);
            slime.setCustomNameVisible(true);
            slime.setGravity(false);
            slime.setAI(false);
            slime.setSilent(true);
            this.entryNpc = slime;
        }

        Bukkit.broadcastMessage("§c[EVENTO] §eIl labirinto è pronto! Clicca sul §f" + npcName + " §eper entrare!");
        startTimeBasedFinishChecker();
    }

    /**
     * Gestisce la logica di visibilità quando un giocatore lascia il labirinto
     * (finendo, morendo, quittando o alla fine dell'evento).
     * Usa i metodi deprecati per NON rimuovere i giocatori dalla TAB.
     */
    @SuppressWarnings("deprecation") // Sopprimiamo l'avviso per hidePlayer/showPlayer
    private void handlePlayerLeave(Player player) {
        UUID playerUuid = player.getUniqueId();
        // Rimuovi il giocatore dal set. Se non c'era, esci.
        if (!playersInMaze.remove(playerUuid)) {
            return;
        }

        // Il giocatore ha lasciato il labirinto.
        // Rendilo di nuovo visibile a TUTTI i giocatori online
        // e rendi TUTTI i giocatori online di nuovo visibili a lui.
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;

            player.showPlayer(other);
            other.showPlayer(player);
        }
    }


    @EventHandler
    @SuppressWarnings("deprecation") // Sopprimiamo l'avviso per hidePlayer
    public void onNpcInteract(PlayerInteractAtEntityEvent event) {
        if (isBuilding || isFinished() || entryNpc == null) return;

        if (event.getRightClicked().equals(this.entryNpc)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            UUID playerUuid = player.getUniqueId();

            // --- INIZIO LOGICA INVISIBILITÀ ---
            // 1. Nascondi tutti i giocatori GIÀ nel labirinto al nuovo giocatore
            //    e nascondi il nuovo giocatore a loro.
            for (UUID otherUuid : playersInMaze) {
                Player other = Bukkit.getPlayer(otherUuid);
                if (other != null) {
                    player.hidePlayer(other); // Usa il metodo deprecato
                    other.hidePlayer(player); // Usa il metodo deprecato
                }
            }
            // 2. Aggiungi il nuovo giocatore al set
            playersInMaze.add(playerUuid);
            // --- FINE LOGICA INVISIBILITÀ ---

            player.setScoreboard(mazeScoreboard);
            mazeTeam.addEntry(player.getName());

            player.teleport(startTeleportLocation);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMEN_TELEPORT, 1f, 1f);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isFinished() || isBuilding || endBlockLocation == null) return;

        Player player = event.getPlayer();
        if (playersFinished.contains(player.getUniqueId())) return;

        Block blockUnderPlayer = event.getTo().clone().subtract(0, 1, 0).getBlock();

        if (blockUnderPlayer.getLocation().equals(endBlockLocation)) {

            int points;
            if (playersFinished.isEmpty()) {
                points = 10;
            } else if (playersFinished.size() == 1) {
                points = 5;
            } else if (playersFinished.size() == 2) {
                points = 3;
            } else {
                points = 1;
            }

            updateProgress(player, points);
            playersFinished.add(player.getUniqueId());

            Bukkit.broadcastMessage("§a[EVENTO] §e" + player.getName() + " §a(#" + playersFinished.size() + ") ha trovato l'uscita e ha guadagnato §e" + points + "§a punti!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

            handlePlayerLeave(player); // <-- AGGIUNTO: Ripristina la visibilità

            mazeTeam.removeEntry(player.getName());
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

            player.teleport(npcLocation.clone().add(0, 0.5, 0));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Rimuovi il giocatore dal tracciamento
        // Non serve ri-mostrare, visto che sta uscendo.
        playersInMaze.remove(event.getPlayer().getUniqueId());
    }


    @Override
    public void finishEvent() {
        super.finishEvent();
        if (entryNpc != null) {
            entryNpc.remove();
            entryNpc = null;
        }
        if (mazeTeam != null) {
            try {
                // Usiamo new HashSet per evitare ConcurrentModificationException
                for (String entry : new HashSet<>(mazeTeam.getEntries())) {
                    Player p = Bukkit.getPlayerExact(entry);
                    if (p != null) {
                        handlePlayerLeave(p); // <-- USA IL NUOVO METODO
                        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    } else {
                        // Il giocatore è offline, rimuovi solo il suo UUID
                        UUID offlineUuid = Bukkit.getOfflinePlayer(entry).getUniqueId();
                        playersInMaze.remove(offlineUuid);
                    }
                    mazeTeam.removeEntry(entry); // Rimuovi dal team scoreboard
                }
            } finally {
                mazeTeam.unregister();
                mazeTeam = null;
                mazeScoreboard = null;
            }
        }

        playersInMaze.clear(); // Pulizia finale di sicurezza

        new BukkitRunnable() {
            private int index = 0;
            private final int blocksPerTick = 1500;
            @Override
            public void run() {
                for(int i = 0; i < blocksPerTick; i++){
                    if(index >= mazeBlocks.size()){
                        this.cancel();
                        return;
                    }
                    mazeBlocks.get(index).getBlock().setType(Material.AIR);
                    index++;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void updateBossBarTitle() {
        if(isBuilding) {
            bossBar.setTitle("§8Costruzione del labirinto in corso...");
            bossBar.setProgress(1.0);
            return;
        }

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
        if (isBuilding) return false;
        return getMinutePassed() >= duration;
    }

    @Override
    public EventType getType() { return EventType.SPOOKY_MAZE; }

    @Override
    public BarColor bossBarColor() { return BarColor.PURPLE; }

    @Override
    public void registerListener() { Bukkit.getPluginManager().registerEvents(this, plugin); }

    @Override
    public void unregisterListener() {
        PlayerMoveEvent.getHandlerList().unregister(this);
        PlayerInteractAtEntityEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this); // <-- AGGIUNTO
    }

    private Location parseFullLocationFromString(String locString) {
        try {
            String[] parts = locString.split(";");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                plugin.getLogger().severe("MONDO NON VALIDO: '" + parts[0] + "' in SpookyMazeEvent");
                return null;
            }
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = (parts.length > 4) ? Float.parseFloat(parts[4]) : 0f;
            float pitch = (parts.length > 5) ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            plugin.getLogger().severe("ERRORE DI CONFIGURAZIONE: La location NPC '" + locString + "' non è formattata bene.");
            return null;
        }
    }
}