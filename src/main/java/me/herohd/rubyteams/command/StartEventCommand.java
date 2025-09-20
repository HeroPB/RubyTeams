package me.herohd.rubyteams.command;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.EventManager;
import me.kr1s_d.commandframework.objects.SubCommand;
import me.kr1s_d.commandframework.utils.CommandMapBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.HashMap; // Importa HashMap
import java.util.List;
import java.util.Map;

public class StartEventCommand implements SubCommand {
    @Override
    public String getSubCommandId() {
        return "startevent";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        EventManager eventManager = RubyTeams.getInstance().getEventManager();

        if (args.length == 1) {
            // --- AVVIO CASUALE ---
            boolean started = eventManager.forceStartRandomEvent();
            if (started) {
                sender.sendMessage("§aEvento casuale avviato con successo!");
            } else {
                sender.sendMessage("§cC'è già un evento attivo!");
            }
        } else {
            // --- AVVIO SPECIFICO ---
            String eventName = args[1].toLowerCase();
            boolean started = eventManager.forceStartSpecificEvent(eventName);
            if (started) {
                sender.sendMessage("§aEvento '" + eventName + "' avviato con successo!");
            } else {
                if(eventManager.getActiveEvent() != null) {
                    sender.sendMessage("§cC'è già un evento attivo!");
                } else {
                    sender.sendMessage("§cEvento '" + eventName + "' non trovato! Controlla i nomi dei file nella cartella /events/");
                }
            }
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
    public Map<Integer, List<String>> getTabCompleter(CommandSender sender, Command command, String label, String[] args) {
        return CommandMapBuilder.builder().set(1, RubyTeams.getInstance().getEventManager().getAvailableEventNames()).getMap();
    }

    @Override
    public boolean allowedConsole() {
        return true;
    }
}