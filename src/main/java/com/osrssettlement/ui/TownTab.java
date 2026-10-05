package com.osrssettlement.ui;

import com.osrssettlement.model.Building;
import com.osrssettlement.model.BuildingType;
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

		List<Building> standard = new ArrayList<>();
		List<Building> special = new ArrayList<>();
		List<Building> legendary = new ArrayList<>();
		for (Building building : Building.values())
		{
			switch (building.getType())
			{
				case STANDARD:
					standard.add(building);
					break;
				case SPECIAL:
					special.add(building);
					break;
				case LEGENDARY:
					legendary.add(building);
					break;
			}
		}

		add(Ui.sectionTitle("Standard buildings"));
		standard.forEach(building -> addCard(new BuildingCard(building, itemManager, actions)));
		add(Ui.sectionTitle("Special buildings"));
		special.forEach(building -> addCard(new BuildingCard(building, itemManager, actions)));
		add(Ui.sectionTitle("Legendary buildings"));
		legendary.forEach(building -> addCard(new BuildingCard(building, itemManager, actions)));
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
