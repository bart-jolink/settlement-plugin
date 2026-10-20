package com.osrssettlement.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.osrssettlement.model.ActiveEvent;
import com.osrssettlement.model.Boost;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementEvent;
import com.osrssettlement.model.SettlementState;
import com.osrssettlement.model.SkillRule;
import com.osrssettlement.model.SkillRules;
import net.runelite.api.Skill;
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

	@Test
	public void globalBuildingBonusesRetainTheirAdditionOrder()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, 7);
		state.setLevel(Building.WONDER, 3);
		state.setLevel(Building.ALTAR, 4);
		state.setLevel(Building.TOWER_OF_VOICES, 6);

		double expected = 1 + 0.02 * 7 + 0.1 * 3 + 0.03 * 4 + 0.05 * 6;
		assertEquals(expected, YieldCalculator.globalMultiplier(state), 0);
	}

	@Test
	public void gatheringAmountsPreserveGroupingWithoutChangingStock()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, 7);
		state.setLevel(Building.QUARRY, 4);
		state.getEvents().add(new ActiveEvent(SettlementEvent.RICH_VEIN, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.SETTLEMENT_FESTIVAL, 100));
		state.getBoosts().add(new Boost(Resource.ORE, 1.7, 100));
		state.getBoosts().add(new Boost(Resource.GEMS, 9, 100));
		SkillRule rule = SkillRules.get(Skill.MINING);
		double units = 13.37;
		double multiplier = ((1 + 0.2 * 4) * (1 + 0.02 * 7)) * (3.0 * 1.5);

		assertEquals(units * 0.5 * multiplier * 1.7,
			YieldCalculator.gatheringYield(state, rule, Resource.ORE, units), 0);
		assertEquals(units * 0.5 * multiplier,
			YieldCalculator.gatheringYield(state, rule, Resource.STONE, units), 0);
		assertEquals(units * 0.05 * multiplier, YieldCalculator.gemByproductYield(state, rule, units), 0);
		assertEquals(0,
			YieldCalculator.gemByproductYield(state, SkillRules.get(Skill.FISHING), units), 0);
		assertTrue(state.getStock().isEmpty());
	}

	@Test
	public void expiredAndUnrelatedBoostsDoNotAffectYield()
	{
		SettlementState state = new SettlementState();
		state.getBoosts().add(new Boost(Resource.LOGS, 9, 0));
		state.getBoosts().add(new Boost(Resource.LOGS, 8, -1));
		state.getBoosts().add(new Boost(Resource.FISH, 7, 10));
		state.getBoosts().add(new Boost(Resource.LOGS, 0.5, 10));

		assertEquals(1, YieldCalculator.boostMultiplier(state, Resource.LOGS), 0);
	}

	@Test
	public void processingAmountUsesCompletedWorkWithoutConsumingStockOrLabour()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, 7);
		state.setLevel(Building.FORGE, 3);
		state.getEvents().add(new ActiveEvent(SettlementEvent.DORICS_SECRETS, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.SETTLEMENT_FESTIVAL, 100));
		state.getBoosts().add(new Boost(Resource.BARS, 1.7, 100));
		state.addStock(Resource.ORE, 100);
		state.getLabour().put(Skill.SMITHING, 25.0);
		double completedWork = 2.37;
		double ratio = ((1 + 0.1 * 3) * (1 + 0.02 * 7)) * (3.0 * 1.5);

		assertEquals(completedWork * ratio * 1.7,
			YieldCalculator.processingYield(state, SkillRules.get(Skill.SMITHING), completedWork), 0);
		assertEquals(0, YieldCalculator.processingYield(state, SkillRules.get(Skill.SMITHING), 0), 0);
		assertEquals(100, state.getStock(Resource.ORE), 0);
		assertEquals(25, state.getLabour(Skill.SMITHING), 0);
		assertEquals(1, state.getStock().size());
	}

	@Test
	public void eventStackingPreservesBuffAndSetbackRules()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.JALTEVAS_PYRAMID, 3);
		state.getEvents().add(new ActiveEvent(SettlementEvent.FISH_MIGRATION, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.FISH_MIGRATION, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.ALGAE_BLOOM, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.ALGAE_BLOOM, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.SETTLEMENT_FESTIVAL, 100));

		double strengthenedBuff = 1 + (3.0 - 1) * (1 + 0.1 * 3);
		double strengthenedGlobal = 1 + (1.5 - 1) * (1 + 0.1 * 3);
		double expected = (strengthenedBuff * 2) * Math.pow(0.5, 2) * strengthenedGlobal;
		assertEquals(expected, YieldCalculator.eventSkillMultiplier(state, Skill.FISHING), 0);
		assertEquals(strengthenedGlobal, YieldCalculator.eventSkillMultiplier(state, Skill.MINING), 0);
		assertEquals(0.5,
			YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.ALGAE_BLOOM), 0);
	}

	@Test
	public void bossAndClueAmountsRetainDifferentMultiplicationGrouping()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TROPHY_HALL, 7);
		state.setLevel(Building.TREASURY, 7);
		state.setLevel(Building.JALTEVAS_PYRAMID, 3);
		state.getEvents().add(new ActiveEvent(SettlementEvent.REDBERRY_PIE, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.FEELING_LUCKY, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.SETTLEMENT_FESTIVAL, 100));
		double strengthenedBuff = 1 + (3.0 - 1) * (1 + 0.1 * 3);
		double strengthenedGlobal = 1 + (1.5 - 1) * (1 + 0.1 * 3);
		double eventMultiplier = strengthenedBuff * strengthenedGlobal;
		double buildingMultiplier = 1 + 0.15 * 7;

		for (double baseAmount : new double[]{1, 2, 13.37, 100, 200, 400, 800})
		{
			assertEquals(baseAmount * buildingMultiplier * eventMultiplier,
				YieldCalculator.bossYield(state, baseAmount), 0);
			assertEquals(baseAmount * (buildingMultiplier * eventMultiplier),
				YieldCalculator.clueYield(state, baseAmount), 0);
		}
		assertTrue(state.getStock().isEmpty());
	}

	@Test
	public void eventsTargetTheirOwnRewardPaths()
	{
		SettlementState state = new SettlementState();
		assertEquals(1, YieldCalculator.eventSkillMultiplier(state, Skill.FISHING), 0);
		assertEquals(1, YieldCalculator.eventBossMultiplier(state), 0);
		assertEquals(1, YieldCalculator.eventClueMultiplier(state), 0);
		state.getEvents().add(new ActiveEvent(SettlementEvent.FISH_MIGRATION, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.DORICS_SECRETS, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.REDBERRY_PIE, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.FEELING_LUCKY, 100));
		state.getEvents().add(new ActiveEvent(SettlementEvent.HEATWAVE, 100));

		assertEquals(3 * 0.75, YieldCalculator.eventSkillMultiplier(state, Skill.FISHING), 0);
		assertEquals(3 * 0.75, YieldCalculator.eventSkillMultiplier(state, Skill.SMITHING), 0);
		assertEquals(3 * 0.75, YieldCalculator.eventSkillMultiplier(state, Skill.SLAYER), 0);
		assertEquals(3 * 0.75, YieldCalculator.eventBossMultiplier(state), 0);
		assertEquals(3 * 0.75, YieldCalculator.eventClueMultiplier(state), 0);
		assertEquals(0.75, YieldCalculator.eventSkillMultiplier(state, Skill.WOODCUTTING), 0);
	}

	@Test
	public void boostDurationPreservesRoundingAndIntegerConversionWithoutMutatingBoosts()
	{
		SettlementState state = new SettlementState();
		state.getBoosts().add(new Boost(Resource.LOGS, 2, 14));
		for (int level : new int[]{0, 1, 3, 10})
		{
			state.setLevel(Building.ARCEUUS_LIBRARY, level);
			for (int baseTicks : new int[]{0, 7, 8, 3000, Integer.MAX_VALUE})
			{
				int expected = (int) Math.round(baseTicks * (1 + 0.2 * level));
				assertEquals(expected, YieldCalculator.boostDurationTicks(state, baseTicks));
			}
		}

		assertEquals(1, state.getBoosts().size());
		assertEquals(14, state.getBoosts().get(0).getRemainingTicks());
	}
}
