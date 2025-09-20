package me.herohd.rubyteams.command;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.EventManager;
import me.herohd.rubyteams.events.TeamEvent;
import me.kr1s_d.commandframework.objects.SubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public class StopEventCommand implements SubCommand {
    @Override
    public String getSubCommandId() {
        return "forcestop";
    }

    @Override
    public void execute(CommandSender commandSender, String[] strings) {
        EventManager manager = RubyTeams.getInstance().getEventManager();
        TeamEvent active = manager.getActiveEvent();
        if(active == null) {
            commandSender.sendMessage("Coglione, nulla è attivo");
            return;
        }

        active.finishEvent();
    }

    @Override
    public String getPermission() {
        return "rubyteams.admin";
    }

    @Override
    public int minArgs() {
        return 0;
    }

    @Override
    public Map<Integer, List<String>> getTabCompleter(CommandSender commandSender, Command command, String s, String[] strings) {
        return null;
    }

    @Override
    public boolean allowedConsole() {
        return true;
    }
}
