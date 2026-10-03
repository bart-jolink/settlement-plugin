package com.osrssettlement.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SkillKind
{
	GATHERING("gathering"),
	PROCESSING("processing"),
	COMBAT("Hitpoints"),
	SLAYER("slayer");

	private final String displayName;
}
