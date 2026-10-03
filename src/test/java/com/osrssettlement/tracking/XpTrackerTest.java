package com.osrssettlement.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.Skill;
import org.junit.Test;

public class XpTrackerTest
{
	@Test
	public void firstUpdateOnlySetsBaseline()
	{
		XpTracker tracker = new XpTracker();
		assertEquals(0, tracker.onStatChanged(Skill.WOODCUTTING, 1_000_000));
		assertEquals(25, tracker.onStatChanged(Skill.WOODCUTTING, 1_000_025));
	}

	@Test
	public void loginSyncIsIgnored()
	{
		XpTracker tracker = new XpTracker();
		tracker.snapshot(skill -> 100);
		tracker.reset();

		assertEquals(0, tracker.onStatChanged(Skill.MINING, 5_000));
		assertFalse(tracker.tick());
		assertTrue(tracker.tick());
		tracker.snapshot(skill -> skill == Skill.MINING ? 5_000 : 0);

		assertEquals(0, tracker.onStatChanged(Skill.MINING, 5_000));
		assertEquals(35, tracker.onStatChanged(Skill.MINING, 5_035));
	}

	@Test
	public void xpNeverGoesNegative()
	{
		XpTracker tracker = new XpTracker();
		tracker.snapshot(skill -> 500);
		assertEquals(0, tracker.onStatChanged(Skill.FISHING, 400));
	}

	@Test
	public void tickIsIdleWithoutReset()
	{
		XpTracker tracker = new XpTracker();
		assertFalse(tracker.tick());
	}
}
