package me.herohd.rubyteams.utils;

import java.util.*;

public class WordSearchGenerator {

    private enum Direction {
        ORIZZONTALE(0, 1),
        ORIZZONTALE_INVERSO(0, -1),
        VERTICALE(1, 0),
        VERTICALE_INVERSO(-1, 0),
        DIAGONALE_GIU_DESTRA(1, 1),
        DIAGONALE_SU_SINISTRA(-1, -1),
        DIAGONALE_GIU_SINISTRA(1, -1),
        DIAGONALE_SU_DESTRA(-1, 1);

        private final int rowChange;
        private final int colChange;

        Direction(int rowChange, int colChange) {
            this.rowChange = rowChange;
            this.colChange = colChange;
        }
    }

    private final int gridSize;
    private char[][] grid;
    private final List<String> successfullyPlacedWords = new ArrayList<>();
    private final Map<String, int[]> wordCoordinates = new HashMap<>();
    private final char EMPTY_CELL = '\0';

    public WordSearchGenerator(int gridSize) {
        this.gridSize = gridSize;
        this.grid = new char[gridSize][gridSize];
    }

    public void generate(List<String> words) {
        List<String> wordList = new ArrayList<>(words);
        // Ordina le parole dalla più lunga alla più corta per aumentare le probabilità di successo
        wordList.sort(Comparator.comparingInt(String::length).reversed());

        for (String word : wordList) {
            String upperWord = word.toUpperCase();
            if (placeWord(upperWord)) {
                successfullyPlacedWords.add(upperWord);
            } else {
                System.err.println("[RubyTeams/WordSearch] Avviso: Impossibile posizionare la parola '" + word + "'.");
            }
        }
        fillRemainingGrid();
    }

    private boolean placeWord(String word) {
        Random random = new Random();
        List<Direction> directions = new ArrayList<>(Arrays.asList(Direction.values()));
        Collections.shuffle(directions);

        for (int i = 0; i < 500; i++) { // Prova fino a 500 posizioni/direzioni casuali
            int startRow = random.nextInt(gridSize);
            int startCol = random.nextInt(gridSize);
            Direction direction = directions.get(random.nextInt(directions.size()));

            if (canWordBePlaced(word, startRow, startCol, direction)) {
                int currentRow = startRow;
                int currentCol = startCol;
                for (char letter : word.toCharArray()) {
                    grid[currentRow][currentCol] = letter;
                    currentRow += direction.rowChange;
                    currentCol += direction.colChange;
                }
                int endRow = startRow + (word.length() - 1) * direction.rowChange;
                int endCol = startCol + (word.length() - 1) * direction.colChange;
                wordCoordinates.put(word, new int[]{startRow, startCol, endRow, endCol});
                return true;
            }
        }
        return false;
    }

    private boolean canWordBePlaced(String word, int startRow, int startCol, Direction direction) {
        int currentRow = startRow;
        int currentCol = startCol;

        for (int i = 0; i < word.length(); i++) {
            // Controllo dei bordi
            if (currentRow < 0 || currentRow >= gridSize || currentCol < 0 || currentCol >= gridSize) {
                return false;
            }
            // Controllo delle collisioni
            char existingChar = grid[currentRow][currentCol];
            if (existingChar != EMPTY_CELL && existingChar != word.charAt(i)) {
                return false;
            }
            currentRow += direction.rowChange;
            currentCol += direction.colChange;
        }
        return true;
    }

    private void fillRemainingGrid() {
        Random random = new Random();
        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                if (grid[r][c] == EMPTY_CELL) {
                    grid[r][c] = (char) ('A' + random.nextInt(26));
                }
            }
        }
    }

    public void printGridToConsole() {
        System.out.println("--- GRIGLIA GENERATA (SOLUZIONE) ---");
        for (int r = 0; r < gridSize; r++) {
            StringBuilder row = new StringBuilder();
            for (int c = 0; c < gridSize; c++) {
                row.append(grid[r][c] == EMPTY_CELL ? '.' : grid[r][c]).append(" ");
            }
            System.out.println(row.toString().trim());
        }
        System.out.println("------------------------------------");
    }

    public char[][] getGrid() { return grid; }
    public List<String> getPlacedWords() { return successfullyPlacedWords; }
    public Map<String, int[]> getWordCoordinates() { return wordCoordinates; }
}