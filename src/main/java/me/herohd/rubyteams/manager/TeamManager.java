package me.herohd.rubyteams.manager;

import me.herohd.rubyteams.RubyTeams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeamManager {

    private final RubyTeams plugin;
    private final MySQLManager mySQLManager;
    private final Map<UUID, String> playerTeam = new HashMap<>();
    private final Map<UUID, Long> playerPoints = new HashMap<>();
    private final Map<String, Long> teamPoints = new HashMap<>();

    private String teamOneName;
    private String teamTwoName;

    public TeamManager(RubyTeams plugin) {
        this.plugin = plugin;
        this.mySQLManager = plugin.getMySQLManager();
    }

    public void loadTeamNames() {
        this.teamOneName = plugin.getConfigYML().getString("teams.team-one-name");
        this.teamTwoName = plugin.getConfigYML().getString("teams.team-two-name");

        // --- AGGIUNGI QUESTA RIGA ---
        // Sincronizza i nomi appena caricati con il database.
        mySQLManager.synchronizeTeamsWithDatabase(this.teamOneName, this.teamTwoName);
    }

    public void load() {
        teamPoints.put(teamOneName, mySQLManager.getTeamAmount(teamOneName));
        teamPoints.put(teamTwoName, mySQLManager.getTeamAmount(teamTwoName));
    }

    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                String team = mySQLManager.getPlayerTeam(uuid.toString());
                long points = mySQLManager.getPlayerAmount(uuid.toString());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (team != null) playerTeam.put(uuid, team);
                        playerPoints.put(uuid, points);
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void saveAndUnloadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        // Rimuovi dalle cache per evitare dati duplicati
        playerTeam.remove(uuid);
        playerPoints.remove(uuid);
    }

    public void addPoints(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        String team = getTeam(player);
        if (team == null) return;

        playerPoints.put(uuid, getPlayerAmount(player) + amount);
        teamPoints.put(team, getTeamPoints(team) + amount);

        new BukkitRunnable() {
            @Override
            public void run() {
                mySQLManager.updatePlayerMoney(uuid.toString(), amount);
            }
        }.runTaskAsynchronously(plugin);
    }

    public int getPosition(Player player) {
        String team = getTeam(player);
        if (team == null) return -1;
        return team.equals(getTeamWithHighestAmount()) ? 1 : 2;
    }

    public String assignPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if(playerTeam.containsKey(uuid)) return null;

        String assignedTeam = mySQLManager.assignTeamToPlayer(uuid.toString());
        if(assignedTeam == null) return null;

        playerTeam.put(uuid, assignedTeam);
        return assignedTeam;
    }

    public String getTeam(Player player) {
        return playerTeam.get(player.getUniqueId());
    }

    public long getTeamPoints(String team) {
        return teamPoints.getOrDefault(team, 0L);
    }

    public long getPlayerAmount(Player player) {
        return playerPoints.getOrDefault(player.getUniqueId(), 0L);
    }

    public String getTeamWithHighestAmount() {
        long amountOne = getTeamPoints(teamOneName);
        long amountTwo = getTeamPoints(teamTwoName);
        return (amountOne >= amountTwo) ? teamOneName : teamTwoName;
    }

    public void clearAll() {
        playerTeam.clear();
        playerPoints.clear();
        teamPoints.clear();
        mySQLManager.updateTeamWinner();
        mySQLManager.nextWeek();
        load();
    }

    /**
     * Aggiunge punti al punteggio globale di un team (cache + database).
     *
     * @param teamName Il nome del team a cui aggiungere i punti.
     * @param amount   La quantità di punti da aggiungere.
     */
    public void addGlobalPoints(String teamName, long amount) {
        if (teamName == null || amount <= 0) return;

        // Aggiorna la cache in memoria
        teamPoints.put(teamName, getTeamPoints(teamName) + amount);

        // Aggiorna il database in modo asincrono
        new BukkitRunnable() {
            @Override
            public void run() {
                mySQLManager.updateTeamAmount(teamName, amount);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Rimuove punti dal punteggio globale di un team (cache + database).
     *
     * @param teamName Il nome del team a cui rimuovere i punti.
     * @param amount   La quantità di punti da rimuovere.
     */
    public void removeGlobalPoints(String teamName, long amount) {
        if (teamName == null || amount <= 0) return;

        // Aggiorna la cache in memoria, assicurandosi che non vada sotto zero
        long currentPoints = getTeamPoints(teamName);
        teamPoints.put(teamName, Math.max(0, currentPoints - amount));

        // Aggiorna il database in modo asincrono passando un valore negativo
        new BukkitRunnable() {
            @Override
            public void run() {
                // Il tuo metodo updateTeamAmount aggiunge il valore, quindi passiamo un negativo per sottrarre
                mySQLManager.updateTeamAmount(teamName, -amount);
            }
        }.runTaskAsynchronously(plugin);
    }

    public String getTeamOneName() { return teamOneName; }
    public String getTeamTwoName() { return teamTwoName; }

    public String getTeamOneColor() {
        return plugin.getConfigYML().getString("teams.team-one-color");
    }

    public String getTeamTwoColor() {
        return plugin.getConfigYML().getString("teams.team-two-color");
    }
}
