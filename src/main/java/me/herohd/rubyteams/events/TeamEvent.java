package me.herohd.rubyteams.events;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.teamevents.*;
import me.herohd.rubyteams.listener.TeamEventFinishEvent;
import me.herohd.rubyteams.listener.TeamEventStartEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import me.herohd.rubyteams.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class TeamEvent {
    protected RubyTeams plugin;
    protected String name;
    protected String bossBarTitle;
    protected String startMessage;
    protected String bossBarTitleWin;
    protected long duration; // Può significare minuti o round, a seconda dell'evento
    protected List<String> rewards;
    protected String team_reward;
    protected Map<String, Long> playerProgress = new HashMap<>();
    protected Map<String, Long> teamProgress = new HashMap<>();

    protected BossBar bossBar;
    protected long startTime;
    protected Config config;
    protected Runnable onFinishCallback;
    private BukkitTask finishCheckerTask; // Task per controllare la fine dell'evento

    public TeamEvent(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        this.plugin = RubyTeams.getInstance();
        this.name = name;
        this.startMessage = startMessage;
        this.bossBarTitle = bossBarTitle;
        this.bossBarTitleWin = bossBarTitleWin;
        this.duration = duration;
        this.config = config;
        this.rewards = reward;
        this.team_reward = team_reward;
    }

    public static TeamEvent loadEvent(Config config) {
        String name = config.getColoredString("name");
        String bossBarTitle = config.getColoredString("boss-bar-message");
        EventType type = EventType.valueOf(config.getString("type"));
        long duration = config.getLong("duration");
        List<String> rewards = config.getStringList("rewards");
        String team_reward = config.getString("team-rewards");
        String bossBarTitleWin = config.getColoredString("boss-bar-message-win");
        String startMessage = config.getColoredString("start-event-message");

        switch (type) {
            case MESSAGE:
                return new MessageEventTeam(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, rewards, team_reward);

            case BLOCK_FIND:
                return new BlockFindEventTeam(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, rewards, team_reward);

            case GOLD_RUSH:
                return new GoldRushEventTeam(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, rewards, team_reward);

            case METEOR_SHOWER:
                return new MeteorShowerEventTeam(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, rewards, team_reward);

            case WORD_SEARCH:
                return new WordSearchEvent(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, rewards, team_reward);

            case ROCK_PAPER_SCISSORS:
                return new RockPaperScissorsEvent(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, rewards, team_reward);

            case SPOOKY_MAZE:
                return new SpookyMazeEvent(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, rewards, team_reward);

            default:
                throw new IllegalArgumentException("Tipo di evento non valido: " + type);
        }
    }

    public void start() {
        startTime = System.currentTimeMillis();
        playerProgress.clear();

        TeamManager teamManager = plugin.getTeamManager();
        teamProgress.put(teamManager.getTeamOneName(), 0L);
        teamProgress.put(teamManager.getTeamTwoName(), 0L);

        bossBar = Bukkit.createBossBar("Inizializzazione...", bossBarColor(), BarStyle.SOLID);
        bossBar.setProgress(1.0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
            player.sendMessage(startMessage);
        }
        updateBossBarTitle();

        Bukkit.getServer().getPluginManager().callEvent(new TeamEventStartEvent(this));
        registerListener();

        // Avvia un task che controlla periodicamente se l'evento è finito
        /*
        this.finishCheckerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isFinished()) {
                    finishEvent();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Controlla ogni secondo
        */
    }

    protected void startTimeBasedFinishChecker() {
        this.finishCheckerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isFinished()) {
                    finishEvent();
                    this.cancel();
                    return;
                }
                updateBossBarTitle(); // Potresti aggiornare la bossbar qui
            }
        }.runTaskTimer(plugin, 20L, 20L); // Controlla ogni secondo
    }

    // 3. MODIFICA il tuo metodo finishEvent() esistente
    public void finishEvent() {
        if (finishCheckerTask != null) {
            finishCheckerTask = null;
        }
        unregisterListener();

        String winner = getWinner();
        Bukkit.getServer().getPluginManager().callEvent(new TeamEventFinishEvent(this, winner));

        // --- NUOVA PARTE: INVIA NOTIFICA DI FINE ---
        // Prendiamo l'EventManager dall'istanza principale del plugin
        EventManager eventManager = plugin.getEventManager();
        if (eventManager != null) {
            String winnerName = (winner != null) ? winner : "Pareggio";
            String jsonInputString = "{\"type\": \"FINISH\", \"event_name\": \"" + ChatColor.stripColor(this.name) + "\", \"winner\": \"" + winnerName + "\"}";
            eventManager.sendDiscordNotification(jsonInputString);
        }
        // --- FINE NUOVA PARTE ---

        if (winner != null) {
            bossBar.setProgress(1.0);
            bossBar.setTitle(bossBarTitleWin.replace("%winner%", winner));

            // --- INIZIO MODIFICA ---
            // Determina il perdente e applica la nuova logica di ricompensa globale
            TeamManager teamManager = plugin.getTeamManager();
            String loser = winner.equals(teamManager.getTeamOneName()) ? teamManager.getTeamTwoName() : teamManager.getTeamOneName();
            applyGlobalTeamRewards(winner, loser);
            // --- FINE MODIFICA ---

            // La logica per le ricompense individuali ai giocatori rimane invariata
            for (Player player : Bukkit.getOnlinePlayers()) {
                String playerTeam = teamManager.getTeam(player);
                if (playerTeam != null && playerTeam.equals(winner) && playerProgress.containsKey(player.getName())) {
                    giveRewardPlayer(player, 1, 1);
                }
            }
        } else {
            bossBar.setTitle("§c§lEVENTO TERMINATO - PAREGGIO!");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (bossBar != null) bossBar.removeAll();
            }
        }.runTaskLater(plugin, 20L * 10);

        if (onFinishCallback != null) {
            onFinishCallback.run();
        }
    }

    protected void updateProgress(Player player, long amount) {
        TeamManager teamManager = plugin.getTeamManager();
        String team = teamManager.getTeam(player);
        if(team == null) return;

        teamProgress.put(team, teamProgress.getOrDefault(team, 0L) + amount);
        playerProgress.put(player.getName(), playerProgress.getOrDefault(player.getName(), 0L) + amount);

        updateBossBarTitle();
    }

    private String getWinner() {
        TeamManager teamManager = plugin.getTeamManager();
        long teamOnePoints = teamProgress.getOrDefault(teamManager.getTeamOneName(), 0L);
        long teamTwoPoints = teamProgress.getOrDefault(teamManager.getTeamTwoName(), 0L);
        System.out.println(teamManager.getTeamOneName() + " " + teamOnePoints);
        System.out.println(teamManager.getTeamTwoName() + " " + teamTwoPoints);

        if (teamOnePoints > teamTwoPoints) return teamManager.getTeamOneName();
        if (teamTwoPoints > teamOnePoints) return teamManager.getTeamTwoName();
        return null; // Pareggio
    }


    // ... resto della classe (metodi astratti, getters, ricompense, etc.) ...
