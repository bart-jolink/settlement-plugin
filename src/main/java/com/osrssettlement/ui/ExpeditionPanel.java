package com.osrssettlement.ui;

import com.osrssettlement.engine.ExpeditionCatalog;
import com.osrssettlement.model.ExpeditionId;
import com.osrssettlement.model.ExpeditionProgress;
import com.osrssettlement.model.SettlementState;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ProgressBar;

class ExpeditionPanel extends JPanel
{
	private final List<ExpeditionBlock> blocks = new ArrayList<>();
	private final JLabel titleLabel = Ui.label("", FontManager.getRunescapeBoldFont(), ColorScheme.TEXT_COLOR);
	private final JLabel rewardLabel = Ui.label("", FontManager.getRunescapeSmallFont(), ColorScheme.BRAND_ORANGE);
	private final JPanel tasksPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
	private final List<TaskRow> taskRows = new ArrayList<>();
	private final JButton claimButton = new JButton();
	private ExpeditionId shownId;

	ExpeditionPanel(ItemManager itemManager, SettlementActions actions)
	{
		setLayout(new DynamicGridLayout(0, 1, 0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		List<ExpeditionCatalog.Definition> expeditions = ExpeditionCatalog.all();
		JPanel strip = new JPanel(new GridLayout(1, expeditions.size(), 4, 0));
		strip.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (ExpeditionCatalog.Definition expedition : expeditions)
		{
			ExpeditionBlock block = new ExpeditionBlock(itemManager, expedition);
			blocks.add(block);
			strip.add(block);
		}

		tasksPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		claimButton.setFocusPainted(false);
		claimButton.addActionListener(event ->
		{
			if (shownId != null)
			{
				actions.claimExpedition(shownId);
			}
		});

		add(strip);
		add(titleLabel);
		add(rewardLabel);
		add(tasksPanel);
		add(claimButton);
	}

	void update(SettlementState state)
	{
		ExpeditionCatalog.Definition current = ExpeditionCatalog.current(state);
		List<ExpeditionCatalog.Definition> expeditions = ExpeditionCatalog.all();
		for (int i = 0; i < blocks.size(); i++)
		{
			ExpeditionCatalog.Definition expedition = expeditions.get(i);
			blocks.get(i).setStatus(state.getExpeditionProgress(expedition.getId()).isClaimed()
				? ExpeditionBlock.Status.COMPLETED
				: expedition == current ? ExpeditionBlock.Status.CURRENT : ExpeditionBlock.Status.LOCKED);
		}

		boolean allDone = current == null;
		rewardLabel.setVisible(!allDone);
		tasksPanel.setVisible(!allDone);
		if (allDone)
		{
			shownId = null;
			titleLabel.setText("All expeditions completed");
			claimButton.setVisible(false);
			return;
		}

		if (current.getId() != shownId)
		{
			shownId = current.getId();
			titleLabel.setText(Ui.wrap(current.getDisplayName() + " Expedition"));
			rewardLabel.setText(Ui.wrap("Reward: " + current.getRewardBlueprint().getDisplayName() + " blueprint"));
			claimButton.setText("Claim blueprint");
			rebuildTasks(current);
		}

		ExpeditionProgress progress = state.getExpeditionProgress(current.getId());
		for (int i = 0; i < taskRows.size(); i++)
		{
			ExpeditionCatalog.Objective objective = current.getObjectives().get(i);
			taskRows.get(i).update(progress.getObjectives().getOrDefault(objective.getId(), 0.0), objective.getTarget());
		}

		boolean complete = ExpeditionCatalog.isComplete(current, progress);
		claimButton.setVisible(complete);
		claimButton.setEnabled(complete);
	}

	private void rebuildTasks(ExpeditionCatalog.Definition expedition)
	{
		tasksPanel.removeAll();
		taskRows.clear();
		for (ExpeditionCatalog.Objective objective : expedition.getObjectives())
		{
			TaskRow row = new TaskRow(objective.getDisplayName());
			taskRows.add(row);
			tasksPanel.add(row);
		}
		tasksPanel.revalidate();
		tasksPanel.repaint();
	}

	private static final class TaskRow extends JPanel
	{
		private final ProgressBar bar = new ProgressBar();

		TaskRow(String name)
		{
			setLayout(new BorderLayout(0, 2));
			setOpaque(false);

			bar.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);

			add(Ui.label(Ui.wrap(name), FontManager.getRunescapeSmallFont(), ColorScheme.TEXT_COLOR), BorderLayout.NORTH);
			add(bar, BorderLayout.CENTER);
		}

		void update(double value, long target)
		{
			long shown = (long) Math.floor(Math.min(value, target));
			bar.setCenterLabel(String.format("%,d / %,d", shown, target));
			bar.setMaximumValue((int) Math.min(Integer.MAX_VALUE, target));
			bar.setValue((int) Math.min(Integer.MAX_VALUE, shown));
			bar.setForeground(shown >= target ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_INPROGRESS_COLOR);
		}
	}
}
