package com.osrssettlement.engine;

import com.osrssettlement.model.ActiveEvent;
import com.osrssettlement.model.Boost;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.EventEffect;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementEvent;
import com.osrssettlement.model.SettlementState;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;
import net.runelite.api.Skill;

/**
 * Converts XP into settlement units and computes the multipliers buildings, boosts and relics apply.
 */
public final class YieldCalculator
{
	private static final double EXPONENT = Math.log(8.0) / Math.log(10.0);

	private YieldCalculator()
	{
	}

	/**
	 * Concave power curve: a 10 xp drop is worth 1 unit and a 100 xp drop 8 units.
	 */
	public static double units(int xp)
	{
		if (xp <= 0)
		{
			return 0;
		}
		return Math.pow(xp / 10.0, EXPONENT);
	}

	public static double globalMultiplier(SettlementState state)
	{
		return 1
			+ Balance.TOWN_HALL_GLOBAL_BONUS * state.getLevel(Building.TOWN_HALL)
			+ Balance.WONDER_GLOBAL_BONUS * state.getLevel(Building.WONDER)
			+ Balance.ALTAR_GLOBAL_BONUS * state.getLevel(Building.ALTAR)
			+ Balance.TOWER_OF_VOICES_GLOBAL_BONUS * state.getLevel(Building.TOWER_OF_VOICES);
	}

	public static double gatherMultiplier(SettlementState state, Building building)
	{
		return (1 + Balance.GATHER_BONUS_PER_LEVEL * state.getLevel(building)) * globalMultiplier(state);
	}

	public static double processRatio(SettlementState state, Building building)
	{
		return (1 + Balance.PROCESS_BONUS_PER_LEVEL * state.getLevel(building)) * globalMultiplier(state);
	}

	public static double trophyMultiplier(SettlementState state)
	{
		return 1 + Balance.TROPHY_BONUS_PER_LEVEL * state.getLevel(Building.TROPHY_HALL);
	}

	public static double treasuryMultiplier(SettlementState state)
	{
		return 1 + Balance.TREASURY_BONUS_PER_LEVEL * state.getLevel(Building.TREASURY);
	}

	public static double museumMultiplier(SettlementState state)
	{
		return 1 + Balance.MUSEUM_BOUNTY_BONUS_PER_LEVEL * state.getLevel(Building.MUSEUM_CAMP);
	}

	public static double libraryDurationMultiplier(SettlementState state)
	{
		return 1 + Balance.LIBRARY_BOOST_DURATION_PER_LEVEL * state.getLevel(Building.ARCEUUS_LIBRARY);
	}

	/**
	 * The event's multiplier after Jaltevas Pyramid strengthens buffs; setbacks are unchanged.
	 */
	public static double effectiveEventMultiplier(SettlementState state, SettlementEvent event)
	{
		double multiplier = event.getMultiplier();
		if (multiplier <= 1)
		{
			return multiplier;
		}
		return 1 + (multiplier - 1) * (1 + Balance.PYRAMID_EVENT_BONUS_PER_LEVEL * state.getLevel(Building.JALTEVAS_PYRAMID));
	}

	public static double boostMultiplier(SettlementState state, Resource resource)
	{
		double multiplier = 1;
		for (Boost boost : state.getBoosts())
		{
			if (boost.getResource() == resource && boost.getRemainingTicks() > 0)
			{
				multiplier = Math.max(multiplier, boost.getMultiplier());
			}
		}
		return multiplier;
	}

	/**
	 * Event multiplier for resources produced by a skill; Slayer counts as a monster drop.
	 */
	public static double eventSkillMultiplier(SettlementState state, Skill skill)
	{
		return combinedEventMultiplier(state, event ->
		{
			switch (event.getEffect())
			{
				case GATHERING:
				case PROCESSING:
					return event.getSkill() == skill;
				case MONSTER_DROPS:
					return skill == Skill.SLAYER;
				case GLOBAL:
					return true;
				default:
					return false;
			}
		});
	}

	public static double eventClueMultiplier(SettlementState state)
	{
		return eventMultiplier(state, EventEffect.CLUES);
	}

	public static double eventBossMultiplier(SettlementState state)
	{
		return eventMultiplier(state, EventEffect.MONSTER_DROPS);
	}

	private static double eventMultiplier(SettlementState state, EventEffect effect)
	{
		return combinedEventMultiplier(state,
			event -> event.getEffect() == effect || event.getEffect() == EventEffect.GLOBAL);
	}

	/**
	 * Different events multiply. The same event running twice adds its factor (x3 twice is x6);
	 * a repeated setback compounds instead, since adding x0.5 twice would cancel it out.
	 */
	private static double combinedEventMultiplier(SettlementState state, Predicate<SettlementEvent> applies)
	{
		Map<SettlementEvent, Integer> counts = new EnumMap<>(SettlementEvent.class);
		for (ActiveEvent active : state.getEvents())
		{
			if (applies.test(active.getEvent()))
			{
				counts.merge(active.getEvent(), 1, Integer::sum);
			}
		}

		double multiplier = 1;
		for (Map.Entry<SettlementEvent, Integer> entry : counts.entrySet())
		{
			double single = effectiveEventMultiplier(state, entry.getKey());
			multiplier *= entry.getKey().isBuff() ? single * entry.getValue() : Math.pow(single, entry.getValue());
		}
		return multiplier;
	}
}
