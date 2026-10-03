package com.osrssettlement.model;

public enum BountyRarity
{
	COMMON("Common"),
	RARE("Rare"),
	EPIC("Epic");

	private final String displayName;

	BountyRarity(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}
}