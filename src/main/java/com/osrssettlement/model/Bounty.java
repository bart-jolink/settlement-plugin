package com.osrssettlement.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.api.Skill;

@Data
@NoArgsConstructor
public class Bounty
{
	private int id;
	private BountyType type;
	private BountyRarity rarity = BountyRarity.COMMON;
	private Skill skill;
	private SkillKind kind;
	private Resource resource;
	private long target;
	private double progress;
	private long expiresAtPlaytime;
	private BountyReward reward;

	public boolean isComplete()
	{
		return type != BountyType.DELIVER && progress >= target;
	}

	public boolean isBlueprint()
	{
		return reward != null && reward.getType() == RewardType.BLUEPRINT;
	}

	public String describe()
	{
		switch (type)
		{
			case SKILL_XP:
				return String.format("Gain %,d %s XP", target, skill.getName());
			case KIND_XP:
				return String.format("Gain %,d %s XP", target, kind.getDisplayName());
			case SLAYER_DROPS:
				return String.format("Collect %,d Basic Monster Parts from Slayer", target);
			case BOSS_KILLS:
				return String.format("Kill %,d %s", target, target == 1 ? "boss" : "bosses");
			case CLUES:
				return String.format("Complete %,d clue %s", target, target == 1 ? "scroll" : "scrolls");
			case DELIVER:
				return String.format("Deliver %,d %s", target, resource.getDisplayName());
			default:
				return type.name();
		}
	}
}
