package com.osrssettlement.engine;

import com.osrssettlement.model.BossTier;
import com.osrssettlement.model.Resource;
import java.util.Map;

/**
 * Every tuning number of the settlement economy. SettlementSimulationTest checks progression
 * against the configured first-Wonder target and acceptance factors.
 */
public final class Balance
{
	public static final int MAX_BUILDING_LEVEL = 10;
	public static final double COST_SCALE = 1;
	public static final double COST_GROWTH = 1.55;
	public static final int TOWN_HALL_UPGRADE_BUILDINGS = 3;
	public static final int WONDER_MIN_BUILDING_LEVEL = 10;
	public static final int WONDER_REQUIRED_BUILDING_COUNT = 20;
	public static final int FIRST_WONDER_TARGET_HOURS = 100;
	public static final double FIRST_WONDER_MAX_TARGET_FACTOR = 2;

	public static final double GATHER_BONUS_PER_LEVEL = 0.2;
	public static final double PROCESS_BONUS_PER_LEVEL = 0.1;
	public static final double TOWN_HALL_GLOBAL_BONUS = 0.02;
	public static final double WONDER_GLOBAL_BONUS = 0.1;
	public static final double ALTAR_GLOBAL_BONUS = 0.03;
	public static final double TROPHY_BONUS_PER_LEVEL = 0.15;
	public static final double TREASURY_BONUS_PER_LEVEL = 0.15;
	public static final double MUSEUM_BOUNTY_BONUS_PER_LEVEL = 0.10;
	public static final double LIBRARY_BOOST_DURATION_PER_LEVEL = 0.20;
	public static final double PYRAMID_EVENT_BONUS_PER_LEVEL = 0.10;
	public static final double TOWER_OF_VOICES_GLOBAL_BONUS = 0.05;

	public static final double PROCESS_INPUT_PER_UNIT = 0.5;

	public static final int GEM_MIN_BUILDING_LEVEL = 3;
	public static final double QUARRY_GEM_RATE = 0.05;
	public static final double THIEVES_GUILD_GEM_RATE = 0.1;
	public static final int KELDAGRIM_CONSORTIUM_INPUT_AMOUNT = 500;
	public static final int KELDAGRIM_CONSORTIUM_PROCESSED_INPUT_AMOUNT = 300;
	public static final int KELDAGRIM_CONSORTIUM_OUTPUT_AMOUNT = 100;
	public static final double KELDAGRIM_CONSORTIUM_OUTPUT_BONUS_PER_LEVEL = 0.1;
	public static final Map<BossTier, Map<Resource, Double>> BOSS_PART_DROP_CHANCES = Map.of(
		BossTier.TIER_1, Map.of(Resource.MONSTER_PARTS, 0.20, Resource.RARE_MONSTER_PARTS, 0.05, Resource.EPIC_MONSTER_PARTS, 0.01),
		BossTier.TIER_2, Map.of(Resource.MONSTER_PARTS, 0.35, Resource.RARE_MONSTER_PARTS, 0.1, Resource.EPIC_MONSTER_PARTS, 0.02),
		BossTier.TIER_3, Map.of(Resource.MONSTER_PARTS, 0.50, Resource.RARE_MONSTER_PARTS, 0.2, Resource.EPIC_MONSTER_PARTS, 0.05));
	public static final Map<BossTier, Integer> BOSS_PARTS_PER_DROP = Map.of(
		BossTier.TIER_1, 100,
		BossTier.TIER_2, 200,
		BossTier.TIER_3, 400);
	public static final int RAID_ARTIFACTS_TIER_2 = 1;
	public static final int RAID_ARTIFACTS_TIER_3 = 2;

	public static final int BOUNTY_SLOTS = 3;
	public static final int BOUNTY_SUPPLY_WEIGHT = 4;
	public static final int BOUNTY_BOOST_WEIGHT = 3;
	public static final int BOUNTY_PARTS_WEIGHT = 2;
	public static final double BOUNTY_COMMON_WEIGHT = 0.70;
	public static final double BOUNTY_RARE_WEIGHT = 0.20;
	public static final double BOUNTY_EPIC_WEIGHT = 0.05;
	public static final double BOUNTY_RARE_DEMAND_SCALE = 1.5;
	public static final double BOUNTY_EPIC_DEMAND_SCALE = 2.5;
	public static final double BOUNTY_RARE_YIELD_SCALE = 2.0;
	public static final double BOUNTY_EPIC_YIELD_SCALE = 4.0;
	// 3 hours of logged-in play; keeps a board of unreachable tasks from locking progress forever
	public static final int BOUNTY_EXPIRY_TICKS = 18_000;
	public static final double BOUNTY_SCALE_PER_TOWN_HALL = 0.3;
	public static final int BOUNTY_RARE_PARTS_TOWN_HALL = 3;
	public static final int BOUNTY_EPIC_PARTS_TOWN_HALL = 5;
	public static final int BOUNTY_ARTIFACT_TOWN_HALL = 6;
	// monster part rewards roll between a tier 1 and a tier 3 boss kill's expected parts
	public static final Map<Resource, Integer> BOUNTY_PARTS_MIN = Map.of(
		Resource.MONSTER_PARTS, 200, Resource.RARE_MONSTER_PARTS, 25, Resource.EPIC_MONSTER_PARTS, 10);
	public static final Map<Resource, Integer> BOUNTY_PARTS_MAX = Map.of(
		Resource.MONSTER_PARTS, 500, Resource.RARE_MONSTER_PARTS, 100, Resource.EPIC_MONSTER_PARTS, 50);
	public static final double BOUNTY_ARTIFACT_CHANCE = 0.02;
	public static final double CLUE_PART_CHANCE = 0.10;
	public static final int BOUNTY_SKILL_XP = 10_000;
	public static final int BOUNTY_GATHERING_XP = 15_000;
	public static final int BOUNTY_PROCESSING_XP = 15_000;
	public static final int BOUNTY_COMBAT_XP = 10_000;
	public static final int BOUNTY_SLAYER_DROPS = 200;
	public static final int BOUNTY_DELIVER = 150;
	public static final int BOUNTY_REWARD_VALUE = 400;
	public static final int BOUNTY_DELIVERY_REWARD_VALUE = 600;
	// lets accounts that never do Slayer still raise the Slayer Tower
	public static final double BOUNTY_MONSTER_PARTS_CHANCE = 0.1;
	public static final double BOOST_MULTIPLIER = 2.0;
	public static final int BOOST_TICKS = 3000;

	// 2 hours of logged-in play, and a new one every hour, so two usually overlap
	public static final int EVENT_DURATION_TICKS = 12_000;
	public static final int EVENT_INTERVAL_TICKS = 6_000;
	public static final double EVENT_BUFF_CHANCE = 0.7;

	public static final int LOG_SIZE = 50;

	public static final Map<Resource, Integer> STARTER_KIT = Map.of(
		Resource.LOGS, 100,
		Resource.STONE, 60,
		Resource.ORE, 40
	);

	private Balance()
	{
	}
}
