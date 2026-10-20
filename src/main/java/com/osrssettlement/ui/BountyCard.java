package com.osrssettlement.ui;

import com.osrssettlement.model.Bounty;
import com.osrssettlement.model.BountyRarity;
import com.osrssettlement.model.BountyType;
import com.osrssettlement.model.SettlementState;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ProgressBar;

class BountyCard extends JPanel
{
	private final JLabel titleLabel = Ui.label("", FontManager.getRunescapeBoldFont(), ColorScheme.TEXT_COLOR);
	private final JLabel rarityLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel rewardLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel expiryLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.MEDIUM_GRAY_COLOR);
	private final ProgressBar progressBar = new ProgressBar();
	private final JButton actionButton = new JButton();
	private final JButton declineButton = new JButton("Decline");
	private int bountyId = -1;
	private String bountyDescription;

	BountyCard(SettlementActions actions)
	{
		setLayout(new BorderLayout(0, 4));
		setBorder(new EmptyBorder(6, 6, 6, 6));
		rarityLabel.setOpaque(true);
		rarityLabel.setBorder(new EmptyBorder(1, 4, 1, 4));

		progressBar.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		progressBar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);

		actionButton.setFocusPainted(false);
		actionButton.addActionListener(e ->
		{
			if (bountyId >= 0)
			{
				actions.claimBounty(bountyId);
			}
		});

		declineButton.setFont(FontManager.getRunescapeSmallFont());
		declineButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		declineButton.setFocusPainted(false);
		declineButton.setMargin(new Insets(1, 4, 1, 4));
		declineButton.addActionListener(event ->
		{
			if (bountyId < 0)
			{
				return;
			}
			Runnable confirmedDecline = actions.prepareDeclineBounty(bountyId);
			Object[] options = {"Decline", "Cancel"};
			int choice = JOptionPane.showOptionDialog(this,
				"Decline this bounty?\n" + bountyDescription
					+ "\n\nYour supplies will be kept, but you will receive no reward.",
				"Decline bounty", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
				null, options, options[1]);
			if (choice == 0)
			{
				confirmedDecline.run();
			}
		});

		JPanel declineRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		declineRow.setOpaque(false);
		declineRow.add(declineButton);
		JPanel buttons = new JPanel(new BorderLayout(0, 4));
		buttons.setOpaque(false);
		buttons.add(actionButton, BorderLayout.NORTH);
		buttons.add(declineRow, BorderLayout.SOUTH);

		JPanel footer = new JPanel(new BorderLayout(0, 4));
		footer.setOpaque(false);
		footer.add(rewardLabel, BorderLayout.NORTH);
		footer.add(expiryLabel, BorderLayout.CENTER);
		footer.add(buttons, BorderLayout.SOUTH);

		JPanel header = new JPanel(new DynamicGridLayout(0, 1, 0, 2));
		header.setOpaque(false);
		JPanel rarityRow = new JPanel(new BorderLayout());
		rarityRow.setOpaque(false);
		rarityRow.add(rarityLabel, BorderLayout.EAST);
		header.add(rarityRow);
		header.add(titleLabel);
		add(header, BorderLayout.NORTH);
		add(progressBar, BorderLayout.CENTER);
		add(footer, BorderLayout.SOUTH);
	}

	void update(Bounty bounty, SettlementState state)
	{
		setVisible(bounty != null);
		if (bounty == null)
		{
			bountyId = -1;
			return;
		}

		bountyId = bounty.getId();
		bountyDescription = bounty.describe();
		titleLabel.setText(Ui.wrap(bountyDescription));
		rarityLabel.setText(bounty.getRarity().getDisplayName());
		setRarityColors(bounty.getRarity());

		boolean delivery = bounty.getType() == BountyType.DELIVER;
		double progress = delivery
			? Math.min(bounty.getTarget(), Math.floor(state.getStock(bounty.getResource())))
			: bounty.getProgress();
		boolean ready = progress >= bounty.getTarget();
		progressBar.setMaximumValue((int) Math.min(Integer.MAX_VALUE, bounty.getTarget()));
		progressBar.setValue((int) Math.min(Integer.MAX_VALUE, Math.floor(progress)));
		String progressLabel = bounty.getType() == BountyType.SLAYER_DROPS
			? String.format("%,.1f / %,d", progress, bounty.getTarget())
			: String.format("%,.0f / %,d", progress, bounty.getTarget());
		progressBar.setCenterLabel(progressLabel);
		progressBar.setForeground(progress >= bounty.getTarget()
			? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_INPROGRESS_COLOR);

		rewardLabel.setText(Ui.wrap("Reward: " + bounty.getReward().describe()));
		rewardLabel.setForeground(bounty.isBlueprint() ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		expiryLabel.setText(ready
			? "Ready to claim"
			: "Expires in " + Ui.formatPlaytime(Math.max(0, bounty.getExpiresAtPlaytime() - state.getPlaytimeTicks())) + " of play");

		actionButton.setText("Claim");
		actionButton.setToolTipText(delivery
			? String.format("Hands in %,d %s", bounty.getTarget(), bounty.getResource().getDisplayName())
			: null);
		actionButton.setVisible(ready);
		actionButton.setEnabled(ready);
		declineButton.setVisible(ready);
		declineButton.setEnabled(ready);
	}

	private void setRarityColors(BountyRarity rarity)
	{
		switch (rarity)
		{
			case RARE:
				setBackground(new Color(45, 57, 69));
				rarityLabel.setBackground(new Color(58, 75, 92));
				break;
			case EPIC:
				setBackground(new Color(60, 49, 67));
				rarityLabel.setBackground(new Color(81, 65, 91));
				break;
			case COMMON:
			default:
				setBackground(new Color(58, 58, 58));
				rarityLabel.setBackground(new Color(77, 77, 77));
				break;
		}
	}
}
