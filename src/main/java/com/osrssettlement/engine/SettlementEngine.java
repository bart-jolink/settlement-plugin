package com.osrssettlement.engine;

import com.osrssettlement.model.Boost;
import com.osrssettlement.model.Bounty;
import com.osrssettlement.model.BountyReward;
import com.osrssettlement.model.BountyType;
import com.osrssettlement.model.BossTier;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.ClueTier;
import com.osrssettlement.model.ExpeditionId;
import com.osrssettlement.model.ExpeditionProgress;
import com.osrssettlement.model.LogEntry;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementState;
import com.osrssettlement.model.SkillRule;
import com.osrssettlement.model.SkillRules;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import lombok.Getter;
import net.runelite.api.Skill;

/**
 * Applies OSRS activity to a settlement. Not thread safe: all calls must come from one thread
 * (the client thread in the plugin).
 */
public class SettlementEngine
{
	@Getter
	private final SettlementState state;
	private final Random random;
	private final BountyBoard bountyBoard;
	private final EventScheduler eventScheduler;

	public SettlementEngine(SettlementState state, Random random)
	{
		this.state = state;
		this.random = random;
		this.bountyBoard = new BountyBoard(random);
		this.eventScheduler = new EventScheduler(random);
		bountyBoard.fill(state);
		if (state.getEvents().isEmpty())
		{
			eventScheduler.start(state);
		}
	}

	public static SettlementState newSettlement()
	{
		SettlementState state = new SettlementState();
		state.setLevel(Building.TOWN_HALL, 1);
		Balance.STARTER_KIT.forEach(state::addStock);
		addLog(state, "Your settlement has been founded. Go out and gather!");
		return state;
	}

	public void onXp(Skill skill, int xp)
	{
		SkillRule rule = SkillRules.get(skill);
		if (rule == null || xp <= 0)
		{
			return;
		}

		double units = YieldCalculator.units(xp);
		if (rule.isProcessing())
		{
			process(rule, units);
		}
		else
		{
			double multiplier = YieldCalculator.gatherMultiplier(state, rule.getBuilding())
				* YieldCalculator.eventSkillMultiplier(state, skill);
			double slayerParts = 0;
			for (Map.Entry<Resource, Double> output : rule.getOutputs().entrySet())
			{
				double amount = units * output.getValue() * multiplier
					* YieldCalculator.boostMultiplier(state, output.getKey());
				gain(output.getKey(), amount);
				if (skill == Skill.SLAYER && output.getKey() == Resource.MONSTER_PARTS)
				{
					slayerParts += amount;
				}
			}
			addByproducts(skill, units, multiplier);
			if (skill == Skill.SLAYER)
			{
				progress(b -> b.getType() == BountyType.SLAYER_DROPS, slayerParts);
			}
			drainLabour();
		}
		progressExpedition(ExpeditionCatalog.ObjectiveType.SKILL_XP, skill, null, xp);

		progress(b -> b.getType() == BountyType.SKILL_XP && b.getSkill() == skill, xp);
		progress(b -> b.getType() == BountyType.KIND_XP && b.getKind() == rule.getKind(), xp);
	}

	public void onBossKill(String boss, BossTier tier)
	{
		onBossKill(boss, tier, false);
	}

	public void onBossKill(String boss, BossTier tier, boolean raid)
	{
		Map<Resource, Double> gained = new EnumMap<>(Resource.class);
		double trophyMultiplier = YieldCalculator.trophyMultiplier(state);
		double eventMultiplier = YieldCalculator.eventBossMultiplier(state);
		new EnumMap<>(Balance.BOSS_PART_DROP_CHANCES.get(tier)).forEach((resource, chance) ->
		{
			if (random.nextDouble() < chance)
			{
				gained.merge(resource, Balance.BOSS_PARTS_PER_DROP.get(tier) * trophyMultiplier * eventMultiplier, Double::sum);
			}
		});
		if (raid)
		{
			int artifacts = tier == BossTier.TIER_2 ? Balance.RAID_ARTIFACTS_TIER_2
				: tier == BossTier.TIER_3 ? Balance.RAID_ARTIFACTS_TIER_3 : 0;
			if (artifacts > 0)
			{
				gained.merge(Resource.ARTIFACT, artifacts * trophyMultiplier * eventMultiplier, Double::sum);
			}
		}
		gained.forEach(this::gain);
		addLog(state, boss + " slain: " + describe(gained));

		progress(b -> b.getType() == BountyType.BOSS_KILLS, 1);
		progressExpedition(ExpeditionCatalog.ObjectiveType.BOSS_KILLS, null, tier, boss, 1);
		if (raid)
		{
			progressExpedition(ExpeditionCatalog.ObjectiveType.RAIDS, null, null, boss, 1);
		}
	}

