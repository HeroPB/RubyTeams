package me.herohd.rubyteams.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
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
        if(params.startsWith("team")) {
            final String team = TeamManager.getTeam(player.getUniqueId().toString());
            if(team == null) return "VUOTO";
            return team;
        }
        if(params.startsWith("position")) {
            final int team = TeamManager.getPosition(player.getUniqueId().toString());
            if(team == -1) return "0";
            return String.valueOf(team);
        }
        if(params.startsWith("status-nido")) {
            return Formatter.format(TeamManager.getAmountTeam("Ordine del Nido"));
        }
        if(params.startsWith("status-gusci")) {
            return Formatter.format(TeamManager.getAmountTeam("Gusci Spezzati"));
        }
        if(params.startsWith("player-amount")) {
            return Formatter.format(TeamManager.getPlayerAmount(player.getUniqueId().toString()));
        }
        return "none";
    }
}
