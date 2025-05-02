package me.herohd.rubyteams;

import me.herohd.rubyteams.command.HHNextCommand;
import me.herohd.rubyteams.command.NextWeekCommand;
import me.herohd.rubyteams.command.OpenRewardCommand;
import me.herohd.rubyteams.command.TopPlayerCommand;
import me.herohd.rubyteams.hooks.PlaceholderExtension;
import me.herohd.rubyteams.listener.PlayerListener;
import me.herohd.rubyteams.manager.HappyHourManager;
import me.herohd.rubyteams.manager.MySQLManager;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.manager.TopPlayerManager;
import me.herohd.rubyteams.utils.Config;
import me.kr1s_d.commandframework.CommandManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class RubyTeams extends JavaPlugin {

    private static RubyTeams instance;

    private Config config;
    private MySQLManager mySQLManager;
    private PlaceholderExtension placeholderExtension;

    private HappyHourManager happyHourManager;
    private TopPlayerManager topPlayerManager;

    @Override
    public void onEnable() {
        instance = this;
        this.config = new Config(this, "config");
        //this.mySQLManager = new MySQLManager("localhost", "testTeam", "root", "TAmebJRzA#1Rb");
        this.mySQLManager = new MySQLManager("localhost", "TeamCompetition", "root", "TAmebJRzA#1Rb");
        this.topPlayerManager = new TopPlayerManager(); // <--- aggiunto qui

        TeamManager.load();
        mySQLManager.loadTopPlayers();
        topPlayerManager.startTop10Updater(mySQLManager);
        new PlayerListener(mySQLManager);

        happyHourManager = new HappyHourManager();
        happyHourManager.startScheduler();
        Bukkit.getPluginManager().registerEvents(happyHourManager, this);

        CommandManager commandManager = new CommandManager("rubyteams", "RubyTeams: ");
        commandManager.register(new NextWeekCommand());
        commandManager.register(new HHNextCommand());
        commandManager.register(new TopPlayerCommand());
        commandManager.register(new OpenRewardCommand());



        this.placeholderExtension = new PlaceholderExtension();
        placeholderExtension.register();
    }

    @Override
    public void onDisable() {
        mySQLManager.disconnect();
        placeholderExtension.unregister();
    }

    public static RubyTeams getInstance() {
        return instance;
    }

    public Config getConfigYML() {
        return config;
    }

    public MySQLManager getMySQLManager() {
        return mySQLManager;
    }

    public TopPlayerManager getTopPlayerManager() {
        return topPlayerManager;
    }
}
