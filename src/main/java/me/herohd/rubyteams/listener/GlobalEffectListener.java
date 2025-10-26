package me.herohd.rubyteams.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import me.herohd.rubyteams.RubyTeams;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GlobalEffectListener implements Listener {

    private final RubyTeams plugin;
    private final Set<UUID> playersWithEffects = Collections.synchronizedSet(new HashSet<>());
    private BukkitTask effectTask;
    private final ProtocolManager protocolManager;
    private boolean effectsGloballyActive = false;

    private static final EnumWrappers.Particle PARTICLE_TYPE = EnumWrappers.Particle.REDSTONE;
    private static final long NIGHT_TIME_TICKS = 18000L;
    private static final long FROZEN_NIGHT_TIME_TICKS = -NIGHT_TIME_TICKS; // Mantiene il valore negativo

    public GlobalEffectListener(RubyTeams plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void startGlobalEffects() {
        if (effectsGloballyActive) return;
        effectsGloballyActive = true;
        startEffectTask(); // Avvia il task
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyEffects(player);
        }
        plugin.getLogger().info("Effetti globali (notte/particelle) ATTIVATI.");
    }

    public void stopGlobalEffects() {
        if (!effectsGloballyActive) return;
        effectsGloballyActive = false;
        stopEffectTask(); // Ferma il task
        Set<UUID> playersToReset = new HashSet<>(playersWithEffects);
        playersWithEffects.clear();
        for (UUID uuid : playersToReset) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                    p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                }
                // Invia UN SOLO pacchetto di reset finale
                sendTimePacket(p, p.getWorld().getTime(), false); // false = non freezare
            }
        }
        plugin.getLogger().info("Effetti globali (notte/particelle) DISATTIVATI.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (effectsGloballyActive) {
            applyEffects(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playersWithEffects.remove(event.getPlayer().getUniqueId());
    }

    private void applyEffects(Player player) {
        if (playersWithEffects.contains(player.getUniqueId())) return;
        if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
        playersWithEffects.add(player.getUniqueId());
        // Invia subito il primo pacchetto notte
        sendTimePacket(player, NIGHT_TIME_TICKS, true);
    }

    private void removeEffects(Player player) {
        // Rimuove solo dal set. Il reset finale è in stopGlobalEffects
        playersWithEffects.remove(player.getUniqueId());
        // Se l'evento NON è più attivo E il giocatore è online, resetta subito
        if (!effectsGloballyActive && player.isOnline()) {
            sendTimePacket(player, player.getWorld().getTime(), false);
        }
    }

    private void sendTimePacket(Player player, long time, boolean freeze) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.UPDATE_TIME);
        packet.getLongs().write(0, player.getWorld().getFullTime());
        // Usa sempre un valore negativo se freeze è true
        packet.getLongs().write(1, freeze ? -Math.abs(time) : Math.abs(time));
        protocolManager.sendServerPacket(player, packet);
    }

    private void startEffectTask() {
        if (effectTask != null && !effectTask.isCancelled()) return;
        effectTask = new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> currentPlayers = new HashSet<>(playersWithEffects);
                if (currentPlayers.isEmpty()) return;

                for (UUID uuid : currentPlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        // Invia pacchetto tempo notte più frequentemente
                        sendTimePacket(p, NIGHT_TIME_TICKS, true); // true = freeza a mezzanotte

                        // Spawna particelle
                        spawnRedstoneParticles(p);
                    } else {
                        playersWithEffects.remove(uuid); // Rimuovi se offline
                    }
                }
            }
            // --- MODIFICA: Esegui ogni 5 tick (quarto di secondo) ---
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void stopEffectTask() {
        if (effectTask != null && !effectTask.isCancelled()) {
            effectTask.cancel();
            effectTask = null;
        }
    }

    // --- METODO MODIFICATO ---
    private void spawnRedstoneParticles(Player player) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.WORLD_PARTICLES);
        Location loc = player.getLocation();
        packet.getParticles().write(0, PARTICLE_TYPE);
        packet.getBooleans().write(0, false);
        packet.getFloat().write(0, (float) loc.getX());
        packet.getFloat().write(1, (float) loc.getY() + 1.0f);
        packet.getFloat().write(2, (float) loc.getZ());
        packet.getFloat().write(3, 0.5f); packet.getFloat().write(4, 0.5f); packet.getFloat().write(5, 0.5f);
        packet.getFloat().write(6, 0.0f);
        packet.getIntegers().write(0, 5);
        packet.getIntegerArrays().write(0, new int[0]);

        protocolManager.sendServerPacket(player, packet);
    }

    public boolean areEffectsActive() {
        return effectsGloballyActive;
    }
}