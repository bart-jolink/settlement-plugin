package com.osrssettlement.ui;

import com.osrssettlement.engine.MarketService;
import com.osrssettlement.model.Building;
import com.osrssettlement.model.MarketCategory;
import com.osrssettlement.model.Resource;
import com.osrssettlement.model.SettlementState;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

class MarketExchange extends JPanel
{
	private final List<TradeRow> rows = new ArrayList<>();
	private final JLabel lockedLabel = Ui.label(Ui.wrap("Build the Keldagrim Consortium to trade."),
		FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
	private SettlementState state;
	private int renderedOutputAmount = -1;

	MarketExchange(ItemManager itemManager, SettlementActions actions)
	{
		setLayout(new DynamicGridLayout(0, 1, 0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(Ui.sectionTitle("Keldagrim Consortium"));
		add(lockedLabel);

		for (MarketCategory category : MarketCategory.values())
		{
			TradeRow row = new TradeRow(itemManager, actions, category);
			rows.add(row);
			add(Ui.label(category.getDisplayName(), FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR));
			add(row.selectors);
			add(row.tradeButton);
		}
	}

	void update(SettlementState state)
	{
		this.state = state;
		boolean locked = state.getLevel(Building.KELDAGRIM_CONSORTIUM) == 0;
		if (locked && lockedLabel.getParent() != this)
		{
			add(lockedLabel, 1);
			revalidate();
		}
		else if (!locked && lockedLabel.getParent() == this)
		{
			remove(lockedLabel);
			revalidate();
		}
		int outputAmount = MarketService.outputAmount(state.getLevel(Building.KELDAGRIM_CONSORTIUM));
		if (outputAmount != renderedOutputAmount)
		{
			rows.forEach(row -> row.updateOutputAmount(outputAmount));
			renderedOutputAmount = outputAmount;
		}
		rows.forEach(TradeRow::refresh);
	}

	/**
	 * Two dropdowns for one category: always trades from the left material into the right one.
	 */
	private final class TradeRow
	{
		private final JComboBox<Resource> input = new JComboBox<>();
		private final JComboBox<Resource> output = new JComboBox<>();
		private final JPanel selectors = new JPanel(new GridBagLayout());
		private final JButton tradeButton = new JButton("Trade");
		private final ResourceRenderer outputRenderer;

		TradeRow(ItemManager itemManager, SettlementActions actions, MarketCategory category)
		{
			for (Resource resource : category.getResources())
			{
				input.addItem(resource);
				output.addItem(resource);
			}
			output.setSelectedIndex(1);
			input.setRenderer(new ResourceRenderer(itemManager, input, MarketService.inputAmount(category)));
			outputRenderer = new ResourceRenderer(itemManager, output, MarketService.outputAmount(0));
			output.setRenderer(outputRenderer);
			input.setFocusable(false);
			output.setFocusable(false);
			input.addActionListener(event -> refresh());
			output.addActionListener(event -> refresh());

			selectors.setOpaque(false);
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			c.weightx = 1;
			selectors.add(input, c);
			c.weightx = 0;
			c.fill = GridBagConstraints.NONE;
			c.insets.left = 4;
			c.insets.right = 4;
			selectors.add(new JLabel(new ArrowIcon()), c);
			c.weightx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			c.insets.left = 0;
			c.insets.right = 0;
			selectors.add(output, c);

			tradeButton.setFocusPainted(false);
			tradeButton.addActionListener(event ->
			{
				Resource from = (Resource) input.getSelectedItem();
				Resource to = (Resource) output.getSelectedItem();
				if (from != null && to != null)
				{
					actions.tradeKeldagrimConsortium(from, to);
				}
			});
			refresh();
		}

		void refresh()
		{
			Resource from = (Resource) input.getSelectedItem();
			Resource to = (Resource) output.getSelectedItem();
			boolean valid = from != null && to != null && MarketService.isValidPair(from, to);
			int consortiumLevel = state == null ? 0 : state.getLevel(Building.KELDAGRIM_CONSORTIUM);
			tradeButton.setToolTipText(valid
				? "Exchange " + MarketService.describe(consortiumLevel, from, to)
				: "Pick two different materials");
			tradeButton.setEnabled(valid && state != null && MarketService.canTrade(state, from, to));
		}

		void updateOutputAmount(int amount)
		{
			outputRenderer.setAmount(amount);
		}
	}

	/**
	 * Shows each material as its item sprite with the traded amount drawn on it.
	 */
	private static final class ResourceRenderer implements ListCellRenderer<Resource>
	{
		private final Map<Resource, JLabel> cells = new EnumMap<>(Resource.class);
		private final ItemManager itemManager;
		private final JComboBox<Resource> selector;
		private int amount;

		ResourceRenderer(ItemManager itemManager, JComboBox<Resource> selector, int amount)
		{
			this.itemManager = itemManager;
			this.selector = selector;
			for (int i = 0; i < selector.getItemCount(); i++)
			{
				Resource resource = selector.getItemAt(i);
				JLabel cell = new JLabel();
				cell.setOpaque(true);
				cells.put(resource, cell);
			}
			setAmount(amount);
		}

		void setAmount(int amount)
		{
			if (this.amount == amount)
			{
				return;
			}
			this.amount = amount;
			cells.forEach((resource, cell) ->
			{
				cell.setToolTipText(String.format("%,d %s", amount, resource.getDisplayName()));
				AsyncBufferedImage image = itemManager.getImage(resource.getIconItemId(), amount, true);
				cell.setIcon(image == null ? null : new ImageIcon(image));
				if (image != null)
				{
					image.onLoaded(selector::repaint);
				}
			});
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Resource> list, Resource value, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			JLabel cell = value == null ? null : cells.get(value);
			if (cell == null)
			{
				return new JLabel();
			}
			cell.setBackground(isSelected && index >= 0 ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			return cell;
		}
	}

	private static final class ArrowIcon implements Icon
	{
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g2.setStroke(new BasicStroke(2f));
			int middle = y + getIconHeight() / 2;
			g2.drawLine(x, middle, x + 7, middle);
			g2.fillPolygon(new int[]{x + 6, x + 12, x + 6}, new int[]{middle - 4, middle, middle + 4}, 3);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 12;
		}

		@Override
		public int getIconHeight()
		{
			return 10;
		}
	}
}