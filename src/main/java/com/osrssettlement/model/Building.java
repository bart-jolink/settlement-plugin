package com.osrssettlement.model;

import static com.osrssettlement.model.Resource.*;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@Getter
public enum Building
{
	TOWN_HALL("Town Hall", BuildingTier.A, ItemID.HAMMER,
		"Raises the level cap of all buildings and boosts every yield.",
		Map.of(LOGS, 80, STONE, 60, COINS, 80, RATIONS, 60, BARS, 40)),
	LUMBER_CAMP("Lumber Camp", BuildingTier.A, ItemID.RUNE_AXE,
		"More Logs from Woodcutting.",
		Map.of(LOGS, 100, ORE, 20)),
	QUARRY("Quarry", BuildingTier.A, ItemID.RUNE_PICKAXE,
		"More Ore and Stone from Mining. Finds Gems from level 3.",
		Map.of(ORE, 50, STONE, 30, LOGS, 20)),
	FISHING_DOCK("Fishing Dock", BuildingTier.A, ItemID.HARPOON,
		"More Fish from Fishing.",
		Map.of(FISH, 120, LOGS, 40)),
	FARMSTEAD("Farmstead", BuildingTier.A, ItemID.RAKE,
		"More Grain and Herbs from Farming.",
		Map.of(GRAIN, 120, HERBS, 30, LOGS, 20)),
	BARRACKS("Barracks", BuildingTier.A, ItemID.RUNE_SCIMITAR,
		"More Bones from combat (Hitpoints XP).",
		Map.of(BONES, 100, ORE, 30, ARROWS, 40)),


	TAVERN("Tavern", BuildingTier.B, ItemID.CHEFS_HAT,
		"More Rations from Cooking.",
		Map.of(RATIONS, 120, FISH, 60, PLANKS, 30)),
	THIEVES_GUILD("Thieves' Guild", BuildingTier.B, ItemID.VMQ4_JANUS_PURSE,
		"More Coins from Thieving. Finds Gems from level 3.",
		Map.of(COINS, 150, CARGO, 70, LEATHER, 60)),
	COURIER_POST("Courier Post", BuildingTier.B, ItemID.GRACEFUL_HOOD,
		"More Marks from Agility.",
		Map.of(MARKS, 220, LEATHER, 80, RATIONS, 40, PLANKS, 20)),
	HUNTERS_LODGE("Hunter's Lodge", BuildingTier.B, ItemID.NOOSE_WAND,
		"More Hides from Hunter.",
		Map.of(HIDES, 180, ARROWS, 70, BONES, 30)),

	SAWMILL("Sawmill", BuildingTier.C, ItemID.POH_SAW,
		"More Planks per Log from Construction.",
		Map.of(PLANKS, 80, BARS, 30, COINS, 50)),
	FORGE("Forge", BuildingTier.C, ItemID.GOLD_BAR,
		"More Bars from Smithing and Charcoal from Firemaking.",
		Map.of(BARS, 40, CHARCOAL, 180, STONE, 40, ORE, 40)),
	APOTHECARY("Apothecary", BuildingTier.C, ItemID.PESTLE_AND_MORTAR,
		"More Potions from Herblore.",
		Map.of(POTIONS, 160, HERBS, 40, CHARCOAL, 90)),
	SLAYER_TOWER("Slayer Tower", BuildingTier.C, ItemID.SLAYER_HELM,
		"More Basic Monster Parts from Slayer.",
		Map.of(MONSTER_PARTS, 200, ARROWS, 100, BONES, 100, POTIONS, 100, RATIONS, 100)),

	TEMPLE("Temple", BuildingTier.D, ItemID.BLESSEDSTAR,
		"More Blessings from Prayer.",
		Map.of(BLESSINGS, 100, BONES, 100, STONE, 60, RATIONS, 50)),
	HARBOR("Harbor", BuildingTier.D, ItemID.ROPE,
		"More Cargo from Sailing.",
		Map.of(CARGO, 200, PLANKS, 60, RATIONS, 40)),
	WORKSHOP("Workshop", BuildingTier.D, ItemID.NEEDLE,
		"More Leather from Crafting and Arrows from Fletching.",
		Map.of(LEATHER, 120, ARROWS, 100, PLANKS, 50)),
	WIZARD_TOWER("Wizard Tower", BuildingTier.D, ItemID.MYSTIC_HAT,
		"More Runes from Runecraft and Enchantments from Magic.",
		Map.of(RUNES, 180, ENCHANTMENTS, 120, GEMS, 15)),

	TROPHY_HALL("Trophy Hall", BuildingTier.E, ItemID.TZHAAR_CAPE_FIRE,
		"More Monster Parts from bosses and Artifacts from raids.",
		Map.of(BARS, 80, BLESSINGS, 40, MARKS, 60, MONSTER_PARTS, 200, RARE_MONSTER_PARTS, 50, EPIC_MONSTER_PARTS, 15, ARTIFACT, 1)),
	TREASURY("Treasury", BuildingTier.E, ItemID.CRYSTAL_KEY,
		"More Curios and Coins from clue scrolls.",
		Map.of(COINS, 150, CURIOS, 50, MARKS, 30, GEMS, 30)),

