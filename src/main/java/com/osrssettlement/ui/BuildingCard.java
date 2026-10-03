package com.osrssettlement.ui;

import com.osrssettlement.engine.Balance;
import com.osrssettlement.engine.BuildingService;
import com.osrssettlement.engine.Requirement;
import com.osrssettlement.engine.UpgradeCheck;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementState;
import com.osrssettlement.model.SkillKind;
import com.osrssettlement.model.SkillRule;
import com.osrssettlement.model.SkillRules;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

class BuildingCard extends JPanel
{
	private final Building building;
	private final ItemManager itemManager;
	private final boolean processing;

	private final JLabel nameLabel = Ui.label("", FontManager.getRunescapeBoldFont(), ColorScheme.TEXT_COLOR);
	private final JLabel levelLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel effectLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.TEXT_COLOR);
	private final JPanel requirementsPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 1));
	private final List<JLabel> requirementLabels = new ArrayList<>();
	private final JPanel costPanel = new JPanel(new GridLayout(0, 5, 2, 2));
	private final Map<Resource, JLabel> costLabels = new EnumMap<>(Resource.class);
	private final JButton upgradeButton = new JButton();
	private final JPanel buttonRow = new JPanel(new BorderLayout());
	private Map<Resource, Integer> shownCost = Map.of();
	private int shownCostLevel = -1;

	BuildingCard(Building building, ItemManager itemManager, SettlementActions actions)
	{
		this.building = building;
		this.itemManager = itemManager;
		this.processing = SkillRules.all().stream()
			.anyMatch(rule -> rule.getBuilding() == building && rule.getKind() == SkillKind.PROCESSING);

		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(6, 6, 6, 6));

		JLabel icon = new JLabel();
		itemManager.getImage(building.getIconItemId()).addTo(icon);
		icon.setBorder(new EmptyBorder(0, 0, 0, 6));

		JPanel titles = new JPanel();
		titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
		titles.setOpaque(false);
		titles.add(nameLabel);
		titles.add(levelLabel);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(icon, BorderLayout.WEST);
		header.add(titles, BorderLayout.CENTER);

		JLabel descriptionLabel = Ui.label(Ui.wrap(building.getDescription()), FontManager.getRunescapeSmallFont(),
			ColorScheme.LIGHT_GRAY_COLOR);

		JPanel body = new JPanel(new BorderLayout(0, 2));
		body.setOpaque(false);
		body.add(descriptionLabel, BorderLayout.NORTH);
		body.add(effectLabel, BorderLayout.SOUTH);

		costPanel.setOpaque(false);
		requirementsPanel.setOpaque(false);
		requirementsPanel.setVisible(false);

		upgradeButton.setFocusPainted(false);
		upgradeButton.addActionListener(e -> actions.upgrade(building));

		buttonRow.setOpaque(false);
		buttonRow.add(upgradeButton, BorderLayout.CENTER);

		JPanel footer = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		footer.setOpaque(false);
		footer.add(requirementsPanel);
		footer.add(costPanel);
		footer.add(buttonRow);

		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
		add(footer, BorderLayout.SOUTH);
	}

	void update(SettlementState state)
	{
		int level = state.getLevel(building);
		boolean maxed = level >= BuildingService.maxLevel(building);

		nameLabel.setText(building.getDisplayName());
		if (building.isWonder())
		{
			levelLabel.setText("Level " + level + " / " + Balance.MAX_BUILDING_LEVEL);
		}
		else
		{
			levelLabel.setText(level == 0 ? "Not built" : "Level " + level + " / " + Balance.MAX_BUILDING_LEVEL);
		}
		effectLabel.setText(Ui.wrap(effectText(level)));

		if (level != shownCostLevel)
		{
			rebuildCosts(maxed ? Map.of() : BuildingService.nextCost(state, building));
			shownCostLevel = level;
		}
		costLabels.forEach((resource, label) ->
		{
			int amount = shownCost.get(resource);
			long have = (long) Math.floor(state.getStock(resource));
			CostIcon icon = (CostIcon) label.getIcon();
			Color color = have >= amount ? Ui.GOOD : Ui.BAD;
			if (icon.color != color)
			{
				icon.color = color;
				label.repaint();
			}
			label.setToolTipText(String.format("%,d %s (you have %,d)", amount, resource.getDisplayName(), have));
		});

		UpgradeCheck check = BuildingService.check(state, building);
		buttonRow.setVisible(!maxed);
		upgradeButton.setText(level == 0 ? "Build" : "Upgrade to level " + (level + 1));
		upgradeButton.setEnabled(check.isOk());
		upgradeButton.setToolTipText(check.isOk() ? null : check.getReason());

		updateRequirements(BuildingService.requirements(state, building));
	}

	private void updateRequirements(List<Requirement> requirements)
	{
		if (requirements.size() != requirementLabels.size())
		{
			requirementsPanel.removeAll();
			requirementLabels.clear();
			if (!requirements.isEmpty())
			{
				requirementsPanel.add(Ui.label("Requires:", FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR));
			}
			for (int i = 0; i < requirements.size(); i++)
			{
				JLabel label = Ui.label("", FontManager.getRunescapeSmallFont(), Ui.BAD);
				requirementLabels.add(label);
				requirementsPanel.add(label);
			}
			requirementsPanel.setVisible(!requirements.isEmpty());
			requirementsPanel.revalidate();
		}

		for (int i = 0; i < requirements.size(); i++)
		{
			Requirement requirement = requirements.get(i);
			JLabel label = requirementLabels.get(i);
			label.setText(Ui.wrap(requirement.getDescription()));
			label.setForeground(requirement.isMet() ? Ui.GOOD : Ui.BAD);
		}
	}

	private void rebuildCosts(Map<Resource, Integer> cost)
	{
		costPanel.removeAll();
		costLabels.clear();
		shownCost = cost;
		cost.forEach((resource, amount) ->
		{
			AsyncBufferedImage image = itemManager.getImage(resource.getIconItemId());
			JLabel label = new JLabel(new CostIcon(image, QuantityFormatter.quantityToRSDecimalStack(amount)));
			image.onLoaded(label::repaint);
			costPanel.add(label);
			costLabels.put(resource, label);
		});
		costPanel.setVisible(!cost.isEmpty());
		costPanel.revalidate();
	}

	private String effectText(int level)
	{
		switch (building)
		{
			case TOWN_HALL:
				return String.format("Now: +%d%% to all yields, buildings up to level %d.",
					Math.round(Balance.TOWN_HALL_GLOBAL_BONUS * 100 * level), level);
			case WONDER:
				return level == 0
					? String.format("Each level: +%d%% to all yields.", Math.round(Balance.WONDER_GLOBAL_BONUS * 100))
					: String.format("Now: +%d%% to all yields.", Math.round(Balance.WONDER_GLOBAL_BONUS * 100 * level));
			case ALTAR:
				return String.format("Now: +%d%% to all yields.", Math.round(Balance.ALTAR_GLOBAL_BONUS * 100 * level));
			case TROPHY_HALL:
				return String.format("Now: +%d%% boss part and raid Artifact output.",
					Math.round(Balance.TROPHY_BONUS_PER_LEVEL * 100 * level));
			case TREASURY:
				return String.format("Now: +%d%% clue scroll rewards.", Math.round(Balance.TREASURY_BONUS_PER_LEVEL * 100 * level));
			case KELDAGRIM_CONSORTIUM:
				return "Unlocks the Keldagrim Consortium in the Stock tab.";
			case MUSEUM_CAMP:
				return String.format("Now: +%d%% bounty supply rewards.",
					Math.round(Balance.MUSEUM_BOUNTY_BONUS_PER_LEVEL * 100 * level));
			case ARCEUUS_LIBRARY:
				return String.format("Now: bounty boosts last +%d%% longer.",
					Math.round(Balance.LIBRARY_BOOST_DURATION_PER_LEVEL * 100 * level));
			case JALTEVAS_PYRAMID:
				return String.format("Now: positive events +%d%% stronger.",
					Math.round(Balance.PYRAMID_EVENT_BONUS_PER_LEVEL * 100 * level));
			case TOWER_OF_VOICES:
				return String.format("Now: +%d%% to all yields.", Math.round(Balance.TOWER_OF_VOICES_GLOBAL_BONUS * 100 * level));
			default:
				double perLevel = processing ? Balance.PROCESS_BONUS_PER_LEVEL : Balance.GATHER_BONUS_PER_LEVEL;
				return String.format("Now: +%d%% %s from %s.", Math.round(perLevel * 100 * level),
					processing ? "output" : "yield", skillNames());
		}
	}

	private String skillNames()
	{
		StringBuilder names = new StringBuilder();
		for (SkillRule rule : SkillRules.all())
		{
			if (rule.getBuilding() == building)
			{
				if (names.length() > 0)
				{
					names.append(" & ");
				}
				names.append(rule.getSkill().getName());
			}
		}
		return names.toString();
	}

	/**
	 * Item sprite with an inventory-style stack number, coloured by whether the cost is affordable.
	 */
	private static final class CostIcon implements Icon
	{
		private final BufferedImage image;
		private final String text;
		private Color color = Ui.BAD;

		CostIcon(BufferedImage image, String text)
		{
			this.image = image;
			this.text = text;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.drawImage(image, x, y, null);
			g2.setFont(FontManager.getRunescapeSmallFont());
			int baseline = y + g2.getFontMetrics().getAscent();
			g2.setColor(Color.BLACK);
			g2.drawString(text, x + 1, baseline + 1);
			g2.setColor(color);
			g2.drawString(text, x, baseline);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return image.getWidth();
		}

		@Override
		public int getIconHeight()
		{
			return image.getHeight();
		}
	}
}
