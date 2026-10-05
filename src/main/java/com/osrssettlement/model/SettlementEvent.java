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
	ALGAE_BLOOM("Algae bloom", "An algae bloom drove the fish away.", EventEffect.GATHERING, Skill.FISHING, 0.5),

	LUMBERJACK_CONTEST("Lumberjack contest", "The Woodcutting Guild is competing for the biggest haul.", EventEffect.GATHERING, Skill.WOODCUTTING, 3),
	TERMITES("Termites", "The redwoods are infested with termites.", EventEffect.GATHERING, Skill.WOODCUTTING, 0.5),

	RICH_VEIN("Rich vein", "Your miners struck a rich vein.", EventEffect.GATHERING, Skill.MINING, 3),
	CAVE_IN("Cave-in", "A cave-in blocked part of the Quarry.", EventEffect.GATHERING, Skill.MINING, 0.5),

	BUMPER_HARVEST("Bumper harvest", "Ultracompost did wonders for the crops.", EventEffect.GATHERING, Skill.FARMING, 3),
	DISEASED_CROPS("Diseased crops", "Crops are diseased and no plant cure is in sight.", EventEffect.GATHERING, Skill.FARMING, 0.5),

	HUNTING_SEASON("Hunting season", "Hunting season is open.", EventEffect.GATHERING, Skill.HUNTER, 3),
	SCARCE_GAME("Scarce game", "The nearby hunting grounds have grown quiet.", EventEffect.GATHERING, Skill.HUNTER, 0.5),

	MARKET_DAY("Market day", "The dwarves' crowded stalls leave purses full and guards distracted.", EventEffect.GATHERING, Skill.THIEVING, 3),
	ALERT_GUARDS("Alert guards", "The guards are watching the market closely.", EventEffect.GATHERING, Skill.THIEVING, 0.5),

	ROOFTOP_RACE("Rooftop race", "The couriers are holding a rooftop race.", EventEffect.GATHERING, Skill.AGILITY, 3),
	SNOWY_ROOFTOPS("Snowy rooftops", "Snow has accumulated on the roofs.", EventEffect.GATHERING, Skill.AGILITY, 0.5),

	FAIR_WINDS("Fair winds", "Fair winds and currents speed your ships home.", EventEffect.GATHERING, Skill.SAILING, 3),
	STORM("Storm", "A storm keeps your ships in the harbour.", EventEffect.GATHERING, Skill.SAILING, 0.5),

	WAR_DRUMS("War drums", "War drums rally your fighters.", EventEffect.GATHERING, Skill.HITPOINTS, 3),
	INSECURE_ADVENTURER("Insecure adventurer", "An adventurer doubts their abilities to fight.", EventEffect.GATHERING, Skill.HITPOINTS, 0.5),

	DORICS_SECRETS("Doric's secrets", "Doric shares his forging secrets with your smiths.", EventEffect.PROCESSING, Skill.SMITHING, 3),
	SMITHS_STRIKE("Smiths' strike", "Your smiths are on strike.", EventEffect.PROCESSING, Skill.SMITHING, 0.5),

	HUGE_BONFIRE("Huge bonfire", "A bonfire keeps everything warm and dry.", EventEffect.PROCESSING, Skill.FIREMAKING, 3),
	DAMP_KINDLING("Damp kindling", "Damp kindling makes it difficult to light a fire..", EventEffect.PROCESSING, Skill.FIREMAKING, 0.5),

	MASTER_CRAFTER("Master crafter", "The master crafters give a workshop demonstration.", EventEffect.PROCESSING, Skill.CRAFTING, 3),
	BROKEN_TANNERY("Broken tannery", "Problems at the tannery slow down leather production.", EventEffect.PROCESSING, Skill.CRAFTING, 0.5),

	BOWYERS_RUSH("Bowyer's rush", "Some guy from Catherby gave a great tip.", EventEffect.PROCESSING, Skill.FLETCHING, 3),
	BLUNT_KNIFE("Blunt knife", "Your knife has become blunt, slowing down fletching.", EventEffect.PROCESSING, Skill.FLETCHING, 0.5),

	HARVEST_FEAST("Harvest feast", "The Tavern is cooking for a harvest feast.", EventEffect.PROCESSING, Skill.COOKING, 3),
	SPOILED_PROVISIONS("Spoiled provisions", "A spoiled food shipment slows the Tavern kitchen.", EventEffect.PROCESSING, Skill.COOKING, 0.5),

	DRUIDIC_RECIPE("Druidic recipe", "Kaqemeex shared an old druidic recipe.", EventEffect.PROCESSING, Skill.HERBLORE, 3),
	WILTED_HERBS("Wilted herbs", "A dry spell has left the apothecary's herbs withered.", EventEffect.PROCESSING, Skill.HERBLORE, 0.5),

	CALM_ABYSS("Calm Abyss", "The Abyss is unusually calm.", EventEffect.PROCESSING, Skill.RUNECRAFT, 3),
	RUNE_INSTABILITY("Rune instability", "Unsteady currents in the Abyss disrupt rune crafting.", EventEffect.PROCESSING, Skill.RUNECRAFT, 0.5),

	ARCANE_ALIGNMENT("Arcane alignment", "The stars align over the Wizard's Tower, strengthening enchantments.", EventEffect.PROCESSING, Skill.MAGIC, 3),
	SPELL_BACKLASH("Spell backlash", "Unstable magic disrupts enchantments at the Wizard's Tower.", EventEffect.PROCESSING, Skill.MAGIC, 0.5),

	SARADOMINS_BLESSING("Saradomin's blessing", "Saradomin's favor strengthens the Temple's blessings.", EventEffect.PROCESSING, Skill.PRAYER, 3),
	RESTLESS_GHOST("Restless ghost", "A restless ghost disturbs the Temple's blessings.", EventEffect.PROCESSING, Skill.PRAYER, 0.5),

	VARROCK_CARPENTERS("Varrock carpenters", "Carpenters from Varrock lend a hand at the Sawmill.", EventEffect.PROCESSING, Skill.CONSTRUCTION, 3),
	MATERIALS_SHORTAGE("Materials shortage", "A shortage of nails and planks stalls construction.", EventEffect.PROCESSING, Skill.CONSTRUCTION, 0.5),

	FEELING_LUCKY("Feeling lucky", "You're feeling lucky!", EventEffect.CLUES, null, 3),
	BROKEN_MIRROR("Broken mirror", "You broke a mirror. Luckily the curse only lasts a while.", EventEffect.CLUES, null, 0.5),

	REDBERRY_PIE("Redberry pie", "You've eaten Thurgo's redberry pie and feel stronger than ever.", EventEffect.MONSTER_DROPS, null, 3),
	DODGY_KEBAB("Dodgy kebab", "That Pollnivneach kebab didn't sit well.", EventEffect.MONSTER_DROPS, null, 0.5),

	SETTLEMENT_FESTIVAL("Settlement festival", "Your settlers work with festive spirit.", EventEffect.GLOBAL, null, 1.5),
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
