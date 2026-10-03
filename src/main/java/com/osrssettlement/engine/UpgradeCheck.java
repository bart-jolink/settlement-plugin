package com.osrssettlement.engine;

import lombok.Value;

@Value
public class UpgradeCheck
{
	public enum Status
	{
		OK,
		MAX_LEVEL,
		NEEDS_TOWN_HALL,
		NEEDS_BLUEPRINT,
		NEEDS_BUILDINGS,
		NEEDS_RESOURCES
	}

	Status status;
	String reason;

	public boolean isOk()
	{
		return status == Status.OK;
	}

	static UpgradeCheck ok()
	{
		return new UpgradeCheck(Status.OK, "");
	}

	static UpgradeCheck fail(Status status, String reason)
	{
		return new UpgradeCheck(status, reason);
	}
}
