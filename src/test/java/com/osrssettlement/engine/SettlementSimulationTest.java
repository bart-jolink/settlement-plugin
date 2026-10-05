package com.osrssettlement.engine;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import com.osrssettlement.model.BossTier;
import com.osrssettlement.model.Bounty;
import com.osrssettlement.model.BountyReward;
import com.osrssettlement.model.BountyType;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.BuildingType;
import com.osrssettlement.model.ClueTier;
import com.osrssettlement.model.ExpeditionId;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementState;
import com.osrssettlement.model.SkillRule;
import com.osrssettlement.model.SkillRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Plays simulated accounts through the real engine to check the configured first-Wonder target.
 * Activities follow the next upgrade's prerequisites; XP per drop and drops per hour are rough mid-level rates.
 */
@RunWith(Enclosed.class)
public class SettlementSimulationTest
{
	private static final int TICKS_PER_MINUTE = 100;
	private static final int SIMULATION_WONDER_BUILDING_LEVEL = Balance.WONDER_MIN_BUILDING_LEVEL;
	private static final BossTier BOSSING_TIER = BossTier.TIER_2;
	private static final int MAX_HOURS = Math.max(300,
		(int) Math.ceil(Balance.FIRST_WONDER_TARGET_HOURS * Balance.FIRST_WONDER_MAX_TARGET_FACTOR) + 1);

	private enum Activity
	{
		WOODCUTTING(Skill.WOODCUTTING, 180, 500),
		MINING(Skill.MINING, 60, 1000),
		FISHING(Skill.FISHING, 200, 250),
		HUNTER(Skill.HUNTER, 150, 400),
		FARMING(Skill.FARMING, 1000, 40),
		THIEVING(Skill.THIEVING, 120, 1000),
		AGILITY(Skill.AGILITY, 60, 700),
		SAILING(Skill.SAILING, 2500, 40),
		SMITHING(Skill.SMITHING, 100, 800),
		FIREMAKING(Skill.FIREMAKING, 200, 600),
		CONSTRUCTION(Skill.CONSTRUCTION, 400, 700),
		CRAFTING(Skill.CRAFTING, 120, 1000),
		FLETCHING(Skill.FLETCHING, 100, 1500),
		COOKING(Skill.COOKING, 120, 1000),
		HERBLORE(Skill.HERBLORE, 100, 1000),
		RUNECRAFT(Skill.RUNECRAFT, 400, 150),
		MAGIC(Skill.MAGIC, 65, 1200),
		PRAYER(Skill.PRAYER, 250, 350),
		COMBAT(null, 0, 0),
		SLAYER(Skill.SLAYER, 100, 400),
		BOSSING(null, 0, 0),
		RAIDING(null, 0, 0),
		CLUES(null, 0, 0);

		final Skill skill;
		final int xp;
		final int actionsPerHour;

		Activity(Skill skill, int xp, int actionsPerHour)
		{
			this.skill = skill;
			this.xp = xp;
			this.actionsPerHour = actionsPerHour;
		}
	}

	@RunWith(Parameterized.class)
	public static class BalancedPlayer
	{
		private final long seed;

		public BalancedPlayer(long seed)
		{
			this.seed = seed;
		}

		@Parameterized.Parameters(name = "seed={0}")
		public static List<Object[]> seeds()
		{
			return Arrays.asList(new Object[][]{{1L}, {2L}, {3L}, {4L}, {5L}});
		}

