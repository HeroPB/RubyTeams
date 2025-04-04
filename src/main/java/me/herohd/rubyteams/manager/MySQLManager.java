package me.herohd.rubyteams.manager;

import java.sql.*;
import java.util.Random;

public class MySQLManager {
    private final String url;
    private final String user;
    private final String password;
    private Connection connection;
    private final Random random = new Random();
    private int currentWeek; // Memorizza la settimana corrente

    public MySQLManager(String host, String database, String user, String password) {
        this.url = "jdbc:mysql://" + host + "/" + database + "?useSSL=false";
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
        int selectedTeamId = (ordineCount < gusciCount) ? 1 : (gusciCount < ordineCount) ? 2 : (random.nextBoolean() ? 1 : 2);

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
}
