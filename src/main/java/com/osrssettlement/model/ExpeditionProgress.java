package com.osrssettlement.model;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExpeditionProgress
{
	private Map<String, Double> objectives = new HashMap<>();
	private boolean claimed;

}