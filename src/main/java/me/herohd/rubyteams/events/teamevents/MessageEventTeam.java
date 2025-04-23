package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.utils.Config;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;

public class MessageEventTeam extends TeamEvent implements Listener {

    public MessageEventTeam(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);
    }

    @EventHandler
    public void onMessageSend(AsyncPlayerChatEvent event) {
        if(event.isCancelled()) return;
        Player player = event.getPlayer();
        updateProgress(player, 1);
    }

    @Override
    public EventType getType() {
        return EventType.MESSAGE;
    }

    @Override
    public boolean isFinished() {
        return getMinutePassed() >= duration;
    }

    @Override
    public BarColor bossBarColor() {
        return BarColor.RED;
    }

    @Override
    public void registerListener() {
        Bukkit.getServer().getPluginManager().registerEvents(this, RubyTeams.getInstance());
    }

    @Override
    public void unregisterListener() {
        AsyncPlayerChatEvent.getHandlerList().unregister(this);
    }
}
