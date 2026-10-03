package com.osrssettlement.engine;

import com.osrssettlement.model.Bounty;
import com.osrssettlement.model.BountyReward;
import com.osrssettlement.model.BountyRarity;
import com.osrssettlement.model.BountyType;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.ResourceCategory;
import com.osrssettlement.model.RewardType;
import com.osrssettlement.model.SettlementState;
import com.osrssettlement.model.SkillKind;
import com.osrssettlement.model.SkillRule;
import com.osrssettlement.model.SkillRules;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Generates bounties for empty board slots. Blueprints that became available always take the
 * next free slot; everything else is drawn from a generic pool any account can work towards.
 */
public class BountyBoard
{
	private static final Map<BountyType, Integer> OBJECTIVE_WEIGHTS = Map.of(
		BountyType.SKILL_XP, 4,
		BountyType.KIND_XP, 3,
		BountyType.SLAYER_DROPS, 2,
		BountyType.BOSS_KILLS, 1,
		BountyType.CLUES, 1,
		BountyType.DELIVER, 2
	);
	private static final SkillKind[] XP_KINDS = {SkillKind.GATHERING, SkillKind.PROCESSING, SkillKind.COMBAT};

	private final Random random;

	public BountyBoard(Random random)
	{
		this.random = random;
	}

	public void fill(SettlementState state)
	{
		while (state.getBounties().size() < Balance.BOUNTY_SLOTS)
		{
			state.getBounties().add(generate(state));
		}
	}

	Bounty generate(SettlementState state)
	{
		int townHall = Math.max(1, state.getLevel(Building.TOWN_HALL));
		double scale = 1 + Balance.BOUNTY_SCALE_PER_TOWN_HALL * (townHall - 1);

		Building blueprint = nextBlueprint(state, random);
		BountyRarity rarity = blueprint == null ? randomRarity() : BountyRarity.COMMON;
		Bounty bounty = randomObjective(state, townHall, scale);
		bounty.setRarity(rarity);
		bounty.setId(state.getNextBountyId());
		bounty.setExpiresAtPlaytime(state.getPlaytimeTicks() + Balance.BOUNTY_EXPIRY_TICKS);
		state.setNextBountyId(state.getNextBountyId() + 1);

		bounty.setReward(blueprint != null ? BountyReward.blueprint(blueprint) : randomReward(state, bounty, townHall, scale));
		scaleForRarity(bounty);
		return bounty;
	}

	private BountyRarity randomRarity()
	{
		double totalWeight = Balance.BOUNTY_COMMON_WEIGHT + Balance.BOUNTY_RARE_WEIGHT + Balance.BOUNTY_EPIC_WEIGHT;
		double roll = random.nextDouble() * totalWeight;
		if (roll < Balance.BOUNTY_COMMON_WEIGHT)
		{
			return BountyRarity.COMMON;
		}
		if (roll < Balance.BOUNTY_COMMON_WEIGHT + Balance.BOUNTY_RARE_WEIGHT)
		{
			return BountyRarity.RARE;
		}
		return BountyRarity.EPIC;
	}

	static void scaleForRarity(Bounty bounty)
	{
		if (bounty.getRarity() == null || bounty.getRarity() == BountyRarity.COMMON)
		{
			return;
		}

		double demandScale;
		double yieldScale;
		if (bounty.getRarity() == BountyRarity.RARE)
		{
			demandScale = Balance.BOUNTY_RARE_DEMAND_SCALE;
			yieldScale = Balance.BOUNTY_RARE_YIELD_SCALE;
		}
		else
		{
			demandScale = Balance.BOUNTY_EPIC_DEMAND_SCALE;
			yieldScale = Balance.BOUNTY_EPIC_YIELD_SCALE;
		}

		bounty.setTarget(roundNice(bounty.getTarget() * demandScale));
		BountyReward reward = bounty.getReward();
		if (reward.getType() == RewardType.RESOURCES)
		{
			reward.getResources().replaceAll((resource, amount) -> (int) Math.min(Integer.MAX_VALUE,
				roundNice(amount * yieldScale)));
		}
		else if (reward.getType() == RewardType.BOOST)
		{
			reward.setBoostTicks((int) Math.min(Integer.MAX_VALUE,
				Math.round(reward.getBoostTicks() * yieldScale)));
		}
	}

	static Building nextBlueprint(SettlementState state, Random random)
	{
		if (state.getBounties().stream().anyMatch(Bounty::isBlueprint))
		{
			return null;
		}

		List<Building> eligible = new ArrayList<>();
		for (Building building : Building.values())
		{
			if (BuildingService.isBlueprintAvailable(state, building))
			{
				eligible.add(building);
			}
		}
		return eligible.isEmpty() ? null : eligible.get(random.nextInt(eligible.size()));
	}

	private Bounty randomObjective(SettlementState state, int townHall, double scale)
	{
		Set<BountyType> taken = EnumSet.noneOf(BountyType.class);
		state.getBounties().forEach(b -> taken.add(b.getType()));

		Map<BountyType, Integer> weights = new EnumMap<>(OBJECTIVE_WEIGHTS);
		if (!weights.keySet().stream().allMatch(taken::contains))
		{
			weights.keySet().removeAll(taken);
		}

		Bounty bounty = new Bounty();
		BountyType type = pickWeighted(weights);
		bounty.setType(type);

		switch (type)
		{
			case SKILL_XP:
				bounty.setSkill(pick(ruledSkills()));
				bounty.setTarget(roundNice(Balance.BOUNTY_SKILL_XP * scale));
				break;
			case KIND_XP:
				SkillKind kind = XP_KINDS[random.nextInt(XP_KINDS.length)];
				bounty.setKind(kind);
				bounty.setTarget(roundNice(kindTarget(kind) * scale));
				break;
			case SLAYER_DROPS:
				bounty.setTarget(roundNice(Balance.BOUNTY_SLAYER_DROPS * scale));
				break;
			case BOSS_KILLS:
				bounty.setTarget(1 + townHall / 3);
				break;
			case CLUES:
				bounty.setTarget(1 + townHall / 4);
				break;
			case DELIVER:
				bounty.setResource(pick(commonResources()));
				bounty.setTarget(roundNice(Balance.BOUNTY_DELIVER * scale));
				break;
			default:
				throw new IllegalStateException("Unhandled bounty type " + type);
		}
		return bounty;
	}

