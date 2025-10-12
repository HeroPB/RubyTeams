package me.herohd.rubyteams.utils;

import java.util.Collections;
import java.util.Arrays;
import java.util.Random;

/**
 * Genera la struttura di un labirinto perfetto usando l'algoritmo Recursive Backtracker.
 * La logica di questa classe è ottimizzata per essere eseguita in modo asincrono.
 */
public class MazeGenerator {

    private final int width;
    private final int height;
    private final int[][] maze;
    private final Random random = new Random();

    public MazeGenerator(int width, int height) {
        // Le dimensioni devono essere dispari per avere un bordo di muri consistente
        this.width = (width % 2 == 0) ? width + 1 : width;
        this.height = (height % 2 == 0) ? height + 1 : height;
        this.maze = new int[this.height][this.width];
    }

    /**
     * Calcola la struttura del labirinto.
     * @return una griglia 2D dove 1 rappresenta un muro e 0 un sentiero.
     */
    public int[][] generate() {
        // Inizializza l'intera griglia come muri
        for (int i = 0; i < height; i++) {
            Arrays.fill(maze[i], 1);
        }

        // --- PUNTO DI PARTENZA CORRETTO ---
        // Trova un punto di partenza casuale con coordinate pari
        int halfWidth = (width - 1) / 2;
        int halfHeight = (height - 1) / 2;

        int startX = random.nextInt(halfWidth + 1) * 2;
        int startY = random.nextInt(halfHeight + 1) * 2;

        // Inizia a "scavare" il labirinto dal punto di partenza
        carve(startX, startY);

        return maze;
    }

    /**
     * Metodo ricorsivo che "scava" i sentieri del labirinto.
     */
    private void carve(int cx, int cy) {
        maze[cy][cx] = 0; // Marca la cella corrente come sentiero

        // Direzioni randomizzate: Nord, Est, Sud, Ovest
        Integer[] dirs = {0, 1, 2, 3};
        Collections.shuffle(Arrays.asList(dirs));

        for (int dir : dirs) {
            int nx = cx;
            int ny = cy;
            int wallX = cx;
            int wallY = cy;

            // Calcola la posizione della cella successiva e del muro da abbattere
            switch (dir) {
                case 0: ny -= 2; wallY -= 1; break; // Nord
                case 1: nx += 2; wallX += 1; break; // Est
                case 2: ny += 2; wallY += 1; break; // Sud
                case 3: nx -= 2; wallX -= 1; break; // Ovest
            }

            // Se la cella successiva è valida e non è stata visitata...
            if (nx >= 0 && nx < width && ny >= 0 && ny < height && maze[ny][nx] == 1) {
                maze[wallY][wallX] = 0; // ...abbatti il muro...
                carve(nx, ny);      // ...e continua a scavare da lì.
            }
        }
    }
}