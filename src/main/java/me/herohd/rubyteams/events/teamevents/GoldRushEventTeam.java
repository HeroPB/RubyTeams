package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import me.herohd.rubyteams.utils.NBTUtils;
import me.herohd.rubyteams.utils.ProbabilityUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static me.herohd.rubyteams.utils.Utils.parseLocationFromString;

public class GoldRushEventTeam extends TeamEvent implements Listener {

    private final double chance;
    private ItemStack fragmentItem;
    // --- INIZIO MODIFICHE CAMPI ---
    private final Location npcLocation;
    private final String npcName;
    private String uuid;
    private Entity npcEntity; // Campo per tenere traccia dell'NPC spawnato

    public GoldRushEventTeam(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);
        this.chance = config.getDouble("chance");

        // --- INIZIO MODIFICHE COSTRUTTORE ---
        // Leggiamo i dati dell'NPC dalla configurazione
        this.npcName = ChatColor.translateAlternateColorCodes('&', config.getString("npc-deposit.name"));
        this.npcLocation = parseLocationFromString(config.getString("npc-deposit.location")).add(0.5, 0, 0.5);
        // --- FINE MODIFICHE COSTRUTTORE --

        // Costruisci l'item del frammento
        ItemStack item = new ItemStack(Material.valueOf(config.getString("fragment-item.material")));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("fragment-item.name")));
        List<String> lore = config.getStringList("fragment-item.lore").stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
        meta.setLore(lore);
        if (config.getBoolean("fragment-item.enchanted")) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        this.fragmentItem = item;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        // Controlla la probabilità di trovare un frammento
        if (ProbabilityUtils.checkChance(this.chance)) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 2, 0.5), fragmentItem);
        }
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (!isActive() || npcEntity == null) return;

        // Controlla se l'entità cliccata è il nostro NPC
        if (!event.getRightClicked().equals(this.npcEntity)) return;

        event.setCancelled(true); // Impedisce l'apertura di menu di scambio

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand != null && itemInHand.isSimilar(this.fragmentItem)) {
            int amount = itemInHand.getAmount();
            updateProgress(player, amount);
            player.getInventory().setItemInMainHand(null);

            player.sendMessage("§6Hai depositato " + amount + " frammenti! §e(+" + amount + " punti)");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
    }

    private boolean isActive() {
        return npcEntity != null && !isFinished();
    }

    @Override
    public void updateBossBarTitle() {
        TeamManager teamManager = plugin.getTeamManager();
        String teamOneName = teamManager.getTeamOneName();
        String teamTwoName = teamManager.getTeamTwoName();

        bossBar.setTitle(bossBarTitle
                .replace("%team_one_name%", teamOneName)
                .replace("%team_two_name%", teamTwoName)
                .replace("%amount_1%", Formatter.format(teamProgress.getOrDefault(teamOneName, 0L)))
                .replace("%amount_2%", Formatter.format(teamProgress.getOrDefault(teamTwoName, 0L)))
                .replace("%time%", getRemainingTime())
        );

        double progress = (double) (duration * 60 - getSecondPassed()) / (duration * 60);
        bossBar.setProgress(Math.max(0, progress));
    }


    @Override
    public EventType getType() {
        return EventType.GOLD_RUSH;
    }

    @Override
    public BarColor bossBarColor() {
        return BarColor.YELLOW;
    }

    @Override
    public void registerListener() {
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void unregisterListener() {
        BlockBreakEvent.getHandlerList().unregister(this);
        PlayerInteractEvent.getHandlerList().unregister(this);
    }

    @Override
    public boolean isFinished() {
        return getMinutePassed() >= duration;
    }

    // --- NUOVI METODI start() e finishEvent() ---
    @Override
    public void start() {
        super.start(); // Esegue la logica base (bossbar, ecc.)
        this.uuid = UUID.randomUUID().toString();
        this.fragmentItem = NBTUtils.addNBT(fragmentItem, "uuid", uuid);

        startTimeBasedFinishChecker(); // Avvia il timer per la fine dell'evento

        // Spawniamo l'NPC
        if (npcLocation.getWorld() == null) {
            plugin.getLogger().severe("Mondo per l'NPC dell'evento Gold Rush non trovato!");
            return;
        }

        // Carichiamo il chunk per sicurezza prima di spawnare
        npcLocation.getChunk().load();

        // Usiamo un Villager come NPC
        Slime slime = npcLocation.getWorld().spawn(npcLocation, Slime.class);
        slime.setSize(3);
        slime.setCustomName(npcName);
        slime.setCustomNameVisible(true);
        slime.setGravity(false);
        slime.setAI(false);
        slime.setInvulnerable(true);
        slime.setSilent(true);
        this.npcEntity = slime; // Memorizziamo l'entità
    }

    @Override
    public void finishEvent() {
        // Rimuoviamo l'NPC prima di terminare l'evento
        if (this.npcEntity != null && this.npcEntity.isValid()) {
            this.npcEntity.remove();
            this.npcEntity = null; // Pulisce il riferimento
        }
        super.finishEvent(); // Esegue la logica base (ricompense, ecc.)
    }
}
