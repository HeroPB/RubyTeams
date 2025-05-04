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

    public MySQLManager(String host, String database, String user, String password) {
        this.url = "jdbc:mysql://" + host + "/" + database + "?useSSL=false&autoReconnect=true";
        this.user = user;
        this.password = password;
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
        int ordineCount = getTeamPlayerCount(1);
        int gusciCount = getTeamPlayerCount(2);

        int selectedTeamId;

        if (currentWeek == 0) {
            // Prima settimana: logica standard senza top player
            selectedTeamId = (ordineCount < gusciCount) ? 1 : (gusciCount < ordineCount) ? 2 : (random.nextBoolean() ? 1 : 2);
        } else {
            int previousWeek = currentWeek - 1;

            // Recupera la lista dei top player della settimana precedente
            List<String> topPlayers = getTopPlayers(previousWeek, 10);
            boolean isTopPlayer = topPlayers.contains(playerUUID);

            int ordineTopCount = getTopPlayerCountInTeam(1);
            int gusciTopCount = getTopPlayerCountInTeam(2);

            if (isTopPlayer) {
                // Se è un top player, assegna al team con meno top player
                if (ordineTopCount < gusciTopCount) {
                    selectedTeamId = 1;
                } else if (gusciTopCount < ordineTopCount) {
                    selectedTeamId = 2;
                } else {
                    // Se sono pari, logica standard
                    selectedTeamId = (ordineCount < gusciCount) ? 1 : (gusciCount < ordineCount) ? 2 : (random.nextBoolean() ? 1 : 2);
                }
            } else {
                // Non è top player, logica standard
                selectedTeamId = (ordineCount < gusciCount) ? 1 : (gusciCount < ordineCount) ? 2 : (random.nextBoolean() ? 1 : 2);
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
        List<String> topPlayers = getTopPlayers(previousWeek, 10);

        if (topPlayers.isEmpty()) return 0;

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < topPlayers.size(); i++) {
            placeholders.append("?");
            if (i < topPlayers.size() - 1) {
                placeholders.append(",");
            }
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

    private int getTeamPlayerCount(int teamId) {
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
            // Otteniamo il numero massimo di settimana esistente
            String maxWeekQuery = "SELECT MAX(week_number) AS max_week FROM weekly_progress";
            int maxWeek = 0;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(maxWeekQuery)) {
                if (rs.next()) {
                    maxWeek = rs.getInt("max_week");
                }
            }

            for (int week = 0; week <= currentWeek && week <= maxWeek; week++) {  // Partiamo da 0 adesso
                List<String> topPlayers = new ArrayList<>();

                String topPlayersQuery = "SELECT player_uuid FROM weekly_progress WHERE week_number = ? ORDER BY money_earned DESC LIMIT 10";
                try (PreparedStatement stmt = connection.prepareStatement(topPlayersQuery)) {
                    stmt.setInt(1, week);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        topPlayers.add(rs.getString("player_uuid"));
                    }
                }

                manager.setTopPlayersForWeek(week, topPlayers);
                System.out.println("[RubyTeams] Top player settimana " + week + ": " + topPlayers);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    public void updatePlayerMoney(String playerUUID, long amount) {
        int teamId = getPlayerTeamId(playerUUID);
        if (teamId == -1) return;

        String playerQuery = "INSERT INTO weekly_progress (player_uuid, team_id, week_number, money_earned) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE money_earned = money_earned + ?";
        try (PreparedStatement stmt = connection.prepareStatement(playerQuery)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, teamId);
            stmt.setInt(3, currentWeek);
            stmt.setLong(4, amount);
            stmt.setLong(5, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String teamQuery = "INSERT INTO weekly_team_stats (team_id, week_number, total_money) " +
                "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE total_money = total_money + ?";
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

    public void updateTeamAmount(String team_name, long amount) {
        int teamId = getTeamId(team_name);

        String teamQuery = "INSERT INTO weekly_team_stats (team_id, week_number, total_money) " +
                "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE total_money = total_money + ?";
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
                System.out.println("Connessione MySQL chiusa!");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Integer> getUnclaimedWeeks(String playerUUID) {
        List<Integer> unclaimedWeeks = new ArrayList<>();

        String query = "SELECT wp.week_number, wp.money_earned, wts.total_money " +
                "FROM weekly_progress wp " +
                "JOIN weekly_team_stats wts ON wp.team_id = wts.team_id AND wp.week_number = wts.week_number " +
                "WHERE wp.player_uuid = ? AND wp.reward_claimed = 0 AND wts.is_winner = 1 " +
                "AND wp.week_number < ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, currentWeek);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                long playerMoney = rs.getLong("money_earned");
                long teamMoney = rs.getLong("total_money");

                if (teamMoney > 0 && playerMoney >= teamMoney * 0.005) { // Deve aver fatto almeno l'1%
                    unclaimedWeeks.add(rs.getInt("week_number"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return unclaimedWeeks;
    }

    public List<Integer> getLostWeeksWithContribution(String playerUUID) {
        List<Integer> lostWeeks = new ArrayList<>();

        String query = "SELECT wp.week_number, wp.money_earned, wts.total_money " +
                "FROM weekly_progress wp " +
                "JOIN weekly_team_stats wts ON wp.team_id = wts.team_id AND wp.week_number = wts.week_number " +
                "WHERE wp.player_uuid = ? AND wp.reward_claimed = 0 AND wts.is_winner = 0 " +
                "AND wp.week_number < ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, currentWeek);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                long playerMoney = rs.getLong("money_earned");
                long teamMoney = rs.getLong("total_money");

                if (teamMoney > 0 && playerMoney >= teamMoney * 0.005) { // Deve aver fatto almeno l'1%
                    lostWeeks.add(rs.getInt("week_number"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return lostWeeks;
    }

    public void claimReward(String playerUUID, int weekNumber) {
        String query = "UPDATE weekly_progress SET reward_claimed = 1 " +
                "WHERE player_uuid = ? AND week_number = ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID);
            stmt.setInt(2, weekNumber);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateTeamWinner() {
        String query = "UPDATE weekly_team_stats wts " +
                "JOIN ( " +
                "   SELECT team_id " +
                "   FROM weekly_team_stats " +
                "   WHERE week_number = ? " +
                "   ORDER BY total_money DESC " +
                "   LIMIT 1 " +
                ") winner " +
                "ON wts.week_number = ? AND wts.team_id = winner.team_id " +
                "SET wts.is_winner = 1";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, currentWeek);
            stmt.setInt(2, currentWeek);
            stmt.executeUpdate();
            System.out.println("🏆 Il team vincitore della settimana " + currentWeek + " è stato impostato!");
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
        return false; // Se non trova il record, supponiamo che non sia stato reclamato
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
}
