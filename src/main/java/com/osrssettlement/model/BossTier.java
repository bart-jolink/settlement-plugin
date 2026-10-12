package com.osrssettlement.model;

import lombok.Getter;

@Getter
public enum BossTier
{
	TIER_1("Tier 1"),
	TIER_2("Tier 2"),
	TIER_3("Tier 3"),
	TIER_4("Tier 4");

	private final String displayName;

	BossTier(String displayName)
	{
		this.displayName = displayName;
	}
}