	public void onClueCompleted(ClueTier tier)
	{
		double multiplier = YieldCalculator.treasuryMultiplier(state) * YieldCalculator.eventClueMultiplier(state);
		Map<Resource, Double> gained = new EnumMap<>(Resource.class);
		tier.getReward().forEach((resource, amount) -> gained.put(resource, amount * multiplier));
		Resource bonusPart = tier.getBonusPart();
		if (bonusPart != null && random.nextDouble() < Balance.CLUE_PART_CHANCE)
		{
			gained.merge(bonusPart, tier.getBonusAmount() * multiplier, Double::sum);
		}
		gained.forEach(this::gain);
		addLog(state, tier.getDisplayName() + " clue completed: " + describe(gained));

		progress(b -> b.getType() == BountyType.CLUES, 1);
		progressExpedition(ExpeditionCatalog.ObjectiveType.CLUES, null, null, 1);
	}

	public boolean isExpeditionComplete(ExpeditionId id)
	{
		ExpeditionCatalog.Definition expedition = ExpeditionCatalog.get(id);
		ExpeditionProgress progress = state.getExpeditionProgress(id);
		return expedition != null && ExpeditionCatalog.isComplete(expedition, progress);
	}

	public String claimExpedition(ExpeditionId id)
	{
		ExpeditionCatalog.Definition expedition = ExpeditionCatalog.get(id);
		if (expedition == null || !ExpeditionCatalog.isUnlocked(expedition, state))
		{
			return null;
		}

		ExpeditionProgress progress = state.getExpeditionProgress(id);
		if (progress.isClaimed() || !ExpeditionCatalog.isComplete(expedition, progress))
		{
			return null;
		}

		progress.setClaimed(true);
		state.getBlueprints().add(expedition.getRewardBlueprint());
		String message = expedition.getRewardBlueprint().getDisplayName() + " blueprint unlocked";
		addLog(state, expedition.getDisplayName() + " expedition complete: " + message + ".");
		return message;
	}

	public String tradeAtKeldagrimConsortium(Resource input, Resource output)
	{
		if (!MarketService.trade(state, input, output))
		{
			return null;
		}

		String message = "Keldagrim Consortium exchanged "
			+ MarketService.describe(state.getLevel(Building.KELDAGRIM_CONSORTIUM), input, output);
		addLog(state, message + ".");
		return message;
	}

	public void setLabourPaused(boolean paused)
	{
		state.setLabourPaused(paused);
		if (!paused)
		{
			drainLabour();
		}
	}

	/**
	 * Called once per logged-in game tick.
	 */
	public void onTick()
	{
		state.setPlaytimeTicks(state.getPlaytimeTicks() + 1);
		eventScheduler.tick(state);

		Iterator<Boost> it = state.getBoosts().iterator();
		while (it.hasNext())
		{
			Boost boost = it.next();
			boost.setRemainingTicks(boost.getRemainingTicks() - 1);
			if (boost.getRemainingTicks() <= 0)
			{
				it.remove();
			}
		}

		List<Bounty> expired = new ArrayList<>();
		for (Bounty bounty : state.getBounties())
		{
			boolean ready = bounty.getType() == BountyType.DELIVER
				? state.getStock(bounty.getResource()) >= bounty.getTarget()
				: bounty.isComplete();
			if (!ready && state.getPlaytimeTicks() >= bounty.getExpiresAtPlaytime())
			{
				expired.add(bounty);
			}
		}
		if (!expired.isEmpty())
		{
			state.getBounties().removeAll(expired);
			expired.forEach(b -> addLog(state, "Bounty expired: " + b.describe()));
			bountyBoard.fill(state);
		}
	}