//    protected void giveRewardTeam(String team) throws ScriptException {
//        if (team_reward == null || team_reward.isEmpty()) return;
//        String finalExpression = team_reward.replace("%amount_team%", String.valueOf(teamProgress.get(team)));
//        ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
//        Object result = engine.eval(finalExpression);
//        int resultNumber = ((Number) result).intValue();
//        RubyTeams.getInstance().getMySQLManager().updateTeamAmount(team, resultNumber);
//    }

    // 2. AGGIUNGI questo nuovo metodo privato per la nuova logica
    private void applyGlobalTeamRewards(String winner, String loser) {
        // Se non c'è un vincitore o un perdente (es. pareggio), non facciamo nulla.
        if (winner == null || loser == null) {
            return;
        }

        TeamManager teamManager = plugin.getTeamManager();

        // Prendi la percentuale dalla configurazione principale
        double percentage = plugin.getConfigYML().getDouble("event-rewards.points-percentage-transfer");
        if (percentage <= 0) return;

        // Calcola i punti da trasferire basandosi sui PUNTI TOTALI del team perdente
        long loserTotalPoints = teamManager.getTeamPoints(loser);
        long pointsToTransfer = (long) (loserTotalPoints * (percentage / 100.0));

        if (pointsToTransfer <= 0) {
            Bukkit.broadcastMessage("§e[EVENTO] §fIl team " + winner + " ha vinto, ma non ci sono punti da trasferire!");
            return;
        }

        // Applica le modifiche ai punteggi globali
        teamManager.addGlobalPoints(winner, pointsToTransfer);
        teamManager.removeGlobalPoints(loser, pointsToTransfer);

        // Annuncia il trasferimento di punti
        Bukkit.broadcastMessage("§e[EVENTO] §fIl team §a" + winner + "§f ha vinto e guadagna §e" + Formatter.format(pointsToTransfer) + "§f punti dal team avversario!");
        Bukkit.broadcastMessage("§e[EVENTO] §fIl team §c" + loser + "§f ha perso §c" + Formatter.format(pointsToTransfer) + "§f punti.");
    }

    protected void giveRewardPlayer(Player player, double percentage, double costPrestige) {
        Pattern pattern = Pattern.compile("\\[([0-9]+)\\*(%percentage%)(?:\\*(%cost_prestige%))?\\]");
        for (String command : rewards) {
            String parsedCommand = command.replace("%player%", player.getName());
            Matcher matcher = pattern.matcher(parsedCommand);
            StringBuffer result = new StringBuffer();
            while (matcher.find()) {
                long baseValue = Long.parseLong(matcher.group(1));
                if (matcher.group(3) != null) {
                    long finalValue = Math.round(baseValue * percentage * costPrestige);
                    matcher.appendReplacement(result, String.valueOf(finalValue));
                } else {
                    long finalValue = Math.round(baseValue * percentage);
                    matcher.appendReplacement(result, String.valueOf(finalValue));
                }
            }
            matcher.appendTail(result);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), result.toString());
        }
    }




    public void setOnFinishCallback(Runnable onFinishCallback) {
        this.onFinishCallback = onFinishCallback;
    }

    public abstract void updateBossBarTitle();
    public abstract boolean isFinished();
    public void addBossBarPlayer(Player p) { if (bossBar != null) bossBar.addPlayer(p); }
    public void removeBossBarPlayer(Player p) { if (bossBar != null) bossBar.removePlayer(p); }

    public abstract EventType getType();
    public abstract BarColor bossBarColor();
    public abstract void registerListener();
    public abstract void unregisterListener();
    public long getMinutePassed() { return TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - startTime); }
    public String getRemainingTime() { return Utils.convertSecond(duration * 60 - getSecondPassed()); }
    public long getSecondPassed() { return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime); }

    public String getName() { return name; }
    public BossBar getBossBar() { return bossBar; }

    public Config getConfig() {
        return config;
    }
}
