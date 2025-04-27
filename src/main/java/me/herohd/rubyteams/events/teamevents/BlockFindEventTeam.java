package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import org.bukkit.boss.BarColor;

import java.util.List;

import static me.herohd.rubyteams.events.EventType.BLOCK_FIND;

public class BlockFindEventTeam extends TeamEvent {
    public BlockFindEventTeam(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);
    }

    @Override
    public EventType getType() {
        return BLOCK_FIND;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public BarColor bossBarColor() {
        return BarColor.RED;
    }

    @Override
    public void registerListener() {

    }

    @Override
    public void unregisterListener() {

    }

    @Override
    public void updateBossBarTitle() {
        bossBar.setTitle(bossBarTitle
                .replace("%amount_nido%", Formatter.format(teamProgress.getOrDefault("Ordine del Nido", 0l)))
                .replace("%amount_gusci%", Formatter.format(teamProgress.getOrDefault("Gusci Spezzati", 0l)))
                .replace("%block%", getRemainingTime())
        );
    }
}
