package me.herohd.rubyteams.manager;

import me.herohd.rubyteams.RubyTeams;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TopPlayerManager {
    private final Map<Integer, List<String>> weeklyTopPlayers = new HashMap<>();
    private final Map<Integer, List<TopPlayerEntry>> top10PerTeam = new HashMap<>(); // Cache per la settimana attuale

    // --- NUOVA CACHE ---
    private final Map<Integer, Map<Integer, List<TopPlayerEntry>>> weeklyTeamTopCache = new ConcurrentHashMap<>();

    // Questo metodo ora aggiorna la cache per la settimana CORRENTE
    public void setTop10ForTeam(int id, List<TopPlayerEntry> topPlayers) {
        top10PerTeam.put(id, topPlayers);
    }

    // Questo metodo ora legge dalla cache della settimana CORRENTE
    public List<TopPlayerEntry> getTop10ForTeam(Integer teamId) {
        return top10PerTeam.getOrDefault(teamId, Collections.emptyList());
    }

    /**
     * Recupera la classifica dei migliori 10 giocatori per un team da una settimana specifica, LEGGENDO DALLA CACHE.
     * La cache viene popolata da un task asincrono ogni 10 minuti.
     *
     * @param teamId L'ID del team (1 o 2).
     * @param weekNumber Il numero della settimana.
     * @return Una lista (potenzialmente vuota) di TopPlayerEntry letta dalla cache.
     */
    public List<TopPlayerEntry> getTop10ForTeamInWeek(int teamId, int weekNumber) {
        // Ottiene la mappa della settimana dalla cache principale
        Map<Integer, List<TopPlayerEntry>> weekCache = weeklyTeamTopCache.get(weekNumber);
        if (weekCache != null) {
            // Se la settimana esiste, restituisce la lista per il team richiesto
            return weekCache.getOrDefault(teamId, Collections.emptyList());
        }
        // Se la settimana non è in cache, restituisce una lista vuota
        return Collections.emptyList();
    }

    public Map<Integer, List<String>> getWeeklyTopPlayers() {
        return weeklyTopPlayers;
    }

    /**
     * Task che aggiorna la cache per la top 10 della settimana CORRENTE ogni 30 secondi.
     * Questo è utile per avere dati quasi in tempo reale per la competizione in corso.
     */
    public void startTop10Updater(MySQLManager mysql) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(RubyTeams.getInstance(), () -> {
            String teamOne = RubyTeams.getInstance().getTeamManager().getTeamOneName();
            String teamTwo = RubyTeams.getInstance().getTeamManager().getTeamTwoName();

            setTop10ForTeam(1, mysql.getTop10PlayersForTeam(teamOne));
            setTop10ForTeam(2, mysql.getTop10PlayersForTeam(teamTwo));

        }, 0L, 20L * 30);
    }

    // --- NUOVO METODO PER L'AGGIORNAMENTO DELLA CACHE STORICA ---
    /**
     * Avvia un task asincrono che popola e aggiorna la cache delle classifiche settimanali ogni 10 minuti.
     * Questo metodo carica i dati di tutte le settimane, dalla prima a quella attuale.
     */
    public void startWeeklyCacheUpdater() {
        MySQLManager mysql = RubyTeams.getInstance().getMySQLManager();
        TeamManager teamManager = RubyTeams.getInstance().getTeamManager();

        // Esegui ogni 10 minuti (20 ticks * 60 secondi * 10 minuti)
        long period = 20L * 60 * 10;

        Bukkit.getScheduler().runTaskTimerAsynchronously(RubyTeams.getInstance(), () -> {
            RubyTeams.getInstance().getLogger().info("Aggiornamento della cache delle classifiche settimanali...");

            int currentWeek = mysql.getCurrentWeek();
            String teamOneName = teamManager.getTeamOneName();
            String teamTwoName = teamManager.getTeamTwoName();

            // Itera su tutte le settimane e aggiorna la cache
            for (int week = 0; week <= currentWeek; week++) {
                List<TopPlayerEntry> topTeamOne = mysql.getTop10PlayersForTeam(teamOneName, week);
                List<TopPlayerEntry> topTeamTwo = mysql.getTop10PlayersForTeam(teamTwoName, week);

                // Inserisce i dati nella cache in modo thread-safe
                Map<Integer, List<TopPlayerEntry>> weekCache = weeklyTeamTopCache.computeIfAbsent(week, k -> new ConcurrentHashMap<>());
                weekCache.put(1, topTeamOne);
                weekCache.put(2, topTeamTwo);
            }
            RubyTeams.getInstance().getLogger().info("Cache delle classifiche settimanali aggiornata con successo per " + (currentWeek + 1) + " settimane.");

        }, 0L, period); // Avvia subito e poi ripete ogni 10 minuti
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
            // Utilizza getOfflinePlayer per evitare problemi se il giocatore non è online
            return Bukkit.getOfflinePlayer(playerUUID).getName();
        }

        public double getMoneyEarned() {
            return moneyEarned;
        }
    }
}