	ALTAR("Altar", BuildingTier.F, ItemID.ELYSIAN_SIGIL,
		"Consecrated with Epic Monster Parts and raid Artifacts. Boosts every yield.",
		Map.of(EPIC_MONSTER_PARTS, 25, BLESSINGS, 100, ENCHANTMENTS, 120, GEMS, 20, ARTIFACT, 1)),

	KELDAGRIM_CONSORTIUM("Keldagrim Consortium", BuildingTier.C, BuildingType.SPECIAL, ItemID.GOLD_LEAF,
		"Exchange materials with the dwarves of Keldagrim. Each level increases the amount received per trade by 10%.",
		Map.of(COINS, 500, ORE, 200, STONE, 150, PLANKS, 150, BARS, 100)),
	MUSEUM_CAMP("Museum Camp", BuildingTier.D, BuildingType.SPECIAL, ItemID.FOSSIL_RARE_UNID,
		"Curators pay more for your finds: bigger bounty supply rewards. Not required for the Wonder.",
		Map.of(PLANKS, 200, HIDES, 180, GRAIN, 180, BONES, 100, RATIONS, 100)),
	ARCEUUS_LIBRARY("Arceuus Library", BuildingTier.E, BuildingType.SPECIAL, ItemID.BOOK_OF_THE_DEAD,
		"Studied tomes make bounty yield boosts last longer. Not required for the Wonder.",
		Map.of(RUNES, 200, BLESSINGS, 100, PLANKS, 60, CURIOS, 50, ENCHANTMENTS, 50)),
	JALTEVAS_PYRAMID("Jaltevas Pyramid", BuildingTier.F, BuildingType.SPECIAL, ItemID.AGILITY_PYRAMID_GOLD_PYRAMID ,
		"Jaltevas's light strengthens positive events. Not required for the Wonder.",
		Map.of(COINS, 200, MARKS, 200, LEATHER, 120, GEMS, 50)),
	TOWER_OF_VOICES("Tower of Voices", BuildingTier.G, BuildingType.SPECIAL, ItemID.LEAGUE_TRAILBLAZER_LAST_RECALL_TELEPORT,
		"The Seren crystal resonates through your settlement, boosting every yield. Not required for the Wonder.",
		Map.of(STONE, 300, BARS, 150, ENCHANTMENTS, 200, MARKS, 250, RARE_MONSTER_PARTS, 100)),

	WONDER("Wonder", BuildingTier.WONDER, BuildingType.LEGENDARY, ItemID.SKILLCAPE_MAX,
		"The crown of your settlement. Can you complete it? Every level boosts all yields.",
		Map.ofEntries(Map.entry(BARS, 3000), Map.entry(CHARCOAL, 3000), Map.entry(PLANKS, 3000),
			Map.entry(LEATHER, 3000), Map.entry(ARROWS, 3000), Map.entry(RATIONS, 3000),
			Map.entry(POTIONS, 3000), Map.entry(BLESSINGS, 3000), Map.entry(ENCHANTMENTS, 3000),
			Map.entry(GEMS, 3000), Map.entry(COINS, 3000), Map.entry(MARKS, 3000),
			Map.entry(CARGO, 3000), Map.entry(CURIOS, 1000), 
			Map.entry(MONSTER_PARTS, 10_000), Map.entry(RARE_MONSTER_PARTS, 2000),
			Map.entry(EPIC_MONSTER_PARTS, 500), Map.entry(ARTIFACT, 50)));

	private final String displayName;
	private final BuildingTier tier;
	private final BuildingType type;
	private final int iconItemId;
	private final String description;
	private final Map<Resource, Integer> baseCost;

	Building(String displayName, BuildingTier tier, int iconItemId, String description, Map<Resource, Integer> baseCost)
	{
		this(displayName, tier, BuildingType.STANDARD, iconItemId, description, baseCost);
	}

	Building(String displayName, BuildingTier tier, BuildingType type, int iconItemId, String description,
		Map<Resource, Integer> baseCost)
	{
		this.displayName = displayName;
		this.tier = tier;
		this.type = type;
		this.iconItemId = iconItemId;
		this.description = description;
		this.baseCost = Collections.unmodifiableMap(new EnumMap<>(baseCost));
	}

	public boolean isWonder()
	{
		return this == WONDER;
	}

	/**
	 * Expedition buildings are not required for the Wonder.
	 */
	public boolean isOptional()
	{
		switch (this)
		{
			case KELDAGRIM_CONSORTIUM:
			case MUSEUM_CAMP:
			case ARCEUUS_LIBRARY:
			case JALTEVAS_PYRAMID:
			case TOWER_OF_VOICES:
				return true;
			default:
				return false;
		}
	}
}
