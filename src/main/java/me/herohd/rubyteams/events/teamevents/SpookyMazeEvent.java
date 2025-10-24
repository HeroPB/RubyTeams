package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import me.herohd.rubyteams.utils.MazeGenerator;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SpookyMazeEvent extends TeamEvent implements Listener {

    // Campi del labirinto
    private final Location corner;
    private final World mazeWorld;
    private final int mazeWidth, mazeHeight, wallHeight;
    private final Material wallMaterial, floorMaterial, endMaterial;

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

        this.mazeScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.mazeTeam = mazeScoreboard.registerNewTeam(teamName);
        this.mazeTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        Bukkit.broadcastMessage("§8[EVENTO] §7Il labirinto maledetto si sta componendo in un'altra dimensione...");

        buildMazeAsynchronously();
    }

    private void buildMazeAsynchronously() {
        CompletableFuture.supplyAsync(() -> {
                    MazeGenerator generator = new MazeGenerator(mazeWidth, mazeHeight);
                    return generator.generate();
                }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable))
                .thenAccept(mazeData -> {
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

                                // --- LOGICA DI COSTRUZIONE CORRETTA ---
                                boolean isPerimeter = (x == 0 || z == 0 || x == mazeData[z].length - 1 || z == mazeData.length - 1);

                                // 1. Piattaforma di Spawn (Priorità 1)
                                if (x < spawnPlatformSize && z < spawnPlatformSize) {
                                    Block floorBlock = baseLoc.getBlock();
                                    floorBlock.setType(spawnPlatformMaterial);
                                    mazeBlocks.add(floorBlock.getLocation());
                                    // Pulisci l'area sopra (fino al tetto)
                                    for (int y = 1; y <= wallHeight; y++) {
                                        baseLoc.clone().add(0, y, 0).getBlock().setType(Material.AIR);
                                    }
                                }
                                // 2. Costruzione Perimetro (Priorità 2)
                                else if (isPerimeter) {
                                    int perimeterWallHeight = roofEnabled ? wallHeight + 1 : wallHeight;
                                    for (int y = 0; y < perimeterWallHeight; y++) {
                                        Location wallBlockLoc = baseLoc.clone().add(0, y, 0);
                                        wallBlockLoc.getBlock().setType(wallMaterial);
                                        mazeBlocks.add(wallBlockLoc);
                                    }
                                }
                                // 3. Labirinto Interno (Priorità 3)
                                else {
                                    if (mazeData[z][x] == 1) { // Muro interno
                                        for (int y = 0; y < wallHeight; y++) {
                                            Location wallBlockLoc = baseLoc.clone().add(0, y, 0);
                                            wallBlockLoc.getBlock().setType(wallMaterial);
                                            mazeBlocks.add(wallBlockLoc);
                                        }
                                    } else { // Sentiero interno
                                        Block floorBlock = baseLoc.getBlock();
                                        floorBlock.setType(floorMaterial);
                                        mazeBlocks.add(floorBlock.getLocation());
                                        // Pulisci l'area sopra
                                        for (int y = 1; y < wallHeight; y++) {
                                            baseLoc.clone().add(0, y, 0).getBlock().setType(Material.AIR);
                                        }
                                    }
                                }

                                // 4. Tetto
                                if (roofEnabled) {
                                    Location roofBlockLoc = baseLoc.clone().add(0, wallHeight, 0);
                                    // Piazza il tetto SOLO se non c'è già un muro perimetrale più alto
                                    // E non sopra lo spawn (che è già stato pulito)
                                    if (roofBlockLoc.getBlock().getType() == Material.AIR && !(x < spawnPlatformSize && z < spawnPlatformSize)) {
                                        roofBlockLoc.getBlock().setType(roofMaterial);
                                        mazeBlocks.add(roofBlockLoc);
                                    }
                                }
                                // --- FINE LOGICA CORRETTA ---

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

    // onMazeBuilt, onNpcInteract, onPlayerMove, finishEvent, updateBossBarTitle,
    // isFinished, e tutti gli altri metodi rimangono IDENTICI a prima.

    // ... (Copia e incolla il resto della classe dalla versione precedente) ...
    // ... (da onMazeBuilt() fino alla fine) ...
    // ...

    private void onMazeBuilt(int[][] mazeData) {
        isBuilding = false;

        // Posiziona il blocco finale.
        // Lo spostiamo di 1 blocco all'interno.
        this.endBlockLocation = corner.clone().add(mazeWidth - 2, 0, mazeHeight - 2);

        // Assicurati che il punto finale sia un sentiero, altrimenti scava
        if (mazeData[mazeHeight - 2][mazeWidth - 2] == 1) {
            endBlockLocation.getBlock().setType(floorMaterial);
            // Pulisci l'aria sopra il blocco finale
            for (int y = 1; y < wallHeight; y++) {
                endBlockLocation.clone().add(0, y, 0).getBlock().setType(Material.AIR);
            }
        }
        // Metti il blocco premio *sopra* il pavimento
        endBlockLocation.clone().add(0, 1, 0).getBlock().setType(endMaterial);

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

    @EventHandler
    public void onNpcInteract(PlayerInteractAtEntityEvent event) {
        if (isBuilding || isFinished() || entryNpc == null) return;

        if (event.getRightClicked().equals(this.entryNpc)) {
            event.setCancelled(true);
            Player player = event.getPlayer();

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

        // Controlla il blocco *sotto* i piedi del giocatore
        Block blockUnderPlayer = event.getTo().clone().subtract(0, 1, 0).getBlock();

        // Confronta la location del blocco pavimento finale
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

            mazeTeam.removeEntry(player.getName());
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

            // Teleporta allo spawn (location dell'NPC)
            player.teleport(npcLocation.clone().add(0, 0.5, 0));
        }
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
                for (String entry : mazeTeam.getEntries()) {
                    Player p = Bukkit.getPlayerExact(entry);
                    if (p != null) {
                        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    }
                }
            } finally {
                mazeTeam.unregister();
                mazeTeam = null;
                mazeScoreboard = null;
            }
        }

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