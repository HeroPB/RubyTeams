package me.herohd.rubyteams.command;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.manager.TeamManager;
import me.kr1s_d.commandframework.objects.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NextWeekCommand implements SubCommand {
    @Override
    public String getSubCommandId() {
        return "reset-week";
    }

    @Override
    public void execute(CommandSender commandSender, String[] strings) {
        final String winnerTeam = RubyTeams.getInstance().getTeamManager().getTeamWithHighestAmount();
        Bukkit.broadcastMessage("§f" +
                "\n§f §f§lCOMPLIMENTI AL TEAM §c§l" + winnerTeam + " " +
                "\n§f Per aver §cvinto §fquesta settimana! I giocatori che" +
                "\n§f facevano parte di quel team potranno riscattare le §cricompense" +
                "\n§f al §c/warp pasqua§f! I team verranno mescolati, buon lavoro!" +
                "\n§f §f");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!Objects.requireNonNull(RubyTeams.getInstance().getTeamManager().getTeam(player)).equalsIgnoreCase(winnerTeam))
                continue;
            player.sendTitle("§c§lCOMPLIMENTI", "§fIl tuo team ha vinto!");
        }
        RubyTeams.getInstance().getTeamManager().clearAll();
        RubyTeams.getInstance().getMySQLManager().loadTopPlayers();

        for (Player player : Bukkit.getOnlinePlayers()) {
            final String s = RubyTeams.getInstance().getTeamManager().assignPlayer(player);
            player.sendTitle("§6§lNUOVO TEAM", "§f" + s);
            player.sendMessage("§f\n" +
                    "§f §6§lPARTECIPI AD UN NUOVO TEAM\n" +
                    "§f Durante questa settimana appartieni al team§8: §e" + s + "\n §f §f");
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
        return null;
    }

    @Override
    public boolean allowedConsole() {
        return true;
    }
}
