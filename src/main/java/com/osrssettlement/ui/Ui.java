package com.osrssettlement.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

final class Ui
{
	static final int TEXT_WIDTH = 170;
	static final Color GOOD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color BAD = ColorScheme.PROGRESS_ERROR_COLOR;

	private Ui()
	{
	}

	static JLabel label(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	static JLabel sectionTitle(String text)
	{
		JLabel label = label(text, FontManager.getRunescapeBoldFont(), ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(6, 0, 2, 0));
		return label;
	}

	/**
	 * Wraps text in html so the label word-wraps within the sidebar.
	 */
	static String wrap(String text)
	{
		return "<html><body style='width: " + TEXT_WIDTH + "px'>" + escape(text) + "</body></html>";
	}

	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Vertically scrollable container that keeps its content at the top instead of stretching it.
	 */
	static JScrollPane scroll(JComponent content)
	{
		JPanel wrapper = new ViewportWidthPanel();
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(content, BorderLayout.NORTH);

		JScrollPane scrollPane = new JScrollPane(wrapper);
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		return scrollPane;
	}

	/**
	 * Takes the viewport's width, so content shrinks beside the scrollbar instead of sliding under it.
	 */
	private static final class ViewportWidthPanel extends JPanel implements Scrollable
	{
		ViewportWidthPanel()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	static String formatPlaytime(long ticks)
	{
		long minutes = Math.round(ticks * 0.6 / 60);
		return String.format("%dh %02dm", minutes / 60, minutes % 60);
	}
}
