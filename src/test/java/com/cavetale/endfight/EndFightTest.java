package com.cavetale.endfight;

import com.google.gson.Gson;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;

public final class EndFightTest {
    @Test
    public void testHorseList() {
        EndFightPlugin.State state = new EndFightPlugin.State();
        state.scores.put(UUID.randomUUID(), (int)(100.0 * Math.random()));
        state.round = (int)(10.0 * Math.random());
        state.mobs = (int)(10.0 * Math.random());
        state.alive = (int)(10.0 * Math.random());
        Gson gson = new Gson();
        String json = gson.toJson(state);
        System.out.println(json);
        EndFightPlugin.State state2 = gson.fromJson(json, EndFightPlugin.State.class);
        String json2 = gson.toJson(state2);
        Assert.assertEquals(json, json2);
        Assert.assertEquals(state, state2);
    }
}