		@Test
		public void balancedPlayerReachesTheWonderWithinConfiguredTarget()
		{
			double minimumHours = Balance.FIRST_WONDER_TARGET_HOURS;
			double maximumHours = Balance.FIRST_WONDER_TARGET_HOURS * Balance.FIRST_WONDER_MAX_TARGET_FACTOR;
			assertTrue("First-Wonder target must be positive", Balance.FIRST_WONDER_TARGET_HOURS > 0);
			assertTrue("First-Wonder acceptance factor must define a finite positive range",
				Double.isFinite(minimumHours) && Double.isFinite(maximumHours)
					&& minimumHours > 0 && maximumHours >= minimumHours);
			Result result = simulate(seed, true);
			String wonderTime = result.wonderHours == Double.MAX_VALUE
				? "not reached within " + MAX_HOURS + " h"
				: String.format(Locale.ROOT, "%.2f h", result.wonderHours);
			System.out.printf(Locale.ROOT, "Seed %d: first Wonder %s; expected %.0f-%.0f h%n",
				seed, wonderTime, minimumHours, maximumHours);
			System.out.printf(Locale.ROOT, "  Activity hours: %s%n", formatActivityHours(result));
			System.out.printf(Locale.ROOT, "  Town Hall milestones: %s%n", formatTownHallMilestones(result));
			System.out.printf(Locale.ROOT, "  Town Hall building gates: %s%n", String.join(", ", result.townHallGateProgress));
			System.out.printf(Locale.ROOT, "  Town Hall upgrade resource gaps: %s%n", formatTownHallResourceGaps(result));
			System.out.printf(Locale.ROOT, "  Wonder resource gaps after other gates: %s%n",
				result.wonderResourceBlocker == null ? "none" : result.wonderResourceBlocker);
			assertTrue("first building after " + result.firstBuildingHours + "h", result.firstBuildingHours < 1);
			assertTrue("Town Hall 2 after " + result.townHallTwoHours + "h", result.townHallTwoHours < 12);
			assertTrue("Wonder after " + result.wonderHours + "h; expected " + minimumHours + "-" + maximumHours
				+ "h (seed " + seed + "); Keldagrim: "
				+ result.expeditionSummary + "; " + result.progressionSummary,
				result.wonderHours >= minimumHours && result.wonderHours <= maximumHours);
		}
	}

	public static class TownHallPlayer
	{
		@Test
		public void focusedPlayerReachesTownHallTen()
		{
			Result result = simulate(1, false);
			assertTrue("Town Hall 10 after " + result.townHallTenHours + "h; " + result.progressionSummary,
				result.townHallTenHours <= MAX_HOURS);
		}
	}

	private static final class Result
	{
		final double[] townHallHours = new double[Balance.MAX_BUILDING_LEVEL + 1];
		final int[] lastTownHallBuildingCount = new int[Balance.MAX_BUILDING_LEVEL + 1];
		final String[] townHallResourceGaps = new String[Balance.MAX_BUILDING_LEVEL + 1];
		final List<String> townHallGateProgress = new ArrayList<>();
		final Map<Activity, Integer> activityMinutes = new EnumMap<>(Activity.class);
		double firstBuildingHours = Double.MAX_VALUE;
		double townHallTwoHours = Double.MAX_VALUE;
		double townHallTenHours = Double.MAX_VALUE;
		double expeditionHours = Double.MAX_VALUE;
		double wonderHours = Double.MAX_VALUE;
		String wonderResourceBlocker;
		String expeditionSummary;
		String progressionSummary;

		Result()
		{
			Arrays.fill(townHallHours, Double.MAX_VALUE);
			Arrays.fill(lastTownHallBuildingCount, -1);
		}
	}

