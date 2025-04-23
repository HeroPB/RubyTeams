package me.herohd.rubyteams.command;

import me.herohd.rubyteams.manager.HappyHourManager;
import me.kr1s_d.commandframework.objects.SubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HHNextCommand implements SubCommand {
    @Override
    public String getSubCommandId() {
        return "nextHappyHour";
    }

    @Override
    public void execute(CommandSender commandSender, String[] strings) {

        if (HappyHourManager.isActive()) {
            commandSender.sendMessage("§6[Happy Hour]§f È attivo ora per il team §e" + HappyHourManager.getCurrentTeam() + "§f!");
        } else {
            long seconds = HappyHourManager.getSecondsUntilNext();
            long min = seconds / 60;
            long sec = seconds % 60;
            commandSender.sendMessage("§6[Happy Hour]§f Il prossimo evento sarà tra §e" + min + "m " + sec + "s§f.");
        }
    }

    @Override
    public String getPermission() {
        return "rubytrades.admin";
    }

    @Override
    public int minArgs() {
        return 0;
    }

    @Override
    public Map<Integer, List<String>> getTabCompleter(CommandSender commandSender, Command command, String s, String[] strings) {
        return Collections.emptyMap();
    }

    @Override
    public boolean allowedConsole() {
        return true;
    }
}
