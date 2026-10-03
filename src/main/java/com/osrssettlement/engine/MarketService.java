package com.osrssettlement.engine;

import com.osrssettlement.model.Building;
import com.osrssettlement.model.MarketCategory;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementState;

public final class MarketService
{
	private MarketService()
	{
	}

	public static int inputAmount(MarketCategory category)
	{
		return category == MarketCategory.RAW
			? Balance.KELDAGRIM_CONSORTIUM_INPUT_AMOUNT
			: Balance.KELDAGRIM_CONSORTIUM_PROCESSED_INPUT_AMOUNT;
	}

	public static int outputAmount(int consortiumLevel)
	{
		return (int) Math.round(Balance.KELDAGRIM_CONSORTIUM_OUTPUT_AMOUNT
			* (1 + Balance.KELDAGRIM_CONSORTIUM_OUTPUT_BONUS_PER_LEVEL * consortiumLevel));
	}

	/**
	 * Both resources must be different and belong to the same category.
	 */
	public static boolean isValidPair(Resource input, Resource output)
	{
		MarketCategory category = MarketCategory.of(input);
		return category != null && input != output && MarketCategory.of(output) == category;
	}

	public static String describe(int consortiumLevel, Resource input, Resource output)
	{
		int bonusPercent = (int) Math.round(Balance.KELDAGRIM_CONSORTIUM_OUTPUT_BONUS_PER_LEVEL * consortiumLevel * 100);
		int bonusPerLevelPercent = (int) Math.round(Balance.KELDAGRIM_CONSORTIUM_OUTPUT_BONUS_PER_LEVEL * 100);
		return String.format("%,d %s for %,d %s (+%d%% output; +%d%% per Keldagrim Consortium level)",
			inputAmount(MarketCategory.of(input)), input.getDisplayName(),
			outputAmount(consortiumLevel), output.getDisplayName(), bonusPercent, bonusPerLevelPercent);
	}

	public static boolean canTrade(SettlementState state, Resource input, Resource output)
	{
		return state.getLevel(Building.KELDAGRIM_CONSORTIUM) > 0
			&& isValidPair(input, output)
			&& state.getStock(input) >= inputAmount(MarketCategory.of(input));
	}

	public static boolean trade(SettlementState state, Resource input, Resource output)
	{
		if (!canTrade(state, input, output))
		{
			return false;
		}

		state.addStock(input, -inputAmount(MarketCategory.of(input)));
		state.addStock(output, outputAmount(state.getLevel(Building.KELDAGRIM_CONSORTIUM)));
		return true;
	}
}