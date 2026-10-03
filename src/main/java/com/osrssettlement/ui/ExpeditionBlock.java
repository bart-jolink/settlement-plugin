package com.osrssettlement.ui;

import com.osrssettlement.engine.ExpeditionCatalog;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/**
 * One expedition in the strip: its item icon, with a padlock drawn over it while locked.
 */
class ExpeditionBlock extends JPanel
{
	enum Status
	{
		LOCKED,
		CURRENT,
		COMPLETED
	}

	private static final Color SHADE = new Color(0, 0, 0, 150);
	private static final Color LOCK_SHACKLE = new Color(0xB0, 0xB0, 0xB0);
	private static final Color LOCK_BODY = new Color(0xC8, 0xA0, 0x3C);

	private final String name;
	private Status status;

	ExpeditionBlock(ItemManager itemManager, ExpeditionCatalog.Definition expedition)
	{
		name = expedition.getDisplayName();
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(36, 40));

		JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		itemManager.getImage(expedition.getIconItemId()).addTo(icon);
		add(icon, BorderLayout.CENTER);

		setStatus(Status.LOCKED);
	}

	void setStatus(Status status)
	{
		if (this.status == status)
		{
			return;
		}
		this.status = status;

		switch (status)
		{
			case CURRENT:
				setBorder(new MatteBorder(2, 2, 2, 2, ColorScheme.BRAND_ORANGE));
				setToolTipText(name + ": in progress");
				break;
			case COMPLETED:
				setBorder(new MatteBorder(2, 2, 2, 2, Ui.GOOD));
				setToolTipText(name + ": completed");
				break;
			case LOCKED:
			default:
				setBorder(new EmptyBorder(2, 2, 2, 2));
				setToolTipText(name + ": locked until the previous expedition is completed");
				break;
		}
		repaint();
	}

	@Override
	protected void paintChildren(Graphics g)
	{
		super.paintChildren(g);
		if (status != Status.LOCKED)
		{
			return;
		}

		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int width = getWidth();
		int height = getHeight();
		g2.setColor(SHADE);
		g2.fillRect(0, 0, width, height);

		int bodyWidth = 14;
		int bodyHeight = 10;
		int x = (width - bodyWidth) / 2;
		int y = (height - bodyHeight) / 2 + 3;

		g2.setColor(LOCK_SHACKLE);
		g2.setStroke(new BasicStroke(2f));
		g2.drawArc(x + 3, y - 7, bodyWidth - 6, 14, 0, 180);
		g2.drawLine(x + 3, y - 1, x + 3, y);
		g2.drawLine(x + bodyWidth - 3, y - 1, x + bodyWidth - 3, y);

		g2.setColor(LOCK_BODY);
		g2.fillRoundRect(x, y, bodyWidth, bodyHeight, 3, 3);
		g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g2.fillRect(x + bodyWidth / 2 - 1, y + 3, 2, 4);
		g2.dispose();
	}
}
