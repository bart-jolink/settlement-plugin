package com.osrssettlement.ui;

import com.osrssettlement.model.LogEntry;
import com.osrssettlement.model.SettlementState;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

class LogTab extends JPanel
{
	private final JLabel playtimeLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
	private final JPanel entries = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
	private final JButton restoreButton = new JButton("Restore settlement");
	private List<LogEntry> shown = List.of();

	LogTab(SettlementActions actions)
	{
		setLayout(new DynamicGridLayout(0, 1, 0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(playtimeLabel);
		entries.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(entries);

		JButton resetButton = new JButton("Reset settlement");
		resetButton.setFocusPainted(false);
		resetButton.addActionListener(e ->
		{
			Runnable confirmedReset = actions.prepareReset();
			int choice = JOptionPane.showConfirmDialog(this,
				"Start over for this account? Your current progress will become the restore point.\n"
					+ "Any previous restore point will be removed and replaced.",
				"Reset settlement", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice == JOptionPane.YES_OPTION)
			{
				confirmedReset.run();
			}
		});
		add(resetButton);
		restoreButton.setFocusPainted(false);
		restoreButton.setEnabled(false);
		restoreButton.addActionListener(event ->
		{
			Runnable confirmedRestore = actions.prepareRestore();
			int choice = JOptionPane.showConfirmDialog(this,
				"Restore the saved settlement for this account?\n"
					+ "Your current progress will become the new restore point, so you can swap back.",
				"Restore settlement", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice == JOptionPane.YES_OPTION)
			{
				confirmedRestore.run();
			}
		});
		add(restoreButton);
	}

	void update(SettlementState state, boolean canRestore)
	{
		playtimeLabel.setText("Playtime: " + Ui.formatPlaytime(state.getPlaytimeTicks()));
		restoreButton.setEnabled(canRestore);
		restoreButton.setToolTipText(canRestore ? null : "No valid restore point is available.");
		if (state.getLog().equals(shown))
		{
			return;
		}
		shown = new ArrayList<>(state.getLog());

		entries.removeAll();
		for (LogEntry entry : shown)
		{
			JPanel row = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			row.setBorder(new EmptyBorder(4, 6, 4, 6));
			row.add(Ui.label(Ui.formatPlaytime(entry.getPlaytimeTicks()), FontManager.getRunescapeSmallFont(),
				ColorScheme.BRAND_ORANGE));
			JLabel message = Ui.label(Ui.wrap(entry.getMessage()), FontManager.getRunescapeSmallFont(), ColorScheme.TEXT_COLOR);
			row.add(message);
			entries.add(row);
		}
		entries.revalidate();
		entries.repaint();
	}
}
