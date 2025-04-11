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

public class PlayerListener implements Listener {


    private MySQLManager manager;
    public PlayerListener(MySQLManager manager) {
        this.manager = manager;
        Bukkit.getServer().getPluginManager().registerEvents(this, RubyTeams.getInstance());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player player = e.getPlayer();
        if(manager.getPlayerTeam(player.getUniqueId().toString()) != null) return;
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
        System.out.println(player.getName());
        if(player.hasPermission("rubyteams.bypass")) return;
        System.out.println(" ");
        String team = manager.getPlayerTeam(player.getUniqueId().toString());
        if(team == null) return;
        System.out.println("3");
        TeamManager.addAmount(player.getUniqueId(), (int) e.getReward());
    }
}
