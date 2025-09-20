package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class RockPaperScissorsEvent extends TeamEvent implements Listener {

    private enum EventState { SIGNUP, DUELING, FINISHED }
    private enum Choice { SASSO, CARTA, FORBICE, NONE }

    private EventState currentState;

    // Gestione Iscrizioni
    private final int signupDuration;
    private final Location npcLocation;
    private final String npcName;
    private Entity signupNpc;
    private final Set<UUID> participantsTeamOne = new HashSet<>();
    private final Set<UUID> participantsTeamTwo = new HashSet<>();

    // Gestione Duelli
    private final int duelCountdown;
    private final int delayBetweenDuels;
    private Queue<UUID> duelQueueOne = new LinkedList<>();
    private Queue<UUID> duelQueueTwo = new LinkedList<>();
    private Player duelistOne, duelistTwo;
    private final Map<UUID, Choice> playerChoices = new HashMap<>();
    private BukkitTask currentDuelTask;

    public RockPaperScissorsEvent(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);
        this.signupDuration = config.getInt("signup-duration-seconds");
        this.duelCountdown = config.getInt("duel-countdown-seconds");
        this.delayBetweenDuels = config.getInt("delay-between-duels-seconds");
        this.npcName = ChatColor.translateAlternateColorCodes('&', config.getString("signup-npc.name"));
        this.npcLocation = parseLocationFromString(config.getString("signup-npc.location"));
    }

    @Override
    public void start() {
        super.start();
        this.currentState = EventState.SIGNUP;

        // FIX 1: Pulisce le liste dei partecipanti all'inizio di ogni evento.
        participantsTeamOne.clear();
        participantsTeamTwo.clear();

        // FIX 2: Spawna un NPC centrato nel blocco e lo rende statico e muto.
        if (npcLocation != null) {
            Location centeredNpcLocation = npcLocation.clone().add(0.5, 0, 0.5);
            Slime slime = centeredNpcLocation.getWorld().spawn(centeredNpcLocation, Slime.class);
            slime.setSize(3);
            slime.setInvulnerable(true);
            slime.setCustomName(npcName);
            slime.setCustomNameVisible(true);
            slime.setGravity(false);
            slime.setAI(false);
            slime.setSilent(true);
            this.signupNpc = slime;
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::startMatchmaking, 20L * signupDuration);
    }

    private void startMatchmaking() {
        this.currentState = EventState.DUELING;
        if (signupNpc != null) {
            signupNpc.remove();
            signupNpc = null;
        }

        if (participantsTeamOne.isEmpty() || participantsTeamTwo.isEmpty()) {
            Bukkit.broadcastMessage("§c[EVENTO] Non ci sono abbastanza giocatori per avviare la sfida!");
            finishEvent();
            return;
        }

        List<UUID> teamOneList = new ArrayList<>(participantsTeamOne);
        List<UUID> teamTwoList = new ArrayList<>(participantsTeamTwo);
        Collections.shuffle(teamOneList);
        Collections.shuffle(teamTwoList);
        duelQueueOne.addAll(teamOneList);
        duelQueueTwo.addAll(teamTwoList);

        Bukkit.broadcastMessage("§a[EVENTO] Iscrizioni chiuse! Iniziano i duelli...");
        startNextDuel();
    }

    private void startNextDuel() {
        // FIX 5: Logica di matchmaking equa. Si ferma quando uno dei due team esaurisce i giocatori.
        if (duelQueueOne.isEmpty() || duelQueueTwo.isEmpty()) {
            finishEvent();
            return;
        }

        duelistOne = Bukkit.getPlayer(duelQueueOne.poll());
        duelistTwo = Bukkit.getPlayer(duelQueueTwo.poll());
        playerChoices.clear();

        if (duelistOne == null || !duelistOne.isOnline()) {
            if (duelistTwo != null && duelistTwo.isOnline()) updateProgress(duelistTwo, 1);
            Bukkit.broadcastMessage("§c[EVENTO] " + (duelistOne != null ? duelistOne.getName() : "Un duellante") + " non è online, il suo avversario vince il round.");
            Bukkit.getScheduler().runTaskLater(plugin, this::startNextDuel, 20L * delayBetweenDuels);
            return;
        }
        if (duelistTwo == null || !duelistTwo.isOnline()) {
            updateProgress(duelistOne, 1);
            Bukkit.broadcastMessage("§c[EVENTO] " + (duelistTwo != null ? duelistTwo.getName() : "Un duellante") + " non è online, il suo avversario vince il round.");
            Bukkit.getScheduler().runTaskLater(plugin, this::startNextDuel, 20L * delayBetweenDuels);
            return;
        }
        String name1 = duelistOne.getName();
        String name2 = duelistTwo.getName();

        // 1. Messaggio pubblico per tutti gli spettatori
        Bukkit.broadcastMessage(" "); // Riga vuota per spaziatura
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&e&lTurno di: &a&l" + name1 + " &f&lVS &c&l" + name2));
        Bukkit.broadcastMessage(" "); // Riga vuota per spaziatura

        // 2. Messaggi privati per i due duellanti
        duelistOne.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&lTocca a te contro &c&l" + name2 + "&6&l! &eScrivi SASSO, CARTA o FORBICE prima della scadenza del timer."));
        duelistTwo.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&lTocca a te contro &a&l" + name1 + "&6&l! &eScrivi SASSO, CARTA o FORBICE prima della scadenza del timer."));


        currentDuelTask = new BukkitRunnable() {
            private int timeLeft = duelCountdown;

            @Override
            public void run() {
                if (!duelistOne.isOnline() || !duelistTwo.isOnline()) {
                    this.cancel();
                    resolveDuel();
                    return;
                }

                if (timeLeft <= 0) {
                    this.cancel();
                    resolveDuel();
                    return;
                }

                String subtitle = "§eTempo rimanente: §c" + timeLeft + "s";
                duelistOne.sendTitle(" ", subtitle, 0, 25, 10);
                duelistTwo.sendTitle(" ", subtitle, 0, 25, 10);
                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void resolveDuel() {
        if (duelistOne == null || duelistTwo == null) return;

        Choice choice1 = playerChoices.getOrDefault(duelistOne.getUniqueId(), Choice.NONE);
        Choice choice2 = playerChoices.getOrDefault(duelistTwo.getUniqueId(), Choice.NONE);

        if (!duelistOne.isOnline() && duelistTwo.isOnline()) choice1 = Choice.NONE;
        if (!duelistTwo.isOnline() && duelistOne.isOnline()) choice2 = Choice.NONE;

        String resultMessage = "§6[RISULTATO] §e" + duelistOne.getName() + " (" + choice1 + ") §fvs §e" + duelistTwo.getName() + " (" + choice2 + ") §f-> ";

        if (choice1 == Choice.NONE || choice2 == Choice.NONE) {
            if (choice1 != Choice.NONE) {
                updateProgress(duelistOne, 1);
                resultMessage += "Vince " + duelistOne.getName() + "!";
            } else if (choice2 != Choice.NONE) {
                updateProgress(duelistTwo, 1);
                resultMessage += "Vince " + duelistTwo.getName() + "!";
            } else {
                resultMessage += "Nessuno ha scelto! Nessun punto.";
            }
        } else if (choice1 == choice2) {
            updateProgress(duelistOne, 1);
            updateProgress(duelistTwo, 1);
            resultMessage += "Pareggio! Un punto a testa!";
        } else if ((choice1 == Choice.SASSO && choice2 == Choice.FORBICE) || (choice1 == Choice.CARTA && choice2 == Choice.SASSO) || (choice1 == Choice.FORBICE && choice2 == Choice.CARTA)) {
            updateProgress(duelistOne, 1);
            resultMessage += "Vince " + duelistOne.getName() + "!";
        } else {
            updateProgress(duelistTwo, 1);
            resultMessage += "Vince " + duelistTwo.getName() + "!";
        }

        Bukkit.broadcastMessage(resultMessage);
        updateBossBarTitle();

        // FIX 4: Resetta i duellanti per bloccare la chat dopo la scelta.
        duelistOne = null;
        duelistTwo = null;

        Bukkit.getScheduler().runTaskLater(plugin, this::startNextDuel, 20L * delayBetweenDuels);
    }

    @Override
    public void finishEvent() {
        this.currentState = EventState.FINISHED;
        if (currentDuelTask != null && !currentDuelTask.isCancelled()) {
            currentDuelTask.cancel();
        }

        // FIX 5: Pulizia completa di tutte le strutture dati.
        participantsTeamOne.clear();
        participantsTeamTwo.clear();
        duelQueueOne.clear();
        duelQueueTwo.clear();
        playerChoices.clear();
        if (signupNpc != null) signupNpc.remove();

        super.finishEvent();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (currentState != EventState.DUELING) return;
        Player player = event.getPlayer();

        if (duelistOne != null && duelistTwo != null && (player.equals(duelistOne) || player.equals(duelistTwo))) {
            event.setCancelled(true);
            String message = event.getMessage().toUpperCase();
            try {
                Choice choice = Choice.valueOf(message);
                if (!playerChoices.containsKey(player.getUniqueId())) {
                    playerChoices.put(player.getUniqueId(), choice);
                    player.sendMessage("§aHai scelto §l" + choice + "§a! Attendi il risultato...");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                }
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cScelta non valida! Scrivi SASSO, CARTA o FORBICE.");
            }
        } else if(!player.hasPermission("rubyteams.admin")){
            event.setCancelled(true);
            player.sendMessage("§cEvento in chat in corso, aspetta la fine...");
        }
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractAtEntityEvent event) {
        if (currentState != EventState.SIGNUP || signupNpc == null || !event.getRightClicked().equals(signupNpc)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        TeamManager tm = plugin.getTeamManager();
        String team = tm.getTeam(player);

        if (team == null) {
            player.sendMessage("§cDevi essere in un team per partecipare!");
            return;
        }

        if (participantsTeamOne.contains(player.getUniqueId()) || participantsTeamTwo.contains(player.getUniqueId())) {
            player.sendMessage("§eSei già iscritto all'evento!");
            return;
        }

        if (team.equals(tm.getTeamOneName())) {
            participantsTeamOne.add(player.getUniqueId());
        } else {
            participantsTeamTwo.add(player.getUniqueId());
        }
        player.sendMessage("§aTi sei iscritto all'evento! Attendi l'inizio dei duelli.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (currentState == EventState.DUELING && (event.getPlayer().equals(duelistOne) || event.getPlayer().equals(duelistTwo))) {
            if (currentDuelTask != null) {
                currentDuelTask.cancel();
                resolveDuel();
            }
        }
    }

    @Override
    public void updateBossBarTitle() {
        if (bossBar == null) return;
        TeamManager teamManager = plugin.getTeamManager();
        String title = this.bossBarTitle
                .replace("%team_one_name%", teamManager.getTeamOneName())
                .replace("%team_two_name%", teamManager.getTeamTwoName())
                .replace("%amount_1%", Formatter.format(teamProgress.getOrDefault(teamManager.getTeamOneName(), 0L)))
                .replace("%amount_2%", Formatter.format(teamProgress.getOrDefault(teamManager.getTeamTwoName(), 0L)));
        bossBar.setTitle(title);
        bossBar.setProgress(1.0);
    }

    @Override
    public boolean isFinished() {
        return currentState == EventState.FINISHED;
    }

    @Override
    public EventType getType() {
        return EventType.ROCK_PAPER_SCISSORS;
    }

    @Override
    public BarColor bossBarColor() {
        return BarColor.WHITE;
    }

    @Override
    public void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void unregisterListener() {
        AsyncPlayerChatEvent.getHandlerList().unregister(this);
        PlayerInteractAtEntityEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);
    }

    private Location parseLocationFromString(String locString) {
        try {
            String[] parts = locString.split(";");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                plugin.getLogger().severe("MONDO NON VALIDO: '" + parts[0] + "'");
                return null;
            }
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (Exception e) {
            plugin.getLogger().severe("ERRORE DI CONFIGURAZIONE: La location '" + locString + "' non è formattata bene.");
            return null;
        }
    }
}