	private BountyReward randomReward(SettlementState state, Bounty bounty, int townHall, double scale)
	{
		int roll = random.nextInt(Balance.BOUNTY_SUPPLY_WEIGHT + Balance.BOUNTY_BOOST_WEIGHT + Balance.BOUNTY_PARTS_WEIGHT);
		if (roll < Balance.BOUNTY_SUPPLY_WEIGHT)
		{
			int budget = bounty.getType() == BountyType.DELIVER
				? Balance.BOUNTY_DELIVERY_REWARD_VALUE : Balance.BOUNTY_REWARD_VALUE;
			double value = budget * scale * YieldCalculator.museumMultiplier(state);
			return resourceReward(bounty.getResource(), value);
		}
		if (roll < Balance.BOUNTY_SUPPLY_WEIGHT + Balance.BOUNTY_BOOST_WEIGHT)
		{
			return BountyReward.boost(pick(boostableResources()), Balance.BOOST_MULTIPLIER, Balance.BOOST_TICKS);
		}
		return monsterPartsReward(townHall);
	}

	private BountyReward resourceReward(Resource exclude, double value)
	{
		List<Resource> pool = commonResources();
		pool.remove(exclude);

		int count = 2 + random.nextInt(2);
		int amount = (int) roundNice(value / count);
		Map<Resource, Integer> resources = new EnumMap<>(Resource.class);
		if (random.nextDouble() < Balance.BOUNTY_MONSTER_PARTS_CHANCE)
		{
			resources.put(Resource.MONSTER_PARTS, amount);
		}
		while (resources.size() < count)
		{
			Resource resource = pool.remove(random.nextInt(pool.size()));
			resources.put(resource, amount);
		}
		return BountyReward.resources(resources);
	}

	private BountyReward monsterPartsReward(int townHall)
	{
		double size = random.nextDouble();
		Map<Resource, Integer> resources = new EnumMap<>(Resource.class);
		resources.put(Resource.MONSTER_PARTS, bountyParts(Resource.MONSTER_PARTS, size));
		if (townHall >= Balance.BOUNTY_RARE_PARTS_TOWN_HALL)
		{
			resources.put(Resource.RARE_MONSTER_PARTS, bountyParts(Resource.RARE_MONSTER_PARTS, size));
		}
		if (townHall >= Balance.BOUNTY_EPIC_PARTS_TOWN_HALL)
		{
			resources.put(Resource.EPIC_MONSTER_PARTS, bountyParts(Resource.EPIC_MONSTER_PARTS, size));
		}
		if (townHall >= Balance.BOUNTY_ARTIFACT_TOWN_HALL && random.nextDouble() < Balance.BOUNTY_ARTIFACT_CHANCE)
		{
			resources.put(Resource.ARTIFACT, 1);
		}
		return BountyReward.resources(resources);
	}

	private static int bountyParts(Resource resource, double size)
	{
		int min = Balance.BOUNTY_PARTS_MIN.get(resource);
		int max = Balance.BOUNTY_PARTS_MAX.get(resource);
		return (int) roundNice(min + (max - min) * size);
	}

	private static int kindTarget(SkillKind kind)
	{
		switch (kind)
		{
			case GATHERING:
				return Balance.BOUNTY_GATHERING_XP;
			case PROCESSING:
				return Balance.BOUNTY_PROCESSING_XP;
			default:
				return Balance.BOUNTY_COMBAT_XP;
		}
	}

	private static List<Skill> ruledSkills()
	{
		List<Skill> skills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			if (SkillRules.get(skill) != null)
			{
				skills.add(skill);
			}
		}
		return skills;
	}

	private static List<Resource> commonResources()
	{
		List<Resource> resources = new ArrayList<>();
		for (Resource resource : Resource.values())
		{
			if (resource.getCategory() == ResourceCategory.RAW || resource.getCategory() == ResourceCategory.REFINED)
			{
				resources.add(resource);
			}
		}
		return resources;
	}

	private static List<Resource> boostableResources()
	{
		Set<Resource> resources = EnumSet.noneOf(Resource.class);
		for (SkillRule rule : SkillRules.all())
		{
			resources.addAll(rule.getOutputs().keySet());
		}
		return new ArrayList<>(resources);
	}

	private <T> T pick(List<T> options)
	{
		return options.get(random.nextInt(options.size()));
	}

	private BountyType pickWeighted(Map<BountyType, Integer> weights)
	{
		int total = weights.values().stream().mapToInt(Integer::intValue).sum();
		int roll = random.nextInt(total);
		for (Map.Entry<BountyType, Integer> entry : weights.entrySet())
		{
			roll -= entry.getValue();
			if (roll < 0)
			{
				return entry.getKey();
			}
		}
		throw new IllegalStateException("Weights exhausted");
	}

	/**
	 * Rounds to two significant digits so targets read nicely (e.g. 13,500 -> 14,000).
	 */
	static long roundNice(double value)
	{
		if (value < 10)
		{
			return Math.max(1, Math.round(value));
		}
		double step = Math.pow(10, Math.floor(Math.log10(value)) - 1);
		return (long) (Math.round(value / step) * step);
	}
}
