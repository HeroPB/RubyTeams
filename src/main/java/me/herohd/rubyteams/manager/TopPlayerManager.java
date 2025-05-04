package me.herohd.rubyteams.manager;

import me.herohd.rubyteams.RubyTeams;
import org.bukkit.Bukkit;

import java.util.*;

public class TopPlayerManager {
    private final Map<Integer, List<String>> weeklyTopPlayers = new HashMap<>();

    private final Map<String, List<TopPlayerEntry>> top10PerTeam = new HashMap<>();

    public void setTop10ForTeam(String teamName, List<TopPlayerEntry> topPlayers) {
        top10PerTeam.put(teamName, topPlayers);
    }

    public List<TopPlayerEntry> getTop10ForTeam(String teamName) {
        return top10PerTeam.getOrDefault(teamName, Collections.emptyList());
    }

    public void startTop10Updater(MySQLManager mysql) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(RubyTeams.getInstance(), () -> {
            for (String team : Arrays.asList("Ordine", "Gusci")) {
                List<TopPlayerEntry> top = mysql.getTop10PlayersForTeam(team.equals("Ordine") ? "Ordine del Nido" : "Gusci Spezzati");
                setTop10ForTeam(team.toLowerCase(), top);
            }
        }, 0L, 20L * 30);
    }

    public List<String> getWeekTopPlayers(int week) {
        return weeklyTopPlayers.get(week);
    }





    public void setTopPlayersForWeek(int weekNumber, List<String> topPlayers) {
        weeklyTopPlayers.put(weekNumber, topPlayers);
    }

    public List<String> getTopPlayersForWeek(int weekNumber) {
        return weeklyTopPlayers.getOrDefault(weekNumber, Collections.emptyList());
    }

    public static class TopPlayerEntry {
        private final UUID playerUUID;
        private final double moneyEarned;

        public TopPlayerEntry(UUID playerUUID, double moneyEarned) {
            this.playerUUID = playerUUID;
            this.moneyEarned = moneyEarned;
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public String getPlayer() {
            return Bukkit.getOfflinePlayer(playerUUID).getName();
        }

        public double getMoneyEarned() {
            return moneyEarned;
        }
    }
}
