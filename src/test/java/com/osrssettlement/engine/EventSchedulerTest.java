package com.osrssettlement.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.osrssettlement.model.ActiveEvent;
import com.osrssettlement.model.EventEffect;
import com.osrssettlement.model.SettlementEvent;
import com.osrssettlement.model.SettlementState;
import com.osrssettlement.model.SkillRule;
import com.osrssettlement.model.SkillRules;
import java.util.Random;
import org.junit.Test;

public class EventSchedulerTest
{
	@Test
	public void newSettlementStartsWithOneEvent()
	{
		SettlementState state = SettlementEngine.newSettlement();
		new SettlementEngine(state, new Random(1));

		assertEquals(1, state.getEvents().size());
		assertEquals(Balance.EVENT_DURATION_TICKS, state.getEvents().get(0).getEndsAtPlaytime());
		assertEquals(Balance.EVENT_INTERVAL_TICKS, state.getNextEventAtPlaytime());
		assertTrue(state.getLog().get(0).getMessage().contains(state.getEvents().get(0).getEvent().getDisplayName()));
	}

	@Test
	public void existingEventsAreKeptOnLoad()
	{
		SettlementState state = new SettlementState();
		state.getEvents().add(new ActiveEvent(SettlementEvent.FISH_MIGRATION, 500));
		state.setNextEventAtPlaytime(400);
		new SettlementEngine(state, new Random(1));

		assertEquals(1, state.getEvents().size());
		assertEquals(SettlementEvent.FISH_MIGRATION, state.getEvents().get(0).getEvent());
		assertEquals(400, state.getNextEventAtPlaytime());
	}

	@Test
	public void aNewEventStartsEveryHourAndTwoOverlap()
	{
		SettlementState state = SettlementEngine.newSettlement();
		SettlementEngine engine = new SettlementEngine(state, new Random(1));
		ActiveEvent first = state.getEvents().get(0);

		tickUntil(engine, state, Balance.EVENT_INTERVAL_TICKS - 1);
		assertEquals(1, state.getEvents().size());

		tickUntil(engine, state, Balance.EVENT_INTERVAL_TICKS);
		assertEquals(2, state.getEvents().size());
		assertEquals(Balance.EVENT_INTERVAL_TICKS + Balance.EVENT_DURATION_TICKS, state.getEvents().get(1).getEndsAtPlaytime());

		tickUntil(engine, state, Balance.EVENT_DURATION_TICKS);
		assertEquals(2, state.getEvents().size());
		assertFalse(state.getEvents().contains(first));

		for (int hour = 3; hour < 10; hour++)
		{
			tickUntil(engine, state, (long) hour * Balance.EVENT_INTERVAL_TICKS + 1);
			assertEquals(2, state.getEvents().size());
		}
	}

	private static void tickUntil(SettlementEngine engine, SettlementState state, long playtime)
	{
		while (state.getPlaytimeTicks() < playtime)
		{
			engine.onTick();
			assertFalse("events should remain live", state.getEvents().isEmpty());
		}
	}

	@Test
	public void anEmptyEventListIsImmediatelyReplenished()
	{
		SettlementState state = SettlementEngine.newSettlement();
		SettlementEngine engine = new SettlementEngine(state, new Random(1));
		state.getEvents().clear();

		engine.onTick();

		assertEquals(1, state.getEvents().size());
		assertTrue(state.getEvents().get(0).getEndsAtPlaytime() > state.getPlaytimeTicks());
	}

	@Test
	public void eventsAreMostlyBuffsAndCanRepeat()
	{
		SettlementState state = new SettlementState();
		EventScheduler scheduler = new EventScheduler(new Random(7));
		int draws = 10_000;
		int buffs = 0;
		int repeats = 0;
		SettlementEvent previous = null;
		for (int i = 0; i < draws; i++)
		{
			state.getEvents().clear();
			scheduler.start(state);
			SettlementEvent event = state.getEvents().get(0).getEvent();
			if (event == previous)
			{
				repeats++;
			}
			previous = event;
			if (event.isBuff())
			{
				buffs++;
			}
		}

		double share = buffs / (double) draws;
		assertTrue("buff share " + share, share > 0.65 && share < 0.75);
		assertTrue("repeats " + repeats, repeats > 0 && repeats < draws / 10);
	}

	@Test
	public void skillEventsMatchTheirSkillRules()
	{
		for (SettlementEvent event : SettlementEvent.values())
		{
			if (event.getEffect() == EventEffect.GATHERING || event.getEffect() == EventEffect.PROCESSING)
			{
				SkillRule rule = SkillRules.get(event.getSkill());
				assertNotNull(event + " skill has no rule", rule);
				assertEquals(event + " effect", event.getEffect() == EventEffect.PROCESSING, rule.isProcessing());
			}
			else
			{
				assertNull(event + " should not target a skill", event.getSkill());
			}
			assertNotEquals(event + " should change something", 1, event.getMultiplier(), 0);
			assertTrue(event + " should never stop production", event.getMultiplier() > 0);
		}
	}
}