	private static Result simulate(long seed, boolean stopAtWonder)
	{
		SettlementEngine engine = new SettlementEngine(SettlementEngine.newSettlement(), new Random(seed));
		SettlementState state = engine.getState();
		Map<Activity, Double> carry = Arrays.stream(Activity.values())
			.collect(Collectors.toMap(a -> a, a -> 0.0));
		double hpCarry = 0;
		Result result = new Result();

		for (int minute = 0; minute < MAX_HOURS * 60; minute++)
		{
			Activity activity = activityForGoal(state, nextGoal(state));
			result.activityMinutes.merge(activity, 1, Integer::sum);
			switch (activity)
			{
				case COMBAT:
					hpCarry = drops(engine, Skill.HITPOINTS, 20, 1300, hpCarry);
					break;
				case BOSSING:
					hpCarry = drops(engine, Skill.HITPOINTS, 30, 900, hpCarry);
					double kills = carry.get(activity) + 12 / 60.0;
					for (; kills >= 1; kills--)
					{
						engine.onBossKill("Sim boss", BOSSING_TIER);
					}
					carry.put(activity, kills);
					break;
				case RAIDING:
					double raids = carry.get(activity) + 3 / 60.0;
					for (; raids >= 1; raids--)
					{
						engine.onBossKill("Sim raid", BossTier.TIER_3, true);
					}
					carry.put(activity, raids);
					break;
				case CLUES:
					double clues = carry.get(activity) + 3 / 60.0;
					for (; clues >= 1; clues--)
					{
						engine.onClueCompleted(ClueTier.MEDIUM);
					}
					carry.put(activity, clues);
					break;
				default:
					carry.put(activity, drops(engine, activity.skill, activity.xp, activity.actionsPerHour, carry.get(activity)));
					break;
			}

			for (int t = 0; t < TICKS_PER_MINUTE; t++)
			{
				engine.onTick();
			}
			claimReadyBounties(engine);

			double hours = (minute + 1) / 60.0;
			recordTownHallMilestones(result, state, hours);
			recordTownHallBuildingGate(result, state, hours);
			recordWonderResourceBlocker(result, state);
			while (upgradeOnce(engine))
			{
				result.firstBuildingHours = Math.min(result.firstBuildingHours, hours);
				recordTownHallMilestones(result, state, hours);
			}
			recordTownHallBuildingGate(result, state, hours);
			recordWonderResourceBlocker(result, state);
			if (state.getLevel(Building.TOWN_HALL) >= 2)
			{
				result.townHallTwoHours = Math.min(result.townHallTwoHours, hours);
			}
			if (state.getLevel(Building.TOWN_HALL) >= Balance.WONDER_MIN_BUILDING_LEVEL)
			{
				result.townHallTenHours = Math.min(result.townHallTenHours, hours);
				if (!stopAtWonder)
				{
					break;
				}
			}
			if (state.getExpeditionProgress(ExpeditionId.KELDAGRIM).isClaimed())
			{
				result.expeditionHours = Math.min(result.expeditionHours, hours);
			}
			if (state.getLevel(Building.WONDER) >= 1)
			{
				result.wonderHours = Math.min(result.wonderHours, hours);
				if (stopAtWonder)
				{
					break;
				}
			}
		}
		result.expeditionSummary = ExpeditionCatalog.get(ExpeditionId.KELDAGRIM).getObjectives().stream()
			.map(objective -> objective.getId() + "="
				+ state.getExpeditionProgress(ExpeditionId.KELDAGRIM).getObjectives()
					.getOrDefault(objective.getId(), 0.0) + "/" + objective.getTarget())
			.collect(Collectors.joining(", "));
		UpgradeCheck wonderCheck = BuildingService.check(state, Building.WONDER);
		long claimedExpeditions = ExpeditionCatalog.all().stream()
			.filter(expedition -> state.getExpeditionProgress(expedition.getId()).isClaimed())
			.count();
		result.progressionSummary = String.format("Town Hall %d, Keldagrim Consortium %d, Consortium blueprint %s, expeditions claimed %d, Wonder %s (%s)",
			state.getLevel(Building.TOWN_HALL), state.getLevel(Building.KELDAGRIM_CONSORTIUM),
			state.getBlueprints().contains(Building.KELDAGRIM_CONSORTIUM), claimedExpeditions,
			state.getLevel(Building.WONDER) > 0 ? "BUILT" : wonderCheck.getStatus(),
			state.getLevel(Building.WONDER) > 0 ? "First Wonder complete" : wonderCheck.getReason());
		return result;
	}

