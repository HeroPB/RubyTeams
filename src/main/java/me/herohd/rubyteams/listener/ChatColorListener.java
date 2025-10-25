package me.herohd.rubyteams.listener; // O dove preferisci

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import me.herohd.rubyteams.RubyTeams;
import net.md_5.bungee.api.chat.BaseComponent; // Import BaseComponent
import net.md_5.bungee.api.chat.TextComponent; // Import TextComponent
import net.md_5.bungee.chat.ComponentSerializer; // Import ComponentSerializer (per 1.12.2)
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

public class ChatColorListener {

    private final RubyTeams plugin;
    private PacketAdapter packetListener;
    private boolean isActive = false;

    public ChatColorListener(RubyTeams plugin) {
        this.plugin = plugin;
    }

    public void registerListener() {
        if (isActive) return;

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        packetListener = new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                WrappedChatComponent chatComponent = event.getPacket().getChatComponents().read(0);
                if (chatComponent == null) return;

                try {
                    // --- NUOVA LOGICA ---
                    // 1. Converti il componente JSON in testo semplice (legacy format)
                    BaseComponent[] components = ComponentSerializer.parse(chatComponent.getJson());
                    String legacyText = BaseComponent.toLegacyText(components);

                    // 2. Rimuovi tutti i codici colore esistenti
                    String strippedText = ChatColor.stripColor(legacyText);

                    // 3. Applica solo il colore rosso
                    String redText = ChatColor.RED + strippedText;

                    // 4. Crea un nuovo WrappedChatComponent dal testo rosso legacy
                    WrappedChatComponent modifiedComponent = WrappedChatComponent.fromLegacyText(redText);
                    // --- FINE NUOVA LOGICA ---

                    // Sovrascrivi il componente nel pacchetto
                    event.getPacket().getChatComponents().write(0, modifiedComponent);

                } catch (Exception e) {
                    // Logga l'errore se qualcosa va storto nella conversione/modifica
                    plugin.getLogger().warning("Impossibile modificare il colore del pacchetto chat: " + e.getMessage());
                }
            }
        };

        protocolManager.addPacketListener(packetListener);
        isActive = true;
        plugin.getLogger().info("Listener ProtocolLib per colorare la chat (SOLO ROSSO) ATTIVATO.");
    }

    public void unregisterListener() {
        if (!isActive || packetListener == null) return;

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.removePacketListener(packetListener);
        packetListener = null;
        isActive = false;
        plugin.getLogger().info("Listener ProtocolLib per colorare la chat DISATTIVATO.");
    }

    public boolean isActive() {
        return isActive;
    }
}