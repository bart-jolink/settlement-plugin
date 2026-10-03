package com.osrssettlement.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.osrssettlement.model.BossTier;
import com.osrssettlement.model.ClueTier;
import org.junit.Test;

public class ChatEventParserTest
{
	private static BossTier bossTier(String message)
	{
		String boss = ChatEventParser.parseKillCount(message);
		return boss == null ? null : BossRegistry.tierOf(boss);
	}

	@Test
	public void parsesBossKillCounts()
	{
		assertEquals("Zulrah", ChatEventParser.parseKillCount("Your Zulrah kill count is: <col=ff0000>4</col>."));
		assertEquals("Nightmare", ChatEventParser.parseKillCount("Your Nightmare kill count is: <col=ff0000>1,130</col>"));
		assertEquals("Kalphite Queen (Echo)",
			ChatEventParser.parseKillCount("Your <col=6800bf>Kalphite Queen (Echo)</col> kill count is:<col=e00a19>1</col>"));
	}

	@Test
	public void assignsBossTiers()
	{
		assertEquals(BossTier.TIER_1, bossTier("Your Barrows chest count is: <col=ff0000>277</col>."));
		assertEquals(BossTier.TIER_1, bossTier("Your <col=6800bf>Kalphite Queen (Echo)</col> kill count is:<col=e00a19>1</col>"));
		assertEquals(BossTier.TIER_1, bossTier("Your subdued Wintertodt count is: <col=ff0000>4</col>."));
		assertEquals(BossTier.TIER_1, bossTier("Your Tempoross kill count is: <col=ff0000>60</col>."));
		assertEquals(BossTier.TIER_2, bossTier("Your Zulrah kill count is: <col=ff0000>4</col>."));
		assertEquals(BossTier.TIER_1, bossTier("Your Zalcano kill count is: <col=ff0000>4</col>."));
		assertEquals(BossTier.TIER_2, bossTier("Your Gauntlet completion count is: <col=ff0000>123</col>."));
		assertEquals(BossTier.TIER_3, bossTier("Your Corrupted Gauntlet completion count is: <col=ff0000>4729</col>."));
		assertEquals(BossTier.TIER_3, bossTier("Your completed Chambers of Xeric count is: <col=ff0000>51</col>."));
		assertEquals(BossTier.TIER_3, bossTier("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>13</col>."));
		assertEquals(BossTier.TIER_3, bossTier("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>1</col>."));
		assertEquals(BossTier.TIER_2, bossTier("Your completed Theatre of Blood: Entry Mode count is: <col=ff0000>73</col>."));
		assertEquals(BossTier.TIER_3, bossTier("Your TzKal-Zuk kill count is: <col=ff0000>2</col>."));
	}

	@Test
	public void identifiesRaidsSeparatelyFromOtherBosses()
	{
		assertTrue(BossRegistry.isRaid("Chambers of Xeric Challenge Mode"));
		assertTrue(BossRegistry.isRaid("Theatre of Blood: Entry Mode"));
		assertTrue(BossRegistry.isRaid("Tombs of Amascut: Expert Mode"));
		assertFalse(BossRegistry.isRaid("Nex"));
		assertFalse(BossRegistry.isRaid("Corrupted Gauntlet"));
	}

	@Test
	public void ignoresNonBossCounts()
	{
		assertNull(bossTier("Your Prifddinas Agility Course lap count is: @mes_hl_red@2</col>."));
		assertNull(bossTier("Your herbiboar harvest count is: <col=ff0000>4091</col>."));
		assertNull(bossTier("Your completion count for TzHaar-Ket-Rak's First Challenge is: <col=ff0000>1</col>."));
		assertNull(bossTier("Your Agility Arena Total Ticket count is: <col=ff0000>206</col>."));
		assertNull(ChatEventParser.parseKillCount("You feel refreshed."));
	}

	@Test
	public void parsesClueCompletions()
	{
		assertEquals(ClueTier.MEDIUM, ChatEventParser.parseClue("<col=3300ff>You have completed 2,823 medium Treasure Trails</col>"));
		assertEquals(ClueTier.BEGINNER, ChatEventParser.parseClue("You have completed 1 beginner Treasure Trail."));
		assertEquals(ClueTier.MASTER, ChatEventParser.parseClue("You have completed 12 master Treasure Trails."));
		assertNull(ChatEventParser.parseClue("You receive a clue scroll."));
	}
}
