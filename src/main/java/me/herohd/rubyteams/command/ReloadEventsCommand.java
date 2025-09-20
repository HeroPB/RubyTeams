package me.herohd.rubyteams.command;
import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.EventManager;
import me.kr1s_d.commandframework.objects.SubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Sottocomando per ricaricare le configurazioni di tutti gli eventi.
 */
public class ReloadEventsCommand implements SubCommand {
    @Override
    public String getSubCommandId() {
        // Il nome del sottocomando, es: /rubyteams reloadevents
        return "reloadevents";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        EventManager eventManager = RubyTeams.getInstance().getEventManager();

        // Chiama il nuovo metodo di reload
        boolean success = eventManager.reloadEvents();

        if (success) {
            int count = eventManager.getAvailableEventsCount();
            sender.sendMessage(ChatColor.GREEN + "Configurazioni degli eventi ricaricate con successo!");
            sender.sendMessage(ChatColor.GRAY + "Sono stati caricati " + count + " eventi.");
        } else {
            sender.sendMessage(ChatColor.RED + "Impossibile ricaricare gli eventi mentre uno è in corso.");
            sender.sendMessage(ChatColor.YELLOW + "Usa /rubyteams stopevent prima di eseguire il reload.");
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