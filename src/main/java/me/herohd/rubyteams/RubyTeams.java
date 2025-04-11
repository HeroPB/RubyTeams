package me.herohd.rubyteams;

import me.herohd.rubyteams.command.NextWeekCommand;
import me.herohd.rubyteams.hooks.PlaceholderExtension;
import me.herohd.rubyteams.listener.PlayerListener;
import me.herohd.rubyteams.manager.MySQLManager;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.kr1s_d.commandframework.CommandManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class RubyTeams extends JavaPlugin {

    private static RubyTeams instance;

    private Config config;
    private MySQLManager mySQLManager;
    private PlaceholderExtension placeholderExtension;

    @Override
    public void onEnable() {
        instance = this;
        this.config = new Config(this, "config");
        this.mySQLManager = new MySQLManager("localhost", "TeamCompetition", "root", "TAmebJRzA#1Rb");
        TeamManager.load();
        new PlayerListener(mySQLManager);
        CommandManager commandManager = new CommandManager("rubyteams", "RubyTeams: ");
        commandManager.register(new NextWeekCommand());

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
}
