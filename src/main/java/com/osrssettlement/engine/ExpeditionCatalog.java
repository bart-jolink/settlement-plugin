package com.osrssettlement.engine;

import com.osrssettlement.model.BossTier;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.ExpeditionId;
import com.osrssettlement.model.ExpeditionProgress;
import com.osrssettlement.model.SettlementState;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Five expeditions worked on one at a time: claiming one unlocks the next.
 */
public final class ExpeditionCatalog
{
	public enum ObjectiveType
	{
		SKILL_XP,
		MONSTER_PARTS,
		RARE_MONSTER_PARTS,
		EPIC_MONSTER_PARTS,
		CLUES,
		BOSS_KILLS,
		RAIDS
	}

	public static final class Objective
	{
		private final String id;
		private final String displayName;
		private final long target;
		private final ObjectiveType type;
		private final Skill skill;
		private final BossTier minTier;
		private final String encounterName;

		private Objective(String id, String displayName, long target, ObjectiveType type, Skill skill, BossTier minTier,
			String encounterName)
		{
			this.id = id;
			this.displayName = displayName;
			this.target = target;
			this.type = type;
			this.skill = skill;
			this.minTier = minTier;
			this.encounterName = encounterName;
		}

		static Objective xp(Skill skill, long target)
		{
			return new Objective(skill.name().toLowerCase(Locale.ROOT) + "Xp", "Gain " + skill.getName() + " XP", target,
				ObjectiveType.SKILL_XP, skill, null, null);
		}

		static Objective monsterParts(long target)
		{
			return new Objective("monsterParts", "Gather Basic Monster Parts", target, ObjectiveType.MONSTER_PARTS,
				null, null, null);
		}

		static Objective rareMonsterParts(long target)
		{
			return new Objective("rareMonsterParts", "Gather Rare Monster Parts", target,
				ObjectiveType.RARE_MONSTER_PARTS,
				null, null, null);
		}

		static Objective epicMonsterParts(long target)
		{
			return new Objective("epicMonsterParts", "Gather Epic Monster Parts", target,
				ObjectiveType.EPIC_MONSTER_PARTS,
				null, null, null);
		}

		static Objective clues(long target)
		{
			return new Objective("clues", "Complete clue scrolls", target, ObjectiveType.CLUES, null, null, null);
		}

		static Objective bosses(BossTier minTier, long target)
		{
			String tiers = minTier == BossTier.TIER_3 ? minTier.getDisplayName() : minTier.getDisplayName() + "+";
			return new Objective("bosses" + (minTier.ordinal() + 1), "Defeat " + tiers + " bosses", target,
				ObjectiveType.BOSS_KILLS, null, minTier, null);
		}

		static Objective raids(long target)
		{
			return new Objective("raids", "Complete raids", target, ObjectiveType.RAIDS, null, null, null);
		}

		static Objective boss(String id, String bossName, long target)
		{
			return new Objective(id, "Defeat " + bossName, target, ObjectiveType.BOSS_KILLS, null, null, bossName);
		}

		static Objective raid(String id, String raidName, long target)
		{
			return new Objective(id, "Complete " + raidName, target, ObjectiveType.RAIDS, null, null, raidName);
		}

		public String getId()
		{
			return id;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		public long getTarget()
		{
			return target;
		}

		boolean matches(ObjectiveType type, Skill skill, BossTier tier, String eventName)
		{
			if (this.type != type || (encounterName != null && !matchesEncounter(eventName, encounterName)))
			{
				return false;
			}
			switch (type)
			{
				case SKILL_XP:
					return this.skill == skill;
				case BOSS_KILLS:
					return encounterName != null || tier != null && tier.ordinal() >= minTier.ordinal();
				default:
					return true;
			}
		}

		private static boolean matchesEncounter(String eventName, String encounterName)
		{
			if (eventName == null)
			{
				return false;
			}

			String actual = normalizeEncounter(eventName);
			String expected = normalizeEncounter(encounterName);
			return actual.equals(expected) || actual.startsWith(expected + ":") || actual.startsWith(expected + " ");
		}

		private static String normalizeEncounter(String name)
		{
			String normalized = name.toLowerCase(Locale.ROOT).trim();
			return normalized.startsWith("the ") ? normalized.substring(4) : normalized;
		}
	}

	public static final class Definition
	{
		private final ExpeditionId id;
		private final String displayName;
		private final int iconItemId;
		private final ExpeditionId prerequisite;
		private final Building rewardBlueprint;
		private final List<Objective> objectives;

		private Definition(ExpeditionId id, String displayName, int iconItemId, ExpeditionId prerequisite,
			Building rewardBlueprint, List<Objective> objectives)
		{
			this.id = id;
			this.displayName = displayName;
			this.iconItemId = iconItemId;
			this.prerequisite = prerequisite;
			this.rewardBlueprint = rewardBlueprint;
			this.objectives = Collections.unmodifiableList(objectives);
		}

		public ExpeditionId getId()
		{
			return id;
		}

		public String getDisplayName()
		{
			return displayName;
		}

		public int getIconItemId()
		{
			return iconItemId;
		}

