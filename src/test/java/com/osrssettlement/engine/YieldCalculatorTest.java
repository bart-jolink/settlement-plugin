package com.osrssettlement.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.osrssettlement.model.Boost;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementState;
import org.junit.Test;

public class YieldCalculatorTest
{
	@Test
	public void tenXpIsOneUnit()
	{
		assertEquals(1.0, YieldCalculator.units(10), 1e-9);
	}

	@Test
	public void hundredXpIsEightUnits()
	{
		assertEquals(8.0, YieldCalculator.units(100), 1e-9);
	}

	@Test
	public void smallerDropsRewardMorePerXp()
	{
		double thousand = YieldCalculator.units(1000);
		assertTrue(thousand > YieldCalculator.units(100));
		assertTrue(10 * YieldCalculator.units(10) > YieldCalculator.units(100));
		assertTrue(YieldCalculator.units(100) / 100 > thousand / 1000);
	}

	@Test
	public void noXpNoUnits()
	{
		assertEquals(0, YieldCalculator.units(0), 0);
		assertEquals(0, YieldCalculator.units(-5), 0);
	}

	@Test
	public void multipliersStack()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, 2);
		state.setLevel(Building.LUMBER_CAMP, 3);

		double global = 1 + 2 * Balance.TOWN_HALL_GLOBAL_BONUS;
		assertEquals(global, YieldCalculator.globalMultiplier(state), 1e-9);
		assertEquals((1 + 3 * Balance.GATHER_BONUS_PER_LEVEL) * global,
			YieldCalculator.gatherMultiplier(state, Building.LUMBER_CAMP), 1e-9);
	}

	@Test
	public void altarBoostsEverything()
	{
		SettlementState state = new SettlementState();
		state.addStock(Resource.EPIC_MONSTER_PARTS, 1000);
		assertEquals(1, YieldCalculator.globalMultiplier(state), 1e-9);

		state.setLevel(Building.ALTAR, 4);
		assertEquals(1 + 4 * Balance.ALTAR_GLOBAL_BONUS, YieldCalculator.globalMultiplier(state), 1e-9);
	}

	@Test
	public void strongestBoostWins()
	{
		SettlementState state = new SettlementState();
		state.getBoosts().add(new Boost(Resource.LOGS, 2, 10));
		state.getBoosts().add(new Boost(Resource.LOGS, 3, 10));
		assertEquals(3, YieldCalculator.boostMultiplier(state, Resource.LOGS), 0);
		assertEquals(1, YieldCalculator.boostMultiplier(state, Resource.FISH), 0);
	}
}