	private static void recordTownHallBuildingGate(Result result, SettlementState state, double hours)
	{
		int townHall = state.getLevel(Building.TOWN_HALL);
		if (townHall >= Balance.WONDER_MIN_BUILDING_LEVEL)
		{
			return;
		}
		int current = 0;
		for (Building building : Building.values())
		{
			if (building != Building.TOWN_HALL && !building.isWonder() && state.getLevel(building) >= townHall)
			{
				current++;
			}
		}
		int required = Balance.TOWN_HALL_UPGRADE_BUILDINGS + townHall - 1;
		if (current > result.lastTownHallBuildingCount[townHall])
		{
			result.townHallGateProgress.add(String.format(Locale.ROOT, "TH%d %d/%d at %.1f h",
				townHall, current, required, hours));
			result.lastTownHallBuildingCount[townHall] = current;
		}
		if (current >= required)
		{
			String missing = resourceShortfalls(state, BuildingService.nextCost(state, Building.TOWN_HALL));
			if (!missing.isEmpty())
			{
				result.townHallResourceGaps[townHall] = missing;
			}
		}
	}

	private static void recordWonderResourceBlocker(Result result, SettlementState state)
	{
		if (state.getLevel(Building.WONDER) > 0
			|| state.getLevel(Building.TOWN_HALL) < Building.WONDER.getTier().getRequiredTownHall()
			|| !state.getBlueprints().contains(Building.WONDER) || !allWonderBuildingsReady(state))
		{
			return;
		}
		String missing = resourceShortfalls(state, BuildingService.cost(Building.WONDER, 1));
		if (!missing.isEmpty())
		{
			result.wonderResourceBlocker = missing;
		}
	}

	private static boolean allWonderBuildingsReady(SettlementState state)
	{
		return Arrays.stream(Building.values())
			.filter(building -> building.getType() == BuildingType.STANDARD || building.getType() == BuildingType.SPECIAL)
			.filter(building -> state.getLevel(building) >= SIMULATION_WONDER_BUILDING_LEVEL)
			.count() >= Balance.WONDER_REQUIRED_BUILDING_COUNT;
	}

	private static String resourceShortfalls(SettlementState state, Map<Resource, Integer> cost)
	{
		return cost.entrySet().stream()
			.filter(entry -> state.getStock(entry.getKey()) < entry.getValue())
			.map(entry -> String.format(Locale.ROOT, "%s short %.0f (%.0f/%d)", entry.getKey(),
				entry.getValue() - state.getStock(entry.getKey()),
				state.getStock(entry.getKey()), entry.getValue()))
			.collect(Collectors.joining(", "));
	}

	private static void recordTownHallMilestones(Result result, SettlementState state, double hours)
	{
		int level = state.getLevel(Building.TOWN_HALL);
		for (int reached = 1; reached <= level; reached++)
		{
			if (result.townHallHours[reached] == Double.MAX_VALUE)
			{
				result.townHallHours[reached] = hours;
			}
		}
	}

	private static String formatTownHallMilestones(Result result)
	{
		return java.util.stream.IntStream.rangeClosed(2, Balance.MAX_BUILDING_LEVEL)
			.mapToObj(level -> level + "=" + (result.townHallHours[level] == Double.MAX_VALUE
				? "not reached" : String.format(Locale.ROOT, "%.1f h", result.townHallHours[level])))
			.collect(Collectors.joining(", "));
	}

	private static String formatActivityHours(Result result)
	{
		return Arrays.stream(Activity.values())
			.filter(activity -> result.activityMinutes.getOrDefault(activity, 0) > 0)
			.map(activity -> String.format(Locale.ROOT, "%s=%.2fh", activity,
				result.activityMinutes.get(activity) / 60.0))
			.collect(Collectors.joining(", "));
	}

	private static String formatTownHallResourceGaps(Result result)
	{
		String gaps = java.util.stream.IntStream.range(1, Balance.MAX_BUILDING_LEVEL)
			.filter(level -> result.townHallResourceGaps[level] != null)
			.mapToObj(level -> "TH" + level + ": " + result.townHallResourceGaps[level])
			.collect(Collectors.joining(" | "));
		return gaps.isEmpty() ? "none" : gaps;
	}

