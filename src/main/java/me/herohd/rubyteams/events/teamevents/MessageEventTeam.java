package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class MessageEventTeam extends TeamEvent implements Listener {

    private final List<String> phrases;
    private String currentTargetPhrase;

    // Logica a Round
    private final int totalRounds;
    private int currentRound = 0;
    private final int roundDurationSeconds = 15;
    private int roundTimeLeft;
    private BukkitTask roundTask;
    private final Set<UUID> playersScoredThisRound = new HashSet<>();

    public MessageEventTeam(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);
        this.totalRounds = (int) duration; // 'duration' ora rappresenta il numero di round
        this.phrases = config.getStringList("phrases");
    }

    @Override
    public void start() {
        super.start();
        startNextRound();
    }

    private void startNextRound() {
        if (roundTask != null) roundTask.cancel();
        playersScoredThisRound.clear();
        currentRound++;

        if (isFinished()) {
            finishEvent();
            return;
        }

        if (phrases.isEmpty()) {
            plugin.getLogger().severe("Nessuna frase trovata nella configurazione per l'evento Simon Says!");
            finishEvent();
            return;
        }

        // Scegli una nuova frase e randomizza le maiuscole/minuscole
        String originalPhrase = phrases.get(new Random().nextInt(phrases.size()));
        this.currentTargetPhrase = randomizeCase(originalPhrase);
        this.roundTimeLeft = roundDurationSeconds;

        // Invia il titolo a tutti i giocatori
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§c§lSIMON DICE", "§f\"" + currentTargetPhrase + "\"");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }

        // Avvia il timer del round
        this.roundTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (roundTimeLeft <= 0) {
                    startNextRound();
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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMessageSend(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Controlla se il messaggio è corretto E se il giocatore non ha già segnato in questo round
        if (message.equals(currentTargetPhrase) && !playersScoredThisRound.contains(player.getUniqueId())) {
            int a = 1;
            event.setCancelled(true);
            if (playersScoredThisRound.isEmpty()) {
                updateProgress(player, 5); // Ogni frase corretta dà 1 punto
                a = 5;
            } else
                updateProgress(player, 1); // Ogni frase corretta dà 1 punto
            playersScoredThisRound.add(player.getUniqueId());

            // Usa un task sincrono per inviare messaggi/suoni
            int finalA = a;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.broadcastMessage("§6§lEVENTO§8: §e" + player.getName() + " §fha fatto guadagnare §e" + finalA + "§f punti al suo team");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                }
            }.runTask(plugin);
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
                .replace("%round%", String.valueOf(currentRound))
                .replace("%total_rounds%", String.valueOf(totalRounds))
        );

        double progress = (double) roundTimeLeft / roundDurationSeconds;
        bossBar.setProgress(Math.max(0, progress));
    }

    /**
     * Prende una stringa e ne randomizza le maiuscole e le minuscole.
     */
    private String randomizeCase(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        Random random = new Random();
        for (char c : input.toCharArray()) {
            if (random.nextBoolean()) {
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    @Override
    public EventType getType() {
        return EventType.MESSAGE;
    }

    @Override
    public BarColor bossBarColor() {
        return BarColor.RED;
    }

    @Override
    public void registerListener() {
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void unregisterListener() {
        AsyncPlayerChatEvent.getHandlerList().unregister(this);
    }
}
