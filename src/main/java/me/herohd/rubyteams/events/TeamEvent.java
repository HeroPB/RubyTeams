package me.herohd.rubyteams.events;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.teamevents.MessageEventTeam;
import me.herohd.rubyteams.listener.TeamEventFinishEvent;
import me.herohd.rubyteams.listener.TeamEventStartEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import me.herohd.rubyteams.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

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
    protected String name;
    protected String bossBarTitle;
    protected String startMessage;
    protected String bossBarTitleWin;
    protected long duration; // minuti
    protected List<String> rewards;
    protected String team_reward;
    protected Map<String, Long> playerProgress = new HashMap<>();
    protected Map<String, Long> teamProgress = new HashMap<>();
    protected long globalProgress = 0;


    protected BossBar bossBar;
    protected long startTime;
    protected Config config;

    public TeamEvent(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        this.name = name;
        this.startMessage = startMessage;
        this.bossBarTitle = bossBarTitle;
        this.bossBarTitleWin = bossBarTitleWin;
        this.duration = duration;
        this.config = config;
        this.rewards = reward;
        this.team_reward = team_reward;
        this.startTime = 0;
    }

    public static TeamEvent loadQuest(Config config) {
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
            default:
                throw new IllegalArgumentException("Cazzo hai scritto per la quest: " + type);
        }
    }
    public void start() {
        globalProgress = 0;
        startTime = System.currentTimeMillis();
        playerProgress.clear();

        String title = "§c§lSFIDA TRA TEAM";
        bossBar = Bukkit.createBossBar(title, bossBarColor(), BarStyle.SOLID);
        bossBar.setProgress(0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
            player.sendMessage(startMessage);
        }

        TeamEventStartEvent event = new TeamEventStartEvent(this);
        Bukkit.getServer().getPluginManager().callEvent(event);

        registerListener();
    }

    protected void giveRewardTeam(String team) throws ScriptException {
        // Pattern per trovare i placeholder tipo %nome_placeholder%

        // Ora la stringa è un'espressione matematica standard, es: "100 * 50"
        String finalExpression = team_reward.replace("%amount_team%", String.valueOf( teamProgress.get(team)));

        // Usa JavaScript engine per valutare l'espressione
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
        Object result = engine.eval(finalExpression);
        int resultNumber = ((Number) result).intValue();
        RubyTeams.getInstance().getMySQLManager().updateTeamAmount(team, resultNumber);
    }

    protected void giveRewardPlayer(Player player, double percentage, double costPrestige) {
        // Modifica la regex per supportare sia il caso con solo %percentage% sia con %cost_prestige%
        Pattern pattern = Pattern.compile("\\[([0-9]+)\\*(%percentage%)(?:\\*(%cost_prestige%))?\\]");

        for (String command : rewards) {
            // Sostituiamo il nome del player
            String parsedCommand = command.replace("%player%", player.getName());

            // Cerchiamo i match per la sintassi aggiornata
            Matcher matcher = pattern.matcher(parsedCommand);
            StringBuffer result = new StringBuffer();

            while (matcher.find()) {
                // Otteniamo il valore numerico di base
                long baseValue = Long.parseLong(matcher.group(1));

                // Verifica se esiste anche %cost_prestige%
                if (matcher.group(3) != null) {
                    // Se esiste %cost_prestige%, calcoliamo il valore con entrambi
                    long finalValue = Math.round(baseValue * percentage * costPrestige);
                    matcher.appendReplacement(result, String.valueOf(finalValue));
                } else {
                    // Se non esiste %cost_prestige%, calcoliamo solo con %percentage%
                    long finalValue = Math.round(baseValue * percentage);
                    matcher.appendReplacement(result, String.valueOf(finalValue));
                }
            }

            // Aggiungiamo la parte rimanente della stringa
            matcher.appendTail(result);

            // Eseguiamo il comando
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), result.toString());
        }
    }

    private String getWinner() {
        long maxAmount = -1;
        String teamWithHighestAmount = null;

        // Scorri tutti i team per trovare quello con l'amount pi&ugrave; alto
        for (Map.Entry<String, Long> entry : teamProgress.entrySet()) {
            if (entry.getValue() > maxAmount) {
                maxAmount = entry.getValue();
                teamWithHighestAmount = entry.getKey();
            }
        }

        return teamWithHighestAmount;
    }

    private void completeQuest() {
        unregisterListener();
        String winner = getWinner();
        TeamEventFinishEvent event = new TeamEventFinishEvent(this, winner);
        Bukkit.getServer().getPluginManager().callEvent(event);

        bossBar.setProgress(1.0);
        bossBar.setTitle(bossBarTitleWin.replace("%winner%", winner));

        new BukkitRunnable() {
            @Override
            public void run() {
                bossBar.removeAll();
            }
        }.runTaskLater(RubyTeams.getInstance(), 40L);

        // TODO Rewards e messaggi
    }

    protected void updateProgress(Player player, long amount) {
        String team = TeamManager.getTeam(player.getUniqueId().toString());
        if(team == null) return;
        globalProgress += amount;
        long amount_team = teamProgress.getOrDefault(team, 0L) + amount;
        teamProgress.put(team, amount_team);
        playerProgress.put(player.getName(), playerProgress.getOrDefault(player.getName(), 0L) + amount);
        updateBossBarTitle();
        bossBar.setProgress((double) globalProgress / teamProgress.getOrDefault("Ordine del Nido", 0L));
    }

    public void updateBossBarTitle() {
        bossBar.setTitle(bossBarTitle
                .replace("%amount_nido%", Formatter.format(teamProgress.getOrDefault("Ordine del Nido", 0l)))
                .replace("%amount_gusci%", Formatter.format(teamProgress.getOrDefault("Gusci Spezzati", 0l)))
                .replace("%time%", getRemainingTime())
        );
    }

    public abstract EventType getType();

    public abstract boolean isFinished();

    public abstract BarColor bossBarColor();

    public abstract void registerListener();

    public abstract void unregisterListener();

    public void addBossBarPlayer(Player player) {
        bossBar.addPlayer(player);
    }

    public void removeBossBarPlayer(Player player) {
        bossBar.removePlayer(player);
    }


    public long getMinutePassed() {
        return TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - startTime);
    }

    public String getRemainingTime() {
        return Utils.convertSecond(duration * 60 - getSecondPassed());
    }

    public long getSecondPassed() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime);
    }


    public String getName() {
        return name;
    }

    public String getBossBarTitle() {
        return bossBarTitle;
    }

    public long getDuration() {
        return duration;
    }

    public List<String> getRewards() {
        return rewards;
    }

    public String getTeam_reward() {
        return team_reward;
    }

    public Map<String, Long> getPlayerProgress() {
        return playerProgress;
    }

    public Map<String, Long> getTeamProgress() {
        return teamProgress;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public long getStartTime() {
        return startTime;
    }

    public Config getConfig() {
        return config;
    }
}
