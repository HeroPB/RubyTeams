package me.herohd.rubyteams;

import me.herohd.rubyteams.manager.MySQLManager;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import org.bukkit.plugin.java.JavaPlugin;

public final class RubyTeams extends JavaPlugin {

    private static RubyTeams instance;

    private Config config;
    private MySQLManager mySQLManager;

    @Override
    public void onEnable() {
        instance = this;
        this.config = new Config(this, "config");
        this.mySQLManager = new MySQLManager("localhost", "RubyTeams", "root", "TAmebJRzA#1Rb");
        TeamManager.load();
    }

    @Override
    public void onDisable() {
        mySQLManager.disconnect();
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
