package me.herohd.rubyteams.manager;

import me.herohd.rubyteams.RubyTeams;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class MySQLManager {
    private final String url;
    private final String user;
    private final String password;
    private Connection connection;
    private final Random random = new Random();
    private int currentWeek; // Memorizza la settimana corrente

    public MySQLManager(RubyTeams plugin) {
        // Ho modificato il costruttore per prendere l'istanza del plugin
        // in modo da poter accedere alla config in modo pulito.
        this.url = "jdbc:mysql://" + plugin.getConfigYML().getString("mysql.host") + "/" + plugin.getConfigYML().getString("mysql.database") + "?useSSL=false&autoReconnect=true";
        this.user = plugin.getConfigYML().getString("mysql.user");
        this.password = plugin.getConfigYML().getString("mysql.password");

        connect();
        loadCurrentWeek(); // Carica la settimana attuale
    }

    private void connect() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, user, password);
                System.out.println("Connessione MySQL riuscita!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadCurrentWeek() {
        String query = "SELECT current_week FROM config";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                currentWeek = rs.getInt("current_week");
            } else {
                currentWeek = 0;
                String insertQuery = "INSERT INTO config (current_week) VALUES (0)";
                try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void nextWeek() {
        currentWeek++;
        String query = "UPDATE config SET current_week = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, currentWeek);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPlayerTeam(String playerUUID) {
        String query = "SELECT team_id FROM weekly_progress WHERE player_uuid = ? AND week_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, currentWeek);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return getTeamName(rs.getInt("team_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String assignTeamToPlayer(String playerUUID) {
        int teamOneId = getTeamId(RubyTeams.getInstance().getTeamManager().getTeamOneName());
        int teamTwoId = getTeamId(RubyTeams.getInstance().getTeamManager().getTeamTwoName());

        int teamOneCount = getTeamPlayerCount(teamOneId);
        int teamTwoCount = getTeamPlayerCount(teamTwoId);

        int selectedTeamId;

        if (currentWeek == 0) {
            selectedTeamId = (teamOneCount <= teamTwoCount) ? teamOneId : teamTwoId;
        } else {
            int previousWeek = currentWeek - 1;
            List<String> topPlayers = getTopPlayers(previousWeek, 10);
            boolean isTopPlayer = topPlayers.contains(playerUUID);

            int teamOneTopCount = getTopPlayerCountInTeam(teamOneId);
            int teamTwoTopCount = getTopPlayerCountInTeam(teamTwoId);

            if (isTopPlayer) {
                if (teamOneTopCount < teamTwoTopCount) {
                    selectedTeamId = teamOneId;
                } else if (teamTwoTopCount < teamOneTopCount) {
                    selectedTeamId = teamTwoId;
                } else {
                    selectedTeamId = (teamOneCount <= teamTwoCount) ? teamOneId : teamTwoId;
                }
            } else {
                selectedTeamId = (teamOneCount <= teamTwoCount) ? teamOneId : teamTwoId;
            }
        }

        String query = "INSERT INTO weekly_progress (player_uuid, team_id, week_number) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, selectedTeamId);
            stmt.setInt(3, currentWeek);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return getTeamName(selectedTeamId);
    }

    private List<String> getTopPlayers(int weekNumber, int limit) {
        List<String> topPlayers = new ArrayList<>();
        String query = "SELECT player_uuid FROM weekly_progress WHERE week_number = ? ORDER BY money_earned DESC LIMIT ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, weekNumber);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                topPlayers.add(rs.getString("player_uuid"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return topPlayers;
    }

    private int getTopPlayerCountInTeam(int teamId) {
        int previousWeek = currentWeek - 1;
        if (previousWeek < 0) return 0;
        List<String> topPlayers = getTopPlayers(previousWeek, 10);
        if (topPlayers.isEmpty()) return 0;

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < topPlayers.size(); i++) {
            placeholders.append("?").append(i < topPlayers.size() - 1 ? "," : "");
        }

        String query = "SELECT COUNT(*) AS count FROM weekly_progress WHERE week_number = ? AND team_id = ? AND player_uuid IN (" + placeholders.toString() + ")";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, currentWeek);
            stmt.setInt(2, teamId);
            for (int i = 0; i < topPlayers.size(); i++) {
                stmt.setString(i + 3, topPlayers.get(i));
            }
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getTeamPlayerCount(int teamId) {
        String query = "SELECT COUNT(*) AS count FROM weekly_progress WHERE team_id = ? AND week_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, teamId);
            stmt.setInt(2, currentWeek);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void loadTopPlayers() {
        TopPlayerManager manager = RubyTeams.getInstance().getTopPlayerManager();
        try {
            for (int week = 0; week <= currentWeek; week++) {
                List<String> topPlayers = getTopPlayers(week, 10);
                manager.setTopPlayersForWeek(week, topPlayers);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addPoints(String playerUUID, int amount) { // Metodo per aggiungere punti (usato dal TeamManager)
        updatePlayerMoney(playerUUID, amount);
    }

    public void setTeam(String playerUUID, String teamName) { // Metodo per impostare il team
        // La logica di assegnazione è in assignTeamToPlayer, questo potrebbe non essere necessario
        // o potrebbe essere usato per forzare un team.
    }

    public void updatePlayerMoney(String playerUUID, long amount) {
        int teamId = getPlayerTeamId(playerUUID);
        if (teamId == -1) return;

        String playerQuery = "UPDATE weekly_progress SET money_earned = money_earned + ? WHERE player_uuid = ? AND week_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(playerQuery)) {
            stmt.setLong(1, amount);
            stmt.setString(2, playerUUID);
            stmt.setInt(3, currentWeek);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String teamQuery = "UPDATE weekly_team_stats SET total_money = total_money + ? WHERE team_id = ? AND week_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(teamQuery)) {
            stmt.setLong(1, amount);
            stmt.setInt(2, teamId);
            stmt.setInt(3, currentWeek);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateTeamAmount(String team_name, long amount) {
        int teamId = getTeamId(team_name);
        if (teamId == -1) return;

        String teamQuery = "INSERT INTO weekly_team_stats (team_id, week_number, total_money) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE total_money = total_money + ?";
        try (PreparedStatement stmt = connection.prepareStatement(teamQuery)) {
            stmt.setInt(1, teamId);
            stmt.setInt(2, currentWeek);
            stmt.setLong(3, amount);
            stmt.setLong(4, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public long getTeamAmount(String teamName) {
        int teamId = getTeamId(teamName);
        if (teamId == -1) return 0;

        String query = "SELECT total_money FROM weekly_team_stats WHERE team_id = ? AND week_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, teamId);
            stmt.setInt(2, currentWeek);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("total_money");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private int getPlayerTeamId(String playerUUID) {
        String query = "SELECT team_id FROM weekly_progress WHERE player_uuid = ? AND week_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, currentWeek);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("team_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private int getTeamId(String teamName) {
        String query = "SELECT id FROM teams WHERE name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, teamName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private String getTeamName(int teamId) {
        String query = "SELECT name FROM teams WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, teamId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Integer> getUnclaimedWeeks(String playerUUID) {
        List<Integer> unclaimedWeeks = new ArrayList<>();
        for (WeeklyStatus status : getPlayerWeeklyStatuses(playerUUID)) {
            if (!status.claimed && status.wasWinner && status.hasContributed) {
                unclaimedWeeks.add(status.weekNumber);
            }
        }
        return unclaimedWeeks;
    }

    public List<Integer> getLostWeeksWithContribution(String playerUUID) {
        List<Integer> lostWeeks = new ArrayList<>();
        for (WeeklyStatus status : getPlayerWeeklyStatuses(playerUUID)) {
            if (!status.claimed && !status.wasWinner && status.hasContributed) {
                lostWeeks.add(status.weekNumber);
            }
        }
        return lostWeeks;
    }

    public void claimReward(String playerUUID, int weekNumber) {
        String query = "UPDATE weekly_progress SET reward_claimed = 1 WHERE player_uuid = ? AND week_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, weekNumber);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateTeamWinner() {
        String query = "UPDATE weekly_team_stats SET is_winner = 1 WHERE week_number = ? AND team_id = " +
                "(SELECT team_id FROM (SELECT team_id FROM weekly_team_stats WHERE week_number = ? ORDER BY total_money DESC LIMIT 1) AS winner)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, currentWeek);
            stmt.setInt(2, currentWeek);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public long getPlayerAmount(String playerUUID) {
        String query = "SELECT money_earned FROM weekly_progress WHERE player_uuid = ? AND week_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, currentWeek);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("money_earned");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public boolean isRewardClaimed(String playerUUID, int weekNumber) {
        String query = "SELECT reward_claimed FROM weekly_progress WHERE player_uuid = ? AND week_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, weekNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("reward_claimed") == 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<TopPlayerManager.TopPlayerEntry> getTop10PlayersForTeam(String teamName) {
        List<TopPlayerManager.TopPlayerEntry> topPlayers = new ArrayList<>();
        int teamId = getTeamId(teamName);
        if (teamId == -1) return topPlayers;

        String query = "SELECT player_uuid, money_earned FROM weekly_progress WHERE team_id = ? AND week_number = ? ORDER BY money_earned DESC LIMIT 10";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, teamId);
            stmt.setInt(2, currentWeek);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                double money = rs.getDouble("money_earned");
                topPlayers.add(new TopPlayerManager.TopPlayerEntry(uuid, money));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return topPlayers;
    }

    public int getCurrentWeek() {
        return currentWeek;
    }

    /**
     * Una classe contenitore per lo stato di un giocatore in una singola settimana.
     */
    public static class WeeklyStatus {
        public final int weekNumber;
        public final boolean claimed;
        public final boolean wasWinner;
        public final boolean hasContributed;

        public WeeklyStatus(int weekNumber, boolean claimed, boolean wasWinner, boolean hasContributed) {
            this.weekNumber = weekNumber;
            this.claimed = claimed;
            this.wasWinner = wasWinner;
            this.hasContributed = hasContributed;
        }
    }

    /**
     * NUOVO METODO OTTIMIZZATO: Recupera lo stato di tutte le settimane passate per un giocatore in una sola query.
     */
    public List<WeeklyStatus> getPlayerWeeklyStatuses(String playerUUID) {
        List<WeeklyStatus> statuses = new ArrayList<>();
        String query = "SELECT wp.week_number, wp.reward_claimed, wts.is_winner, wp.money_earned, wts.total_money " +
                "FROM weekly_progress wp " +
                "LEFT JOIN weekly_team_stats wts ON wp.team_id = wts.team_id AND wp.week_number = wts.week_number " +
                "WHERE wp.player_uuid = ? AND wp.week_number < ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, currentWeek);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int week = rs.getInt("week_number");
                boolean claimed = rs.getBoolean("reward_claimed");
                boolean wasWinner = rs.getBoolean("is_winner");

                long playerMoney = rs.getLong("money_earned");
                long teamMoney = rs.getLong("total_money");
                boolean hasContributed = (teamMoney > 0 && playerMoney >= teamMoney * 0.005);

                statuses.add(new WeeklyStatus(week, claimed, wasWinner, hasContributed));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return statuses;
    }

    public void synchronizeTeamsWithDatabase(String teamOneName, String teamTwoName) {
        // Query per il Team 1 (ID fisso a 1)
        String queryTeamOne = "INSERT INTO teams (id, name) VALUES (1, ?) ON DUPLICATE KEY UPDATE name = ?";

        try (PreparedStatement stmt = connection.prepareStatement(queryTeamOne)) {
            stmt.setString(1, teamOneName); // Valore per INSERT
            stmt.setString(2, teamOneName); // Valore per UPDATE
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[RubyTeams] Impossibile sincronizzare il Team 1 con il database.");
            e.printStackTrace();
        }

        // Query per il Team 2 (ID fisso a 2)
        String queryTeamTwo = "INSERT INTO teams (id, name) VALUES (2, ?) ON DUPLICATE KEY UPDATE name = ?";

        try (PreparedStatement stmt = connection.prepareStatement(queryTeamTwo)) {
            stmt.setString(1, teamTwoName); // Valore per INSERT
            stmt.setString(2, teamTwoName); // Valore per UPDATE
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[RubyTeams] Impossibile sincronizzare il Team 2 con il database.");
            e.printStackTrace();
        }

        System.out.println("[RubyTeams] Nomi dei team sincronizzati con il database.");
    }

    // --- NUOVO METODO ---
    /**
     * Ottiene i migliori 10 giocatori di un team per una settimana SPECIFICA.
     * @param teamName Il nome del team.
     * @param weekNumber Il numero della settimana da interrogare.
     * @return Una lista di TopPlayerEntry per quella settimana.
     */
    public List<TopPlayerManager.TopPlayerEntry> getTop10PlayersForTeam(String teamName, int weekNumber) {
        List<TopPlayerManager.TopPlayerEntry> topPlayers = new ArrayList<>();
        int teamId = getTeamId(teamName);
        if (teamId == -1) return topPlayers;

        String query = "SELECT player_uuid, money_earned FROM weekly_progress WHERE team_id = ? AND week_number = ? ORDER BY money_earned DESC LIMIT 10";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, teamId);
            stmt.setInt(2, weekNumber); // Usa il parametro della settimana
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                double money = rs.getDouble("money_earned");
                topPlayers.add(new TopPlayerManager.TopPlayerEntry(uuid, money));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return topPlayers;
    }
}