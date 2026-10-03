package com.osrssettlement.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum Resource
{
	LOGS("Logs", ResourceCategory.RAW, ItemID.LOGS),
	ORE("Ore", ResourceCategory.RAW, ItemID.IRON_ORE),
	STONE("Stone", ResourceCategory.RAW, ItemID.LIMESTONE),
	FISH("Fish", ResourceCategory.RAW, ItemID.RAW_LOBSTER),
	HIDES("Hides", ResourceCategory.RAW, ItemID.COW_HIDE),
	GRAIN("Grain", ResourceCategory.RAW, ItemID.GRAIN),
	HERBS("Herbs", ResourceCategory.RAW, ItemID.UNIDENTIFIED_GUAM),
	BONES("Bones", ResourceCategory.RAW, ItemID.BONES),

	BARS("Bars", ResourceCategory.REFINED, ItemID.STEEL_BAR),
	CHARCOAL("Charcoal", ResourceCategory.REFINED, ItemID.CHARCOAL),
	PLANKS("Planks", ResourceCategory.REFINED, ItemID.WOODPLANK),
	LEATHER("Leather", ResourceCategory.REFINED, ItemID.LEATHER),
	ARROWS("Arrows", ResourceCategory.REFINED, ItemID.BRONZE_ARROW_4),
	RATIONS("Rations", ResourceCategory.REFINED, ItemID.BREAD),
	POTIONS("Potions", ResourceCategory.REFINED, ItemID._3DOSEPRAYERRESTORE),
	RUNES("Runes", ResourceCategory.REFINED, ItemID.NATURERUNE),
	ENCHANTMENTS("Enchantments", ResourceCategory.SPECIAL, ItemID.SCROLL_CHARGE_DRAGONSTONE),
	BLESSINGS("Blessings", ResourceCategory.REFINED, ItemID.HOLY_WATER),

	GEMS("Gems", ResourceCategory.SPECIAL, ItemID.UNCUT_SAPPHIRE),
	COINS("Coins", ResourceCategory.SPECIAL, ItemID.COINS_1000),
	MARKS("Marks", ResourceCategory.SPECIAL, ItemID.GRACE),
	CARGO("Cargo", ResourceCategory.SPECIAL, ItemID.SAILING_CHARTING_DRINK_CRATE_SMUGGLED_RUM),

	CURIOS("Curios", ResourceCategory.SPOILS, ItemID.CASKET),
	MONSTER_PARTS("Basic Monster Parts", ResourceCategory.SPOILS, ItemID.LIZARDMAN_FANG),
	RARE_MONSTER_PARTS("Rare Monster Parts", ResourceCategory.SPOILS, ItemID.MAGIC_FANG),
	EPIC_MONSTER_PARTS("Epic Monster Parts", ResourceCategory.SPOILS, ItemID.NIHIL_SHARD),
	ARTIFACT("Artifact", ResourceCategory.SPOILS, ItemID.RAIDS_PRAYERSCROLL);

	private final String displayName;
	private final ResourceCategory category;
	private final int iconItemId;
}
