package com.osrssettlement.engine;

import com.osrssettlement.model.Building;
import com.osrssettlement.model.BuildingType;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class BuildingService
{
	private BuildingService()
	{
	}

	/**
	 * @param level the level being built, starting at 1
	 */
	public static Map<Resource, Integer> cost(Building building, int level)
	{
		Map<Resource, Integer> cost = new EnumMap<>(Resource.class);
		building.getBaseCost().forEach((resource, base) ->
		{
			double amount = base * Balance.COST_SCALE * Math.pow(Balance.COST_GROWTH, level - 1);
			cost.put(resource, (int) Math.ceil(amount));
		});
		return Collections.unmodifiableMap(cost);
	}

	public static Map<Resource, Integer> nextCost(SettlementState state, Building building)
	{
		return cost(building, state.getLevel(building) + 1);
	}

	public static int maxLevel(Building building)
	{
		return Balance.MAX_BUILDING_LEVEL;
	}

	public static boolean hasTownHallFor(SettlementState state, Building building)
	{
		return state.getLevel(Building.TOWN_HALL) >= building.getTier().getRequiredTownHall();
	}

	/**
	 * A blueprint can be offered on the bounty board once the Town Hall is high enough and it is not owned yet.
	 */
	public static boolean isBlueprintAvailable(SettlementState state, Building building)
	{
		return ExpeditionCatalog.sourceOf(building) == null
			&& building.getTier().isBlueprintRequired()
			&& !state.getBlueprints().contains(building)
			&& hasTownHallFor(state, building);
	}

	/**
	 * Every non-resource requirement for the next level, in the order they are checked.
	 */
	public static List<Requirement> requirements(SettlementState state, Building building)
	{
		List<Requirement> requirements = new ArrayList<>();
		int level = state.getLevel(building);
		if (level >= maxLevel(building))
		{
			return requirements;
		}

		int townHall = state.getLevel(Building.TOWN_HALL);
		if (building != Building.TOWN_HALL && !building.isWonder())
		{
			int requiredTownHall = building.getTier().getRequiredTownHall();
			if (!building.isWonder())
			{
				requiredTownHall = Math.max(requiredTownHall, level + 1);
			}
			requirements.add(new Requirement(UpgradeCheck.Status.NEEDS_TOWN_HALL,
				"Town Hall level " + requiredTownHall, townHall >= requiredTownHall));
		}

		boolean ownsBlueprint = state.getBlueprints().contains(building);
		if (building.getTier().isBlueprintRequired() && (level == 0 || !ownsBlueprint))
		{
			ExpeditionCatalog.Definition expedition = ExpeditionCatalog.sourceOf(building);
			String source = expedition != null ? "expedition" : "bounty board";
			requirements.add(new Requirement(UpgradeCheck.Status.NEEDS_BLUEPRINT, "Blueprint (" + source + ")", ownsBlueprint));
		}

		if (building == Building.TOWN_HALL)
		{
			int required = Balance.TOWN_HALL_UPGRADE_BUILDINGS + level - 1;
			int current = countAtLevel(state, level);
			requirements.add(new Requirement(UpgradeCheck.Status.NEEDS_BUILDINGS,
				"Buildings at level " + level + " (" + current + "/" + required + ")",
				current >= required));
		}
		else if (building.isWonder() && level == 0)
		{
			int requiredLevel = Balance.WONDER_MIN_BUILDING_LEVEL;
			int current = countWonderEligibleBuildingsAtLevel(state, requiredLevel);
			int required = Balance.WONDER_REQUIRED_BUILDING_COUNT;
			requirements.add(new Requirement(UpgradeCheck.Status.NEEDS_BUILDINGS,
				"Buildings at level " + requiredLevel + " (" + current + "/" + required + ")",
				current >= required));
		}
		return requirements;
	}

	public static UpgradeCheck check(SettlementState state, Building building)
	{
		int level = state.getLevel(building);
		if (level >= maxLevel(building))
		{
			return UpgradeCheck.fail(UpgradeCheck.Status.MAX_LEVEL, "Maximum level reached");
		}

		for (Requirement requirement : requirements(state, building))
		{
			if (!requirement.isMet())
			{
				return UpgradeCheck.fail(requirement.getStatus(), "Requires: " + requirement.getDescription());
			}
		}

		if (!canAfford(state, cost(building, level + 1)))
		{
			return UpgradeCheck.fail(UpgradeCheck.Status.NEEDS_RESOURCES, "Not enough resources");
		}

		return UpgradeCheck.ok();
	}

	/**
	 * Pays for and applies the next level if allowed.
	 */
	static UpgradeCheck upgrade(SettlementState state, Building building)
	{
		UpgradeCheck check = check(state, building);
		if (!check.isOk())
		{
			return check;
		}

		int next = state.getLevel(building) + 1;
		cost(building, next).forEach((resource, amount) -> state.addStock(resource, -amount));
		state.setLevel(building, next);
		return check;
	}

	public static boolean canAfford(SettlementState state, Map<Resource, Integer> cost)
	{
		for (Map.Entry<Resource, Integer> entry : cost.entrySet())
		{
			if (state.getStock(entry.getKey()) < entry.getValue())
			{
				return false;
			}
		}
		return true;
	}

	private static int countAtLevel(SettlementState state, int level)
	{
		int count = 0;
		for (Building building : Building.values())
		{
			if (building != Building.TOWN_HALL && !building.isWonder() && state.getLevel(building) >= level)
			{
				count++;
			}
		}
		return count;
	}

	private static int countWonderEligibleBuildingsAtLevel(SettlementState state, int level)
	{
		int count = 0;
		for (Building building : Building.values())
		{
			if ((building.getType() == BuildingType.STANDARD || building.getType() == BuildingType.SPECIAL)
				&& state.getLevel(building) >= level)
			{
				count++;
			}
		}
		return count;
	}
}
