package me.herohd.rubyteams.listener;

import me.herohd.rubyteams.events.TeamEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TeamEventFinishEvent extends Event {
    private static final HandlerList HANDLERS_LIST = new HandlerList();

    private TeamEvent teamEvent;
    private String teamWinner;
    public TeamEventFinishEvent(TeamEvent teamEvent, String teamWinner) {
        this.teamEvent = teamEvent;
        this.teamWinner = teamWinner;
    }


    public HandlerList getHandlers() {
        return HANDLERS_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS_LIST;
    }

    public TeamEvent getTeamEvent() {
        return teamEvent;
    }

    public String getTeamWinner() {
        return teamWinner;
    }
}