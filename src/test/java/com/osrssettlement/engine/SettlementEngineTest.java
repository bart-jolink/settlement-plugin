package com.osrssettlement.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.osrssettlement.model.ActiveEvent;
import com.osrssettlement.model.Boost;
import com.osrssettlement.model.BossTier;
import com.osrssettlement.model.Bounty;
import com.osrssettlement.model.BountyReward;
import com.osrssettlement.model.BountyType;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.ClueTier;
import com.osrssettlement.model.ExpeditionId;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementEvent;
import com.osrssettlement.model.SettlementState;
import com.osrssettlement.model.SkillKind;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

public class SettlementEngineTest
{
	private SettlementState state;
	private SettlementEngine engine;

	@Before
	public void setUp()
	{
		state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, 1);
		engine = new SettlementEngine(state, new Random(42));
		state.getBounties().clear();
		clearEvent(state);
	}

	private static void clearEvent(SettlementState state)
	{
		state.getEvents().clear();
		state.setNextEventAtPlaytime(Long.MAX_VALUE);
	}

	private static void activate(SettlementState state, SettlementEvent event)
	{
		state.getEvents().add(new ActiveEvent(event, Long.MAX_VALUE));
	}

	private SettlementEngine engineWithRoll(SettlementState state, double roll)
	{
		state.setLevel(Building.TOWN_HALL, 1);
		Random controlledRandom = new Random(42)
		{
			@Override
			public double nextDouble()
			{
				return roll;
			}
		};
		SettlementEngine controlledEngine = new SettlementEngine(state, controlledRandom);
		state.getBounties().clear();
		clearEvent(state);
		return controlledEngine;
	}

	private double global()
	{
		return YieldCalculator.globalMultiplier(state);
	}

	@Test
	public void newSettlementHasStarterKitAndFullBoard()
	{
		SettlementState fresh = SettlementEngine.newSettlement();
		new SettlementEngine(fresh, new Random(1));

		assertEquals(1, fresh.getLevel(Building.TOWN_HALL));
		Balance.STARTER_KIT.forEach((resource, amount) -> assertEquals(amount, fresh.getStock(resource), 1e-9));
		assertEquals(Balance.BOUNTY_SLOTS, fresh.getBounties().size());
		assertFalse(fresh.getLog().isEmpty());
	}

	@Test
	public void gatheringProducesResources()
	{
		engine.onXp(Skill.WOODCUTTING, 100);
		assertEquals(YieldCalculator.units(100) * global(), state.getStock(Resource.LOGS), 1e-9);
	}

	@Test
	public void miningSplitsOreAndStone()
	{
		engine.onXp(Skill.MINING, 100);
		double units = YieldCalculator.units(100) * global();
		assertEquals(units * 0.5, state.getStock(Resource.ORE), 1e-9);
		assertEquals(units * 0.5, state.getStock(Resource.STONE), 1e-9);
		assertEquals(0, state.getStock(Resource.GEMS), 0);
	}

	@Test
	public void quarryFindsGemsFromLevelThree()
	{
		state.setLevel(Building.QUARRY, Balance.GEM_MIN_BUILDING_LEVEL);
		engine.onXp(Skill.MINING, 100);
		assertTrue(state.getStock(Resource.GEMS) > 0);
	}

	@Test
	public void ignoredSkillsProduceNothing()
	{
		engine.onXp(Skill.ATTACK, 1000);
		engine.onXp(Skill.STRENGTH, 1000);
		assertTrue(state.getStock().values().stream().allMatch(v -> v == 0));
	}

	@Test
	public void buildingsBoostGathering()
	{
		state.setLevel(Building.LUMBER_CAMP, 2);
		engine.onXp(Skill.WOODCUTTING, 10);
		assertEquals((1 + 2 * Balance.GATHER_BONUS_PER_LEVEL) * global(), state.getStock(Resource.LOGS), 1e-9);
	}

	@Test
	public void boostMultipliesYield()
	{
		state.getBoosts().add(new Boost(Resource.LOGS, 2, 100));
		engine.onXp(Skill.WOODCUTTING, 10);
		assertEquals(2 * global(), state.getStock(Resource.LOGS), 1e-9);
	}

	@Test
	public void processingConsumesRawMaterials()
	{
		state.addStock(Resource.ORE, 100);
		engine.onXp(Skill.SMITHING, 10);

		assertEquals(100 - Balance.PROCESS_INPUT_PER_UNIT, state.getStock(Resource.ORE), 1e-9);
		assertEquals(global(), state.getStock(Resource.BARS), 1e-9);
		assertEquals(0, state.getLabour(Skill.SMITHING), 0);
	}

	@Test
	public void processingWithoutRawMaterialsBanksLabour()
	{
		engine.onXp(Skill.SMITHING, 100);
		assertEquals(0, state.getStock(Resource.BARS), 0);
		assertEquals(YieldCalculator.units(100), state.getLabour(Skill.SMITHING), 1e-9);

		engine.onXp(Skill.MINING, 1000);
		assertEquals(YieldCalculator.units(100) * global(), state.getStock(Resource.BARS), 1e-9);
		assertEquals(0, state.getLabour(Skill.SMITHING), 0);
	}

	@Test
	public void pausedLabourWaitsWhileFreshProcessingUsesInputs()
	{
		engine.onXp(Skill.FLETCHING, 100);
		double queuedFletching = state.getLabour(Skill.FLETCHING);
		assertTrue(queuedFletching > 0);

		engine.setLabourPaused(true);
		double freshFiremaking = YieldCalculator.units(10);
		state.addStock(Resource.LOGS, (queuedFletching + freshFiremaking) * Balance.PROCESS_INPUT_PER_UNIT);
		engine.onXp(Skill.FIREMAKING, 10);

		assertEquals(queuedFletching, state.getLabour(Skill.FLETCHING), 1e-9);
		assertEquals(0, state.getLabour(Skill.FIREMAKING), 1e-9);
		assertTrue(state.getStock(Resource.CHARCOAL) > 0);

		engine.setLabourPaused(false);
		assertEquals(0, state.getLabour(Skill.FLETCHING), 1e-9);
		assertTrue(state.getStock(Resource.ARROWS) > 0);
	}

	@Test
	public void labourIsUnlimited()
	{
		for (int i = 0; i < 1000; i++)
		{
			engine.onXp(Skill.SMITHING, 1000);
		}
		assertEquals(1000 * YieldCalculator.units(1000), state.getLabour(Skill.SMITHING), 1e-6);

		state.addStock(Resource.ORE, 1_000_000);
		engine.onXp(Skill.MINING, 10);
		assertEquals(0, state.getLabour(Skill.SMITHING), 0);
		assertEquals(1000 * YieldCalculator.units(1000) * global(), state.getStock(Resource.BARS), 1e-6);
	}

	@Test
	public void cookingFallsBackToGrain()
	{
		state.addStock(Resource.GRAIN, 10);
		engine.onXp(Skill.COOKING, 10);
		assertEquals(10 - Balance.PROCESS_INPUT_PER_UNIT, state.getStock(Resource.GRAIN), 1e-9);
		assertTrue(state.getStock(Resource.RATIONS) > 0);
	}

	@Test
	public void nonRaidBossKillDoesNotGiveArtifactsAndProgressesBounty()
	{
		Bounty bounty = bounty(BountyType.BOSS_KILLS, 2);
		state.getBounties().add(bounty);

		engine.onBossKill("Vorkath", BossTier.TIER_2);
		assertEquals(0, state.getStock(Resource.ARTIFACT), 0);
		assertEquals(1, bounty.getProgress(), 1e-9);
	}

	@Test
	public void bossPartDropOddsIncreaseWithTierAndAreSeeded()
	{
		for (Resource resource : new Resource[]{Resource.MONSTER_PARTS, Resource.RARE_MONSTER_PARTS, Resource.EPIC_MONSTER_PARTS})
		{
			double tierOne = bossPartStock(BossTier.TIER_1, resource, 2_000);
			double tierTwo = bossPartStock(BossTier.TIER_2, resource, 2_000);
			double tierThree = bossPartStock(BossTier.TIER_3, resource, 2_000);

			assertTrue(resource + " should drop at tier 1", tierOne > 0);
			assertTrue(resource + " odds should increase at tier 2", tierTwo > tierOne);
			assertTrue(resource + " odds should increase at tier 3", tierThree > tierTwo);
			assertEquals(tierOne, bossPartStock(BossTier.TIER_1, resource, 2_000), 0);
		}
	}

	@Test
	public void successfulBossPartRollAwardsTierQuantityForEachRarity()
	{
		for (BossTier tier : new BossTier[]{BossTier.TIER_1, BossTier.TIER_2, BossTier.TIER_3})
		{
			SettlementState dropState = new SettlementState();
			SettlementEngine dropEngine = engineWithRoll(dropState, 0);
			dropEngine.onBossKill("Test boss", tier);

			double expected = Balance.BOSS_PARTS_PER_DROP.get(tier);
			assertEquals(expected, dropState.getStock(Resource.MONSTER_PARTS), 1e-9);
			assertEquals(expected, dropState.getStock(Resource.RARE_MONSTER_PARTS), 1e-9);
			assertEquals(expected, dropState.getStock(Resource.EPIC_MONSTER_PARTS), 1e-9);
		}
	}

	@Test
	public void trophyHallIncreasesBossPartOutputWithoutChangingDropRolls()
	{
		state.setLevel(Building.TROPHY_HALL, 5);
		double multiplier = YieldCalculator.trophyMultiplier(state);
		for (Resource resource : new Resource[]{Resource.MONSTER_PARTS, Resource.RARE_MONSTER_PARTS, Resource.EPIC_MONSTER_PARTS})
		{
			double withoutTrophyHall = bossPartStock(BossTier.TIER_3, resource, 2_000, 0);
			double withTrophyHall = bossPartStock(BossTier.TIER_3, resource, 2_000, 5);
			assertTrue(resource + " should drop in the seeded sample", withoutTrophyHall > 0);
			assertEquals(resource + " drop rolls should stay the same", withoutTrophyHall * multiplier,
				withTrophyHall, 1e-6);
		}
	}

	@Test
	public void trophyHallMultipliesRaidArtifactOutput()
	{
		int trophyLevel = 5;
		state.setLevel(Building.TROPHY_HALL, trophyLevel);
		engine.onBossKill("Tier 2 raid", BossTier.TIER_2, true);
		engine.onBossKill("Tier 3 raid", BossTier.TIER_3, true);

		double expected = (Balance.RAID_ARTIFACTS_TIER_2 + Balance.RAID_ARTIFACTS_TIER_3)
			* YieldCalculator.trophyMultiplier(state);
		assertEquals(expected, state.getStock(Resource.ARTIFACT), 1e-9);
	}

	@Test
	public void artifactsComeOnlyFromRaidsAndScaleByRaidTier()
	{
		engine.onBossKill("Nex", BossTier.TIER_3);
		assertEquals(0, state.getStock(Resource.ARTIFACT), 0);

		engine.onBossKill("Theatre of Blood: Entry Mode", BossTier.TIER_2, true);
		assertEquals(Balance.RAID_ARTIFACTS_TIER_2, state.getStock(Resource.ARTIFACT), 0);

		engine.onBossKill("Chambers of Xeric", BossTier.TIER_3, true);
		assertEquals(Balance.RAID_ARTIFACTS_TIER_2 + Balance.RAID_ARTIFACTS_TIER_3,
			state.getStock(Resource.ARTIFACT), 0);
	}

	@Test
	public void clueGivesCurios()
	{
		engine.onClueCompleted(ClueTier.MASTER);
		assertEquals(ClueTier.MASTER.getReward().get(Resource.CURIOS), state.getStock(Resource.CURIOS), 1e-9);
		assertEquals(ClueTier.MASTER.getReward().get(Resource.RARE_MONSTER_PARTS),
			state.getStock(Resource.RARE_MONSTER_PARTS), 1e-9);
	}

	@Test
	public void completedObjectiveProgressNeverRegressesAfterRebalance()
	{
		Map<String, Double> progress = state.getExpeditionProgress(ExpeditionId.KELDAGRIM).getObjectives();
		progress.put("miningXp", 200_000.0);
		engine.onXp(Skill.MINING, 100);
		assertEquals(200_000.0, progress.get("miningXp"), 0);
	}

	@Test
	public void keldagrimObjectivesTrackTheirActivitySources()
	{
		Map<String, Double> progress = state.getExpeditionProgress(ExpeditionId.KELDAGRIM).getObjectives();

		engine.onXp(Skill.MINING, 100);
		assertEquals(100, progress.get("miningXp"), 0);

		engine.onXp(Skill.SMITHING, 5);
		assertEquals(5, progress.get("smithingXp"), 0);

		engine.onXp(Skill.WOODCUTTING, 100);
		assertFalse(progress.containsKey("woodcuttingXp"));

		engine.onXp(Skill.SLAYER, 10);
		assertEquals(state.getStock(Resource.MONSTER_PARTS), progress.get("monsterParts"), 1e-9);

		engine.onBossKill("Test boss", BossTier.TIER_1);
		assertEquals(state.getStock(Resource.MONSTER_PARTS), progress.get("monsterParts"), 1e-9);
		assertEquals(1, progress.get("bosses1"), 0);

		Bounty bounty = bounty(BountyType.CLUES, 1);
		bounty.setReward(BountyReward.resources(Map.of(Resource.MONSTER_PARTS, 7)));
		state.getBounties().add(bounty);
		engine.onClueCompleted(ClueTier.EASY);
		assertEquals(1, progress.get("clues"), 0);
		double beforeClaim = progress.get("monsterParts");
		engine.claimBounty(bounty.getId());
		assertEquals(beforeClaim + 7, progress.get("monsterParts"), 1e-9);
	}

	@Test
	public void rareAndEpicMonsterPartsAdvanceTheirMatchingExpeditionObjectives()
	{
		completeExpedition(ExpeditionId.KELDAGRIM);
		completeExpedition(ExpeditionId.FOSSIL_ISLAND);
		completeExpedition(ExpeditionId.GREAT_KOUREND);
		Map<String, Double> desert = state.getExpeditionProgress(ExpeditionId.KHARIDIAN_DESERT).getObjectives();

		Bounty rareParts = bounty(BountyType.CLUES, 1);
		rareParts.setProgress(rareParts.getTarget());
		rareParts.setReward(BountyReward.resources(Map.of(Resource.RARE_MONSTER_PARTS, 17)));
		state.getBounties().add(rareParts);
		engine.claimBounty(rareParts.getId());
		assertEquals(17, desert.get("rareMonsterParts"), 0);
		assertFalse(desert.containsKey("monsterParts"));

		completeExpedition(ExpeditionId.KHARIDIAN_DESERT);
		Map<String, Double> prifddinas = state.getExpeditionProgress(ExpeditionId.PRIFDDINAS).getObjectives();
		Bounty epicParts = bounty(BountyType.CLUES, 1);
		epicParts.setProgress(epicParts.getTarget());
		epicParts.setReward(BountyReward.resources(Map.of(Resource.EPIC_MONSTER_PARTS, 11)));
		state.getBounties().add(epicParts);
		engine.claimBounty(epicParts.getId());
		assertEquals(11, prifddinas.get("epicMonsterParts"), 0);
		assertFalse(prifddinas.containsKey("monsterParts"));
		assertFalse(prifddinas.containsKey("rareMonsterParts"));
	}

	@Test
	public void bossDropRollsUseStableResourceOrder()
	{
		AtomicInteger rollIndex = new AtomicInteger();
		double[] rolls = {0.10, 0.04, 0.50};
		Random orderedRandom = new Random(42)
		{
			@Override
			public double nextDouble()
			{
				return rolls[rollIndex.getAndIncrement() % rolls.length];
			}
		};
		SettlementState fresh = new SettlementState();
		fresh.setLevel(Building.TOWN_HALL, 1);
		SettlementEngine orderedEngine = new SettlementEngine(fresh, orderedRandom);
		fresh.getBounties().clear();
		clearEvent(fresh);
		rollIndex.set(0);
		orderedEngine.onBossKill("Obor", BossTier.TIER_1);
		assertEquals(Balance.BOSS_PARTS_PER_DROP.get(BossTier.TIER_1), fresh.getStock(Resource.MONSTER_PARTS), 0);
		assertEquals(Balance.BOSS_PARTS_PER_DROP.get(BossTier.TIER_1), fresh.getStock(Resource.RARE_MONSTER_PARTS), 0);
		assertEquals(0, fresh.getStock(Resource.EPIC_MONSTER_PARTS), 0);
	}

	@Test
	public void higherBossTiersCountTowardLowerTierTasks()
	{
		Map<String, Double> keldagrim = state.getExpeditionProgress(ExpeditionId.KELDAGRIM).getObjectives();
		engine.onBossKill("Vorkath", BossTier.TIER_2);
		engine.onBossKill("Nex", BossTier.TIER_3);
		assertEquals(2, keldagrim.get("bosses1"), 0);

		completeExpedition(ExpeditionId.KELDAGRIM);
		completeExpedition(ExpeditionId.FOSSIL_ISLAND);
		Map<String, Double> kourend = state.getExpeditionProgress(ExpeditionId.GREAT_KOUREND).getObjectives();
		engine.onBossKill("Obor", BossTier.TIER_1);
		assertFalse(kourend.containsKey("skotizoKills"));
		engine.onBossKill("Vorkath", BossTier.TIER_2);
		assertFalse(kourend.containsKey("skotizoKills"));
		engine.onBossKill("Skotizo", BossTier.TIER_1);
		assertEquals(1, kourend.get("skotizoKills"), 0);
		engine.onBossKill("Chambers of Xeric Challenge Mode", BossTier.TIER_3, true);
		assertEquals(1, kourend.get("chambersOfXericCompletions"), 0);
	}

	@Test
	public void specificDesertAndPrifddinasEncountersProgressOnlyTheirObjectives()
	{
		completeExpedition(ExpeditionId.KELDAGRIM);
		completeExpedition(ExpeditionId.FOSSIL_ISLAND);
		completeExpedition(ExpeditionId.GREAT_KOUREND);
		Map<String, Double> desert = state.getExpeditionProgress(ExpeditionId.KHARIDIAN_DESERT).getObjectives();

		engine.onBossKill("Tombs of Amascut: Entry Mode", BossTier.TIER_2, true);
		engine.onBossKill("Tombs of Amascut: Expert Mode", BossTier.TIER_3, true);
		assertEquals(2, desert.get("tombsOfAmascutCompletions"), 0);
		engine.onBossKill("Theatre of Blood: Entry Mode", BossTier.TIER_2, true);
		assertEquals(2, desert.get("tombsOfAmascutCompletions"), 0);
		engine.onBossKill("Kalphite Queen", BossTier.TIER_1);
		assertEquals(1, desert.get("kalphiteQueenKills"), 0);

		completeExpedition(ExpeditionId.KHARIDIAN_DESERT);
		Map<String, Double> prifddinas = state.getExpeditionProgress(ExpeditionId.PRIFDDINAS).getObjectives();
		engine.onBossKill("Corrupted Gauntlet", BossTier.TIER_3);
		assertEquals(1, prifddinas.get("corruptedGauntletCompletions"), 0);
		engine.onBossKill("Gauntlet", BossTier.TIER_2);
		assertEquals(1, prifddinas.get("corruptedGauntletCompletions"), 0);
		engine.onBossKill("Zalcano", BossTier.TIER_1);
		assertEquals(1, prifddinas.get("zalcanoKills"), 0);
	}

	@Test
	public void onlyTheCurrentExpeditionProgressesAndClaimingUnlocksTheNext()
	{
		Map<String, Double> fossil = state.getExpeditionProgress(ExpeditionId.FOSSIL_ISLAND).getObjectives();
		engine.onXp(Skill.WOODCUTTING, 100);
		assertTrue(fossil.isEmpty());
		assertNull(engine.claimExpedition(ExpeditionId.FOSSIL_ISLAND));
		assertEquals(ExpeditionId.KELDAGRIM, ExpeditionCatalog.current(state).getId());

		completeExpedition(ExpeditionId.KELDAGRIM);
		assertEquals(ExpeditionId.FOSSIL_ISLAND, ExpeditionCatalog.current(state).getId());
		double keldagrimMining = state.getExpeditionProgress(ExpeditionId.KELDAGRIM).getObjectives().get("miningXp");
		engine.onXp(Skill.MINING, 100);
		assertEquals(keldagrimMining, state.getExpeditionProgress(ExpeditionId.KELDAGRIM).getObjectives().get("miningXp"), 0);
		engine.onXp(Skill.WOODCUTTING, 100);
		assertEquals(100, fossil.get("woodcuttingXp"), 0);
	}

	@Test
	public void everyExpeditionAwardsItsOwnBlueprint()
	{
		for (ExpeditionCatalog.Definition expedition : ExpeditionCatalog.all())
		{
			completeExpedition(expedition.getId());
			assertTrue(state.getBlueprints().contains(expedition.getRewardBlueprint()));
		}
		assertNull(ExpeditionCatalog.current(state));
		assertEquals(5, ExpeditionCatalog.all().size());
	}

	@Test
	public void towerOfVoicesBoostsEveryYield()
	{
		double before = global();
		state.setLevel(Building.TOWER_OF_VOICES, 2);
		assertEquals(before + 2 * Balance.TOWER_OF_VOICES_GLOBAL_BONUS, global(), 1e-9);
	}

	@Test
	public void jaltevasPyramidStrengthensBuffsButNotSetbacks()
	{
		state.setLevel(Building.JALTEVAS_PYRAMID, 5);
		double strengthened = 1 + (SettlementEvent.FISH_MIGRATION.getMultiplier() - 1)
			* (1 + 5 * Balance.PYRAMID_EVENT_BONUS_PER_LEVEL);
		assertEquals(strengthened, YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.FISH_MIGRATION), 1e-9);
		assertEquals(SettlementEvent.TERMITES.getMultiplier(),
			YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.TERMITES), 0);

		activate(state, SettlementEvent.FISH_MIGRATION);
		engine.onXp(Skill.FISHING, 100);
		assertEquals(YieldCalculator.units(100) * global() * strengthened, state.getStock(Resource.FISH), 1e-9);
	}

	@Test
	public void arceuusLibraryExtendsBoostRewards()
	{
		int libraryLevel = 5;
		state.setLevel(Building.ARCEUUS_LIBRARY, libraryLevel);
		Bounty bounty = bounty(BountyType.CLUES, 1);
		bounty.setReward(BountyReward.boost(Resource.LOGS, Balance.BOOST_MULTIPLIER, Balance.BOOST_TICKS));
		state.getBounties().add(bounty);

		engine.onClueCompleted(ClueTier.EASY);
		engine.claimBounty(bounty.getId());
		long expectedTicks = Math.round(Balance.BOOST_TICKS * (1 + libraryLevel * Balance.LIBRARY_BOOST_DURATION_PER_LEVEL));
		assertEquals(expectedTicks, state.getBoosts().get(0).getRemainingTicks());
	}

	@Test
	public void museumCampRaisesBountySupplyRewards()
	{
		assertTrue(supplyRewardTotal(5) > supplyRewardTotal(0));
	}

	private void completeExpedition(ExpeditionId id)
	{
		Map<String, Double> progress = state.getExpeditionProgress(id).getObjectives();
		ExpeditionCatalog.get(id).getObjectives().forEach(objective ->
			progress.put(objective.getId(), (double) objective.getTarget()));
		assertTrue(engine.claimExpedition(id) != null);
	}

	private static int supplyRewardTotal(int museumLevel)
	{
		SettlementState rewardState = new SettlementState();
		rewardState.setLevel(Building.TOWN_HALL, 1);
		rewardState.setLevel(Building.MUSEUM_CAMP, museumLevel);
		BountyBoard board = new BountyBoard(new Random(11));
		int total = 0;
		for (int i = 0; i < 200; i++)
		{
			Bounty bounty = board.generate(rewardState);
			if (bounty.getType() != BountyType.BOSS_KILLS)
			{
				total += bounty.getReward().getResources().values().stream().mapToInt(Integer::intValue).sum();
			}
		}
		return total;
	}

	@Test
	public void expeditionClaimRequiresCompletionAndAwardsBlueprintOnce()
	{
		Map<String, Double> progress = state.getExpeditionProgress(ExpeditionId.KELDAGRIM).getObjectives();
		assertNull(engine.claimExpedition(ExpeditionId.KELDAGRIM));

		ExpeditionCatalog.get(ExpeditionId.KELDAGRIM).getObjectives().forEach(objective ->
			progress.put(objective.getId(), (double) objective.getTarget()));
		assertTrue(engine.isExpeditionComplete(ExpeditionId.KELDAGRIM));
		assertEquals("Keldagrim Consortium blueprint unlocked", engine.claimExpedition(ExpeditionId.KELDAGRIM));
		assertTrue(state.getBlueprints().contains(Building.KELDAGRIM_CONSORTIUM));
		assertNull(engine.claimExpedition(ExpeditionId.KELDAGRIM));
	}

	@Test
	public void cluePartBonusesMatchClueTier()
	{
		assertTrue(cluePartStock(ClueTier.HARD, Resource.RARE_MONSTER_PARTS) > 0);
		assertTrue(cluePartStock(ClueTier.ELITE, Resource.EPIC_MONSTER_PARTS) > 0);
		assertTrue(cluePartStock(ClueTier.MASTER, Resource.EPIC_MONSTER_PARTS) > 0);
	}

	@Test
	public void eliteCluesAverageATierOneBossKill()
	{
		int clues = 2000;
		for (Resource part : new Resource[]{Resource.MONSTER_PARTS, Resource.RARE_MONSTER_PARTS, Resource.EPIC_MONSTER_PARTS})
		{
			double perKill = Balance.BOSS_PART_DROP_CHANCES.get(BossTier.TIER_1).get(part)
				* Balance.BOSS_PARTS_PER_DROP.get(BossTier.TIER_1);
			double perClue = cluePartStock(ClueTier.ELITE, part, clues) / clues;
			assertEquals(part + " per elite clue", perKill, perClue, perKill * 0.25);
		}
	}

	@Test
	public void lowerClueTiersGiveFewerParts()
	{
		double previous = -1;
		for (ClueTier tier : ClueTier.values())
		{
			double parts = cluePartStock(tier, Resource.MONSTER_PARTS)
				+ cluePartStock(tier, Resource.RARE_MONSTER_PARTS)
				+ cluePartStock(tier, Resource.EPIC_MONSTER_PARTS);
			assertTrue(tier + " should give more parts than the tier below", parts > previous);
			previous = parts;
		}
	}

	@Test
	public void completedBountyWaitsForClaim()
	{
		Bounty bounty = bounty(BountyType.SKILL_XP, 50);
		bounty.setSkill(Skill.WOODCUTTING);
		bounty.setReward(BountyReward.resources(Map.of(Resource.FISH, 25)));
		state.getBounties().add(bounty);

		engine.onXp(Skill.WOODCUTTING, 60);

		assertEquals(0, state.getStock(Resource.FISH), 0);
		assertEquals(0, state.getBountiesCompleted());
		assertTrue(state.getBounties().contains(bounty));

		assertEquals("+25 Fish", engine.claimBounty(bounty.getId()));
		assertEquals(25, state.getStock(Resource.FISH), 1e-9);
		assertEquals(1, state.getBountiesCompleted());
		assertEquals(Balance.BOUNTY_SLOTS, state.getBounties().size());
		assertFalse(state.getBounties().contains(bounty));
	}

	@Test
	public void kindBountyCountsAnySkillOfThatKind()
	{
		Bounty bounty = bounty(BountyType.KIND_XP, 1_000_000);
		bounty.setKind(SkillKind.GATHERING);
		state.getBounties().add(bounty);

		engine.onXp(Skill.FISHING, 100);
		engine.onXp(Skill.MINING, 50);
		engine.onXp(Skill.SMITHING, 70);
		assertEquals(150, bounty.getProgress(), 1e-9);
	}

	@Test
	public void combatXpBountyTracksHitpointsOnly()
	{
		Bounty bounty = bounty(BountyType.KIND_XP, 1_000);
		bounty.setKind(SkillKind.COMBAT);
		state.getBounties().add(bounty);

		engine.onXp(Skill.ATTACK, 100);
		engine.onXp(Skill.STRENGTH, 100);
		assertEquals(0, bounty.getProgress(), 0);

		engine.onXp(Skill.HITPOINTS, 75);
		assertEquals(75, bounty.getProgress(), 0);
		assertTrue(bounty.describe().endsWith(" Hitpoints XP"));
	}

	@Test
	public void slayerDropsBountyTracksGeneratedPartsOnly()
	{
		Bounty bounty = bounty(BountyType.SLAYER_DROPS, 100);
		state.getBounties().add(bounty);

		engine.onXp(Skill.SLAYER, 1);
		assertEquals(YieldCalculator.units(1) * global(), bounty.getProgress(), 1e-9);
		engine.onBossKill("Test boss", BossTier.TIER_3);
		assertEquals(YieldCalculator.units(1) * global(), bounty.getProgress(), 1e-9);
	}

	@Test
	public void slayerXpUsesTheStandardGatherYield()
	{
		SettlementState dropState = new SettlementState();
		SettlementEngine dropEngine = engineWithRoll(dropState, 0.99);
		dropEngine.onXp(Skill.SLAYER, 100);

		assertEquals(YieldCalculator.units(100) * YieldCalculator.gatherMultiplier(dropState, Building.SLAYER_TOWER),
			dropState.getStock(Resource.MONSTER_PARTS), 1e-9);
	}

	@Test
	public void slayerTowerAndResourceBoostMultiplyNormalYield()
	{
		state.setLevel(Building.SLAYER_TOWER, 2);
		state.getBoosts().add(new Boost(Resource.MONSTER_PARTS, 2, 100));

		engine.onXp(Skill.SLAYER, 10);

		double expected = YieldCalculator.units(10)
			* YieldCalculator.gatherMultiplier(state, Building.SLAYER_TOWER) * 2;
		assertEquals(expected, state.getStock(Resource.MONSTER_PARTS), 1e-9);
	}

	@Test
	public void monsterDropEventMultipliesSlayerYield()
	{
		SettlementState eventState = new SettlementState();
		SettlementEngine eventEngine = engineWithRoll(eventState, 0.99);
		activate(eventState, SettlementEvent.REDBERRY_PIE);

		eventEngine.onXp(Skill.SLAYER, 100);

		double expected = YieldCalculator.units(100)
			* YieldCalculator.gatherMultiplier(eventState, Building.SLAYER_TOWER)
			* YieldCalculator.eventSkillMultiplier(eventState, Skill.SLAYER);
		assertEquals(expected, eventState.getStock(Resource.MONSTER_PARTS), 1e-9);
	}

	@Test
	public void blueprintRewardUnlocksBuilding()
	{
		Bounty bounty = bounty(BountyType.CLUES, 1);
		bounty.setReward(BountyReward.blueprint(Building.SAWMILL));
		state.getBounties().add(bounty);

		engine.onClueCompleted(ClueTier.EASY);
		engine.claimBounty(bounty.getId());
		assertTrue(state.getBlueprints().contains(Building.SAWMILL));
	}

	@Test
	public void boostRewardExtendsExistingBoost()
	{
		state.getBoosts().add(new Boost(Resource.LOGS, 2, 100));
		Bounty bounty = bounty(BountyType.CLUES, 1);
		bounty.setReward(BountyReward.boost(Resource.LOGS, 2, 50));
		state.getBounties().add(bounty);

		engine.onClueCompleted(ClueTier.EASY);
		engine.claimBounty(bounty.getId());
		assertEquals(1, state.getBoosts().size());
		assertEquals(150, state.getBoosts().get(0).getRemainingTicks());
	}

	@Test
	public void deliverNeedsTheFullAmount()
	{
		Bounty bounty = bounty(BountyType.DELIVER, 100);
		bounty.setResource(Resource.PLANKS);
		state.getBounties().add(bounty);

		state.addStock(Resource.PLANKS, 99);
		assertNull(engine.claimBounty(bounty.getId()));

		state.addStock(Resource.PLANKS, 1);
		assertEquals("+1 Coins", engine.claimBounty(bounty.getId()));
		assertEquals(0, state.getStock(Resource.PLANKS), 1e-9);
		assertEquals(1, state.getBountiesCompleted());
	}

	@Test
	public void deliveryBountiesDoNotCompleteFromXp()
	{
		Bounty bounty = bounty(BountyType.DELIVER, 1);
		bounty.setResource(Resource.LOGS);
		state.getBounties().add(bounty);

		engine.onXp(Skill.WOODCUTTING, 1000);
		assertTrue(state.getBounties().contains(bounty));
	}

	@Test
	public void boostsExpireWithPlaytime()
	{
		state.getBoosts().add(new Boost(Resource.LOGS, 2, 2));
		engine.onTick();
		assertEquals(1, state.getBoosts().size());
		engine.onTick();
		assertTrue(state.getBoosts().isEmpty());
		assertEquals(2, state.getPlaytimeTicks());
	}

	@Test
	public void bountiesExpireAndAreReplaced()
	{
		Bounty bounty = bounty(BountyType.BOSS_KILLS, 100);
		bounty.setExpiresAtPlaytime(2);
		state.getBounties().add(bounty);

		engine.onTick();
		assertTrue(state.getBounties().contains(bounty));
		engine.onTick();
		assertFalse(state.getBounties().contains(bounty));
		assertEquals(Balance.BOUNTY_SLOTS, state.getBounties().size());
		assertEquals(0, state.getBountiesCompleted());
	}

	@Test
	public void completedBountyDoesNotExpireBeforeClaim()
	{
		Bounty bounty = bounty(BountyType.BOSS_KILLS, 1);
		bounty.setProgress(1);
		bounty.setExpiresAtPlaytime(1);
		state.getBounties().add(bounty);

		engine.onTick();

		assertTrue(state.getBounties().contains(bounty));
		assertEquals(0, state.getBountiesCompleted());
	}

	@Test
	public void upgradeIsLogged()
	{
		state.addStock(Resource.LOGS, 1000);
		state.addStock(Resource.ORE, 1000);
		assertTrue(engine.upgrade(Building.LUMBER_CAMP).isOk());
		assertTrue(state.getLog().get(0).getMessage().contains("Lumber Camp"));
	}

	@Test
	public void logIsBounded()
	{
		for (int i = 0; i < Balance.LOG_SIZE * 2; i++)
		{
			engine.onBossKill("Obor", BossTier.TIER_1);
		}
		assertEquals(Balance.LOG_SIZE, state.getLog().size());
	}

	@Test
	public void gatheringEventMultipliesOnlyItsSkill()
	{
		activate(state, SettlementEvent.FISH_MIGRATION);
		engine.onXp(Skill.FISHING, 100);
		engine.onXp(Skill.WOODCUTTING, 100);

		assertEquals(YieldCalculator.units(100) * global()
			* YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.FISH_MIGRATION),
			state.getStock(Resource.FISH), 1e-9);
		assertEquals(YieldCalculator.units(100) * global(), state.getStock(Resource.LOGS), 1e-9);
	}

	@Test
	public void debuffEventReducesYield()
	{
		activate(state, SettlementEvent.TERMITES);
		engine.onXp(Skill.WOODCUTTING, 100);
		assertEquals(YieldCalculator.units(100) * global()
			* YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.TERMITES),
			state.getStock(Resource.LOGS), 1e-9);
	}

	@Test
	public void eventStacksWithBoosts()
	{
		activate(state, SettlementEvent.LUMBERJACK_CONTEST);
		state.getBoosts().add(new Boost(Resource.LOGS, Balance.BOOST_MULTIPLIER, 100));
		engine.onXp(Skill.WOODCUTTING, 10);
		double eventMultiplier = YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.LUMBERJACK_CONTEST);
		assertEquals(Balance.BOOST_MULTIPLIER * eventMultiplier * global(), state.getStock(Resource.LOGS), 1e-9);
	}

	@Test
	public void processingEventMultipliesOutputNotInput()
	{
		activate(state, SettlementEvent.DORICS_SECRETS);
		state.addStock(Resource.ORE, 100);
		engine.onXp(Skill.SMITHING, 10);

		assertEquals(100 - Balance.PROCESS_INPUT_PER_UNIT, state.getStock(Resource.ORE), 1e-9);
		assertEquals(YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.DORICS_SECRETS) * global(),
			state.getStock(Resource.BARS), 1e-9);
	}

	@Test
	public void clueEventMultipliesRewards()
	{
		activate(state, SettlementEvent.FEELING_LUCKY);
		engine.onClueCompleted(ClueTier.MASTER);
		assertEquals(ClueTier.MASTER.getReward().get(Resource.CURIOS)
			* YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.FEELING_LUCKY),
			state.getStock(Resource.CURIOS), 1e-9);
	}

	@Test
	public void monsterDropEventMultipliesBossDrops()
	{
		activate(state, SettlementEvent.REDBERRY_PIE);
		engine.onBossKill("Chambers of Xeric", BossTier.TIER_3, true);
		assertEquals(Balance.RAID_ARTIFACTS_TIER_3
			* YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.REDBERRY_PIE),
			state.getStock(Resource.ARTIFACT), 1e-9);
	}

	@Test
	public void globalEventAppliesEverywhere()
	{
		activate(state, SettlementEvent.SETTLEMENT_FESTIVAL);
		state.addStock(Resource.ORE, 100);
		engine.onXp(Skill.WOODCUTTING, 10);
		engine.onXp(Skill.SMITHING, 10);
		engine.onClueCompleted(ClueTier.BEGINNER);
		engine.onBossKill("Theatre of Blood: Entry Mode", BossTier.TIER_2, true);

		double festivalMultiplier = YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.SETTLEMENT_FESTIVAL);
		assertEquals(festivalMultiplier * global(), state.getStock(Resource.LOGS), 1e-9);
		assertEquals(festivalMultiplier * global(), state.getStock(Resource.BARS), 1e-9);
		assertEquals(ClueTier.BEGINNER.getReward().get(Resource.CURIOS) * festivalMultiplier,
			state.getStock(Resource.CURIOS), 1e-9);
		assertEquals(Balance.RAID_ARTIFACTS_TIER_2 * festivalMultiplier,
			state.getStock(Resource.ARTIFACT), 1e-9);
	}

	@Test
	public void sameEventTwiceSumsItsFactor()
	{
		activate(state, SettlementEvent.FISH_MIGRATION);
		activate(state, SettlementEvent.FISH_MIGRATION);
		double expectedMultiplier = 2
			* YieldCalculator.effectiveEventMultiplier(state, SettlementEvent.FISH_MIGRATION);
		assertEquals(expectedMultiplier, YieldCalculator.eventSkillMultiplier(state, Skill.FISHING), 1e-9);

		engine.onXp(Skill.FISHING, 100);
		assertEquals(YieldCalculator.units(100) * global() * expectedMultiplier,
			state.getStock(Resource.FISH), 1e-9);
	}

	@Test
	public void differentEventsMultiply()
	{
		activate(state, SettlementEvent.LUMBERJACK_CONTEST);
		activate(state, SettlementEvent.SETTLEMENT_FESTIVAL);
		assertEquals(4.5, YieldCalculator.eventSkillMultiplier(state, Skill.WOODCUTTING), 1e-9);
		assertEquals(1.5, YieldCalculator.eventSkillMultiplier(state, Skill.FISHING), 1e-9);
	}

	@Test
	public void sameSetbackTwiceCompounds()
	{
		activate(state, SettlementEvent.TERMITES);
		activate(state, SettlementEvent.TERMITES);
		assertEquals(0.25, YieldCalculator.eventSkillMultiplier(state, Skill.WOODCUTTING), 1e-9);
	}

	private Bounty bounty(BountyType type, long target)
	{
		Bounty bounty = new Bounty();
		bounty.setId(1000 + state.getBounties().size());
		bounty.setType(type);
		bounty.setTarget(target);
		bounty.setReward(BountyReward.resources(Map.of(Resource.COINS, 1)));
		return bounty;
	}

	private static double bossPartStock(BossTier tier, Resource resource, int kills)
	{
		return bossPartStock(tier, resource, kills, 0);
	}

	private static double bossPartStock(BossTier tier, Resource resource, int kills, int trophyHallLevel)
	{
		SettlementState testState = new SettlementState();
		testState.setLevel(Building.TROPHY_HALL, trophyHallLevel);
		SettlementEngine testEngine = new SettlementEngine(testState, new Random(42));
		testState.getBounties().clear();
		clearEvent(testState);
		for (int kill = 0; kill < kills; kill++)
		{
			testEngine.onBossKill("Test boss", tier);
		}
		return testState.getStock(resource);
	}

	private static double cluePartStock(ClueTier tier, Resource resource)
	{
		return cluePartStock(tier, resource, 200);
	}

	private static double cluePartStock(ClueTier tier, Resource resource, int clues)
	{
		SettlementState testState = new SettlementState();
		SettlementEngine testEngine = new SettlementEngine(testState, new Random(42));
		testState.getBounties().clear();
		clearEvent(testState);
		for (int clue = 0; clue < clues; clue++)
		{
			testEngine.onClueCompleted(tier);
		}
		return testState.getStock(resource);
	}
}