	private static double drops(SettlementEngine engine, Skill skill, int xp, int actionsPerHour, double carry)
	{
		double pending = carry + actionsPerHour / 60.0;
		for (; pending >= 1; pending--)
		{
			engine.onXp(skill, xp);
		}
		return pending;
	}

	private static void claimReadyBounties(SettlementEngine engine)
	{
		SettlementState state = engine.getState();
		Building goal = nextGoal(state);
		Bounty needed = needsBlueprint(state, goal) ? nextBlueprintBounty(state) : null;
		List<Integer> ids = new ArrayList<>();
		for (Bounty bounty : engine.getState().getBounties())
		{
			boolean ready = bounty.getType() == BountyType.DELIVER
				? bounty == needed && state.getStock(bounty.getResource()) >= bounty.getTarget()
				: bounty.isComplete();
			if (ready)
			{
				ids.add(bounty.getId());
			}
		}
		ids.forEach(engine::claimBounty);
	}

	private static boolean upgradeOnce(SettlementEngine engine)
	{
		SettlementState state = engine.getState();
		for (ExpeditionCatalog.Definition expedition : ExpeditionCatalog.all())
		{
			if (!state.getExpeditionProgress(expedition.getId()).isClaimed()
				&& engine.isExpeditionComplete(expedition.getId())
				&& engine.claimExpedition(expedition.getId()) != null)
			{
				return true;
			}
		}

		return engine.upgrade(nextGoal(state)).isOk();
	}

	private static Building nextGoal(SettlementState state)
	{
		int townHall = state.getLevel(Building.TOWN_HALL);
		List<Building> core = Arrays.stream(Building.values())
			.filter(building -> building != Building.TOWN_HALL && !building.isWonder() && !building.isOptional())
			.collect(Collectors.toList());
		if (townHall < Balance.WONDER_MIN_BUILDING_LEVEL)
		{
			long ready = Arrays.stream(Building.values())
				.filter(building -> building != Building.TOWN_HALL && !building.isWonder()
					&& state.getLevel(building) >= townHall).count();
			if (ready >= Balance.TOWN_HALL_UPGRADE_BUILDINGS + townHall - 1)
			{
				return Building.TOWN_HALL;
			}
			return core.stream()
				.filter(building -> state.getLevel(building) < townHall && BuildingService.hasTownHallFor(state, building))
				.min(Comparator.<Building>comparingInt(state::getLevel).reversed()
					.thenComparingInt(building -> totalCost(state, building)))
				.orElseThrow(() -> new IllegalStateException("No building can satisfy the Town Hall gate"));
		}
		if (allWonderBuildingsReady(state))
		{
			return Building.WONDER;
		}
		return core.stream()
			.filter(building -> state.getLevel(building) < SIMULATION_WONDER_BUILDING_LEVEL)
			.min(Comparator.<Building>comparingInt(state::getLevel)
				.thenComparingInt(building -> totalCost(state, building)))
			.orElse(Building.WONDER);
	}

	private static Activity activityForGoal(SettlementState state, Building goal)
	{
		if (needsBlueprint(state, goal))
		{
			return activityForBounty(state, nextBlueprintBounty(state));
		}
		for (Map.Entry<Resource, Integer> cost : BuildingService.nextCost(state, goal).entrySet())
		{
			if (state.getStock(cost.getKey()) < cost.getValue())
			{
				return activityForResource(state, cost.getKey());
			}
		}
		return Activity.WOODCUTTING;
	}

	private static boolean needsBlueprint(SettlementState state, Building goal)
	{
		return goal.getTier().isBlueprintRequired() && !state.getBlueprints().contains(goal);
	}

	private static Bounty nextBlueprintBounty(SettlementState state)
	{
		return state.getBounties().stream().filter(Bounty::isBlueprint).findFirst()
			.orElseGet(() -> state.getBounties().stream().findFirst()
				.orElseThrow(() -> new IllegalStateException("No bounty can unlock the required blueprint")));
	}

