package com.osrssettlement.engine;

import com.osrssettlement.model.ActiveEvent;
import com.osrssettlement.model.SettlementEvent;
import com.osrssettlement.model.SettlementState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Starts a new event every hour of playtime; each lasts two hours, so after the first hour two overlap.
 */
public class EventScheduler
{
	private final Random random;

	public EventScheduler(Random random)
	{
		this.random = random;
	}

	public void tick(SettlementState state)
	{
		long now = state.getPlaytimeTicks();
		state.getEvents().removeIf(active -> active.getEndsAtPlaytime() <= now);
		if (state.getEvents().isEmpty() || now >= state.getNextEventAtPlaytime())
		{
			start(state);
		}
	}

	public void start(SettlementState state)
	{
		boolean buff = random.nextDouble() < Balance.EVENT_BUFF_CHANCE;
		List<SettlementEvent> pool = new ArrayList<>();
		for (SettlementEvent event : SettlementEvent.values())
		{
			if (event.isBuff() == buff)
			{
				pool.add(event);
			}
		}

		SettlementEvent next = pool.get(random.nextInt(pool.size()));
		long now = state.getPlaytimeTicks();
		state.getEvents().add(new ActiveEvent(next, now + Balance.EVENT_DURATION_TICKS));
		state.setNextEventAtPlaytime(now + Balance.EVENT_INTERVAL_TICKS);
		SettlementEngine.addLog(state, "Event: " + next.getDisplayName() + " - "
			+ next.describeEffect(YieldCalculator.effectiveEventMultiplier(state, next)));
	}
}
