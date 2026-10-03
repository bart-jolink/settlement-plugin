package com.osrssettlement.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ResourceCategory
{
	RAW("Raw materials"),
	REFINED("Refined goods"),
	SPECIAL("Special goods"),
	SPOILS("Spoils");

	private final String displayName;
}