	private static Activity activityForBounty(SettlementState state, Bounty bounty)
	{
		switch (bounty.getType())
		{
			case SKILL_XP:
				return activityForSkill(bounty.getSkill());
			case KIND_XP:
				return activityForSkill(SkillRules.all().stream()
					.filter(rule -> rule.getKind() == bounty.getKind()).findFirst()
					.orElseThrow(() -> new IllegalStateException("No skill for bounty kind")).getSkill());
			case SLAYER_DROPS:
				return Activity.SLAYER;
			case BOSS_KILLS:
				return Activity.BOSSING;
			case CLUES:
				return Activity.CLUES;
			case DELIVER:
				return activityForResource(state, bounty.getResource());
			default:
				throw new IllegalStateException("Unhandled bounty: " + bounty.getType());
		}
	}

	private static Activity activityForSkill(Skill skill)
	{
		if (skill == Skill.HITPOINTS)
		{
			return Activity.COMBAT;
		}
		return Arrays.stream(Activity.values()).filter(activity -> activity.skill == skill).findFirst()
			.orElseThrow(() -> new IllegalStateException("No activity for skill: " + skill));
	}

	private static Activity activityForResource(SettlementState state, Resource resource)
	{
		switch (resource)
		{
			case GEMS:
				return Activity.MINING;
			case CURIOS:
				return Activity.CLUES;
			case RARE_MONSTER_PARTS:
			case EPIC_MONSTER_PARTS:
				return Activity.BOSSING;
			case ARTIFACT:
				return Activity.RAIDING;
			default:
				SkillRule rule = SkillRules.all().stream()
					.filter(candidate -> candidate.getOutputs().containsKey(resource)).findFirst()
					.orElseThrow(() -> new IllegalStateException("No producer for resource: " + resource));
				if (rule.isProcessing())
				{
					boolean hasInput = rule.getInputs().stream()
						.anyMatch(input -> state.getStock(input) >= Balance.PROCESS_INPUT_PER_UNIT);
					if (!hasInput)
					{
						return activityForResource(state, rule.getInputs().get(0));
					}
				}
				return activityForSkill(rule.getSkill());
		}
	}

	public static class GoalSelection
	{
		@Test
		public void gathersLogsImmediatelyForTownHall()
		{
			SettlementState state = SettlementEngine.newSettlement();
			Arrays.stream(Building.values())
				.filter(building -> building != Building.TOWN_HALL && !building.isWonder() && !building.isOptional()
					&& BuildingService.hasTownHallFor(state, building))
				.limit(Balance.TOWN_HALL_UPGRADE_BUILDINGS).forEach(building -> state.setLevel(building, 1));
			state.addStock(Resource.LOGS, -state.getStock(Resource.LOGS));
			assertEquals(Building.TOWN_HALL, nextGoal(state));
			assertEquals(Activity.WOODCUTTING, activityForGoal(state, Building.TOWN_HALL));
		}

		@Test
		public void pursuesBuildingPrerequisitesAndProcessingInputs()
		{
			SettlementState state = new SettlementState();
			state.setLevel(Building.TOWN_HALL, 4);
			state.setLevel(Building.SAWMILL, 3);
			state.getBlueprints().add(Building.SAWMILL);
			assertEquals(Building.SAWMILL, nextGoal(state));
			assertEquals(Activity.WOODCUTTING, activityForResource(state, Resource.PLANKS));
			state.addStock(Resource.LOGS, 100);
			assertEquals(Activity.CONSTRUCTION, activityForResource(state, Resource.PLANKS));
			assertEquals(Activity.MINING, activityForResource(new SettlementState(), Resource.ENCHANTMENTS));
		}

