package com.osrssettlement.model;

import static com.osrssettlement.model.Resource.*;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

public final class SkillRules
{
	private static final Map<Skill, SkillRule> RULES = new EnumMap<>(Skill.class);

	static
	{
		gather(Skill.WOODCUTTING, Building.LUMBER_CAMP, Map.of(LOGS, 1.0));
		gather(Skill.MINING, Building.QUARRY, Map.of(ORE, 0.5, STONE, 0.5));
		gather(Skill.FISHING, Building.FISHING_DOCK, Map.of(FISH, 1.0));
		gather(Skill.HUNTER, Building.HUNTERS_LODGE, Map.of(HIDES, 1.0));
		gather(Skill.FARMING, Building.FARMSTEAD, Map.of(GRAIN, 0.5, HERBS, 0.5));
		gather(Skill.THIEVING, Building.THIEVES_GUILD, Map.of(COINS, 1.0));
		gather(Skill.AGILITY, Building.COURIER_POST, Map.of(MARKS, 1.0));
		gather(Skill.SAILING, Building.HARBOR, Map.of(CARGO, 1.0));

		add(SkillRule.produce(Skill.HITPOINTS, SkillKind.COMBAT, Building.BARRACKS, Map.of(BONES, 1.0)));
		add(SkillRule.produce(Skill.SLAYER, SkillKind.SLAYER, Building.SLAYER_TOWER, Map.of(MONSTER_PARTS, 1.0)));

		add(SkillRule.process(Skill.SMITHING, Building.FORGE, BARS, ORE));
		add(SkillRule.process(Skill.FIREMAKING, Building.FORGE, CHARCOAL, LOGS));
		add(SkillRule.process(Skill.CONSTRUCTION, Building.SAWMILL, PLANKS, LOGS));
		add(SkillRule.process(Skill.CRAFTING, Building.WORKSHOP, LEATHER, HIDES));
		add(SkillRule.process(Skill.FLETCHING, Building.WORKSHOP, ARROWS, LOGS));
		add(SkillRule.process(Skill.COOKING, Building.TAVERN, RATIONS, FISH, GRAIN));
		add(SkillRule.process(Skill.HERBLORE, Building.APOTHECARY, POTIONS, HERBS));
		add(SkillRule.process(Skill.RUNECRAFT, Building.WIZARD_TOWER, RUNES, STONE));
		add(SkillRule.process(Skill.MAGIC, Building.WIZARD_TOWER, ENCHANTMENTS, RUNES));
		add(SkillRule.process(Skill.PRAYER, Building.TEMPLE, BLESSINGS, BONES));
	}

	private SkillRules()
	{
	}

	private static void gather(Skill skill, Building building, Map<Resource, Double> outputs)
	{
		add(SkillRule.produce(skill, SkillKind.GATHERING, building, outputs));
	}

	private static void add(SkillRule rule)
	{
		RULES.put(rule.getSkill(), rule);
	}

	/**
	 * @return the rule for a skill, or null if the skill does not feed the settlement
	 */
	public static SkillRule get(Skill skill)
	{
		return RULES.get(skill);
	}

	public static Collection<SkillRule> all()
	{
		return Collections.unmodifiableCollection(RULES.values());
	}
}
