package me.herohd.rubyteams.gui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.manager.MySQLManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RewardGui {
    private Player player;
    private MySQLManager mySQLManager;
    private Gui gui;

    private List<Integer> lostId = new ArrayList<>();
    private List<Integer> winId = new ArrayList<>();

    public RewardGui(Player player) {
        this.player = player;
        this.mySQLManager = RubyTeams.getInstance().getMySQLManager();
        lostId = mySQLManager.getLostWeeksWithContribution(player.getUniqueId().toString());
        winId = mySQLManager.getUnclaimedWeeks(player.getUniqueId().toString());
        this.gui = Gui.gui().rows(3).title(Component.text("§c§lRICOMPENSE")).create();
        gui.disableAllInteractions();
        build();
        gui.open(player);
    }

    private void build() {
        int start = 10;
        int currentWeek = mySQLManager.getCurrentWeek();

        for(int i = 0; i < currentWeek; i++) {
            if(lostId.contains(i)) {
                gui.setItem(start+i, createLostItem(i));
            } else if (winId.contains(i)) {
                gui.setItem(start+i, createWinItem(i));
            } else if (mySQLManager.isRewardClaimed(player.getUniqueId().toString(), i)) {
                gui.setItem(start+i, createAlredyClaimed(i));
            } else {
                gui.setItem(start+i, createNoClaim(i));
            }
        }
        gui.getFiller().fill(new GuiItem(new ItemStack(Material.STAINED_GLASS_PANE, 1, (byte) 15)));
    }

    private GuiItem createLostItem(int id) {
        int next = id+1;
        ItemStack itemStack = new ItemStack(Material.STAINED_GLASS, 1, (byte) 1);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName("§6Ricompense settimana " + next);
        List<String> lore = new ArrayList<>();
        lore.add("§7Sfortunatamente il tuo team non ha vinto questa");
        lore.add("§7settimana ma puoi comunque riscattare le");
        lore.add("§7ricompense di consolazione!");
        lore.add("§f");
        lore.add("§e§oClicca per riscattare");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
        meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        itemStack.setItemMeta(meta);

        GuiItem a = new GuiItem(itemStack);
        a.setAction(k -> {
            if(mySQLManager.isRewardClaimed(player.getUniqueId().toString(), id)) return;
            mySQLManager.claimReward(player.getUniqueId().toString(), id);
            for (String reward : RubyTeams.getInstance().getConfigYML().getStringList("rewards-loser." + id)) {
                boolean check = true;
                int n = estraiNumero(reward);
                for(int i = 0; i < n; i++) {
                    if(RubyTeams.getInstance().getTopPlayerManager().getWeekTopPlayers(id).contains(player.getUniqueId().toString())) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.replaceFirst("\\[\\d+\\]\\s*", "").replace("%player%", player.getName()));
                        check = false;
                        break;
                    }
                }
                if(check) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.replace("%player%", player.getName()));
            }
            gui.close(player);
        });
        return a;
    }

    private GuiItem createWinItem(int id) {
        int next = id+1;
        ItemStack itemStack = new ItemStack(Material.STAINED_GLASS, 1, (byte) 5);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName("§aRicompense settimana " + next);
        List<String> lore = new ArrayList<>();
        lore.add("§7Complimenti a te ed al tuo team!");
        lore.add("§7Ora puoi riscattare le ricompense");
        lore.add("§f");
        lore.add("§a§oClicca per riscattare");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        itemStack.setItemMeta(meta);

        GuiItem a = new GuiItem(itemStack);
        a.setAction(k -> {
            if(mySQLManager.isRewardClaimed(player.getUniqueId().toString(), id)) return;
            mySQLManager.claimReward(player.getUniqueId().toString(), id);
            for (String reward : RubyTeams.getInstance().getConfigYML().getStringList("rewards-winner." + id)) {
                boolean check = true;
                int n = estraiNumero(reward);
                for(int i = 0; i < n; i++) {
                    if(RubyTeams.getInstance().getTopPlayerManager().getWeekTopPlayers(id).contains(player.getUniqueId().toString())) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.replaceFirst("\\[\\d+\\]\\s*", "").replace("%player%", player.getName()));
                        check = false;
                        break;
                    }
                }
                if(check) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.replace("%player%", player.getName()));
            }
            gui.close(player);
        });
        return a;
    }

    private GuiItem createAlredyClaimed(int id) {
        int next = id+1;
        ItemStack itemStack = new ItemStack(Material.STAINED_GLASS, 1, (byte) 15);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName("§7Ricompensa della settimana " + next + " già riscattata!");
        List<String> lore = new ArrayList<>();
        lore.add("§fRicompensa già riscattata!");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        itemStack.setItemMeta(meta);

        return new GuiItem(itemStack);
    }


    private GuiItem createNoClaim(int id) {
        int next = id+1;
        ItemStack itemStack = new ItemStack(Material.STAINED_GLASS, 1, (byte) 14);
        ItemMeta meta = itemStack.getItemMeta();
        meta.setDisplayName("§cNessuna ricompensa per la settimana " + next);
        List<String> lore = new ArrayList<>();
        lore.add("§fNon hai contribuito abbastanza, mi dispiace!");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        itemStack.setItemMeta(meta);

        return new GuiItem(itemStack);
    }

    public int estraiNumero(String command) {
        Pattern pattern = Pattern.compile("\\[(\\d+)]");
        Matcher matcher = pattern.matcher(command);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }



}
