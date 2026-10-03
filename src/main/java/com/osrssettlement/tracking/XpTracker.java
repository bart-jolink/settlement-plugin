package com.osrssettlement.tracking;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.runelite.api.Skill;

/**
 * Turns absolute skill XP into gained XP. The stat sync right after login or a world hop only
 * sets the baseline, so XP earned while the plugin was not running is never credited.
 */
public class XpTracker
{
	static final int LOGIN_SYNC_TICKS = 2;

	private final Map<Skill, Integer> baseline = new EnumMap<>(Skill.class);
	private int syncTicks;

	/**
	 * Forget the baseline and ignore stat updates for the next couple of ticks.
	 */
	public void reset()
	{
		baseline.clear();
		syncTicks = LOGIN_SYNC_TICKS;
	}

	/**
	 * @return true on the tick the login sync finishes and a baseline snapshot should be taken
	 */
	public boolean tick()
	{
		return syncTicks > 0 && --syncTicks == 0;
	}

	public void snapshot(ToIntFunction<Skill> currentXp)
	{
		syncTicks = 0;
		for (Skill skill : Skill.values())
		{
			baseline.put(skill, currentXp.applyAsInt(skill));
		}
	}

	/**
	 * @return the XP gained since the last update of this skill, or 0 while syncing or without a baseline
	 */
	public int onStatChanged(Skill skill, int xp)
	{
		if (syncTicks > 0)
		{
			return 0;
		}

		Integer previous = baseline.put(skill, xp);
		if (previous == null)
		{
			return 0;
		}
		return Math.max(0, xp - previous);
	}
}
