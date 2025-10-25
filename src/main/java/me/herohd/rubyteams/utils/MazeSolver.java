package me.herohd.rubyteams.utils;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MazeSolver {

    // Una semplice classe per memorizzare le coordinate
    public static class Point {
        public final int x;
        public final int z;
        public Point parent;

        public Point(int x, int z) {
            this.x = x;
            this.z = z;
            this.parent = null;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Point point = (Point) obj;
            return x == point.x && z == point.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }
    }

    /**
     * Risolve un labirinto usando l'algoritmo Breadth-First Search (BFS).
     * @param maze La griglia 0=sentiero, 1=muro
     * @param start Il punto di partenza
     * @param end Il punto di arrivo
     * @return Una lista di Point che rappresenta il percorso, o una lista vuota se non c'è soluzione.
     */
    public static List<Point> solve(int[][] maze, Point start, Point end) {
        List<Point> path = new LinkedList<>();
        Queue<Point> queue = new LinkedList<>();
        boolean[][] visited = new boolean[maze.length][maze[0].length];

        queue.add(start);
        visited[start.z][start.x] = true;

        Point current = null;
        boolean found = false;

        while (!queue.isEmpty()) {
            current = queue.poll();

            if (current.equals(end)) {
                found = true;
                break;
            }

            int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}}; // E, S, W, N
            for (int[] dir : directions) {
                int nextX = current.x + dir[0];
                int nextZ = current.z + dir[1];

                // Controlla i bordi, se è un sentiero (0) e se non è stato visitato
                if (nextX >= 0 && nextX < maze[0].length && nextZ >= 0 && nextZ < maze.length &&
                        maze[nextZ][nextX] == 0 && !visited[nextZ][nextX]) {

                    Point next = new Point(nextX, nextZ);
                    next.parent = current;
                    visited[nextZ][nextX] = true;
                    queue.add(next);
                }
            }
        }

        if (found) {
            // Ricostruisci il percorso all'indietro dal punto finale
            while (current != null) {
                path.add(current);
                current = current.parent;
            }
            Collections.reverse(path); // Metti il percorso nell'ordine corretto
        }

        return path;
    }
}