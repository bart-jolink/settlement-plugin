package com.osrssettlement.ui;

import com.osrssettlement.engine.ExpeditionCatalog;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.SettlementState;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;

class TownTab extends JPanel
{
	private final List<BuildingCard> cards = new ArrayList<>();

	TownTab(ItemManager itemManager, SettlementActions actions)
	{
		setLayout(new DynamicGridLayout(0, 1, 0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(Ui.sectionTitle("Buildings"));
		List<Building> special = new ArrayList<>();
		for (Building building : Building.values())
		{
			if (building.isWonder())
			{
				continue;
			}
			if (ExpeditionCatalog.sourceOf(building) != null)
			{
				special.add(building);
			}
			else
			{
				addCard(new BuildingCard(building, itemManager, actions));
			}
		}

		add(Ui.sectionTitle("Special buildings"));
		special.forEach(building -> addCard(new BuildingCard(building, itemManager, actions)));
		addCard(new BuildingCard(Building.WONDER, itemManager, actions));
	}

	private void addCard(BuildingCard card)
	{
		cards.add(card);
		add(card);
	}

	void update(SettlementState state)
	{
		cards.forEach(card -> card.update(state));
	}
}
