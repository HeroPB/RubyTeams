package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static me.herohd.rubyteams.events.EventType.BLOCK_FIND;

public class BlockFindEventTeam extends TeamEvent {

    private Map<ItemStack, String> blocks;

    public BlockFindEventTeam(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);
        blocks = new HashMap<>();

        for (String s : config.getStringList("blocks")) {
            String[] blocco = s.split(";");
            ItemStack itemStack = new ItemStack(Material.valueOf(blocco[0]), 1, Byte.parseByte(blocco[1]));
            blocks.put(itemStack, blocco[2]);
        }
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
