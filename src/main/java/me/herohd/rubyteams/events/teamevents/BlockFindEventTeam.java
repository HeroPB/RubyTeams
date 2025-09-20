package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BlockFindEventTeam extends TeamEvent implements Listener {

    private final List<Map.Entry<ItemStack, String>> blocksToFind;
    private ItemStack currentTargetBlock;
    private String currentTargetBlockName;

    // Logica a Round
    private final int totalRounds;
    private int currentRound = 0;
    private final int roundDurationSeconds;
    private int roundTimeLeft;
    private BukkitTask roundTask;
    private final Set<UUID> playersFoundThisRound = new HashSet<>();

    public BlockFindEventTeam(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);
        this.totalRounds = config.getInt("rounds");
        this.roundDurationSeconds = (int) duration; // 'duration' ora ha un senso chiaro
        this.blocksToFind = new ArrayList<>();

        for (String s : config.getStringList("blocks")) {
            String[] parts = s.split(";", 3);
            ItemStack itemStack = new ItemStack(Material.valueOf(parts[0]), 1, Byte.parseByte(parts[1]));
            blocksToFind.add(new AbstractMap.SimpleEntry<>(itemStack, parts[2]));
        }
    }

    @Override
    public void start() {
        super.start(); // Chiama la logica di start della classe base
        currentRound = 0;
        startNextRound(); // Avvia il primo round

    }

    private void startNextRound() {
        // Pulisci i dati del round precedente
        if (roundTask != null) roundTask.cancel();
        playersFoundThisRound.clear();
        currentRound++;

        // Se abbiamo finito i round, termina l'evento
        if (isFinished()) {
            finishEvent();
            return;
        }

        // Scegli un nuovo blocco casuale
        Map.Entry<ItemStack, String> randomEntry = blocksToFind.get(new Random().nextInt(blocksToFind.size()));
        this.currentTargetBlock = randomEntry.getKey();
        this.currentTargetBlockName = randomEntry.getValue();
        this.roundTimeLeft = roundDurationSeconds;

        // Annuncia il nuovo blocco
        Bukkit.broadcastMessage("§6§l[EVENTO] §fProssimo blocco da trovare: §e§l" + currentTargetBlockName);
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        // Avvia il timer per questo round
        this.roundTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (roundTimeLeft <= 0) {
                    startNextRound(); // Avvia il round successivo
                    return;
                }
                updateBossBarTitle();
                roundTimeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public boolean isFinished() {
        return currentRound > totalRounds;
    }

    @Override
    public void finishEvent() {
        if (roundTask != null) {
            roundTask.cancel();
            roundTask = null;
        }
        super.finishEvent();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (currentTargetBlock == null) return;

        Player player = event.getPlayer();
        ItemStack brokenBlock = new ItemStack(event.getBlock().getType(), 1, event.getBlock().getData());

        // Controlla se è il blocco giusto E se il giocatore non l'ha già trovato in questo round
        if (brokenBlock.isSimilar(currentTargetBlock) && !playersFoundThisRound.contains(player.getUniqueId())) {
            playersFoundThisRound.add(player.getUniqueId());
            updateProgress(player, 1); // Ogni blocco trovato dà 1 punto
            player.sendMessage("§a+1 punto per il tuo team!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        }
    }

    @Override
    public void updateBossBarTitle() {
        TeamManager teamManager = plugin.getTeamManager();
        String teamOneName = teamManager.getTeamOneName();
        String teamTwoName = teamManager.getTeamTwoName();

        bossBar.setTitle(bossBarTitle
                .replace("%team_one_name%", teamOneName)
                .replace("%team_two_name%", teamTwoName)
                .replace("%amount_1%", Formatter.format(teamProgress.getOrDefault(teamOneName, 0L)))
                .replace("%amount_2%", Formatter.format(teamProgress.getOrDefault(teamTwoName, 0L)))
                .replace("%block%", currentTargetBlockName != null ? currentTargetBlockName : "...")
                .replace("%round%", String.valueOf(currentRound))
                .replace("%total_rounds%", String.valueOf(totalRounds))
                .replace("%time%", String.valueOf(roundTimeLeft)) // Sostituito con il tempo del round
        );

        // La barra dei progressi ora mostra il tempo rimanente del round
        double progress = (double) roundTimeLeft / roundDurationSeconds;
        bossBar.setProgress(Math.max(0, progress));
    }

    @Override
    public EventType getType() {
        return EventType.BLOCK_FIND;
    }

    @Override
    public BarColor bossBarColor() {
        return BarColor.BLUE;
    }

    @Override
    public void registerListener() {
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void unregisterListener() {
        BlockBreakEvent.getHandlerList().unregister(this);
    }
}
