package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.MazeGenerator;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SpookyMazeEvent extends TeamEvent implements Listener {

    private final Location corner;
    private final World mazeWorld;
    private final int mazeWidth, mazeHeight;
    private final Material wallMaterial, floorMaterial, endMaterial;

    private Location endBlockLocation;
    private final List<Location> mazeBlocks = new ArrayList<>(); // Usiamo una lista per la rimozione ordinata
    private final Set<UUID> playersFinished = new HashSet<>();
    private boolean isBuilding = false;

    public SpookyMazeEvent(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);

        String[] cornerParts = config.getString("maze.corner-location").split(";");
        this.mazeWorld = Bukkit.getWorld(cornerParts[0]);
        this.corner = new Location(mazeWorld, Double.parseDouble(cornerParts[1]), Double.parseDouble(cornerParts[2]), Double.parseDouble(cornerParts[3]));

        this.mazeWidth = config.getInt("maze.width");
        this.mazeHeight = config.getInt("maze.height");
        this.wallMaterial = Material.valueOf(config.getString("maze.wall-material"));
        this.floorMaterial = Material.valueOf(config.getString("maze.floor-material"));
        this.endMaterial = Material.valueOf(config.getString("maze.end-block-material"));
    }

    @Override
    public void start() {
        super.start();
        playersFinished.clear();
        mazeBlocks.clear();
        isBuilding = true;

        Bukkit.broadcastMessage("§8[EVENTO] §7Il labirinto maledetto si sta componendo in un'altra dimensione...");

        // Avvia l'intero processo di generazione e costruzione
        buildMazeAsynchronously();
    }

    private void buildMazeAsynchronously() {
        // --- FASE 1: Calcolo del labirinto (Asincrono) ---
        CompletableFuture.supplyAsync(() -> {
            MazeGenerator generator = new MazeGenerator(mazeWidth, mazeHeight);
            return generator.generate();
        }).thenAccept(mazeData -> {
            // --- FASE 2: Costruzione del labirinto a lotti (Sincrono) ---
            new BukkitRunnable() {
                private int z = 0;
                private int x = 0;
                private final int blocksPerTick = 1000; // Valore configurabile per bilanciare velocità e performance

                @Override
                public void run() {
                    for (int i = 0; i < blocksPerTick; i++) {
                        if (z >= mazeData.length) {
                            // Costruzione completata
                            onMazeBuilt();
                            this.cancel();
                            return;
                        }

                        Location loc = corner.clone().add(x, 0, z);
                        // Forziamo il caricamento del chunk se non è già caricato
                        if (!loc.getChunk().isLoaded()) {
                            loc.getChunk().load();
                        }

                        mazeBlocks.add(loc);
                        Block block = loc.getBlock();
                        if (mazeData[z][x] == 1) { // Muro
                            block.setType(wallMaterial, false); // false = non applicare fisica
                        } else { // Sentiero
                            block.setType(floorMaterial, false);
                        }

                        x++;
                        if (x >= mazeData[z].length) {
                            x = 0;
                            z++;
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L); // Esegui ogni tick
        });
    }

    private void onMazeBuilt() {
        isBuilding = false;

        // Posiziona il blocco finale
        this.endBlockLocation = corner.clone().add(mazeWidth - 1, 0, mazeHeight - 1);
        endBlockLocation.getBlock().setType(endMaterial);

        // Teletrasporta i giocatori all'inizio del labirinto
        Location startLocation = corner.clone().add(1, 1, 1);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(startLocation);
        }

        Bukkit.broadcastMessage("§c[EVENTO] §eTrovate l'uscita del labirinto! Il primo che la raggiunge darà più punti!");
        startTimeBasedFinishChecker(); // Avvia il timer dell'evento solo ora
    }


    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isFinished() || isBuilding || endBlockLocation == null) return;

        Player player = event.getPlayer();
        if (playersFinished.contains(player.getUniqueId())) return;

        // Usiamo toBlockLocation() per ignorare le coordinate decimali
        if (event.getTo().toBlockLocation().equals(endBlockLocation.toBlockLocation())) {
            int points = playersFinished.isEmpty() ? 10 : 3; // 10 punti al primo, 3 agli altri
            updateProgress(player, points);
            playersFinished.add(player.getUniqueId());

            Bukkit.broadcastMessage("§a[EVENTO] §e" + player.getName() + " §aha trovato l'uscita e ha guadagnato §e" + points + "§a punti!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

            // Teletrasporta il giocatore fuori dal labirinto
            player.teleport(corner.clone().add(0, 1, -3));
        }
    }

    @Override
    public void finishEvent() {
        super.finishEvent();
        // Rimuovi il labirinto a lotti per non laggare
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
        bossBar.setTitle(bossBarTitle.replace("%time%", getRemainingTime()));
        double progress = (double) (duration * 60 - getSecondPassed()) / (duration * 60);
        bossBar.setProgress(Math.max(0, progress));
    }

    @Override
    public boolean isFinished() {
        return getMinutePassed() >= duration;
    }

    @Override
    public EventType getType() { return EventType.SPOOKY_MAZE; }

    @Override
    public BarColor bossBarColor() { return BarColor.PURPLE; }

    @Override
    public void registerListener() { Bukkit.getPluginManager().registerEvents(this, plugin); }

    @Override
    public void unregisterListener() { PlayerMoveEvent.getHandlerList().unregister(this); }
}