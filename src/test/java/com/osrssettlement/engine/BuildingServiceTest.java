package com.osrssettlement.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.BuildingType;
import com.osrssettlement.model.MarketCategory;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.ResourceCategory;
import com.osrssettlement.model.SettlementState;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class BuildingServiceTest
{
	private static SettlementState richState()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, 1);
		for (Resource resource : Resource.values())
		{
			state.addStock(resource, 1_000_000);
		}
		return state;
	}

	@Test
	public void buildingsHaveTheirDeclaredTypes()
	{
		assertEquals(BuildingType.STANDARD, Building.LUMBER_CAMP.getType());
		assertEquals(BuildingType.SPECIAL, Building.KELDAGRIM_CONSORTIUM.getType());
		assertEquals(BuildingType.SPECIAL, Building.MUSEUM_CAMP.getType());
		assertEquals(BuildingType.SPECIAL, Building.ARCEUUS_LIBRARY.getType());
		assertEquals(BuildingType.SPECIAL, Building.JALTEVAS_PYRAMID.getType());
		assertEquals(BuildingType.SPECIAL, Building.TOWER_OF_VOICES.getType());
		assertEquals(BuildingType.LEGENDARY, Building.WONDER.getType());
	}

	@Test
	public void costGrowsExponentiallyForCommonResources()
	{
		int base = Building.LUMBER_CAMP.getBaseCost().get(Resource.LOGS);
		assertEquals((int) Math.ceil(base * Balance.COST_SCALE),
			(int) BuildingService.cost(Building.LUMBER_CAMP, 1).get(Resource.LOGS));
		assertEquals((int) Math.ceil(base * Balance.COST_SCALE * Balance.COST_GROWTH * Balance.COST_GROWTH),
			(int) BuildingService.cost(Building.LUMBER_CAMP, 3).get(Resource.LOGS));
	}

	@Test
	public void spoilsScaleExponentiallyLikeOtherResources()
	{
		int base = Building.ALTAR.getBaseCost().get(Resource.EPIC_MONSTER_PARTS);
		assertEquals((int) Math.ceil(base * Balance.COST_SCALE * Math.pow(Balance.COST_GROWTH, 4)),
			(int) BuildingService.cost(Building.ALTAR, 5).get(Resource.EPIC_MONSTER_PARTS));

		int monsterParts = Building.TROPHY_HALL.getBaseCost().get(Resource.MONSTER_PARTS);
		assertEquals((int) Math.ceil(monsterParts * Balance.COST_SCALE * Math.pow(Balance.COST_GROWTH, 4)),
			(int) BuildingService.cost(Building.TROPHY_HALL, 5).get(Resource.MONSTER_PARTS));
		assertEquals((int) Math.ceil(50 * Balance.COST_SCALE * Math.pow(Balance.COST_GROWTH, 4)),
			(int) BuildingService.cost(Building.TROPHY_HALL, 5).get(Resource.RARE_MONSTER_PARTS));
		assertEquals((int) Math.ceil(15 * Balance.COST_SCALE * Math.pow(Balance.COST_GROWTH, 4)),
			(int) BuildingService.cost(Building.TROPHY_HALL, 5).get(Resource.EPIC_MONSTER_PARTS));
		assertEquals((int) Math.ceil(1 * Balance.COST_SCALE * Math.pow(Balance.COST_GROWTH, 4)),
			(int) BuildingService.cost(Building.TROPHY_HALL, 5).get(Resource.ARTIFACT));
	}

	@Test
	public void specialResourcesScaleExponentially()
	{
		int base = Building.KELDAGRIM_CONSORTIUM.getBaseCost().get(Resource.COINS);
		assertEquals((int) Math.ceil(base * Balance.COST_SCALE * Math.pow(Balance.COST_GROWTH, 4)),
			(int) BuildingService.cost(Building.KELDAGRIM_CONSORTIUM, 5).get(Resource.COINS));
	}

	@Test
	public void wonderUsesRegularBuildingCostGrowth()
	{
		for (Map.Entry<Resource, Integer> entry : Building.WONDER.getBaseCost().entrySet())
		{
			for (int level : new int[]{1, 10})
			{
				int expected = (int) Math.ceil(entry.getValue() * Balance.COST_SCALE
					* Math.pow(Balance.COST_GROWTH, level - 1));
				assertEquals(expected, (int) BuildingService.cost(Building.WONDER, level).get(entry.getKey()));
			}
		}
	}

	@Test
	public void altarArtifactsUseStandardExponentialCostsAtEveryLevel()
	{
		int base = Building.ALTAR.getBaseCost().get(Resource.ARTIFACT);
		for (int level = 1; level <= Balance.MAX_BUILDING_LEVEL; level++)
		{
			assertEquals((int) Math.ceil(base * Balance.COST_SCALE * Math.pow(Balance.COST_GROWTH, level - 1)),
				(int) BuildingService.cost(Building.ALTAR, level).get(Resource.ARTIFACT));
		}
	}

	@Test
	public void tierABuildingNeedsNoBlueprint()
	{
		SettlementState state = richState();
		assertTrue(BuildingService.check(state, Building.LUMBER_CAMP).isOk());
	}

	@Test
	public void buildingsCannotOutlevelTownHall()
	{
		SettlementState state = richState();
		state.setLevel(Building.LUMBER_CAMP, 1);
		assertEquals(UpgradeCheck.Status.NEEDS_TOWN_HALL, BuildingService.check(state, Building.LUMBER_CAMP).getStatus());
	}

	@Test
	public void blueprintBuildingNeedsTownHallThenBlueprint()
	{
		SettlementState state = richState();
		assertEquals(UpgradeCheck.Status.NEEDS_TOWN_HALL, BuildingService.check(state, Building.SAWMILL).getStatus());

		state.setLevel(Building.TOWN_HALL, 3);
		assertEquals(UpgradeCheck.Status.NEEDS_TOWN_HALL, BuildingService.check(state, Building.SAWMILL).getStatus());

		state.setLevel(Building.TOWN_HALL, 4);
		assertEquals(UpgradeCheck.Status.NEEDS_BLUEPRINT, BuildingService.check(state, Building.SAWMILL).getStatus());

		state.getBlueprints().add(Building.SAWMILL);
		assertTrue(BuildingService.check(state, Building.SAWMILL).isOk());
	}

	@Test
	public void expeditionBlueprintsAreNotOfferedByBounties()
	{
		SettlementState state = richState();
		state.setLevel(Building.TOWN_HALL, Balance.MAX_BUILDING_LEVEL);

		for (ExpeditionCatalog.Definition expedition : ExpeditionCatalog.all())
		{
			Building reward = expedition.getRewardBlueprint();
			assertFalse(BuildingService.isBlueprintAvailable(state, reward));
			assertFalse(reward == BountyBoard.nextBlueprint(state, new Random(7)));
			assertTrue(reward.isOptional());
			assertEquals(UpgradeCheck.Status.NEEDS_BLUEPRINT, BuildingService.check(state, reward).getStatus());
			assertEquals("Requires: Blueprint (expedition)", BuildingService.check(state, reward).getReason());
		}
	}

	@Test
	public void requirementsListEveryGateIncludingTheBlueprint()
	{
		SettlementState state = richState();
		List<Requirement> requirements = BuildingService.requirements(state, Building.SAWMILL);
		assertEquals(2, requirements.size());
		assertEquals(UpgradeCheck.Status.NEEDS_TOWN_HALL, requirements.get(0).getStatus());
		assertFalse(requirements.get(0).isMet());
		assertEquals(UpgradeCheck.Status.NEEDS_BLUEPRINT, requirements.get(1).getStatus());
		assertTrue(requirements.get(1).getDescription().contains("bounty board"));
		assertFalse(requirements.get(1).isMet());

		state.getBlueprints().add(Building.SAWMILL);
		assertTrue(BuildingService.requirements(state, Building.SAWMILL).get(1).isMet());
	}

	@Test
	public void townHallBuildingRequirementShowsProgressAndCompletion()
	{
		for (int townHallLevel = 1; townHallLevel < Balance.MAX_BUILDING_LEVEL; townHallLevel++)
		{
			SettlementState state = new SettlementState();
			state.setLevel(Building.TOWN_HALL, townHallLevel);
			int required = Balance.TOWN_HALL_UPGRADE_BUILDINGS + townHallLevel - 1;
			Requirement requirement = BuildingService.requirements(state, Building.TOWN_HALL).get(0);
			assertEquals("Buildings at level " + townHallLevel + " (0/" + required + ")", requirement.getDescription());
			assertFalse(requirement.isMet());
			int current = 0;
			for (Building building : Building.values())
			{
				if (building == Building.TOWN_HALL || building.isWonder())
				{
					continue;
				}
				state.setLevel(building, townHallLevel);
				current++;
				requirement = BuildingService.requirements(state, Building.TOWN_HALL).get(0);
				assertEquals("Buildings at level " + townHallLevel + " (" + current + "/" + required + ")",
					requirement.getDescription());
				assertEquals(current >= required, requirement.isMet());
				if (current > required)
				{
					break;
				}
			}
			assertTrue(requirement.isMet());
		}
	}

	@Test
	public void requirementsSkipTheBlueprintWhenNotNeeded()
	{
		SettlementState state = richState();
		List<Requirement> requirements = BuildingService.requirements(state, Building.LUMBER_CAMP);
		assertEquals(1, requirements.size());
		assertEquals(UpgradeCheck.Status.NEEDS_TOWN_HALL, requirements.get(0).getStatus());
		assertTrue(requirements.get(0).isMet());

		state.setLevel(Building.TOWN_HALL, 2);
		state.getBlueprints().add(Building.SAWMILL);
		state.setLevel(Building.SAWMILL, 1);
		assertTrue(BuildingService.requirements(state, Building.SAWMILL).stream()
			.noneMatch(requirement -> requirement.getStatus() == UpgradeCheck.Status.NEEDS_BLUEPRINT));

		state.setLevel(Building.TOWN_HALL, Balance.MAX_BUILDING_LEVEL);
		assertTrue(BuildingService.requirements(state, Building.TOWN_HALL).isEmpty());
	}

	@Test
	public void processedConsortiumTradesUseTheirOwnRatio()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.KELDAGRIM_CONSORTIUM, 1);
		state.addStock(Resource.PLANKS, Balance.KELDAGRIM_CONSORTIUM_PROCESSED_INPUT_AMOUNT);
		assertTrue(MarketService.trade(state, Resource.PLANKS, Resource.BARS));
		assertEquals(0, state.getStock(Resource.PLANKS), 0);
		assertEquals(MarketService.outputAmount(1), state.getStock(Resource.BARS), 0);
		assertFalse(MarketService.trade(state, Resource.BARS, Resource.RUNES));
	}

	@Test
	public void consortiumOutputScalesWithLevelWithoutChangingInputCost()
	{
		for (int level : new int[]{1, 5, Balance.MAX_BUILDING_LEVEL})
		{
			long expected = Math.round(Balance.KELDAGRIM_CONSORTIUM_OUTPUT_AMOUNT
				* (1 + Balance.KELDAGRIM_CONSORTIUM_OUTPUT_BONUS_PER_LEVEL * level));
			assertEquals(expected, MarketService.outputAmount(level));
		}
		long bonusPercent = Math.round(Balance.KELDAGRIM_CONSORTIUM_OUTPUT_BONUS_PER_LEVEL * 5 * 100);
		long perLevelBonusPercent = Math.round(Balance.KELDAGRIM_CONSORTIUM_OUTPUT_BONUS_PER_LEVEL * 100);
		String description = MarketService.describe(5, Resource.LOGS, Resource.ORE);
		assertTrue(description.contains("+" + bonusPercent + "% output"));
		assertTrue(description.contains("+" + perLevelBonusPercent + "% per Keldagrim Consortium level"));

		SettlementState state = new SettlementState();
		state.setLevel(Building.KELDAGRIM_CONSORTIUM, Balance.MAX_BUILDING_LEVEL);
		state.addStock(Resource.LOGS, Balance.KELDAGRIM_CONSORTIUM_INPUT_AMOUNT);
		assertTrue(MarketService.trade(state, Resource.LOGS, Resource.ORE));
		assertEquals(0, state.getStock(Resource.LOGS), 0);
		long expected = Math.round(Balance.KELDAGRIM_CONSORTIUM_OUTPUT_AMOUNT
			* (1 + Balance.KELDAGRIM_CONSORTIUM_OUTPUT_BONUS_PER_LEVEL * Balance.MAX_BUILDING_LEVEL));
		assertEquals(expected, state.getStock(Resource.ORE), 0);
	}

	@Test
	public void consortiumTradesAnyPairWithinACategory()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.KELDAGRIM_CONSORTIUM, 1);
		state.addStock(Resource.STONE, Balance.KELDAGRIM_CONSORTIUM_INPUT_AMOUNT * 3);
		state.addStock(Resource.FISH, Balance.KELDAGRIM_CONSORTIUM_INPUT_AMOUNT);
		state.addStock(Resource.RUNES, Balance.KELDAGRIM_CONSORTIUM_PROCESSED_INPUT_AMOUNT);
		state.addStock(Resource.CHARCOAL, Balance.KELDAGRIM_CONSORTIUM_PROCESSED_INPUT_AMOUNT);
		state.addStock(Resource.GEMS, Balance.KELDAGRIM_CONSORTIUM_PROCESSED_INPUT_AMOUNT);

		assertTrue(MarketService.trade(state, Resource.STONE, Resource.ORE));
		assertTrue(MarketService.trade(state, Resource.STONE, Resource.LOGS));
		assertTrue(MarketService.trade(state, Resource.FISH, Resource.HIDES));
		assertTrue(MarketService.trade(state, Resource.CHARCOAL, Resource.LEATHER));
		assertTrue(MarketService.trade(state, Resource.GEMS, Resource.MARKS));
		assertFalse(MarketService.trade(state, Resource.STONE, Resource.STONE));
		assertFalse(MarketService.trade(state, Resource.STONE, Resource.PLANKS));
		assertFalse(MarketService.trade(state, Resource.RUNES, Resource.LOGS));
		assertFalse(MarketService.isValidPair(Resource.FISH, Resource.COINS));
		assertFalse(MarketService.isValidPair(Resource.MONSTER_PARTS, Resource.BONES));
		assertFalse(MarketService.isValidPair(Resource.CURIOS, Resource.ARTIFACT));
		assertEquals(Balance.KELDAGRIM_CONSORTIUM_INPUT_AMOUNT, state.getStock(Resource.STONE), 0);
		assertEquals(0, state.getStock(Resource.FISH), 0);
		assertEquals(0, state.getStock(Resource.CHARCOAL), 0);
		assertEquals(0, state.getStock(Resource.GEMS), 0);
		assertEquals(MarketService.outputAmount(1), state.getStock(Resource.MARKS), 0);
		assertEquals(Balance.KELDAGRIM_CONSORTIUM_PROCESSED_INPUT_AMOUNT, state.getStock(Resource.RUNES), 0);
	}

	@Test
	public void marketCategoriesFollowResourceCategories()
	{
		assertEquals(MarketCategory.SPECIAL, MarketCategory.of(Resource.ENCHANTMENTS));
		assertTrue(MarketCategory.SPECIAL.getResources().contains(Resource.ENCHANTMENTS));

		for (Resource resource : Resource.values())
		{
			switch (resource.getCategory())
			{
				case RAW:
					assertEquals(MarketCategory.RAW, MarketCategory.of(resource));
					assertTrue(MarketCategory.RAW.getResources().contains(resource));
					break;
				case REFINED:
					assertEquals(MarketCategory.PROCESSED, MarketCategory.of(resource));
					assertTrue(MarketCategory.PROCESSED.getResources().contains(resource));
					break;
				case SPECIAL:
					assertEquals(MarketCategory.SPECIAL, MarketCategory.of(resource));
					assertTrue(MarketCategory.SPECIAL.getResources().contains(resource));
					break;
				case SPOILS:
					assertNull(MarketCategory.of(resource));
					break;
				default:
					assertNull(MarketCategory.of(resource));
			}
		}
	}

	@Test
	public void consortiumTradeRequiresBuildingAndFullCost()
	{
		SettlementState state = new SettlementState();
		state.addStock(Resource.LOGS, Balance.KELDAGRIM_CONSORTIUM_INPUT_AMOUNT - 1);
		assertFalse(MarketService.trade(state, Resource.LOGS, Resource.ORE));
		assertEquals(Balance.KELDAGRIM_CONSORTIUM_INPUT_AMOUNT - 1, state.getStock(Resource.LOGS), 0);
		assertEquals(0, state.getStock(Resource.ORE), 0);

		state.setLevel(Building.KELDAGRIM_CONSORTIUM, 1);
		assertFalse(MarketService.trade(state, Resource.LOGS, Resource.ORE));
		assertEquals(Balance.KELDAGRIM_CONSORTIUM_INPUT_AMOUNT - 1, state.getStock(Resource.LOGS), 0);

		state.addStock(Resource.LOGS, 1);
		assertTrue(MarketService.trade(state, Resource.LOGS, Resource.ORE));
		assertEquals(0, state.getStock(Resource.LOGS), 0);
		assertEquals(MarketService.outputAmount(1), state.getStock(Resource.ORE), 0);
	}

	@Test
	public void townHallNeedsThreeBuildingsAtItsLevel()
	{
		SettlementState state = richState();
		state.setLevel(Building.LUMBER_CAMP, 1);
		state.setLevel(Building.QUARRY, 1);
		assertEquals(UpgradeCheck.Status.NEEDS_BUILDINGS, BuildingService.check(state, Building.TOWN_HALL).getStatus());

		state.setLevel(Building.FISHING_DOCK, 1);
		assertTrue(BuildingService.check(state, Building.TOWN_HALL).isOk());
	}

	@Test
	public void wonderNeedsEveryBuildingAtMinimumLevel()
	{
		SettlementState state = richState();
		state.setLevel(Building.TOWN_HALL, Balance.MAX_BUILDING_LEVEL);
		state.getBlueprints().add(Building.WONDER);
		for (Building building : Building.values())
		{
			if (building != Building.TOWN_HALL && !building.isWonder())
			{
				state.setLevel(building, Balance.WONDER_MIN_BUILDING_LEVEL);
			}
		}
		state.setLevel(Building.TREASURY, Balance.WONDER_MIN_BUILDING_LEVEL - 1);
		assertEquals(UpgradeCheck.Status.NEEDS_BUILDINGS, BuildingService.check(state, Building.WONDER).getStatus());

		state.setLevel(Building.TREASURY, Balance.WONDER_MIN_BUILDING_LEVEL);
		assertTrue(BuildingService.check(state, Building.WONDER).isOk());
	}

	@Test
	public void expeditionBuildingsAreOptionalForTheWonder()
	{
		SettlementState state = richState();
		state.setLevel(Building.TOWN_HALL, Balance.MAX_BUILDING_LEVEL);
		state.getBlueprints().add(Building.WONDER);
		for (Building building : Building.values())
		{
			if (building != Building.TOWN_HALL && !building.isWonder() && !building.isOptional())
			{
				state.setLevel(building, Balance.WONDER_MIN_BUILDING_LEVEL);
			}
		}
		assertEquals(0, state.getLevel(Building.KELDAGRIM_CONSORTIUM));
		assertEquals(0, state.getLevel(Building.TOWER_OF_VOICES));
		assertTrue(BuildingService.check(state, Building.WONDER).isOk());
	}

	@Test
	public void wonderRequirementIncludesTownHallAndExcludesOptionalBuildings()
	{
		SettlementState state = richState();
		state.getBlueprints().add(Building.WONDER);
		state.setLevel(Building.TOWN_HALL, Balance.MAX_BUILDING_LEVEL);
		state.setLevel(Building.LUMBER_CAMP, Balance.WONDER_MIN_BUILDING_LEVEL);
		for (Building building : Building.values())
		{
			if (building.isOptional())
			{
				state.setLevel(building, Balance.WONDER_MIN_BUILDING_LEVEL);
			}
		}

		List<Requirement> requirements = BuildingService.requirements(state, Building.WONDER);
		for (Requirement requirement : requirements)
		{
			assertFalse(requirement.getStatus() == UpgradeCheck.Status.NEEDS_TOWN_HALL);
			if (requirement.getStatus() == UpgradeCheck.Status.NEEDS_BUILDINGS)
			{
				assertEquals("Buildings at level 10 (2/21)", requirement.getDescription());
				return;
			}
		}
		throw new AssertionError("Wonder building requirement not found");
	}

	@Test
	public void bossBuildingsAreRequiredForTheWonder()
	{
		assertFalse(Building.TROPHY_HALL.isOptional());
		assertFalse(Building.ALTAR.isOptional());

		SettlementState state = richState();
		state.setLevel(Building.TOWN_HALL, Balance.MAX_BUILDING_LEVEL);
		state.getBlueprints().add(Building.WONDER);
		for (Building building : Building.values())
		{
			if (building != Building.TOWN_HALL && !building.isWonder() && !building.isOptional())
			{
				state.setLevel(building, Balance.WONDER_MIN_BUILDING_LEVEL);
			}
		}
		state.setLevel(Building.ALTAR, Balance.WONDER_MIN_BUILDING_LEVEL - 1);
		assertEquals(UpgradeCheck.Status.NEEDS_BUILDINGS, BuildingService.check(state, Building.WONDER).getStatus());
	}

	@Test
	public void wonderRequiresArtifacts()
	{
		SettlementState state = richState();
		state.setLevel(Building.TOWN_HALL, Balance.MAX_BUILDING_LEVEL);
		state.getBlueprints().add(Building.WONDER);
		for (Building building : Building.values())
		{
			if (building != Building.TOWN_HALL && !building.isWonder())
			{
				state.setLevel(building, Balance.WONDER_MIN_BUILDING_LEVEL);
			}
		}
		state.addStock(Resource.ARTIFACT, -1_000_000);

		int artifactCost = (int) Math.ceil(Building.WONDER.getBaseCost().get(Resource.ARTIFACT) * Balance.COST_SCALE);
		assertEquals(artifactCost, (int) BuildingService.cost(Building.WONDER, 1).get(Resource.ARTIFACT));
		assertEquals(UpgradeCheck.Status.NEEDS_RESOURCES, BuildingService.check(state, Building.WONDER).getStatus());

		state.addStock(Resource.ARTIFACT, artifactCost);
		assertTrue(BuildingService.check(state, Building.WONDER).isOk());
	}

	@Test
	public void wonderIsCappedAtMaximumBuildingLevel()
	{
		assertEquals(Balance.MAX_BUILDING_LEVEL, BuildingService.maxLevel(Building.WONDER));
		SettlementState state = richState();
		state.setLevel(Building.TOWN_HALL, Balance.MAX_BUILDING_LEVEL);
		state.getBlueprints().add(Building.WONDER);
		state.setLevel(Building.WONDER, Balance.MAX_BUILDING_LEVEL);
		assertEquals(UpgradeCheck.Status.MAX_LEVEL, BuildingService.check(state, Building.WONDER).getStatus());
	}

	@Test
	public void maxLevelIsReported()
	{
		SettlementState state = richState();
		state.setLevel(Building.TOWN_HALL, Balance.MAX_BUILDING_LEVEL);
		assertEquals(UpgradeCheck.Status.MAX_LEVEL, BuildingService.check(state, Building.TOWN_HALL).getStatus());
	}

	@Test
	public void upgradePaysTheCost()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, 1);
		Map<Resource, Integer> cost = BuildingService.cost(Building.LUMBER_CAMP, 1);
		cost.forEach((resource, amount) -> state.addStock(resource, amount + 5));

		assertTrue(BuildingService.upgrade(state, Building.LUMBER_CAMP).isOk());
		assertEquals(1, state.getLevel(Building.LUMBER_CAMP));
		cost.keySet().forEach(resource -> assertEquals(5, state.getStock(resource), 1e-9));

		assertEquals(UpgradeCheck.Status.NEEDS_TOWN_HALL, BuildingService.upgrade(state, Building.LUMBER_CAMP).getStatus());
	}

	@Test
	public void notEnoughResources()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, 1);
		assertEquals(UpgradeCheck.Status.NEEDS_RESOURCES, BuildingService.check(state, Building.LUMBER_CAMP).getStatus());
	}
}
