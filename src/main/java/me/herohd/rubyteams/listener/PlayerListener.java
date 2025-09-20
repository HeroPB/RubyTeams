package me.herohd.rubyteams.listener;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.EventManager;
import me.herohd.rubyteams.manager.HappyHourManager;
import me.herohd.rubyteams.manager.TeamManager;
import me.kr1s_d.rubyevent.event.EconomyGainEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerListener implements Listener {

    private final RubyTeams plugin;
    private final TeamManager teamManager;
    private final HappyHourManager happyHourManager;

    public PlayerListener(RubyTeams plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
        this.happyHourManager = plugin.getHappyHourManager();
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        final Player player = e.getPlayer();
        teamManager.loadPlayerData(player);

        EventManager manager = RubyTeams.getInstance().getEventManager();
        if(manager.getActiveEvent() != null)
            manager.getActiveEvent().getBossBar().addPlayer(player);    

        // Controlla se il giocatore ha un team dopo un piccolo ritardo per dar tempo al caricamento
        new BukkitRunnable() {
            @Override
            public void run() {
                if (teamManager.getTeam(player) == null) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            String assignedTeam = teamManager.assignPlayer(player);
                            if (assignedTeam != null) {
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        player.sendTitle("§6§lNUOVO TEAM", "§f" + assignedTeam);
                                        player.sendMessage("§f\n §6§lPARTECIPI AD UN NUOVO TEAM\n §f Durante questa settimana appartieni al team§8: §e" + assignedTeam + "\n §f");
                                    }
                                }.runTaskLater(plugin, 20 * 5L);
                            }
                        }
                    }.runTaskAsynchronously(plugin);
                }
            }
        }.runTaskLater(plugin, 40L); // 2 secondi di ritardo
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        teamManager.saveAndUnloadPlayerData(e.getPlayer());
    }

    @EventHandler
    public void onEconomy(EconomyGainEvent e) {
        final Player player = e.getPlayer();
        if (player.hasPermission("rubyteams.bypass")) return;

        String team = teamManager.getTeam(player);
        if (team == null) return;

        int amount = (int) e.getReward();
        if (happyHourManager.isHappyHourActiveForTeam(team)) {
            amount *= 2;
        }
        teamManager.addPoints(player, amount);
    }
}
