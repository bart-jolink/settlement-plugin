package com.osrssettlement.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuildingTier
{
	A(1, false),
	B(3, true),
	C(4, true),
	D(5, true),
	E(6, true),
	F(8, true),
	G(10, true),
	WONDER(10, true);

	private final int requiredTownHall;
	private final boolean blueprintRequired;
}
