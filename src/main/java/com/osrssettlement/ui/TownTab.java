package com.osrssettlement.ui;

import com.osrssettlement.model.Building;
import com.osrssettlement.model.BuildingType;
import com.osrssettlement.model.SettlementState;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

class TownTab extends JPanel
{
	private static final Color SUBTAB_BACKGROUND = new Color(46, 46, 46);
	private final List<BuildingCard> cards = new ArrayList<>();

	TownTab(ItemManager itemManager, SettlementActions actions)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel display = new JPanel();
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		tabGroup.setOpaque(true);
		tabGroup.setBackground(SUBTAB_BACKGROUND);
		tabGroup.setBorder(new EmptyBorder(0, 0, 6, 0));
		Font tabFont = FontManager.getRunescapeSmallFont();
		MaterialTab standard = new MaterialTab("Standard", tabGroup,
			Ui.scroll(buildingList(BuildingType.STANDARD, itemManager, actions)));
		standard.setFont(tabFont);
		tabGroup.addTab(standard);
		MaterialTab special = new MaterialTab("Special", tabGroup,
			Ui.scroll(buildingList(BuildingType.SPECIAL, itemManager, actions)));
		special.setFont(tabFont);
		tabGroup.addTab(special);
		MaterialTab legendary = new MaterialTab("Legendary", tabGroup,
			Ui.scroll(buildingList(BuildingType.LEGENDARY, itemManager, actions)));
		legendary.setFont(tabFont);
		tabGroup.addTab(legendary);
		tabGroup.select(standard);

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}

	private JPanel buildingList(BuildingType type, ItemManager itemManager, SettlementActions actions)
	{
		JPanel list = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (Building building : Building.values())
		{
			if (building.getType() == type)
			{
				BuildingCard card = new BuildingCard(building, itemManager, actions);
				cards.add(card);
				list.add(card);
			}
		}
		return list;
	}

	void update(SettlementState state)
	{
		cards.forEach(card -> card.update(state));
	}
}
