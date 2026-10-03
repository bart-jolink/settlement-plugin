package com.osrssettlement.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;

/**
 * Clue rewards. On average an Elite clue gives about what a tier 1 boss kill does (20 Basic, 5 Rare, 1 Epic).
 */
@Getter
public enum ClueTier
{
	BEGINNER("Beginner", Map.of(Resource.CURIOS, 20, Resource.COINS, 60)),
	EASY("Easy", Map.of(Resource.CURIOS, 30, Resource.COINS, 120, Resource.MONSTER_PARTS, 2)),
	MEDIUM("Medium", Map.of(Resource.CURIOS, 50, Resource.COINS, 200, Resource.MONSTER_PARTS, 5)),
	HARD("Hard", Map.of(Resource.CURIOS, 80, Resource.COINS, 350, Resource.MONSTER_PARTS, 10),
		Resource.RARE_MONSTER_PARTS, 20),
	ELITE("Elite", Map.of(Resource.CURIOS, 120, Resource.COINS, 600, Resource.MONSTER_PARTS, 20,
		Resource.RARE_MONSTER_PARTS, 5), Resource.EPIC_MONSTER_PARTS, 10),
	MASTER("Master", Map.of(Resource.CURIOS, 200, Resource.COINS, 1000, Resource.MONSTER_PARTS, 40,
		Resource.RARE_MONSTER_PARTS, 12, Resource.EPIC_MONSTER_PARTS, 2), Resource.EPIC_MONSTER_PARTS, 20);

	private final String displayName;
	private final Map<Resource, Integer> reward;
	/** Awarded in full on a successful bonus roll. */
	private final Resource bonusPart;
	private final int bonusAmount;

	ClueTier(String displayName, Map<Resource, Integer> reward)
	{
		this(displayName, reward, null, 0);
	}

	ClueTier(String displayName, Map<Resource, Integer> reward, Resource bonusPart, int bonusAmount)
	{
		this.displayName = displayName;
		this.reward = Collections.unmodifiableMap(new EnumMap<>(reward));
		this.bonusPart = bonusPart;
		this.bonusAmount = bonusAmount;
	}

	/**
	 * @return the tier matching an in-game tier name such as "medium", or null if unknown
	 */
	public static ClueTier fromName(String name)
	{
		for (ClueTier tier : values())
		{
			if (tier.displayName.equalsIgnoreCase(name))
			{
				return tier;
			}
		}
		return null;
	}
}