		@Test
		public void targetsBossingForEpicParts()
		{
			assertEquals(Activity.BOSSING, activityForResource(new SettlementState(), Resource.EPIC_MONSTER_PARTS));
			assertEquals(BossTier.TIER_2, BOSSING_TIER);
			assertEquals(Activity.RAIDING, activityForResource(new SettlementState(), Resource.ARTIFACT));
			assertEquals(Activity.CLUES, activityForResource(new SettlementState(), Resource.CURIOS));
		}

		@Test
		public void completesBlueprintBountiesAndOpensSlotsWhenNeeded()
		{
			SettlementState state = new SettlementState();
			Bounty bounty = new Bounty();
			bounty.setType(BountyType.SKILL_XP);
			bounty.setSkill(Skill.AGILITY);
			state.getBounties().add(bounty);
			assertEquals(Activity.AGILITY, activityForGoal(state, Building.SAWMILL));
			Bounty blueprint = new Bounty();
			blueprint.setType(BountyType.BOSS_KILLS);
			blueprint.setReward(BountyReward.blueprint(Building.SAWMILL));
			state.getBounties().add(blueprint);
			assertEquals(Activity.BOSSING, activityForGoal(state, Building.SAWMILL));
		}

		@Test
		public void doesNotSpendGoalResourcesOnUnrelatedUpgradesOrDeliveries()
		{
			SettlementEngine engine = new SettlementEngine(SettlementEngine.newSettlement(), new Random(1));
			SettlementState state = engine.getState();
			Arrays.stream(Building.values())
				.filter(building -> building != Building.TOWN_HALL && !building.isWonder() && !building.isOptional()
					&& BuildingService.hasTownHallFor(state, building))
				.limit(Balance.TOWN_HALL_UPGRADE_BUILDINGS).forEach(building -> state.setLevel(building, 1));
			for (Resource resource : Resource.values())
			{
				state.addStock(resource, 1000000);
			}
			state.addStock(Resource.LOGS, -state.getStock(Resource.LOGS));
			Bounty delivery = new Bounty();
			delivery.setType(BountyType.DELIVER);
			delivery.setResource(Resource.STONE);
			delivery.setTarget(1);
			state.getBounties().clear();
			state.getBounties().add(delivery);
			double stone = state.getStock(Resource.STONE);
			assertTrue(!upgradeOnce(engine));
			claimReadyBounties(engine);
			assertEquals(stone, state.getStock(Resource.STONE), 0.0);
			assertEquals(1, state.getLevel(Building.TOWN_HALL));
			assertTrue(state.getBounties().contains(delivery));
		}

		@Test
		public void requiresConfiguredStandardOrSpecialBuildingCountAtWonderLevel()
		{
			SettlementState state = new SettlementState();
			state.setLevel(Building.TOWN_HALL, Balance.WONDER_MIN_BUILDING_LEVEL);
			long specialCount = Arrays.stream(Building.values())
				.filter(building -> building.getType() == BuildingType.SPECIAL)
				.count();
			Arrays.stream(Building.values())
				.filter(building -> building.getType() == BuildingType.SPECIAL)
				.forEach(building -> state.setLevel(building, SIMULATION_WONDER_BUILDING_LEVEL));
			Arrays.stream(Building.values())
				.filter(building -> building.getType() == BuildingType.STANDARD && building != Building.TOWN_HALL)
				.limit(Balance.WONDER_REQUIRED_BUILDING_COUNT - 1 - specialCount)
				.forEach(building -> state.setLevel(building, SIMULATION_WONDER_BUILDING_LEVEL));
			assertEquals(Building.WONDER, nextGoal(state));

			state.setLevel(Building.TOWER_OF_VOICES, SIMULATION_WONDER_BUILDING_LEVEL - 1);
			assertTrue(nextGoal(state) != Building.WONDER);
			state.setLevel(Building.TOWER_OF_VOICES, SIMULATION_WONDER_BUILDING_LEVEL);
			assertEquals(Building.WONDER, nextGoal(state));
		}
	}

	private static int totalCost(SettlementState state, Building building)
	{
		return BuildingService.nextCost(state, building).values().stream().mapToInt(Integer::intValue).sum();
	}
}