	public UpgradeCheck upgrade(Building building)
	{
		UpgradeCheck check = BuildingService.upgrade(state, building);
		if (check.isOk())
		{
			int level = state.getLevel(building);
			if (building.isWonder())
			{
				addLog(state, level == 1 ? "The Wonder is complete! Your settlement is legendary."
					: "The Wonder has grown to level " + level + ".");
			}
			else
			{
				addLog(state, level == 1 ? building.getDisplayName() + " built."
					: building.getDisplayName() + " upgraded to level " + level + ".");
			}
		}
		return check;
	}

	/** @return reward feedback text, or null when this bounty cannot be claimed */
	public String claimBounty(int bountyId)
	{
		Bounty bounty = state.getBounties().stream()
			.filter(b -> b.getId() == bountyId)
			.findFirst()
			.orElse(null);
		if (bounty == null)
		{
			return null;
		}

		if (bounty.getType() == BountyType.DELIVER)
		{
			if (state.getStock(bounty.getResource()) < bounty.getTarget())
			{
				return null;
			}

			state.addStock(bounty.getResource(), -bounty.getTarget());
			bounty.setProgress(bounty.getTarget());
		}
		else if (!bounty.isComplete())
		{
			return null;
		}

		String feedback = rewardFeedback(bounty.getReward());
		state.getBounties().remove(bounty);
		complete(bounty);
		bountyBoard.fill(state);
		return feedback;
	}

	private void process(SkillRule rule, double work)
	{
		Skill skill = rule.getSkill();
		double storedLabour = state.getLabour(skill);
		boolean paused = state.isLabourPaused();
		double pending = (paused ? 0 : storedLabour) + work;
		double done = 0;

		for (Resource input : rule.getInputs())
		{
			double take = Math.min(pending - done, state.getStock(input) / Balance.PROCESS_INPUT_PER_UNIT);
			if (take > 0)
			{
				state.addStock(input, -take * Balance.PROCESS_INPUT_PER_UNIT);
				done += take;
			}
			if (done >= pending)
			{
				break;
			}
		}

		if (done > 0)
		{
			Resource output = rule.getOutput();
			double ratio = YieldCalculator.processRatio(state, rule.getBuilding()) * YieldCalculator.eventSkillMultiplier(state, skill);
			state.addStock(output, done * ratio * YieldCalculator.boostMultiplier(state, output));
		}

		double leftover = pending - done;
		if (paused && storedLabour + leftover > 0)
		{
			state.getLabour().put(skill, storedLabour + leftover);
		}
		else if (leftover > 0)
		{
			state.getLabour().put(skill, leftover);
		}
		else
		{
			state.getLabour().remove(skill);
		}
	}

	/**
	 * Uses banked labour of processing skills now that new raw materials may have arrived.
	 */
	private void drainLabour()
	{
		if (state.isLabourPaused())
		{
			return;
		}

		for (Skill skill : new ArrayList<>(state.getLabour().keySet()))
		{
			SkillRule rule = SkillRules.get(skill);
			if (rule != null && rule.isProcessing())
			{
				process(rule, 0);
			}
		}
	}

	private void addByproducts(Skill skill, double units, double multiplier)
	{
		switch (skill)
		{
			case MINING:
				if (state.getLevel(Building.QUARRY) >= Balance.GEM_MIN_BUILDING_LEVEL)
				{
					state.addStock(Resource.GEMS, units * Balance.QUARRY_GEM_RATE * multiplier);
				}
				break;
			case THIEVING:
				if (state.getLevel(Building.THIEVES_GUILD) >= Balance.GEM_MIN_BUILDING_LEVEL)
				{
					state.addStock(Resource.GEMS, units * Balance.THIEVES_GUILD_GEM_RATE * multiplier);
				}
				break;
			default:
				break;
		}
	}

	private void progress(Predicate<Bounty> matches, double amount)
	{
		for (Bounty bounty : state.getBounties())
		{
			if (matches.test(bounty))
			{
				bounty.setProgress(Math.min(bounty.getTarget(), bounty.getProgress() + amount));
			}
		}
	}

	private void progressExpedition(ExpeditionCatalog.ObjectiveType type, Skill skill, BossTier tier, double amount)
	{
		progressExpedition(type, skill, tier, null, amount);
	}

