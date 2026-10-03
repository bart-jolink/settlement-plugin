package com.osrssettlement.model;

import lombok.Getter;
import net.runelite.api.Skill;

/**
 * Settlement-wide events. A new one starts every hour of playtime and lasts two, so two usually overlap.
 */
@Getter
public enum SettlementEvent
{
	FISH_MIGRATION("Fish migration", "A great fish migration is passing the coast.", EventEffect.GATHERING, Skill.FISHING, 3),
	LUMBERJACK_CONTEST("Lumberjack contest", "The Lumber Camp is competing for the biggest haul.", EventEffect.GATHERING, Skill.WOODCUTTING, 3),
	RICH_VEIN("Rich vein", "Your miners struck a rich vein.", EventEffect.GATHERING, Skill.MINING, 3),
	BUMPER_HARVEST("Bumper harvest", "Perfect weather brings a bumper harvest.", EventEffect.GATHERING, Skill.FARMING, 3),
	HUNTING_SEASON("Hunting season", "Hunting season is open.", EventEffect.GATHERING, Skill.HUNTER, 3),
	MARKET_DAY("Keldagrim Consortium trading day", "The dwarves' crowded stalls leave purses full and guards distracted.", EventEffect.GATHERING, Skill.THIEVING, 3),
	ROOFTOP_RACE("Rooftop race", "The couriers are holding a rooftop race.", EventEffect.GATHERING, Skill.AGILITY, 3),
	FAIR_WINDS("Fair winds", "Fair winds speed your ships home.", EventEffect.GATHERING, Skill.SAILING, 3),
	WAR_DRUMS("War drums", "War drums rally your fighters.", EventEffect.GATHERING, Skill.HITPOINTS, 3),
	DORICS_SECRETS("Doric's secrets", "Doric shares his forging secrets with your smiths.", EventEffect.PROCESSING, Skill.SMITHING, 2),
	HARVEST_FEAST("Harvest feast", "The Tavern is cooking for a harvest feast.", EventEffect.PROCESSING, Skill.COOKING, 2),
	DRUIDIC_RECIPE("Druidic recipe", "Kaqemeex shared an old druidic recipe.", EventEffect.PROCESSING, Skill.HERBLORE, 2),
	CALM_ABYSS("Calm Abyss", "The Abyss is unusually calm.", EventEffect.PROCESSING, Skill.RUNECRAFT, 2),
	VARROCK_CARPENTERS("Varrock carpenters", "Carpenters from Varrock lend a hand at the Sawmill.", EventEffect.PROCESSING, Skill.CONSTRUCTION, 2),
	FEELING_LUCKY("Feeling lucky", "You're feeling lucky!", EventEffect.CLUES, null, 2),
	REDBERRY_PIE("Redberry pie", "You've eaten Thurgo's redberry pie and feel stronger than ever.", EventEffect.MONSTER_DROPS, null, 2),
	SETTLEMENT_FESTIVAL("Settlement festival", "Your settlers work with festive spirit.", EventEffect.GLOBAL, null, 1.5),

	TERMITES("Termites", "Termites are feasting on the lumber yard.", EventEffect.GATHERING, Skill.WOODCUTTING, 0.5),
	CAVE_IN("Cave-in", "A cave-in blocked part of the Quarry.", EventEffect.GATHERING, Skill.MINING, 0.5),
	ALGAE_BLOOM("Algae bloom", "An algae bloom drove the fish away.", EventEffect.GATHERING, Skill.FISHING, 0.5),
	CROP_BLIGHT("Crop blight", "Blight is spreading through the fields.", EventEffect.GATHERING, Skill.FARMING, 0.5),
	STORM("Storm", "A storm keeps your ships in the harbour.", EventEffect.GATHERING, Skill.SAILING, 0.5),
	SMITHS_STRIKE("Smiths' strike", "Your smiths are on strike.", EventEffect.PROCESSING, Skill.SMITHING, 0.5),
	BROKEN_MIRROR("Broken mirror", "You broke a mirror. Luckily the curse only lasts a while.", EventEffect.CLUES, null, 0.5),
	DODGY_KEBAB("Dodgy kebab", "That Karamja kebab didn't sit well.", EventEffect.MONSTER_DROPS, null, 0.5),
	HEATWAVE("Heatwave", "A heatwave slows everyone down.", EventEffect.GLOBAL, null, 0.75);

	private final String displayName;
	private final String flavour;
	private final EventEffect effect;
	/** Only set for GATHERING and PROCESSING events. */
	private final Skill skill;
	private final double multiplier;

	SettlementEvent(String displayName, String flavour, EventEffect effect, Skill skill, double multiplier)
	{
		this.displayName = displayName;
		this.flavour = flavour;
		this.effect = effect;
		this.skill = skill;
		this.multiplier = multiplier;
	}

	public boolean isBuff()
	{
		return multiplier > 1;
	}

	public String describeEffect(double effective)
	{
		double rounded = Math.round(effective * 100) / 100.0;
		String factor = "x" + (rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded));
		switch (effect)
		{
			case GATHERING:
				return skill.getName() + " yield " + factor;
			case PROCESSING:
				return skill.getName() + " output " + factor;
			case CLUES:
				return "Clue rewards " + factor;
			case MONSTER_DROPS:
				return "Monster drops " + factor;
			case GLOBAL:
			default:
				return "All yields " + factor;
		}
	}
}
