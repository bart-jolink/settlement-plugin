package com.osrssettlement.engine;

import lombok.Value;

@Value
public class Requirement
{
	UpgradeCheck.Status status;
	String description;
	boolean met;
}
