package me.herohd.rubyteams.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Formatter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderExtension extends PlaceholderExpansion {
    @Override
    public @NotNull String getIdentifier() {
        return "rubyteams";
    }

    @Override
    public @NotNull String getAuthor() {
        return "H3r0HD_";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        String[] par = params.split("_");
        if(params.startsWith("team")) {
            final String team = RubyTeams.getInstance().getTeamManager().getTeam(player);
            if(team == null) return "VUOTO";
            return team;
        }
        if(params.startsWith("position")) {
            final int team = RubyTeams.getInstance().getTeamManager().getPosition(player);
            if(team == -1) return "0";
            return String.valueOf(team);
        }
        if(params.startsWith("player-amount")) {
            return Formatter.format(RubyTeams.getInstance().getTeamManager().getPlayerAmount(player));
        }
        if(params.startsWith("top-earned")) {
            Integer topTeam = Integer.valueOf(par[1].toLowerCase());
            int post = Integer.parseInt(par[2])-1;
            if(RubyTeams.getInstance().getTopPlayerManager().getTop10ForTeam(topTeam).size() < post) return "0";
            return Formatter.format(RubyTeams.getInstance().getTopPlayerManager().getTop10ForTeam(topTeam).get(post).getMoneyEarned());
        }
        if(params.startsWith("top")) {
            Integer topTeam = Integer.valueOf(par[1].toLowerCase());
            int post = Integer.parseInt(par[2])-1;
            if(RubyTeams.getInstance().getTopPlayerManager().getTop10ForTeam(topTeam).size() < post) return "VUOTO";
            final String player1 = RubyTeams.getInstance().getTopPlayerManager().getTop10ForTeam(topTeam).get(post).getPlayer();
            if(player1 == null) {
                return "VUOTO";
            }
            return player1;
        }
        if (params.startsWith("points_")) {
            TeamManager teamManager = RubyTeams.getInstance().getTeamManager();
            if (par.length < 2) return "Formato non valido";

            String teamIdentifier = par[1]; // Sarà "one" o "two"
            long teamPoints = 0;

            if (teamIdentifier.equalsIgnoreCase("one")) {
                String teamName = teamManager.getTeamOneName();
                // Questa riga chiama il metodo che hai nel tuo TeamManager ed è corretta!
                teamPoints = teamManager.getTeamPoints(teamName);
            } else if (teamIdentifier.equalsIgnoreCase("two")) {
                String teamName = teamManager.getTeamTwoName();
                // Anche questa riga è corretta!
                teamPoints = teamManager.getTeamPoints(teamName);
            }

            return Formatter.format(teamPoints);
        }
        return "none";
    }
}
