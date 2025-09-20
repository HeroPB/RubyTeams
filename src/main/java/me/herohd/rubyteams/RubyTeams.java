package me.herohd.rubyteams;

import me.herohd.rubyteams.command.*;
import me.herohd.rubyteams.events.EventManager;
import me.herohd.rubyteams.hooks.PlaceholderExtension;
import me.herohd.rubyteams.listener.PlayerListener;
import me.herohd.rubyteams.manager.HappyHourManager;
import me.herohd.rubyteams.manager.MySQLManager;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.manager.TopPlayerManager;
import me.herohd.rubyteams.utils.Config;
import me.kr1s_d.commandframework.CommandManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RubyTeams extends JavaPlugin {

    private static RubyTeams instance;

    // Managers (non più static)
    private Config config;
    private MySQLManager mySQLManager;
    private TeamManager teamManager;
    private TopPlayerManager topPlayerManager;
    private HappyHourManager happyHourManager;
    private EventManager eventManager; // --- AGGIUNTO ---
    private PlaceholderExtension placeholderExtension;

    @Override
    public void onEnable() {
        instance = this;
        this.config = new Config(this, "config");

        // Inizializza i manager in ordine di dipendenza
        this.mySQLManager = new MySQLManager(this);
        this.teamManager = new TeamManager(this);
        this.topPlayerManager = new TopPlayerManager();
        this.happyHourManager = new HappyHourManager();
        this.eventManager = new EventManager(this); // --- AGGIUNTO ---

        // Carica i dati di configurazione
        this.teamManager.loadTeamNames();
        this.teamManager.load(); // Carica i punteggi iniziali

        // Avvia i task
        mySQLManager.loadTopPlayers();
        topPlayerManager.startTop10Updater(mySQLManager);
        topPlayerManager.startWeeklyCacheUpdater();
        happyHourManager.startScheduler();

        // Registra i listener
        new PlayerListener(this);
        Bukkit.getPluginManager().registerEvents(happyHourManager, this);

        // Registra i comandi
        CommandManager commandManager = new CommandManager("rubyteams", "RubyTeams: ");
        commandManager.register(new NextWeekCommand());
        commandManager.register(new HHNextCommand());
        commandManager.register(new TopPlayerCommand());
        commandManager.register(new StartEventCommand());
        commandManager.register(new OpenRewardCommand());
        commandManager.register(new StopEventCommand());
        commandManager.register(new ReloadEventsCommand());

        // Registra PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderExtension = new PlaceholderExtension();
            this.placeholderExtension.register();
            getLogger().info("PlaceholderAPI trovato, espansione registrata.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Salvataggio dati di RubyTeams in corso...");
        // Salva i dati per tutti i giocatori online
        for (Player player : Bukkit.getOnlinePlayers()) {
            teamManager.saveAndUnloadPlayerData(player);
        }

        if (mySQLManager != null) {
            mySQLManager.disconnect();
        }
        if (placeholderExtension != null) {
            placeholderExtension.unregister();
        }
        getLogger().info("RubyTeams disabilitato correttamente.");
    }

    // --- Getters per accedere alle istanze ---
    public static RubyTeams getInstance() {
        return instance;
    }

    public Config getConfigYML() {
        return config;
    }

    public MySQLManager getMySQLManager() {
        return mySQLManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public TopPlayerManager getTopPlayerManager() {
        return topPlayerManager;
    }

    public HappyHourManager getHappyHourManager() {
        return happyHourManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }
}
