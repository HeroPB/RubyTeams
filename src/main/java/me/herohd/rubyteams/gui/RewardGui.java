package me.herohd.rubyteams.gui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.manager.MySQLManager;
import me.herohd.rubyteams.manager.TopPlayerManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RewardGui {
    private final Player player;
    private final MySQLManager mySQLManager;
    private final Gui gui;

    public RewardGui(Player player) {
        this.player = player;
        this.mySQLManager = RubyTeams.getInstance().getMySQLManager();
        this.gui = Gui.gui().rows(3).title(Component.text("§c§lRICOMPENSE")).create();
        gui.disableAllInteractions();
        build();
        gui.open(player);
    }

    private void build() {
        int startSlot = 10;
        // Ottieni tutti gli stati con una sola chiamata al DB
        List<MySQLManager.WeeklyStatus> statuses = mySQLManager.getPlayerWeeklyStatuses(player.getUniqueId().toString());

        for (MySQLManager.WeeklyStatus status : statuses) {
            int weekDisplayNumber = status.weekNumber + 1;

            if (status.claimed) {
                gui.setItem(startSlot + status.weekNumber, createRewardItem(
                        "§7Ricompensa settimana " + weekDisplayNumber + " già riscattata!",
                        Arrays.asList("§fRicompensa già riscattata!"),
                        Material.STAINED_GLASS_PANE, (byte) 15, false, null
                ));
            } else if (status.hasContributed) {
                if (status.wasWinner) {
                    gui.setItem(startSlot + status.weekNumber, createRewardItem(
                            "§aRicompense settimana " + weekDisplayNumber,
                            Arrays.asList("§7Complimenti a te ed al tuo team!", "§7Ora puoi riscattare le ricompense", "§f", "§a§oClicca per riscattare"),
                            Material.STAINED_GLASS, (byte) 5, true, () -> giveRewards(status.weekNumber, "rewards-winner")
                    ));
                } else {
                    gui.setItem(startSlot + status.weekNumber, createRewardItem(
                            "§6Ricompense settimana " + weekDisplayNumber,
                            Arrays.asList("§7Sfortunatamente il tuo team non ha vinto,", "§7ma puoi comunque riscattare una ricompensa!", "§f", "§e§oClicca per riscattare"),
                            Material.STAINED_GLASS, (byte) 1, true, () -> giveRewards(status.weekNumber, "rewards-loser")
                    ));
                }
            } else {
                gui.setItem(startSlot + status.weekNumber, createRewardItem(
                        "§cNessuna ricompensa per la settimana " + weekDisplayNumber,
                        Arrays.asList("§fNon hai contribuito abbastanza, mi dispiace!"),
                        Material.STAINED_GLASS, (byte) 14, false, null
                ));
            }
        }
        gui.getFiller().fill(new GuiItem(new ItemStack(Material.STAINED_GLASS_PANE, 1, (byte) 15)));
    }

    /**
     * Metodo centralizzato per creare un item della GUI.
     */
    private GuiItem createRewardItem(String name, List<String> lore, Material material, byte data, boolean enchanted, Runnable action) {
        ItemStack itemStack = new ItemStack(material, 1, data);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        if (enchanted) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        itemStack.setItemMeta(meta);

        GuiItem guiItem = new GuiItem(itemStack);
        if (action != null) {
            guiItem.setAction(event -> action.run());
        }
        return guiItem;
    }

    /**
     * Metodo centralizzato per dare le ricompense.
     */
    private void giveRewards(int weekId, String configSection) {
        mySQLManager.claimReward(player.getUniqueId().toString(), weekId);
        List<TopPlayerManager.TopPlayerEntry> teamOne = RubyTeams.getInstance().getTopPlayerManager().getTop10ForTeamInWeek(1, weekId);
        List<TopPlayerManager.TopPlayerEntry> teamTwo = RubyTeams.getInstance().getTopPlayerManager().getTop10ForTeamInWeek(2, weekId);

        for (String rewardCommand : RubyTeams.getInstance().getConfigYML().getStringList(configSection + "." + weekId)) {
            int requiredTopPosition = estraiNumero(rewardCommand);
            String command = rewardCommand.replaceFirst("\\[\\d+\\]\\s*", "");

            // Se il comando non ha un requisito di posizione OPPURE il giocatore è nella top list
            if (requiredTopPosition == 0) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                continue;
            }
            for(int i = 0; i < requiredTopPosition; i++) {
                if(teamOne.get(i).getPlayer().equalsIgnoreCase(player.getName())){
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                    break;
                }
                if(teamTwo.get(i).getPlayer().equalsIgnoreCase(player.getName())){
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                    break;
                }
            }
        }
        gui.close(player);
        player.sendMessage("§aHai riscattato le tue ricompense!");
    }

    private int estraiNumero(String command) {
        Pattern pattern = Pattern.compile("\\[(\\d+)]");
        Matcher matcher = pattern.matcher(command);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
