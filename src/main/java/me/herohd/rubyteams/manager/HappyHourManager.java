package me.herohd.rubyteams.manager;

import me.herohd.rubyteams.RubyTeams;
import org.bukkit.Bukkit;
import org.bukkit.boss.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalTime;
import java.util.*;

public class HappyHourManager implements Listener, CommandExecutor {

    private static final long DURATION_HAPPY_HOUR_TICKS = 5 * 60 * 20; // 5 minuti in tick
    private static final long MIN_DELAY_TICKS = 20 * 60 * 20; // 20 minuti in tick
    private static final long MAX_DELAY_TICKS = 45 * 60 * 20; // 45 minuti in tick

    private static String currentTeam = null;
    private static boolean isActive = false;
    private static long timeUntilNext = -1;
    private static BossBar bossBar;

    public void startScheduler() {
        scheduleNextHappyHour();
    }

    private void scheduleNextHappyHour() {
        if (!isWithinAllowedHours()) {
            // Controlla di nuovo tra 5 minuti se rientra nell'orario
            new BukkitRunnable() {
                @Override
                public void run() {
                    scheduleNextHappyHour();
                }
            }.runTaskLater(RubyTeams.getInstance(), 20L * 60 * 5);
            return;
        }

        long delay = MIN_DELAY_TICKS + (long) (Math.random() * (MAX_DELAY_TICKS - MIN_DELAY_TICKS));
        timeUntilNext = delay / 20; // in secondi

        new BukkitRunnable() {
            @Override
            public void run() {
                activateHappyHour();
            }
        }.runTaskLater(RubyTeams.getInstance(), delay);

        // Timer countdown visibile da comando
        new BukkitRunnable() {
            @Override
            public void run() {
                if (timeUntilNext <= 0 || isActive) {
                    cancel();
                    return;
                }
                timeUntilNext--;
            }
        }.runTaskTimerAsynchronously(RubyTeams.getInstance(), 0L, 20L);
    }

    private void activateHappyHour() {
        List<String> teams = new ArrayList<>(TeamManager.teamStatus.keySet());
        if (teams.isEmpty()) {
            scheduleNextHappyHour();
            return;
        }

        currentTeam = teams.get(new Random().nextInt(teams.size()));
        isActive = true;

        Bukkit.broadcastMessage("\n§f Il team §e" + currentTeam + "§f guadagna §cx2 punti§f per i prossimi §c5 minuti§f!\n §f");

        bossBar = Bukkit.createBossBar("§6Punti doppi per il team: §e" + currentTeam, BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            String team = TeamManager.getTeam(player.getUniqueId().toString());
            if (team != null && team.equals(currentTeam)) {
                bossBar.addPlayer(player);
            }
        }

        final long[] secondsRemaining = {DURATION_HAPPY_HOUR_TICKS / 20};

        new BukkitRunnable() {
            @Override
            public void run() {
                if (secondsRemaining[0] <= 0) {
                    bossBar.removeAll();
                    bossBar.setVisible(false);
                    isActive = false;
                    currentTeam = null;
                    bossBar = null;
                    Bukkit.broadcastMessage("\n§f L'evento §cpunti doppi§f è terminato! \n §f");
                    scheduleNextHappyHour();
                    cancel();
                    return;
                }

                float progress = (float) secondsRemaining[0] / (DURATION_HAPPY_HOUR_TICKS / 20);
                bossBar.setProgress(progress);
                bossBar.setTitle("§6Punti Doppi: §e" + currentTeam + " §f- §c" + secondsRemaining[0] + "s");
                secondsRemaining[0]--;
            }
        }.runTaskTimer(RubyTeams.getInstance(), 0L, 20L);
    }

    private boolean isWithinAllowedHours() {
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(23, 30);
        return !now.isBefore(start) && !now.isAfter(end);
    }

    public static boolean isHappyHourActiveForTeam(String team) {
        return isActive && team != null && team.equals(currentTeam);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        String team = TeamManager.getTeam(player.getUniqueId().toString());
        if (isActive && team != null && team.equals(currentTeam) && bossBar != null) {
            bossBar.addPlayer(player);
        }
    }

    public static long getSecondsUntilNext() {
        return timeUntilNext;
    }

    public static boolean isActive() {
        return isActive;
    }

    public static String getCurrentTeam() {
        return currentTeam;
    }

    // ==========================
    // Comando /happyhour
    // ==========================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Solo i giocatori possono usare questo comando.");
            return true;
        }

        Player player = (Player) sender;

        if (isActive) {
            if (currentTeam != null && currentTeam.equals(TeamManager.getTeam(player.getUniqueId().toString()))) {
                player.sendMessage("§6Happy Hour attivo per il tuo team §e" + currentTeam + "§6! Punti doppi attivi!");
            } else {
                player.sendMessage("§eHappy Hour attivo per il team: §c" + currentTeam);
            }
        } else if (timeUntilNext > 0) {
            long min = timeUntilNext / 60;
            long sec = timeUntilNext % 60;
            player.sendMessage("§fProssimo Happy Hour tra §e" + min + "m " + sec + "s");
        } else {
            player.sendMessage("§fNessun Happy Hour programmato al momento.");
        }
        return true;
    }
}
