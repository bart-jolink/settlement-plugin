package com.osrssettlement.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Boost
{
	private Resource resource;
	private double multiplier;
	private int remainingTicks;
}
