package me.herohd.rubyteams.events.teamevents;

import me.herohd.rubyteams.events.EventType;
import me.herohd.rubyteams.events.TeamEvent;
import me.herohd.rubyteams.manager.TeamManager;
import me.herohd.rubyteams.utils.Config;
import me.herohd.rubyteams.utils.Formatter;
import me.herohd.rubyteams.utils.WordSearchGenerator;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.util.*;

public class WordSearchEvent extends TeamEvent implements Listener {

    private final int gridSize;
    private List<String> wordsInGame; // Rinominato per chiarezza
    private final Map<String, String> foundWordByTeam = new HashMap<>();
    private Map<String, int[]> wordCoordinates;
    private BufferedImage currentImage;
    private Font gridFont;

    private final Location mapScreenLocation;
    private final int screenWidth, screenHeight;
    private final BlockFace screenFacing;
    private final List<ItemFrame> screenFrames = new ArrayList<>();
    private final List<Location> screenBackingBlocks = new ArrayList<>();

    public WordSearchEvent(String name, String startMessage, String bossBarTitle, String bossBarTitleWin, long duration, Config config, List<String> reward, String team_reward) {
        super(name, startMessage, bossBarTitle, bossBarTitleWin, duration, config, reward, team_reward);
        this.gridSize = config.getInt("word-search.grid-size");
        // Non carichiamo più le parole qui, ma nel metodo start()
        this.mapScreenLocation = parseLocationFromString(config.getString("word-search.map-screen-location"));
        this.screenWidth = config.getInt("word-search.screen-width");
        this.screenHeight = config.getInt("word-search.screen-height");
        this.screenFacing = BlockFace.valueOf(config.getString("word-search.screen-facing").toUpperCase());
    }

