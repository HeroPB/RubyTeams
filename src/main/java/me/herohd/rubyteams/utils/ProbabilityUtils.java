package me.herohd.rubyteams.utils;

import java.util.concurrent.ThreadLocalRandom;

public class ProbabilityUtils {

    /**
     * Controlla se un evento si verifica in base a una data probabilità percentuale.
     * @param percentage La probabilità (es. 0.5 per 0.5%).
     * @return true se l'evento si è verificato, altrimenti false.
     */
    public static boolean checkChance(double percentage) {
        if (percentage >= 100.0) return true;
        if (percentage <= 0.0) return false;
        return ThreadLocalRandom.current().nextDouble(100) < percentage;
    }
}
