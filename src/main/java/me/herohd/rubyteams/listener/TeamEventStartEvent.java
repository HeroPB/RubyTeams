package me.herohd.rubyteams.listener;

import me.herohd.rubyteams.events.TeamEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class TeamEventStartEvent extends Event {
    private static final HandlerList HANDLERS_LIST = new HandlerList();

    private TeamEvent teamEvent;
    public TeamEventStartEvent(TeamEvent teamEvent) {
        this.teamEvent = teamEvent;
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
}
