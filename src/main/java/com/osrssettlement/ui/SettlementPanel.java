package com.osrssettlement.ui;

import com.osrssettlement.model.SettlementState;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Sidebar panel. All methods must be called on the Swing event dispatch thread.
 */
public class SettlementPanel extends PluginPanel
{
	private static final String CARD_TABS = "tabs";
	private static final String CARD_LOGGED_OUT = "loggedOut";
	private static final String CARD_PROTECTED = "protected";
	private final JLabel protectionLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);

	private final JLabel subtitleLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
	private final CardLayout bodyLayout = new CardLayout();
	private final JPanel body = new JPanel(bodyLayout);

	private final TownTab townTab;
	private final StockTab stockTab;
	private final ActivityTab activityTab;
	private final LogTab logTab;
	private final SettlementActions actions;

	private volatile boolean active;

	public SettlementPanel(ItemManager itemManager, SettlementActions actions)
	{
		super(false);
		this.actions = actions;
		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(8, 6, 6, 6));

		townTab = new TownTab(itemManager, actions);
		stockTab = new StockTab(itemManager, actions);
		activityTab = new ActivityTab(itemManager, actions);
		logTab = new LogTab(actions);

		JPanel header = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.add(Ui.label("Your Settlement", FontManager.getRunescapeBoldFont(), ColorScheme.BRAND_ORANGE));
		header.add(subtitleLabel);

		JPanel display = new JPanel();
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		tabGroup.setBorder(new EmptyBorder(0, 0, 6, 0));
		MaterialTab town = new MaterialTab("Town", tabGroup, townTab);
		tabGroup.addTab(town);
		MaterialTab stock = new MaterialTab("Stock", tabGroup, Ui.scroll(stockTab));
		tabGroup.addTab(stock);
		MaterialTab activities = new MaterialTab("Activities", tabGroup, Ui.scroll(activityTab));
		tabGroup.addTab(activities);
		MaterialTab log = new MaterialTab("Log", tabGroup, Ui.scroll(logTab));
		tabGroup.addTab(log);
		tabGroup.select(town);

		JPanel tabs = new JPanel(new BorderLayout());
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabs.add(tabGroup, BorderLayout.NORTH);
		tabs.add(display, BorderLayout.CENTER);

		JLabel loggedOut = Ui.label(Ui.wrap("Log in to visit your settlement. Every account has its own."),
			FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
		loggedOut.setVerticalAlignment(JLabel.TOP);

		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(tabs, CARD_TABS);
		body.add(loggedOut, CARD_LOGGED_OUT);
		JPanel protectedView = new JPanel(new DynamicGridLayout(0, 1, 0, 6));
		protectedView.setBackground(ColorScheme.DARK_GRAY_COLOR);
		protectedView.add(protectionLabel);
		JButton retry = new JButton("Retry");
		retry.setFocusPainted(false);
		retry.addActionListener(event -> actions.retry());
		protectedView.add(retry);
		body.add(protectedView, CARD_PROTECTED);

		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);

		update((SettlementState) null);
	}

	@Override
	public void onActivate()
	{
		active = true;
		actions.refresh();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}

	/**
	 * @return whether the panel is open in the sidebar; safe to call from any thread
	 */
	public boolean isActive()
	{
		return active;
	}

	/**
	 * @param state a snapshot that is not modified afterwards, or null when no account is loaded
	 */
	public void update(SettlementState state)
	{
		update(state, false);
	}

	public void update(SettlementState state, boolean canRestore)
	{
		if (state == null)
		{
			subtitleLabel.setText("No account loaded");
			bodyLayout.show(body, CARD_LOGGED_OUT);
			return;
		}

		bodyLayout.show(body, CARD_TABS);
		subtitleLabel.setText("");

		townTab.update(state);
		stockTab.update(state);
		activityTab.update(state);
		logTab.update(state, canRestore);
	}

	public void showRestoreError(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Restore cancelled", JOptionPane.WARNING_MESSAGE);
	}

	public void showBountyReward(String text)
	{
		activityTab.showReward(text);
	}

	public void showProtected(String message)
	{
		subtitleLabel.setText("Progress protected");
		protectionLabel.setText(Ui.wrap(message));
		bodyLayout.show(body, CARD_PROTECTED);
	}

}
