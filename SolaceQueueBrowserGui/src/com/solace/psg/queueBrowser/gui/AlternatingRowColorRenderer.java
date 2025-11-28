package com.solace.psg.queueBrowser.gui;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

public class AlternatingRowColorRenderer extends DefaultTableCellRenderer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (!isSelected) {
			if (row % 2 == 0) {
				c.setBackground(new Color(248, 248, 248)); // Very soft gray
			} else {
				c.setBackground(Color.WHITE);
			}
			c.setForeground(Color.BLACK); // Normal text color
		} else {
			c.setBackground(new Color(255, 250, 150)); // Darker yellow highlight for better visibility
			c.setForeground(Color.BLACK); // Black text on yellow background for readability
		}
		return c;
	}
}