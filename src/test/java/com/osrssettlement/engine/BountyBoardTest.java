package com.osrssettlement.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.osrssettlement.model.Bounty;
import com.osrssettlement.model.BountyRarity;
import com.osrssettlement.model.BountyReward;
import com.osrssettlement.model.BountyType;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.RewardType;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.ResourceCategory;
import com.osrssettlement.model.SettlementState;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class BountyBoardTest
{
	private static SettlementState stateWithTownHall(int level)
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, level);
		return state;
	}

	@Test
	public void fillsAllSlots()
	{
		SettlementState state = stateWithTownHall(1);
		new BountyBoard(new Random(7)).fill(state);

		assertEquals(Balance.BOUNTY_SLOTS, state.getBounties().size());
		Set<Integer> ids = new HashSet<>();
		state.getBounties().forEach(b -> ids.add(b.getId()));
		assertEquals(Balance.BOUNTY_SLOTS, ids.size());
	}

	@Test
	public void monsterPartRewardsVaryBetweenTierOneAndTierThreeBossKills()
	{
		SettlementState state = stateWithTownHall(6);
		state.getBlueprints().addAll(EnumSet.allOf(Building.class));
		BountyBoard board = new BountyBoard(new Random(11));
		Set<Integer> basicAmounts = new HashSet<>();
		for (int i = 0; i < 500; i++)
		{
			Map<Resource, Integer> resources = board.generate(state).getReward().getResources();
			if (resources == null || !resources.containsKey(Resource.EPIC_MONSTER_PARTS))
			{
				continue;
			}
			for (Resource part : Balance.BOUNTY_PARTS_MIN.keySet())
			{
				int amount = resources.get(part);
				assertTrue(part + " " + amount, amount >= Balance.BOUNTY_PARTS_MIN.get(part)
					&& amount <= Balance.BOUNTY_PARTS_MAX.get(part) * Balance.BOUNTY_EPIC_YIELD_SCALE);
			}
			basicAmounts.add(resources.get(Resource.MONSTER_PARTS));
		}
		assertTrue("reward sizes " + basicAmounts, basicAmounts.size() > 3);
	}

	@Test
	public void noBlueprintsBeforeTownHallTwo()
	{
		SettlementState state = stateWithTownHall(1);
		assertNull(BountyBoard.nextBlueprint(state, new Random(7)));

		new BountyBoard(new Random(7)).fill(state);
		assertTrue(state.getBounties().stream().noneMatch(Bounty::isBlueprint));
	}

	@Test
	public void availableBlueprintsTakeOpenSlotsWithoutDuplicates()
	{
		SettlementState state = stateWithTownHall(2);
		BountyBoard board = new BountyBoard(new Random(7));
		board.fill(state);

		assertEquals(1, state.getBounties().stream().filter(Bounty::isBlueprint).count());
		assertEquals(2, state.getBounties().stream().filter(b -> !b.isBlueprint()).count());
		Building offered = state.getBounties().get(0).getReward().getBlueprint();
		assertTrue(BuildingService.isBlueprintAvailable(state, offered));
		assertEquals(BountyRarity.COMMON, state.getBounties().get(0).getRarity());
		assertNull(BountyBoard.nextBlueprint(state, new Random(7)));

		state.getBlueprints().add(offered);
		state.getBounties().remove(0);
		board.fill(state);
		assertEquals(1, state.getBounties().stream().filter(Bounty::isBlueprint).count());
		Building next = state.getBounties().stream().filter(Bounty::isBlueprint)
			.findFirst().get().getReward().getBlueprint();
		assertTrue(next != offered);
		assertTrue(BuildingService.isBlueprintAvailable(state, next));
	}

	@Test
	public void ownedBlueprintsAreNotOfferedAgain()
	{
		SettlementState state = stateWithTownHall(2);
		state.getBlueprints().add(Building.SAWMILL);
		Building offered = BountyBoard.nextBlueprint(state, new Random(7));
		assertNotNull(offered);
		assertTrue(offered != Building.SAWMILL);
		assertTrue(BuildingService.isBlueprintAvailable(state, offered));
	}

	@Test
	public void wonderBlueprintNeedsTownHallTen()
	{
		SettlementState state = stateWithTownHall(9);
		for (Building building : Building.values())
		{
			if (building.getTier().isBlueprintRequired() && !building.isWonder())
			{
				state.getBlueprints().add(building);
			}
		}
		assertNull(BountyBoard.nextBlueprint(state, new Random(7)));

		state.setLevel(Building.TOWN_HALL, 10);
		assertEquals(Building.WONDER, BountyBoard.nextBlueprint(state, new Random(7)));
	}

	@Test
	public void generatedBountiesAreComplete()
	{
		Random random = new Random(3);
		for (int townHall = 1; townHall <= 10; townHall++)
		{
			SettlementState state = stateWithTownHall(townHall);
			for (Building building : Building.values())
			{
				state.getBlueprints().add(building);
			}
			BountyBoard board = new BountyBoard(random);
			for (int i = 0; i < 200; i++)
			{
				Bounty bounty = board.generate(state);
				assertNotNull(bounty.getType());
				assertNotNull(bounty.getRarity());
				assertTrue(bounty.getTarget() > 0);
				assertNotNull(bounty.getReward());
				assertTrue(bounty.getReward().getType() != RewardType.BLUEPRINT);
				assertNotNull(bounty.describe());
				assertNotNull(bounty.getReward().describe());
				if (bounty.getType() == BountyType.SKILL_XP)
				{
					assertNotNull(bounty.getSkill());
				}
				if (bounty.getType() == BountyType.KIND_XP)
				{
					assertNotNull(bounty.getKind());
				}
				if (bounty.getType() == BountyType.DELIVER)
				{
					assertNotNull(bounty.getResource());
					assertTrue(bounty.getResource().getCategory() == ResourceCategory.RAW
						|| bounty.getResource().getCategory() == ResourceCategory.REFINED);
					assertTrue(bounty.getReward().getResources().keySet().stream().noneMatch(r -> r == bounty.getResource()));
				}
			}
		}
	}

	@Test
	public void laterBountiesCanRewardAllPartsAndArtifactsRarely()
	{
		SettlementState state = stateWithTownHall(10);
		for (Building building : Building.values())
		{
			state.getBlueprints().add(building);
		}
		BountyBoard board = new BountyBoard(new Random(23));
		Set<Resource> rewarded = EnumSet.noneOf(Resource.class);
		int basicCount = 0;
		int rareCount = 0;
		int epicCount = 0;
		int artifactCount = 0;

		for (int i = 0; i < 20_000; i++)
		{
			Bounty bounty = board.generate(state);
			Map<Resource, Integer> resources = bounty.getReward().getResources();
			basicCount += resources.getOrDefault(Resource.MONSTER_PARTS, 0) > 0 ? 1 : 0;
			rareCount += resources.getOrDefault(Resource.RARE_MONSTER_PARTS, 0) > 0 ? 1 : 0;
			epicCount += resources.getOrDefault(Resource.EPIC_MONSTER_PARTS, 0) > 0 ? 1 : 0;
			artifactCount += resources.getOrDefault(Resource.ARTIFACT, 0) > 0 ? 1 : 0;
			rewarded.addAll(resources.keySet());
			assertTrue(resources.getOrDefault(Resource.RARE_MONSTER_PARTS, 0)
				<= Balance.BOUNTY_PARTS_MAX.get(Resource.RARE_MONSTER_PARTS) * Balance.BOUNTY_EPIC_YIELD_SCALE);
			assertTrue(resources.getOrDefault(Resource.EPIC_MONSTER_PARTS, 0)
				<= Balance.BOUNTY_PARTS_MAX.get(Resource.EPIC_MONSTER_PARTS) * Balance.BOUNTY_EPIC_YIELD_SCALE);
			assertTrue(resources.getOrDefault(Resource.ARTIFACT, 0) <= Balance.BOUNTY_EPIC_YIELD_SCALE);
		}

		assertTrue(rewarded.contains(Resource.MONSTER_PARTS));
		assertTrue(rewarded.contains(Resource.RARE_MONSTER_PARTS));
		assertTrue(rewarded.contains(Resource.EPIC_MONSTER_PARTS));
		assertTrue(rewarded.contains(Resource.ARTIFACT));
		assertTrue(basicCount > rareCount);
		assertEquals(rareCount, epicCount);
		assertTrue(epicCount > artifactCount);
	}

	@Test
	public void boardAvoidsDuplicateObjectiveTypes()
	{
		SettlementState state = stateWithTownHall(3);
		for (Building building : Building.values())
		{
			state.getBlueprints().add(building);
		}
		new BountyBoard(new Random(11)).fill(state);

		Set<BountyType> types = new HashSet<>();
		state.getBounties().forEach(b -> types.add(b.getType()));
		assertEquals(Balance.BOUNTY_SLOTS, types.size());
	}

	@Test
	public void allObjectivesUseConfiguredRewardWeights()
	{
		int totalWeight = Balance.BOUNTY_SUPPLY_WEIGHT + Balance.BOUNTY_BOOST_WEIGHT + Balance.BOUNTY_PARTS_WEIGHT;
		for (BountyType type : BountyType.values())
		{
			for (int roll = 0; roll < totalWeight; roll++)
			{
				Bounty bounty = controlledBounty(type, 1, roll, 2, commonRarityRoll(), 0);
				assertEquals(type, bounty.getType());
				BountyReward reward = bounty.getReward();
				if (roll < Balance.BOUNTY_SUPPLY_WEIGHT)
				{
					assertEquals(RewardType.RESOURCES, reward.getType());
					assertEquals(2, reward.getResources().size());
					assertTrue(reward.getResources().keySet().stream().allMatch(resource ->
						resource.getCategory() == ResourceCategory.RAW || resource.getCategory() == ResourceCategory.REFINED));
				}
				else if (roll < Balance.BOUNTY_SUPPLY_WEIGHT + Balance.BOUNTY_BOOST_WEIGHT)
				{
					assertEquals(RewardType.BOOST, reward.getType());
					assertEquals(Balance.BOOST_MULTIPLIER, reward.getBoostMultiplier(), 0);
					assertEquals(Balance.BOOST_TICKS, reward.getBoostTicks());
				}
				else
				{
					assertEquals(RewardType.RESOURCES, reward.getType());
					assertEquals(Set.of(Resource.MONSTER_PARTS), reward.getResources().keySet());
				}
			}
		}
	}

	@Test
	public void deliverySuppliesSplitConfiguredBudgetAcrossTwoOrThreeResources()
	{
		double total = Balance.BOUNTY_COMMON_WEIGHT + Balance.BOUNTY_RARE_WEIGHT + Balance.BOUNTY_EPIC_WEIGHT;
		double[] rarityRolls = {commonRarityRoll(),
			(Balance.BOUNTY_COMMON_WEIGHT + Balance.BOUNTY_RARE_WEIGHT / 2) / total,
			(Balance.BOUNTY_COMMON_WEIGHT + Balance.BOUNTY_RARE_WEIGHT + Balance.BOUNTY_EPIC_WEIGHT / 2) / total};
		double[] multipliers = {1, Balance.BOUNTY_RARE_YIELD_SCALE, Balance.BOUNTY_EPIC_YIELD_SCALE};
		for (int count = 2; count <= 3; count++)
		{
			for (int rarity = 0; rarity < rarityRolls.length; rarity++)
			{
				Bounty bounty = controlledBounty(BountyType.DELIVER, 1, 0, count, rarityRolls[rarity], 0);
				assertEquals(count, bounty.getReward().getResources().size());
				long baseAmount = BountyBoard.roundNice(Balance.BOUNTY_DELIVERY_REWARD_VALUE / (double) count);
				long expected = rarity == 0 ? baseAmount : BountyBoard.roundNice(baseAmount * multipliers[rarity]);
				assertTrue(bounty.getReward().getResources().values().stream()
					.allMatch(amount -> amount == expected));
				assertTrue(!bounty.getReward().getResources().containsKey(bounty.getResource()));
			}
		}
	}

	@Test
	public void townHallScalesDemandsAndSuppliesUsingBalance()
	{
		int[] levels = {1, 3, Balance.MAX_BUILDING_LEVEL};
		for (int level : levels)
		{
			double scale = 1 + Balance.BOUNTY_SCALE_PER_TOWN_HALL * (level - 1);
			Bounty bounty = controlledBounty(BountyType.SKILL_XP, level, 0, 2, commonRarityRoll(), 0);
			assertEquals(BountyBoard.roundNice(Balance.BOUNTY_SKILL_XP * scale), bounty.getTarget());
			long expected = BountyBoard.roundNice(Balance.BOUNTY_REWARD_VALUE * scale / 2);
			assertTrue(bounty.getReward().getResources().values().stream().allMatch(amount -> amount == expected));
		}
		double scale = 1 + 2 * Balance.BOUNTY_SCALE_PER_TOWN_HALL;
		double museumBonus = 1 + 5 * Balance.MUSEUM_BOUNTY_BONUS_PER_LEVEL;
		Bounty delivery = controlledBounty(BountyType.DELIVER, 3, 0, 2, commonRarityRoll(), 5);
		assertEquals(BountyBoard.roundNice(Balance.BOUNTY_DELIVER * scale), delivery.getTarget());
		long expected = BountyBoard.roundNice(Balance.BOUNTY_DELIVERY_REWARD_VALUE * scale * museumBonus / 2);
		assertTrue(delivery.getReward().getResources().values().stream().allMatch(amount -> amount == expected));
		Bounty slayer = controlledBounty(BountyType.SLAYER_DROPS, 3, 0, 2, commonRarityRoll(), 0);
		assertEquals(BountyBoard.roundNice(Balance.BOUNTY_SLAYER_DROPS * scale), slayer.getTarget());
	}

	@Test
	public void bundleUnlocksUseConfiguredTownHallLevels()
	{
		int lastUnlock = Math.max(Balance.BOUNTY_ARTIFACT_TOWN_HALL,
			Math.max(Balance.BOUNTY_RARE_PARTS_TOWN_HALL, Balance.BOUNTY_EPIC_PARTS_TOWN_HALL));
		int bundleRoll = Balance.BOUNTY_SUPPLY_WEIGHT + Balance.BOUNTY_BOOST_WEIGHT;
		for (int townHall = 1; townHall <= lastUnlock + 1; townHall++)
		{
			Map<Resource, Integer> resources = controlledBounty(BountyType.BOSS_KILLS, townHall, bundleRoll, 2, commonRarityRoll(), 0)
				.getReward().getResources();
			assertTrue(resources.containsKey(Resource.MONSTER_PARTS));
			assertEquals(townHall >= Balance.BOUNTY_RARE_PARTS_TOWN_HALL, resources.containsKey(Resource.RARE_MONSTER_PARTS));
			assertEquals(townHall >= Balance.BOUNTY_EPIC_PARTS_TOWN_HALL, resources.containsKey(Resource.EPIC_MONSTER_PARTS));
			assertEquals(townHall >= Balance.BOUNTY_ARTIFACT_TOWN_HALL && Balance.BOUNTY_ARTIFACT_CHANCE > 0,
				resources.containsKey(Resource.ARTIFACT));
		}
	}

	@Test
	public void everyEligibleBlueprintCanBeSelected()
	{
		SettlementState state = stateWithTownHall(10);
		Set<Building> offered = EnumSet.noneOf(Building.class);
		Set<Building> eligible = EnumSet.noneOf(Building.class);
		for (Building building : Building.values())
		{
			if (BuildingService.isBlueprintAvailable(state, building))
			{
				eligible.add(building);
			}
		}
		for (int index = 0; index < eligible.size(); index++)
		{
			int selection = index;
			Random random = new Random(7)
			{
				@Override
				public int nextInt(int bound)
				{
					assertEquals(eligible.size(), bound);
					return selection;
				}
			};
			offered.add(BountyBoard.nextBlueprint(state, random));
		}
		assertEquals(eligible, offered);
	}

	private static Bounty controlledBounty(BountyType type, int townHall, int rewardRoll, int count,
		double rarityRoll, int museumLevel)
	{
		SettlementState state = stateWithTownHall(townHall);
		state.setLevel(Building.MUSEUM_CAMP, museumLevel);
		state.getBlueprints().addAll(EnumSet.allOf(Building.class));
		for (BountyType other : BountyType.values())
		{
			if (other != type)
			{
				Bounty existing = new Bounty();
				existing.setType(other);
				existing.setReward(BountyReward.resources(Map.of(Resource.LOGS, 1)));
				state.getBounties().add(existing);
			}
		}
		Random random = new Random(7)
		{
			private int integerCalls;
			private int doubleCalls;

			@Override
			public int nextInt(int bound)
			{
				int rewardCall = type == BountyType.SKILL_XP || type == BountyType.KIND_XP || type == BountyType.DELIVER ? 2 : 1;
				int call = integerCalls++;
				if (call == rewardCall)
				{
					assertEquals(Balance.BOUNTY_SUPPLY_WEIGHT + Balance.BOUNTY_BOOST_WEIGHT + Balance.BOUNTY_PARTS_WEIGHT, bound);
					return rewardRoll;
				}
				return call == rewardCall + 1 && rewardRoll < Balance.BOUNTY_SUPPLY_WEIGHT ? count - 2 : 0;
			}

			@Override
			public double nextDouble()
			{
				int call = doubleCalls++;
				if (call == 0)
				{
					return rarityRoll;
				}
				if (rewardRoll < Balance.BOUNTY_SUPPLY_WEIGHT)
				{
					return (1 + Balance.BOUNTY_MONSTER_PARTS_CHANCE) / 2;
				}
				return call == 1 ? 0.5 : 0;
			}
		};
		return new BountyBoard(random).generate(state);
	}

	private static double commonRarityRoll()
	{
		return Balance.BOUNTY_COMMON_WEIGHT / 2
			/ (Balance.BOUNTY_COMMON_WEIGHT + Balance.BOUNTY_RARE_WEIGHT + Balance.BOUNTY_EPIC_WEIGHT);
	}

	@Test
	public void roundsToTwoSignificantDigits()
	{
		assertEquals(14_000, BountyBoard.roundNice(13_500));
		assertEquals(6_000, BountyBoard.roundNice(6_000));
		assertEquals(230, BountyBoard.roundNice(225));
		assertEquals(3, BountyBoard.roundNice(3.2));
		assertEquals(1, BountyBoard.roundNice(0.2));
	}

	@Test
	public void rarityScalesDemandAndYield()
	{
		Bounty rare = scaledBounty(BountyRarity.RARE);
		long rareTarget = BountyBoard.roundNice(rare.getTarget() * Balance.BOUNTY_RARE_DEMAND_SCALE);
		int rareAmount = (int) BountyBoard.roundNice(rare.getReward().getResources().get(Resource.COINS) * Balance.BOUNTY_RARE_YIELD_SCALE);
		BountyBoard.scaleForRarity(rare);
		assertEquals(rareTarget, rare.getTarget());
		assertEquals(Integer.valueOf(rareAmount), rare.getReward().getResources().get(Resource.COINS));

		Bounty epic = scaledBounty(BountyRarity.EPIC);
		long epicTarget = BountyBoard.roundNice(epic.getTarget() * Balance.BOUNTY_EPIC_DEMAND_SCALE);
		int epicAmount = (int) BountyBoard.roundNice(epic.getReward().getResources().get(Resource.COINS) * Balance.BOUNTY_EPIC_YIELD_SCALE);
		BountyBoard.scaleForRarity(epic);
		assertEquals(epicTarget, epic.getTarget());
		assertEquals(Integer.valueOf(epicAmount), epic.getReward().getResources().get(Resource.COINS));
	}

	private static Bounty scaledBounty(BountyRarity rarity)
	{
		Bounty bounty = new Bounty();
		bounty.setRarity(rarity);
		bounty.setTarget(10_000);
		bounty.setReward(com.osrssettlement.model.BountyReward.resources(Map.of(Resource.COINS, 1_000)));
		return bounty;
	}
}
