package me.herohd.rubyteams.manager;

import me.herohd.rubyteams.RubyTeams;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeamManager {

    public static Map<String, Long> teamStatus = new HashMap<>();
    public static Map<String, String> playerTeam = new HashMap<>();
    public static Map<String, Long> uuidAmount = new HashMap<>();

    public static void load() {
        final long spezzati = RubyTeams.getInstance().getMySQLManager().getTeamAmount("Gusci Spezzati");
        final long nido = RubyTeams.getInstance().getMySQLManager().getTeamAmount("Ordine del Nido");
        teamStatus.put("Gusci Spezzati", spezzati);
        teamStatus.put("Ordine del Nido", nido);
    }

    public static int getPosition(String uuid) {
        if (!playerTeam.containsKey(uuid)) {
            return -1;
        }

        String playerTeamName = playerTeam.get(uuid);

        long amountGusci = teamStatus.getOrDefault("Gusci Spezzati", 0L);
        long amountOrdine = teamStatus.getOrDefault("Ordine del Nido", 0L);

        String firstPlaceTeam = (amountGusci >= amountOrdine) ? "Gusci Spezzati" : "Ordine del Nido";
        return playerTeamName.equals(firstPlaceTeam) ? 1 : 2;
    }

    public static String assignPlayer(String uuid) {
        if(playerTeam.containsKey(uuid)) return null;
        String assignTeam = RubyTeams.getInstance().getMySQLManager().assignTeamToPlayer(uuid);
        if(assignTeam == null) return null;
        playerTeam.put(uuid, assignTeam);
        return assignTeam;
    }

    public static void addAmount(UUID p, int amount) {
        new BukkitRunnable() {
            @Override
            public void run() {
                RubyTeams.getInstance().getMySQLManager().updatePlayerMoney(p.toString(), amount);
            }
        }.runTaskAsynchronously(RubyTeams.getInstance());


        String team;
        if(!playerTeam.containsKey(p.toString())) {
            team = RubyTeams.getInstance().getMySQLManager().getPlayerTeam(p.toString());
            if(team == null) return;
            playerTeam.put(p.toString(), team);
        }
        else team = playerTeam.get(p.toString());
        teamStatus.put(team, teamStatus.getOrDefault(team, 0L) + amount);
        uuidAmount.put(p.toString(), uuidAmount.getOrDefault(p.toString(), 0L) + amount);
    }

    public static String getTeam(String uuid) {
        if(playerTeam.containsKey(uuid)) return playerTeam.get(uuid);
        final String team = RubyTeams.getInstance().getMySQLManager().getPlayerTeam(uuid);
        if(team == null) return null;
        playerTeam.put(uuid, team);
        return team;
    }

    public static long getAmountTeam(String team) {
        return teamStatus.get(team);
    }

    public static long getPlayerAmount(String uuid) {
        return uuidAmount.getOrDefault(uuid, 0L);
    }
    public static String getTeamWithHighestAmount() {
        long maxAmount = -1;
        String teamWithHighestAmount = null;

        // Scorri tutti i team per trovare quello con l'amount più alto
        for (Map.Entry<String, Long> entry : teamStatus.entrySet()) {
            if (entry.getValue() > maxAmount) {
                maxAmount = entry.getValue();
                teamWithHighestAmount = entry.getKey();
            }
        }

        return teamWithHighestAmount;
    }

    public static void clearAll() {
        teamStatus.clear();
        playerTeam.clear();
        uuidAmount.clear();
        RubyTeams.getInstance().getMySQLManager().updateTeamWinner();
        RubyTeams.getInstance().getMySQLManager().nextWeek();
    }

    public static void addPlayerExist(String uuid) {
        long playerAmount = RubyTeams.getInstance().getMySQLManager().getPlayerAmount(uuid);
        uuidAmount.put(uuid, playerAmount);
    }

}