    @Override
    public void start() {

        List<String> allWordsFromConfig = new ArrayList<>(config.getStringList("word-search.words-to-find"));

        if (allWordsFromConfig.size() > 10) {
            Collections.shuffle(allWordsFromConfig);
            this.wordsInGame = new ArrayList<>(allWordsFromConfig.subList(0, 10));
        } else {
            this.wordsInGame = allWordsFromConfig;
        }

        super.start();
        startTimeBasedFinishChecker();

        // --- MODIFICA 2: Seleziona 10 parole casuali dalla lista ---

        WordSearchGenerator generator = new WordSearchGenerator(gridSize);
        generator.generate(wordsInGame);
        generator.printGridToConsole();

        char[][] grid = generator.getGrid();

        // Assicurati che la lista delle parole da trovare corrisponda a quelle effettivamente piazzate
        this.wordsInGame = new ArrayList<>(generator.getPlacedWords());
        this.wordCoordinates = generator.getWordCoordinates();

        this.currentImage = createImageFromGrid(grid);

        if(mapScreenLocation != null) {
            createMapScreen(currentImage);
        }

        Bukkit.broadcastMessage("§a§l[EVENTO] §eCerca la Parola è iniziato!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if(player.hasPermission("negro"))
                player.sendMessage("§f" + String.join(", ", wordsInGame));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWordFind(AsyncPlayerChatEvent event) {
        if (isFinished()) return;
        Player player = event.getPlayer();
        String message = event.getMessage().toUpperCase();

        if (wordsInGame.contains(message) && !foundWordByTeam.containsKey(message)) {
            event.setCancelled(true);
            String teamName = plugin.getTeamManager().getTeam(player);
            if (teamName == null) return;

            foundWordByTeam.put(message, teamName);
            updateProgress(player, 1);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.broadcastMessage("§a[EVENTO] §eIl team " + teamName + " ha trovato la parola: §b" + message + "§e!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                updateFoundWordOnMap(message, teamName);
            });
        }
    }

    // --- MODIFICA 1: Colori dell'evidenziazione dinamici ---
    private void updateFoundWordOnMap(String word, String teamName) {
        if (currentImage == null || !wordCoordinates.containsKey(word)) return;
        TeamManager tm = plugin.getTeamManager();
        int[] coords = wordCoordinates.get(word);
        int startRow = coords[0], startCol = coords[1], endRow = coords[2], endCol = coords[3];
        int dRow = Integer.compare(endRow, startRow);
        int dCol = Integer.compare(endCol, startCol);

        Graphics2D g = currentImage.createGraphics();
        g.setFont(this.gridFont);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Prende il colore dal TeamManager in base al team
        String teamColorHex = teamName.equals(tm.getTeamOneName()) ?
                tm.getTeamOneColor() : tm.getTeamTwoColor();

        // Decodifica il colore esadecimale e lo rende semi-trasparente
        Color highlightColor = new Color(
                Color.decode(teamColorHex).getRed(),
                Color.decode(teamColorHex).getGreen(),
                Color.decode(teamColorHex).getBlue(),
                120 // Alfa per la trasparenza
        );

        int totalWidth = screenWidth * 128;
        int cellSize = totalWidth / gridSize;

        for (int i = 0; i < word.length(); i++) {
            int r = startRow + i * dRow;
            int c = startCol + i * dCol;

            int cellX = c * cellSize;
            int cellY = r * cellSize;

            g.setColor(highlightColor);
            g.fillRect(cellX, cellY, cellSize, cellSize);

            g.setColor(Color.DARK_GRAY);
            String letter = String.valueOf(word.charAt(i));
            int fontHeight = g.getFontMetrics().getHeight();
            int fontAscent = g.getFontMetrics().getAscent();
            int fontWidth = g.getFontMetrics().stringWidth(letter);
            int textX = c * cellSize + (cellSize - fontWidth) / 2;
            int textY = r * cellSize + (cellSize - fontHeight) / 2 + fontAscent;
            g.drawString(letter, textX, textY);
        }
        g.dispose();
    }

    private BufferedImage createImageFromGrid(char[][] grid) {
        int totalWidth = screenWidth * 128;
        int totalHeight = screenHeight * 128;
        int cellSize = totalWidth / gridSize;

        BufferedImage image = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(250, 250, 240));
        g.fillRect(0, 0, totalWidth, totalHeight);
        g.setColor(Color.DARK_GRAY);

        this.gridFont = new Font("Monospaced", Font.BOLD, (int)(cellSize * 0.9));
        g.setFont(this.gridFont);

        int fontHeight = g.getFontMetrics().getHeight();
        int fontAscent = g.getFontMetrics().getAscent();

        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                String letter = String.valueOf(grid[r][c]);
                int fontWidth = g.getFontMetrics().stringWidth(letter);
                int x = c * cellSize + (cellSize - fontWidth) / 2;
                int y = r * cellSize + (cellSize - fontHeight) / 2 + fontAscent;
                g.drawString(letter, x, y);
            }
        }
        g.dispose();
        return image;
    }

    private void createMapScreen(BufferedImage image) {
        BlockFace wallDirection = screenFacing.getOppositeFace();
        for (int y = 0; y < screenHeight; y++) {
            for (int x = 0; x < screenWidth; x++) {
                Location frameLocation = mapScreenLocation.clone();
                switch (screenFacing) {
                    case SOUTH: frameLocation.add(-x, -y, 0); break;
                    case NORTH: frameLocation.add(x, -y, 0); break;
                    case EAST:  frameLocation.add(0, -y, x); break;
                    case WEST:  frameLocation.add(0, -y, -x); break;
                }
                Location backingBlockLocation = frameLocation.clone().add(wallDirection.getModX(), wallDirection.getModY(), wallDirection.getModZ());
                backingBlockLocation.getBlock().setType(Material.BARRIER);
                screenBackingBlocks.add(backingBlockLocation);

                ItemFrame frame = (ItemFrame) frameLocation.getWorld().spawnEntity(frameLocation, EntityType.ITEM_FRAME);
                frame.setFacingDirection(screenFacing);
                screenFrames.add(frame);

                MapView mapView = Bukkit.createMap(frameLocation.getWorld());
                mapView.getRenderers().forEach(mapView::removeRenderer);

                // --- INIZIO CORREZIONE DEFINITIVA ---
                // Calcoliamo la coordinata X corretta per la "fetta" di immagine
                // per compensare l'inversione a specchio nelle direzioni SOUTH e WEST.
                int correctedX = x;
                if (screenFacing == BlockFace.SOUTH || screenFacing == BlockFace.WEST) {
                    correctedX = screenWidth - 1 - x;
                }

                int subImageX = correctedX * 128;
                int subImageY = y * 128;
                // --- FINE CORREZIONE ---

                BufferedImage subImage = image.getSubimage(subImageX, subImageY, 128, 128);

                mapView.addRenderer(new MapRenderer(true) {
                    @Override
                    public void render(MapView map, MapCanvas canvas, Player player) {
                        canvas.drawImage(0, 0, subImage);
                    }
                });

                ItemStack mapItem = new ItemStack(Material.MAP, 1, mapView.getId());
                frame.setItem(mapItem);
            }
        }
    }

    @Override
    public void finishEvent() {
        screenFrames.forEach(Entity::remove);
        screenFrames.clear();
        wordsInGame.clear();
        foundWordByTeam.clear();
        for (Location loc : screenBackingBlocks) {
            if (loc.getBlock().getType() == Material.BARRIER) {
                loc.getBlock().setType(Material.WOOD);
                loc.getBlock().setData((byte) 1);
            }
        }
        screenBackingBlocks.clear();
        super.finishEvent();
    }

    @Override
    public void updateBossBarTitle() {
        if (bossBar == null) return;
        TeamManager tm = plugin.getTeamManager();
        int foundCount = foundWordByTeam.size();

        int totalWords = (wordsInGame != null) ? wordsInGame.size() : 0;

        bossBar.setTitle(this.bossBarTitle
                .replace("%team_one_name%", tm.getTeamOneName())
                .replace("%team_two_name%", tm.getTeamTwoName())
                .replace("%amount_1%", Formatter.format(teamProgress.getOrDefault(tm.getTeamOneName(), 0L)))
                .replace("%amount_2%", Formatter.format(teamProgress.getOrDefault(tm.getTeamTwoName(), 0L)))
                .replace("%found%", String.valueOf(foundCount))
                .replace("%total%", String.valueOf(totalWords))
        );
        if (totalWords > 0) {
            double progress = (double) foundCount / totalWords;
            bossBar.setProgress(Math.max(0.0, progress));
        }
    }

    @Override
    public boolean isFinished() {
        boolean timeIsUp = getMinutePassed() >= duration;
        boolean allWordsFound = !wordsInGame.isEmpty() && foundWordByTeam.size() >= wordsInGame.size();

        // --- NUOVA LOGICA PER LA VITTORIA ANTICIPATA ---
        if (!wordsInGame.isEmpty()) {
            TeamManager tm = plugin.getTeamManager();
            long teamOneScore = teamProgress.getOrDefault(tm.getTeamOneName(), 0L);
            long teamTwoScore = teamProgress.getOrDefault(tm.getTeamTwoName(), 0L);
            double halfWords = wordsInGame.size() / 2.0;

            if (teamOneScore > halfWords || teamTwoScore > halfWords) {
                return true; // Un team ha superato la metà, l'evento finisce
            }
        }

        return timeIsUp || allWordsFound;
    }

    @Override
    public EventType getType() { return EventType.WORD_SEARCH; }

    @Override
    public BarColor bossBarColor() { return BarColor.GREEN; }

    @Override
    public void registerListener() { Bukkit.getPluginManager().registerEvents(this, plugin); }

    @Override
    public void unregisterListener() { AsyncPlayerChatEvent.getHandlerList().unregister(this); }

    private Location parseLocationFromString(String locString) {
        try {
            String[] parts = locString.split(";");
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                plugin.getLogger().severe("MONDO NON VALIDO: '" + parts[0] + "' in WordSearchEvent");
                return null;
            }
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (Exception e) {
            plugin.getLogger().severe("ERRORE DI CONFIGURAZIONE: La location '" + locString + "' non è formattata bene in WordSearchEvent.");
            return null;
        }
    }
}