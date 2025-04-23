package me.herohd.rubyteams.listener;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.manager.MySQLManager;
import me.herohd.rubyteams.manager.TeamManager;
import me.kr1s_d.rubyevent.event.EconomyGainEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import static me.herohd.rubyteams.manager.HappyHourManager.isHappyHourActiveForTeam;

public class PlayerListener implements Listener {


    private MySQLManager manager;
    public PlayerListener(MySQLManager manager) {
        this.manager = manager;
        Bukkit.getServer().getPluginManager().registerEvents(this, RubyTeams.getInstance());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player player = e.getPlayer();
        if(manager.getPlayerTeam(player.getUniqueId().toString()) != null) {
            TeamManager.addPlayerExist(player.getUniqueId().toString());
            return;
        }
        String assignTeam = TeamManager.assignPlayer(player.getUniqueId().toString());
        if(assignTeam == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("§6§lNUOVO TEAM", "§f" + assignTeam);
                player.sendMessage("§f\n" +
                        "§f §6§lPARTECIPI AD UN NUOVO TEAM\n" +
                        "§f Durante questa settimana appartieni al team§8: §e" + assignTeam + "\n §f §f");
            }
        }.runTaskLater(RubyTeams.getInstance(), 20*15L);
    }

    @EventHandler
    public void onEconomy(EconomyGainEvent e) {
        final Player player = e.getPlayer();
        if(player.hasPermission("rubyteams.bypass")) return;
        String team = manager.getPlayerTeam(player.getUniqueId().toString());
        if(team == null) return;
        int amount = (int) e.getReward();

        if (isHappyHourActiveForTeam(team)) {
            amount *= 2;
        }
        TeamManager.addAmount(player.getUniqueId(), amount);
    }
}
