package com.osrssettlement.ui;

import com.osrssettlement.engine.YieldCalculator;
import com.osrssettlement.model.ActiveEvent;
import com.osrssettlement.model.SettlementEvent;
import com.osrssettlement.model.SettlementState;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

class EventBanner extends JPanel
{
	private final JLabel titleLabel = Ui.label("", FontManager.getRunescapeBoldFont(), Ui.GOOD);
	private final JLabel effectLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.TEXT_COLOR);
	private final JLabel flavourLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);

	EventBanner()
	{
		setLayout(new DynamicGridLayout(0, 1, 0, 2));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(6, 6, 6, 6));
		add(titleLabel);
		add(effectLabel);
		add(flavourLabel);
	}

	void update(SettlementState state, ActiveEvent active)
	{
		setVisible(active != null);
		if (active == null)
		{
			return;
		}

		SettlementEvent event = active.getEvent();
		long remaining = Math.max(0, active.getEndsAtPlaytime() - state.getPlaytimeTicks());
		titleLabel.setText(event.getDisplayName());
		titleLabel.setForeground(event.isBuff() ? Ui.GOOD : Ui.BAD);
		effectLabel.setText(Ui.wrap(event.describeEffect(YieldCalculator.effectiveEventMultiplier(state, event))
			+ "  |  " + Ui.formatPlaytime(remaining) + " left"));
		flavourLabel.setText(Ui.wrap(event.getFlavour()));
	}
}
