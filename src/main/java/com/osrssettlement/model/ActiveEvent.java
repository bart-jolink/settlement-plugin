package com.osrssettlement.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveEvent
{
	private SettlementEvent event;
	private long endsAtPlaytime;
}
