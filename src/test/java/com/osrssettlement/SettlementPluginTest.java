package com.osrssettlement;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SettlementPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SettlementPlugin.class);
		RuneLite.main(args);
	}
}
