package com.osrssettlement.ui;

import com.osrssettlement.engine.Balance;
import com.osrssettlement.model.ActiveEvent;
import com.osrssettlement.model.Boost;
import com.osrssettlement.model.Bounty;
import com.osrssettlement.model.SettlementState;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

class ActivityTab extends JPanel
{
	private final List<BountyCard> cards = new ArrayList<>();
	private final JPanel boostPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
	private final JLabel completedLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel rewardToast = Ui.label("", FontManager.getRunescapeBoldFont(), Ui.GOOD);
	private final List<EventBanner> eventBanners = new ArrayList<>();
	private final JPanel eventPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private final JPanel eventBlock = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
	private final JLabel eventsTitle = Ui.sectionTitle("Events");
	private final ExpeditionPanel expeditionPanel;
	private Timer rewardTimer;

	ActivityTab(ItemManager itemManager, SettlementActions actions)
	{
		setLayout(new DynamicGridLayout(0, 1, 0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		rewardToast.setHorizontalAlignment(SwingConstants.CENTER);
		rewardToast.setVisible(false);
		eventPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		eventBlock.setOpaque(false);
		eventBlock.add(eventPanel);
		boostPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		boostPanel.setBorder(new EmptyBorder(6, 6, 6, 6));
		boostPanel.add(Ui.label("Active boosts", FontManager.getRunescapeBoldFont(), Ui.GOOD));
		add(eventsTitle);
		add(eventBlock);
		add(rewardToast);

		add(Ui.sectionTitle("Bounties"));
		for (int i = 0; i < Balance.BOUNTY_SLOTS; i++)
		{
			BountyCard card = new BountyCard(actions);
			cards.add(card);
			add(card);
		}
		add(completedLabel);

		add(Ui.sectionTitle("Expeditions"));
		expeditionPanel = new ExpeditionPanel(itemManager, actions);
		add(expeditionPanel);
	}

	void showReward(String text)
	{
		if (rewardTimer != null)
		{
			rewardTimer.stop();
		}

		Color start = Ui.GOOD;
		Color end = ColorScheme.DARK_GRAY_COLOR;
		long startedAt = System.currentTimeMillis();
		rewardToast.setText(Ui.wrap(text));
		rewardToast.setForeground(start);
		rewardToast.setVisible(true);
		revalidate();

		rewardTimer = new Timer(35, event ->
		{
			double progress = Math.min(1, (System.currentTimeMillis() - startedAt) / 1100.0);
			int red = (int) (start.getRed() + (end.getRed() - start.getRed()) * progress);
			int green = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * progress);
			int blue = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * progress);
			rewardToast.setForeground(new Color(red, green, blue));
			if (progress >= 1)
			{
				rewardTimer.stop();
				rewardToast.setVisible(false);
				revalidate();
			}
		});
		rewardTimer.start();
	}

	void update(SettlementState state)
	{
		List<ActiveEvent> events = state.getEvents();
		while (eventBanners.size() < events.size())
		{
			EventBanner banner = new EventBanner();
			eventBanners.add(banner);
			eventPanel.add(banner);
		}
		while (eventBanners.size() > events.size())
		{
			eventPanel.remove(eventBanners.remove(eventBanners.size() - 1));
		}
		for (int i = 0; i < eventBanners.size(); i++)
		{
			eventBanners.get(i).update(state, i < events.size() ? events.get(i) : null);
		}
		List<Boost> boosts = state.getBoosts();
		if (boosts.isEmpty())
		{
			eventBlock.remove(boostPanel);
		}
		else if (boostPanel.getParent() != eventBlock)
		{
			eventBlock.add(boostPanel);
		}
		List<Bounty> bounties = state.getBounties();
		for (int i = 0; i < cards.size(); i++)
		{
			cards.get(i).update(i < bounties.size() ? bounties.get(i) : null, state);
		}

		boostPanel.removeAll();
		if (boosts.size() > 1)
		{
			boostPanel.add(Ui.label("Active boosts", FontManager.getRunescapeBoldFont(), Ui.GOOD));
		}
		for (Boost boost : boosts)
		{
			String prefix = boosts.size() == 1 ? "Active boost: " : "";
			String text = prefix + String.format("%sx %s: %s left", formatMultiplier(boost.getMultiplier()),
				boost.getResource().getDisplayName(), Ui.formatPlaytime(boost.getRemainingTicks()));
			boostPanel.add(Ui.label(text, FontManager.getRunescapeSmallFont(), Ui.GOOD));
		}
		boostPanel.revalidate();
		boostPanel.repaint();
		eventPanel.revalidate();
		eventPanel.repaint();
		eventBlock.revalidate();
		eventBlock.repaint();

		completedLabel.setText("Bounties completed: " + state.getBountiesCompleted());
		expeditionPanel.update(state);
	}

	private static String formatMultiplier(double multiplier)
	{
		return multiplier == Math.rint(multiplier) ? String.valueOf((long) multiplier) : String.valueOf(multiplier);
	}
}
