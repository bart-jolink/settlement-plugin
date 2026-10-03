package com.osrssettlement.tracking;

import com.osrssettlement.model.BossTier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Allowlist of bosses whose kill count messages grant settlement rewards. Kill count messages also exist
 * for agility laps, Wintertodt, Tempoross, etc.; anything not listed here is ignored.
 */
public final class BossRegistry
{
	private static final Map<String, BossTier> TIERS = new HashMap<>();
	private static final Set<String> RAIDS = Set.of("chambers of xeric", "theatre of blood", "tombs of amascut");
	private static final List<String> NAMES_LONGEST_FIRST;

	static
	{
		register(BossTier.TIER_1,
			"giant mole", "obor", "bryophyta", "scurrius", "sarachnis", "barrows", "deranged archaeologist",
			"crazy archaeologist", "chaos fanatic", "chaos elemental", "king black dragon", "kalphite queen",
			"dagannoth prime", "dagannoth rex", "dagannoth supreme", "skotizo", "hespori", "zalcano", "kraken",
			"tempoross",
			"thermonuclear smoke devil", "amoxliatl", "lunar", "royal titans", "mimic", "scorpia", "artio",
			"calvar'ion", "spindel", "brutus", "wintertodt");
		register(BossTier.TIER_2,
			"zulrah", "vorkath", "commander zilyana", "general graardor", "kree'arra", "k'ril tsutsaroth",
			"cerberus", "abyssal sire", "alchemical hydra", "grotesque guardians", "gauntlet", "phantom muspah",
			"callisto", "venenatis", "vet'ion", "corporeal beast", "nightmare", "duke sucellus", "leviathan",
			"whisperer", "vardorvis", "hueycoatl", "tztok-jad", "araxxor", "doom of mokhaiotl",
			"shellbane gryphon", "maggot king", "mad angel");
		register(BossTier.TIER_3,
			"chambers of xeric", "theatre of blood", "tombs of amascut", "nex", "corrupted gauntlet",
			"phosani's nightmare", "tzkal-zuk", "sol heredit", "yama");

		List<String> names = new ArrayList<>(TIERS.keySet());
		names.sort(Comparator.comparingInt(String::length).reversed());
		NAMES_LONGEST_FIRST = List.copyOf(names);
	}

	private BossRegistry()
	{
	}

	private static void register(BossTier tier, String... names)
	{
		for (String name : names)
		{
			TIERS.put(name, tier);
		}
	}

	/**
	 * @param bossName the name as shown in a kill count message, e.g. "Theatre of Blood: Entry Mode"
	 * @return the reward tier, or null if this is not a rewarded boss
	 */
	public static BossTier tierOf(String bossName)
	{
		String name = bossName.toLowerCase(Locale.ROOT).trim();
		if (name.startsWith("the "))
		{
			name = name.substring(4);
		}
		name = name.replace("(echo)", "").trim();

		boolean awakened = name.contains("(awakened)");
		name = name.replace("(awakened)", "").trim();

		boolean entryMode = name.contains("entry mode") || name.contains("story mode");

		for (String known : NAMES_LONGEST_FIRST)
		{
			if (name.startsWith(known))
			{
				BossTier tier = TIERS.get(known);
				if (awakened)
				{
					return BossTier.TIER_3;
				}
				if (entryMode && tier == BossTier.TIER_3)
				{
					return BossTier.TIER_2;
				}
				return tier;
			}
		}
		return null;
	}

	public static boolean isRaid(String bossName)
	{
		String name = bossName.toLowerCase(Locale.ROOT).trim();
		if (name.startsWith("the "))
		{
			name = name.substring(4);
		}
		for (String raid : RAIDS)
		{
			if (name.startsWith(raid))
			{
				return true;
			}
		}
		return false;
	}
}
