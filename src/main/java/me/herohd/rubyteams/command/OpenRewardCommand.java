package me.herohd.rubyteams.command;

import me.herohd.rubyteams.gui.RewardGui;
import me.kr1s_d.commandframework.objects.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OpenRewardCommand implements SubCommand {
    @Override
    public String getSubCommandId() {
        return "open";
    }

    @Override
    public void execute(CommandSender commandSender, String[] strings) {
        String player = strings[1];
        Player p = Bukkit.getPlayer(player);
        if(p == null) {
            commandSender.sendMessage("§cPlayer offline");
            return;
        }

        new RewardGui(p);
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
        return Collections.emptyMap();
    }

    @Override
    public boolean allowedConsole() {
        return true;
    }
}
