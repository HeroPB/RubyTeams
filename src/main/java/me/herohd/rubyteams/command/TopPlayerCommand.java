package me.herohd.rubyteams.command;

import me.herohd.rubyteams.RubyTeams;
import me.kr1s_d.commandframework.objects.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TopPlayerCommand implements SubCommand  {
    @Override
    public String getSubCommandId() {
        return "gettopplayer";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        int week;
        try {
            week = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInserisci un numero valido per la settimana!");
            return;
        }

        List<String> topPlayers = RubyTeams.getInstance().getTopPlayerManager().getTopPlayersForWeek(week);
        if (topPlayers.isEmpty()) {
            sender.sendMessage("§cNessun dato trovato per la settimana " + week + "!");
            return;
        }

        sender.sendMessage("§eTop 10 player della settimana §b" + week + "§e:");
        int pos = 1;
        for (String uuid : topPlayers) {
            sender.sendMessage("§6#" + pos + " §7- " + Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName());
            pos++;
        }
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
