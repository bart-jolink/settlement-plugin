package com.osrssettlement.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BountyReward
{
	private RewardType type;
	private Map<Resource, Integer> resources = new EnumMap<>(Resource.class);
	private Resource boostResource;
	private double boostMultiplier;
	private int boostTicks;
	private Building blueprint;

	public static BountyReward resources(Map<Resource, Integer> resources)
	{
		BountyReward reward = new BountyReward();
		reward.type = RewardType.RESOURCES;
		reward.resources = new EnumMap<>(resources);
		return reward;
	}

	public static BountyReward boost(Resource resource, double multiplier, int ticks)
	{
		BountyReward reward = new BountyReward();
		reward.type = RewardType.BOOST;
		reward.boostResource = resource;
		reward.boostMultiplier = multiplier;
		reward.boostTicks = ticks;
		return reward;
	}

	public static BountyReward blueprint(Building building)
	{
		BountyReward reward = new BountyReward();
		reward.type = RewardType.BLUEPRINT;
		reward.blueprint = building;
		return reward;
	}

	public String describe()
	{
		switch (type)
		{
			case BLUEPRINT:
				return "Blueprint: " + blueprint.getDisplayName();
			case BOOST:
				return String.format("%sx %s for %d min", formatMultiplier(boostMultiplier),
					boostResource.getDisplayName(), Math.round(boostTicks * 0.6 / 60));
			case RESOURCES:
			default:
				List<String> parts = new ArrayList<>();
				resources.forEach((resource, amount) -> parts.add(String.format("%,d %s", amount, resource.getDisplayName())));
				return String.join(", ", parts);
		}
	}

	private static String formatMultiplier(double multiplier)
	{
		return multiplier == Math.rint(multiplier) ? String.valueOf((long) multiplier) : String.valueOf(multiplier);
	}
}
