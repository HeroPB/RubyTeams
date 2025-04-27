package me.herohd.rubyteams.manager;

import java.util.*;

public class TopPlayerManager {
    private final Map<Integer, List<String>> weeklyTopPlayers = new HashMap<>();

    public void setTopPlayersForWeek(int weekNumber, List<String> topPlayers) {
        weeklyTopPlayers.put(weekNumber, topPlayers);
    }

    public List<String> getTopPlayersForWeek(int weekNumber) {
        return weeklyTopPlayers.getOrDefault(weekNumber, Collections.emptyList());
    }
}
