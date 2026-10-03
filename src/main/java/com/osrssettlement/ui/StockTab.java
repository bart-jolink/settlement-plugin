package com.osrssettlement.ui;

import com.osrssettlement.model.Resource;
import com.osrssettlement.model.ResourceCategory;
import com.osrssettlement.model.SettlementState;
import com.osrssettlement.model.SkillRule;
import com.osrssettlement.model.SkillRules;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.JToggleButton;
import javax.swing.Icon;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

class StockTab extends JPanel
{
	private final Map<Resource, JLabel> cells = new EnumMap<>(Resource.class);
	private final JPanel labourPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	private final JToggleButton labourPauseButton = new JToggleButton();
	private final MarketExchange marketExchange;

	StockTab(ItemManager itemManager, SettlementActions actions)
	{
		setLayout(new DynamicGridLayout(0, 1, 0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (ResourceCategory category : ResourceCategory.values())
		{
			add(Ui.sectionTitle(category.getDisplayName()));

			JPanel grid = new JPanel(new GridLayout(0, 4, 2, 2));
			grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
			for (Resource resource : Resource.values())
			{
				if (resource.getCategory() == category)
				{
					JLabel cell = Ui.label("0", FontManager.getRunescapeSmallFont(), ColorScheme.TEXT_COLOR);
					cell.setOpaque(true);
					cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
					cell.setHorizontalAlignment(SwingConstants.CENTER);
					cell.setHorizontalTextPosition(SwingConstants.CENTER);
					cell.setVerticalTextPosition(SwingConstants.BOTTOM);
					itemManager.getImage(resource.getIconItemId()).addTo(cell);
					grid.add(cell);
					cells.put(resource, cell);
				}
			}
			add(grid);
		}

		add(Ui.sectionTitle("Labour"));
		labourPauseButton.setFocusPainted(false);
		labourPauseButton.setIcon(new LabourIcon(false));
		labourPauseButton.setToolTipText("Pause automatic use of queued processing work");
		labourPauseButton.getAccessibleContext().setAccessibleName("Pause queued labour");
		labourPauseButton.addActionListener(event -> actions.setLabourPaused(labourPauseButton.isSelected()));
		add(labourPauseButton);
		labourPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(labourPanel);

		marketExchange = new MarketExchange(itemManager, actions);
		add(marketExchange);
	}

	void update(SettlementState state)
	{
		boolean paused = state.isLabourPaused();
		labourPauseButton.setSelected(paused);
		labourPauseButton.setIcon(new LabourIcon(paused));
		labourPauseButton.setToolTipText(paused
			? "Resume automatic use of queued processing work"
			: "Pause automatic use of queued processing work");
		labourPauseButton.getAccessibleContext().setAccessibleName(paused
			? "Resume queued labour" : "Pause queued labour");
		cells.forEach((resource, cell) ->
		{
			long amount = (long) Math.floor(state.getStock(resource));
			cell.setText(QuantityFormatter.quantityToStackSize(amount));
			cell.setToolTipText(String.format("%s: %,d (%s)", resource.getDisplayName(), amount, sourceOf(resource)));
		});

		labourPanel.removeAll();
		List<Skill> waiting = new ArrayList<>(state.getLabour().keySet());
		waiting.sort(null);
		for (Skill skill : waiting)
		{
			SkillRule rule = SkillRules.get(skill);
			long labour = (long) Math.floor(state.getLabour(skill));
			if (rule == null || labour <= 0)
			{
				continue;
			}
			String text = String.format("%s: %,d waiting for %s", skill.getName(), labour,
				rule.getInputs().get(0).getDisplayName());
			labourPanel.add(Ui.label(Ui.wrap(text), FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR));
		}
		if (labourPanel.getComponentCount() == 0)
		{
			labourPanel.add(Ui.label(Ui.wrap("Processing skills store their work here when raw materials run out."),
				FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR));
		}
		labourPanel.revalidate();
		labourPanel.repaint();

		marketExchange.update(state);
	}

	private static String sourceOf(Resource resource)
	{
		List<String> sources = new ArrayList<>();
		List<String> requiredGoods = new ArrayList<>();
		for (SkillRule rule : SkillRules.all())
		{
			if (rule.getOutputs().containsKey(resource))
			{
				sources.add(rule.getSkill().getName());
				if (rule.isProcessing())
				{
					rule.getInputs().forEach(input -> requiredGoods.add(input.getDisplayName()));
				}
			}
		}

		switch (resource)
		{
			case GEMS:
				return "Quarry & Thieves' Guild byproduct";
			case CURIOS:
				return "clue scrolls";
			case MONSTER_PARTS:
				return "Slayer XP, bosses, hard and elite clues, bounties";
			case RARE_MONSTER_PARTS:
				return "bosses, elite clues, master clues, bounties";
			case EPIC_MONSTER_PARTS:
				return "bosses, master clues.";
			case ARTIFACT:
				return "raids and rare bounties.";
			default:
				String source = sources.isEmpty() ? "bounties" : String.join(", ", sources);
				return requiredGoods.isEmpty() ? source
					: source + "; requires " + String.join(", ", requiredGoods);
		}
	}

	private static final class LabourIcon implements Icon
	{
		private final boolean paused;

		private LabourIcon(boolean paused)
		{
			this.paused = paused;
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(ColorScheme.TEXT_COLOR);
			if (paused)
			{
				g.fillPolygon(new Polygon(new int[]{x + 4, x + 14, x + 4},
					new int[]{y + 2, y + 8, y + 14}, 3));
			}
			else
			{
				g.fillRect(x + 3, y + 2, 4, 12);
				g.fillRect(x + 10, y + 2, 4, 12);
			}
			g.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 18;
		}

		@Override
		public int getIconHeight()
		{
			return 16;
		}
	}
}
