package com.osrssettlement.tracking;

import com.osrssettlement.model.ClueTier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Recognises the game messages that reward the settlement: boss kill counts and clue completions.
 */
public final class ChatEventParser
{
	// Same pattern RuneLite's chat commands plugin uses to track kill counts
	private static final Pattern KILLCOUNT_PATTERN = Pattern.compile("Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? (?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?(?:<col=[0-9a-f]{6}>|@.+?@)(?<kc>[0-9,]+)</col>");
	private static final Pattern CLUE_PATTERN = Pattern.compile("You have completed [0-9,]+ ([a-z]+) Treasure Trails?");

	private ChatEventParser()
	{
	}

	/**
	 * @return the boss name of a kill count message, or null if the message is not one
	 */
	public static String parseKillCount(String message)
	{
		Matcher matcher = KILLCOUNT_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return null;
		}

		String pre = matcher.group("pre");
		String post = matcher.group("post");
		if ((pre == null || pre.isEmpty()) && (post == null || post.isEmpty()))
		{
			return null;
		}
		return Text.removeTags(matcher.group("boss")).trim();
	}

	/**
	 * @return the tier of a clue completion message, or null if the message is not one
	 */
	public static ClueTier parseClue(String message)
	{
		Matcher matcher = CLUE_PATTERN.matcher(Text.removeTags(message));
		if (!matcher.find())
		{
			return null;
		}
		return ClueTier.fromName(matcher.group(1));
	}
}
