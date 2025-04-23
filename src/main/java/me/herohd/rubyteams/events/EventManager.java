package me.herohd.rubyteams.events;

import me.herohd.rubyteams.utils.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class EventManager {
    private final int EVENT_PER_DAY;
    private final int[] EVENT_RANGE;
    private final List<String> HOUR_EVENT;
    private final List<TeamEvent> TEAM_EVENTS;
    private Config config;

    public EventManager(Config config) {
        this.config = config;
        this.EVENT_PER_DAY = config.getInt("event-per-day");
        this.EVENT_RANGE = Arrays.stream(config.getString("event-hour-range").split("-"))
                .mapToInt(Integer::parseInt).toArray();
        this.TEAM_EVENTS = new ArrayList<>();
        if(!config.getBoolean("debug-date")) this.HOUR_EVENT = getHoursNow();
        else this.HOUR_EVENT = new ArrayList<>(Arrays.asList(config.getString("debug-quest-hour")));
    }


    private List<String> getHoursNow() {
        Random random = new Random();
        List<String> times = new ArrayList<>();
        int time = EVENT_RANGE[0] * 60;

        for (int i = 0; i < EVENT_PER_DAY; i++) {
            time += 25 + random.nextInt(60);
            if (time > EVENT_RANGE[1] * 60) {
                break;
            }
            final String format = String.format("%02d:%02d", time / 60, time % 60);
            times.add(format);
        }
        return times;
    }

}
