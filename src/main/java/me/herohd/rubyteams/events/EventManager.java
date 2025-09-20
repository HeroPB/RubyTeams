package me.herohd.rubyteams.events;

import me.herohd.rubyteams.RubyTeams;
import me.herohd.rubyteams.utils.Config;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class EventManager {
    private final RubyTeams plugin;
    private final List<String> scheduledHours = new ArrayList<>();
    private final List<TeamEvent> availableEvents = new ArrayList<>();
    private TeamEvent activeEvent = null;

    public EventManager(RubyTeams plugin) {
        this.plugin = plugin;
        loadEventConfigurations();
        generateEventTimes();
        startScheduler();
    }

    /**
     * Carica tutte le configurazioni degli eventi dalla cartella /events/.
     */
    private void loadEventConfigurations() {
        File eventsFolder = new File(plugin.getDataFolder(), "events");
        if (!eventsFolder.exists()) {
            eventsFolder.mkdirs();
        }
        File[] eventFiles = eventsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (eventFiles == null) return;

        for (File eventFile : eventFiles) {
            Config eventConfig = new Config(plugin, "events/" + eventFile.getName().replace(".yml", ""));
            try {
                availableEvents.add(TeamEvent.loadEvent(eventConfig));
                plugin.getLogger().info("[RubyTeams] Caricato evento: " + eventFile.getName());
            } catch (Exception e) {
                plugin.getLogger().severe("Errore nel caricamento del file evento: " + eventFile.getName());
                e.printStackTrace();
            }
        }
    }

    /**
     * Genera gli orari casuali per gli eventi del giorno.
     */
    private void generateEventTimes() {
        Config mainConfig = plugin.getConfigYML();
        if (mainConfig.getBoolean("debug-date")) {
            scheduledHours.add(mainConfig.getString("debug-quest-hour"));
            plugin.getLogger().info("[RubyTeams] Modalità debug: evento programmato per le " + scheduledHours.get(0));
            return;
        }

        int eventsPerDay = mainConfig.getInt("event-per-day");
        String[] range = mainConfig.getString("event-hour-range").split("-");
        int startHour = Integer.parseInt(range[0]);
        int endHour = Integer.parseInt(range[1]);
        Random random = new Random();

        for (int i = 0; i < eventsPerDay; i++) {
            int hour = startHour + random.nextInt(endHour - startHour);
            int minute = random.nextInt(60);
            scheduledHours.add(String.format("%02d:%02d", hour, minute));
        }
        plugin.getLogger().info("[RubyTeams] Orari eventi programmati per oggi: " + scheduledHours);
    }

    /**
     * Avvia il task che controlla l'ora e fa partire gli eventi.
     */
    private void startScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeEvent != null) {
                    return; // Non fare nulla se un evento è già in corso
                }

                LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES); // Ora attuale, senza secondi
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

                // Usiamo un Iterator per poter rimuovere elementi durante il ciclo in sicurezza
                Iterator<String> iterator = scheduledHours.iterator();
                while (iterator.hasNext()) {
                    String scheduledTimeStr = iterator.next();
                    LocalTime scheduledTime = LocalTime.parse(scheduledTimeStr, formatter);

                    // --- LOGICA PER LA NOTIFICA 5 MINUTI PRIMA ---
                    LocalTime notificationTime = scheduledTime.minusMinutes(5);
                    if (now.equals(notificationTime)) {
                        String jsonInputString = "{\"type\": \"PRE_START\", \"event_time\": \"" + scheduledTimeStr + "\"}";
                        sendDiscordNotification(jsonInputString);
                    }

                    // --- LOGICA PER FAR PARTIRE L'EVENTO ---
                    if (now.equals(scheduledTime)) {
                        iterator.remove(); // Rimuovi l'orario per non ripeterlo
                        startRandomEvent();
                        break; // Esci dal ciclo una volta avviato un evento
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L * 60); // Controlla ogni minuto (20 tick * 60 secondi)
    }

    /**
     * Invia una notifica asincrona al bot Discord esterno.
     * @param eventTime L'ora di inizio dell'evento (es. "15:30")
     */
    /**
     * Invia una notifica asincrona al bot Discord esterno con un payload JSON specifico.
     * @param jsonPayload La stringa JSON da inviare nel corpo della richiesta.
     */
    public void sendDiscordNotification(String jsonPayload) { // <-- Cambiato a public e accetta una stringa
        Config mainConfig = plugin.getConfigYML();
        if (!mainConfig.getBoolean("discord-notifier.enabled")) {
            return;
        }

        String urlString = mainConfig.getString("discord-notifier.webhook-url");
        String secretKey = mainConfig.getString("discord-notifier.secret-key");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("X-Secret-Key", secretKey);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    plugin.getLogger().info("[RubyTeams] Inviata notifica a Discord con successo.");
                } else {
                    plugin.getLogger().warning("[RubyTeams] Notifica a Discord inviata, ma con codice di risposta: " + responseCode);
                }
                conn.disconnect();

            } catch (Exception e) {
                plugin.getLogger().severe("Impossibile inviare la notifica a Discord: " + e.getMessage());
            }
        });
    }

    private void startRandomEvent() {
        if (availableEvents.isEmpty()) {
            plugin.getLogger().warning("Nessun evento da avviare, controlla la cartella /events/");
            return;
        }
        activeEvent = availableEvents.get(new Random().nextInt(availableEvents.size()));
        activeEvent.setOnFinishCallback(() -> activeEvent = null); // Quando l'evento finisce, imposta activeEvent a null


        // --- NUOVA PARTE: INVIA NOTIFICA DI INIZIO ---
        String eventName = activeEvent.getName();
        String jsonInputString = "{\"type\": \"START\", \"event_name\": \"" + ChatColor.stripColor(eventName) + "\"}";
        sendDiscordNotification(jsonInputString);
        // --- FINE NUOVA PARTE ---

        activeEvent.start();
    }

    public boolean forceStartSpecificEvent(String eventName) {
        if (activeEvent != null) {
            return false; // C'è già un evento attivo
        }

        // Cerca l'evento nella lista di quelli disponibili
        for (TeamEvent event : availableEvents) {
            // Confrontiamo il nome del file di configurazione dell'evento
            if (event.getConfig().getName().replace(".yml", "").equalsIgnoreCase(eventName)) {
                activeEvent = event;
                activeEvent.setOnFinishCallback(() -> activeEvent = null);
                activeEvent.start();
                return true; // Evento trovato e avviato
            }
        }

        return false; // Evento non trovato
    }

    public boolean forceStartRandomEvent() {
        // Controlla se c'è già un evento attivo
        if (activeEvent != null) {
            return false; // Comunica al chiamante che non è stato possibile avviare l'evento
        }

        // Se non ci sono eventi attivi, avvia la logica esistente
        startRandomEvent();
        return true; // Comunica che l'avvio è andato a buon fine
    }

    public List<String> getAvailableEventNames() {
        List<String> names = new ArrayList<>();
        for (TeamEvent event : availableEvents) {
            names.add(event.getConfig().getName().replace(".yml", ""));
        }
        return names;
    }


    public boolean reloadEvents() {
        // Sicurezza: non ricaricare se un evento è attivo.
        if (activeEvent != null) {
            return false;
        }

        // Svuota la lista degli eventi attuali
        availableEvents.clear();

        // Ricarica tutti i file di configurazione degli eventi
        loadEventConfigurations(); // Usiamo il metodo che già esiste!

        return true;
    }

    /**
     * Restituisce il numero di eventi attualmente caricati.
     * Utile per dare un feedback nel comando di reload.
     *
     * @return il numero di eventi disponibili.
     */
    public int getAvailableEventsCount() {
        return availableEvents.size();
    }

    public TeamEvent getActiveEvent() {
        return activeEvent;
    }
}
