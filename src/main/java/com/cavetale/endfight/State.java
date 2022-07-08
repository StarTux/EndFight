package com.cavetale.endfight;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public final class State {
    boolean enabled;
    int round;
    int mobs;
    int alive;
    int roundTicks;
    Map<UUID, Integer> scores = new HashMap<>();
}
