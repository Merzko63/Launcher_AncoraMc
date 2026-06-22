package net.minecraft;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public class VersionSelector extends JPanel {
    private static final long serialVersionUID = 1L;
    private String selectedVersion = "1.5.2";
    private boolean expanded = false;
    private JLabel displayLabel;
    private List<String> versions = new ArrayList<String>();
    private List<VersionListener> listeners = new ArrayList<VersionListener>();
    private JPanel dropdownPanel;

    public interface VersionListener {
        void onVersionSelected(String version);
    }

    public VersionSelector() {
        versions.add("1.5.2");
        versions.add("1.1");

        setOpaque(false);
        setLayout(null);

        displayLabel = new JLabel("Версия: " + selectedVersion + " ▼");
        displayLabel.setForeground(Color.WHITE);
        displayLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        displayLabel.setHorizontalAlignment(JLabel.CENTER);
        displayLabel.setBorder(new LineBorder(new Color(100, 100, 100), 1));
        displayLabel.setBackground(new Color(40, 40, 40));
        displayLabel.setOpaque(true);
        displayLabel.setBounds(0, 0, 100, 24);
        displayLabel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                toggleExpanded();
            }
            public void mouseEntered(MouseEvent e) {
                displayLabel.setBackground(new Color(60, 60, 60));
                displayLabel.setBorder(new LineBorder(new Color(150, 150, 150), 1));
            }
            public void mouseExited(MouseEvent e) {
                if (!expanded) {
                    displayLabel.setBackground(new Color(40, 40, 40));
                    displayLabel.setBorder(new LineBorder(new Color(100, 100, 100), 1));
                }
            }
        });
        add(displayLabel);

        setPreferredSize(new java.awt.Dimension(100, 24));
        setSize(100, 24);
    }

    private void toggleExpanded() {
        expanded = !expanded;
        if (expanded) {
            showDropdown();
            displayLabel.setBackground(new Color(60, 60, 60));
        } else {
            removeDropdown();
            displayLabel.setBackground(new Color(40, 40, 40));
        }
    }

    private void showDropdown() {
        removeDropdown();

        dropdownPanel = new JPanel(null);
        dropdownPanel.setOpaque(true);
        dropdownPanel.setBackground(new Color(40, 40, 40));
        dropdownPanel.setBorder(new LineBorder(new Color(100, 100, 100), 1));

        int y = 0;
        for (String version : versions) {
            if (version.equals(selectedVersion))
                continue;
            JLabel label = new JLabel(version);
            label.setForeground(Color.WHITE);
            label.setFont(new Font("Arial", Font.PLAIN, 12));
            label.setHorizontalAlignment(JLabel.CENTER);
            label.setBorder(new LineBorder(new Color(80, 80, 80), 1));
            label.setBackground(new Color(40, 40, 40));
            label.setOpaque(true);
            label.setBounds(0, y, 100, 24);

            final String v = version;
            label.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    selectVersion(v);
                }
                public void mouseEntered(MouseEvent e) {
                    label.setBackground(new Color(60, 60, 60));
                    label.setBorder(new LineBorder(new Color(150, 150, 150), 1));
                }
                public void mouseExited(MouseEvent e) {
                    label.setBackground(new Color(40, 40, 40));
                    label.setBorder(new LineBorder(new Color(80, 80, 80), 1));
                }
            });
            dropdownPanel.add(label);
            y += 24;
        }

        dropdownPanel.setBounds(0, 24, 100, y);
        dropdownPanel.setSize(100, y);
        add(dropdownPanel);

        int totalHeight = 24 + y;
        setSize(100, totalHeight);
        setPreferredSize(new java.awt.Dimension(100, totalHeight));
        revalidate();
        repaint();
    }

    private void removeDropdown() {
        if (dropdownPanel != null) {
            remove(dropdownPanel);
            dropdownPanel = null;
        }
        setSize(100, 24);
        setPreferredSize(new java.awt.Dimension(100, 24));
        revalidate();
        repaint();
    }

    private void selectVersion(String version) {
        selectedVersion = version;
        displayLabel.setText("Версия: " + version + " ▼");
        expanded = false;
        removeDropdown();
        displayLabel.setBackground(new Color(40, 40, 40));
        displayLabel.setBorder(new LineBorder(new Color(100, 100, 100), 1));
        for (VersionListener listener : listeners) {
            listener.onVersionSelected(version);
        }
    }

    public void setSelectedVersion(String version) {
        this.selectedVersion = version;
        displayLabel.setText("Версия: " + version + " ▼");
        expanded = false;
        removeDropdown();
    }

    public void addVersionListener(VersionListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }
}