	private void progressExpedition(ExpeditionCatalog.ObjectiveType type, Skill skill, BossTier tier, String eventName,
		double amount)
	{
		ExpeditionCatalog.Definition expedition = ExpeditionCatalog.current(state);
		if (amount <= 0 || expedition == null)
		{
			return;
		}

		Map<String, Double> progress = state.getExpeditionProgress(expedition.getId()).getObjectives();
		for (ExpeditionCatalog.Objective objective : expedition.getObjectives())
		{
			if (objective.matches(type, skill, tier, eventName))
			{
				double current = progress.getOrDefault(objective.getId(), 0.0);
				progress.put(objective.getId(), Math.max(current, Math.min(objective.getTarget(), current + amount)));
			}
		}
	}

	private void gain(Resource resource, double amount)
	{
		state.addStock(resource, amount);
		switch (resource)
		{
			case MONSTER_PARTS:
				progressExpedition(ExpeditionCatalog.ObjectiveType.MONSTER_PARTS, null, null, amount);
				break;
			case RARE_MONSTER_PARTS:
				progressExpedition(ExpeditionCatalog.ObjectiveType.RARE_MONSTER_PARTS, null, null, amount);
				break;
			case EPIC_MONSTER_PARTS:
				progressExpedition(ExpeditionCatalog.ObjectiveType.EPIC_MONSTER_PARTS, null, null, amount);
				break;
			default:
				break;
		}
	}

	private void complete(Bounty bounty)
	{
		BountyReward reward = bounty.getReward();
		switch (reward.getType())
		{
			case BLUEPRINT:
				state.getBlueprints().add(reward.getBlueprint());
				break;
			case BOOST:
				grantBoost(reward);
				break;
			case RESOURCES:
			default:
				reward.getResources().forEach(this::gain);
				drainLabour();
				break;
		}
		state.setBountiesCompleted(state.getBountiesCompleted() + 1);
		addLog(state, "Bounty complete (" + bounty.describe() + "): " + reward.describe());
	}

	private static String rewardFeedback(BountyReward reward)
	{
		switch (reward.getType())
		{
			case BOOST:
				double multiplier = reward.getBoostMultiplier();
				String formatted = multiplier == Math.rint(multiplier)
					? String.valueOf((long) multiplier) : String.valueOf(multiplier);
				return "+" + formatted + "x " + reward.getBoostResource().getDisplayName() + " boost";
			case BLUEPRINT:
				return "+ " + reward.getBlueprint().getDisplayName() + " blueprint";
			case RESOURCES:
			default:
				StringBuilder feedback = new StringBuilder();
				reward.getResources().forEach((resource, amount) ->
				{
					if (feedback.length() > 0)
					{
						feedback.append("   ");
					}
					feedback.append(String.format("+%,d %s", amount, resource.getDisplayName()));
				});
				return feedback.toString();
		}
	}

	private void grantBoost(BountyReward reward)
	{
		int ticks = (int) Math.round(reward.getBoostTicks() * YieldCalculator.libraryDurationMultiplier(state));
		for (Boost boost : state.getBoosts())
		{
			if (boost.getResource() == reward.getBoostResource())
			{
				boost.setRemainingTicks(boost.getRemainingTicks() + ticks);
				boost.setMultiplier(Math.max(boost.getMultiplier(), reward.getBoostMultiplier()));
				return;
			}
		}
		state.getBoosts().add(new Boost(reward.getBoostResource(), reward.getBoostMultiplier(), ticks));
	}

	private static String describe(Map<Resource, Double> gained)
	{
		List<String> parts = new ArrayList<>();
		gained.forEach((resource, amount) -> parts.add(String.format("+%s %s", formatAmount(amount), resource.getDisplayName())));
		return String.join(", ", parts);
	}

	private static String formatAmount(double amount)
	{
		return amount == Math.rint(amount) ? String.format("%,d", (long) amount) : String.format("%.2f", amount);
	}

	static void addLog(SettlementState state, String message)
	{
		state.getLog().add(0, new LogEntry(state.getPlaytimeTicks(), message));
		while (state.getLog().size() > Balance.LOG_SIZE)
		{
			state.getLog().remove(state.getLog().size() - 1);
		}
	}
}
