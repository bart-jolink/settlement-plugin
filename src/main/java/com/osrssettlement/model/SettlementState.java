package com.osrssettlement.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.api.Skill;

/**
 * Everything persisted for one account's settlement. Stock is stored as doubles so that
 * fractional yields accumulate; the UI shows the floored value.
 */
@Data
@NoArgsConstructor
public class SettlementState
{
	public static final int SCHEMA_VERSION = 6;

	private int schemaVersion = SCHEMA_VERSION;
	private Map<Resource, Double> stock = new EnumMap<>(Resource.class);
	private Map<Building, Integer> levels = new EnumMap<>(Building.class);
	private Set<Building> blueprints = EnumSet.noneOf(Building.class);
	private Map<Skill, Double> labour = new EnumMap<>(Skill.class);
	private boolean labourPaused;
	private List<Boost> boosts = new ArrayList<>();
	private List<Bounty> bounties = new ArrayList<>();
	private Map<ExpeditionId, ExpeditionProgress> expeditions = new EnumMap<>(ExpeditionId.class);
	private List<LogEntry> log = new ArrayList<>();
	private long playtimeTicks;
	private int nextBountyId = 1;
	private int bountiesCompleted;
	private List<ActiveEvent> events = new ArrayList<>();
	private long nextEventAtPlaytime;

	public double getStock(Resource resource)
	{
		return stock.getOrDefault(resource, 0.0);
	}

	public void addStock(Resource resource, double amount)
	{
		stock.merge(resource, amount, Double::sum);
	}

	public int getLevel(Building building)
	{
		return levels.getOrDefault(building, 0);
	}

	public void setLevel(Building building, int level)
	{
		levels.put(building, level);
	}

	public double getLabour(Skill skill)
	{
		return labour.getOrDefault(skill, 0.0);
	}

	public ExpeditionProgress getExpeditionProgress(ExpeditionId id)
	{
		return expeditions.computeIfAbsent(id, ignored -> new ExpeditionProgress());
	}

}
