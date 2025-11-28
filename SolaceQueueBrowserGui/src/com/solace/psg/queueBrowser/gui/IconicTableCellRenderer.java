package com.solace.psg.queueBrowser.gui;

import java.awt.Color;
import java.awt.Component;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

public class IconicTableCellRenderer extends DefaultTableCellRenderer {
	private static final long serialVersionUID = 1L;

	@Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (value instanceof ImageIcon) {
            label.setIcon((ImageIcon) value);
            label.setText(""); // Remove text
        }

        // Apply alternating row colors and selection highlighting
        if (!isSelected) {
            if (row % 2 == 0) {
                label.setBackground(new Color(248, 248, 248)); // Very soft gray
            } else {
                label.setBackground(Color.WHITE);
            }
            label.setForeground(Color.BLACK); // Normal text color
        } else {
            label.setBackground(new Color(255, 250, 150)); // Darker yellow highlight for better visibility
            label.setForeground(Color.BLACK); // Black text on yellow background for readability
        }

        return label;
    }
}