		public Building getRewardBlueprint()
		{
			return rewardBlueprint;
		}

		public List<Objective> getObjectives()
		{
			return objectives;
		}
	}

	private static final List<Definition> EXPEDITIONS = List.of(
		new Definition(ExpeditionId.KELDAGRIM, "Keldagrim", ItemID.DWARF_GOLDROCK_HELMET, null, Building.KELDAGRIM_CONSORTIUM, List.of(
			Objective.xp(Skill.COOKING, 100_000),
			Objective.xp(Skill.MINING, 100_000),
			Objective.xp(Skill.SMITHING, 50_000),
			Objective.monsterParts(2_000),
			Objective.clues(10),
			Objective.bosses(BossTier.TIER_1, 20))),
		new Definition(ExpeditionId.FOSSIL_ISLAND, "Fossil Island", ItemID.FOSSIL_LARGE_UNID, ExpeditionId.KELDAGRIM,
			Building.MUSEUM_CAMP, List.of(
			Objective.xp(Skill.WOODCUTTING, 150_000),
			Objective.xp(Skill.HUNTER, 150_000),
			Objective.xp(Skill.FARMING, 150_000),
			Objective.xp(Skill.CONSTRUCTION, 100_000),
			Objective.monsterParts(5_000),
			Objective.boss("derangedArchaeologistKills", "Deranged Archaeologist", 5))),
		new Definition(ExpeditionId.GREAT_KOUREND, "Great Kourend", ItemID.XERIC_TALISMAN, ExpeditionId.FOSSIL_ISLAND,
			Building.ARCEUUS_LIBRARY, List.of(
			Objective.xp(Skill.FISHING, 250_000),
			Objective.xp(Skill.FIREMAKING, 200_000),
			Objective.xp(Skill.RUNECRAFT, 150_000),
			Objective.xp(Skill.PRAYER, 100_000),
			Objective.clues(15),
			Objective.boss("skotizoKills", "Skotizo", 1),
			Objective.boss("wintertodtKills", "Wintertodt", 10),
			Objective.raid("chambersOfXericCompletions", "Chambers of Xeric", 5))),
		new Definition(ExpeditionId.KHARIDIAN_DESERT, "Kharidian Desert", ItemID.PHARAOHS_SCEPTRE, ExpeditionId.GREAT_KOUREND,
			Building.JALTEVAS_PYRAMID, List.of(
			Objective.xp(Skill.THIEVING, 300_000),
			Objective.xp(Skill.FLETCHING, 300_000),
			Objective.xp(Skill.CRAFTING, 250_000),
			Objective.xp(Skill.MAGIC, 200_000),
			Objective.rareMonsterParts(3_000),
			Objective.boss("kalphiteQueenKills", "Kalphite Queen", 25),
			Objective.raid("tombsOfAmascutCompletions", "Tombs of Amascut", 10))),
		new Definition(ExpeditionId.PRIFDDINAS, "Prifddinas", ItemID.ELVEN_SIGNET, ExpeditionId.KHARIDIAN_DESERT,
			Building.TOWER_OF_VOICES, List.of(
			Objective.xp(Skill.SMITHING, 400_000),
			Objective.xp(Skill.AGILITY, 400_000),
			Objective.xp(Skill.HERBLORE, 400_000),
			Objective.xp(Skill.SAILING, 300_000),
			Objective.xp(Skill.CONSTRUCTION, 300_000),
			Objective.clues(20),
			Objective.epicMonsterParts(1_000),
			Objective.boss("corruptedGauntletCompletions", "Corrupted Gauntlet", 15),
			Objective.boss("zalcanoKills", "Zalcano", 15),
			Objective.raids(15))));

	private ExpeditionCatalog()
	{
	}

	public static List<Definition> all()
	{
		return EXPEDITIONS;
	}

	public static Definition get(ExpeditionId id)
	{
		return EXPEDITIONS.stream().filter(expedition -> expedition.getId() == id).findFirst().orElse(null);
	}

	/**
	 * @return the expedition being worked on, or null once every expedition is claimed
	 */
	public static Definition current(SettlementState state)
	{
		for (Definition expedition : EXPEDITIONS)
		{
			if (!state.getExpeditionProgress(expedition.getId()).isClaimed())
			{
				return expedition;
			}
		}
		return null;
	}

	/**
	 * @return the expedition that rewards this building's blueprint, or null if it comes from the bounty board
	 */
	public static Definition sourceOf(Building building)
	{
		return EXPEDITIONS.stream().filter(expedition -> expedition.getRewardBlueprint() == building).findFirst().orElse(null);
	}

	public static boolean isUnlocked(Definition expedition, SettlementState state)
	{
		return expedition.prerequisite == null
			|| state.getExpeditionProgress(expedition.prerequisite).isClaimed();
	}

	public static boolean isComplete(Definition expedition, ExpeditionProgress progress)
	{
		return expedition.getObjectives().stream()
			.allMatch(objective -> progress.getObjectives().getOrDefault(objective.getId(), 0.0) >= objective.getTarget());
	}
}