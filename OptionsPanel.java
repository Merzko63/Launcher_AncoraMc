package net.minecraft;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class OptionsPanel extends JDialog
{
  private static final long serialVersionUID = 1L;

  private JTextField minMemoryField;
  private JTextField maxMemoryField;
  private JTextField pathField;
  private JCheckBox customPathCheck;

  public OptionsPanel(Frame parent)
  {
    super(parent);
    setTitle("Настройки лаунчера");
    setModal(true);

    JPanel panel = new JPanel(new BorderLayout());
    JLabel label = new JLabel("Настройки", 0);
    label.setBorder(new EmptyBorder(0, 0, 16, 0));
    label.setFont(new Font("Default", 1, 16));
    panel.add(label, "North");

    JPanel optionsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new java.awt.Insets(5, 5, 5, 5);

    optionsPanel.add(new JLabel("Минимальная ОЗУ (МБ):"), gbc);
    gbc.gridx = 1;
    minMemoryField = new JTextField(getSetting("minMemory", "512"), 10);
    optionsPanel.add(minMemoryField, gbc);

    gbc.gridx = 0;
    gbc.gridy = 1;
    optionsPanel.add(new JLabel("Максимальная ОЗУ (МБ):"), gbc);
    gbc.gridx = 1;
    maxMemoryField = new JTextField(getSetting("maxMemory", "1024"), 10);
    optionsPanel.add(maxMemoryField, gbc);

    gbc.gridx = 0;
    gbc.gridy = 2;
    optionsPanel.add(new JLabel("Путь к клиенту:"), gbc);
    gbc.gridx = 1;
    pathField = new JTextField(getSetting("gamePath", Util.getWorkingDirectory().getAbsolutePath()), 20);
    optionsPanel.add(pathField, gbc);

    gbc.gridx = 2;
    JButton browseButton = new JButton("Обзор...");
    browseButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Выберите папку для клиента");
        if (chooser.showOpenDialog(OptionsPanel.this) == JFileChooser.APPROVE_OPTION) {
          pathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
      }
    });
    optionsPanel.add(browseButton, gbc);

    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 3;
    final JButton forceButton = new JButton("Принудительно обновить клиент!");
    forceButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        GameUpdater.forceUpdate = true;
        forceButton.setText("Клиент будет обновлён!");
        forceButton.setEnabled(false);
      }
    });
    optionsPanel.add(forceButton, gbc);

    gbc.gridx = 0;
    gbc.gridy = 4;
    gbc.gridwidth = 3;
    TransparentLabel dirLink = new TransparentLabel("Текущий путь: " + Util.getWorkingDirectory().toString()) {
      private static final long serialVersionUID = 0L;

      public void paint(Graphics g) { super.paint(g);
        int x = 0;
        int y = 0;
        FontMetrics fm = g.getFontMetrics();
        int width = fm.stringWidth(getText());
        int height = fm.getHeight();
        if (getAlignmentX() == 2.0F) x = 0;
        else if (getAlignmentX() == 0.0F) x = getBounds().width / 2 - width / 2;
        else if (getAlignmentX() == 4.0F) x = getBounds().width - width;
        y = getBounds().height / 2 + height / 2 - 1;
        g.drawLine(x + 2, y, x + width - 2, y); }
    };
    dirLink.setCursor(Cursor.getPredefinedCursor(12));
    dirLink.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent arg0) {
        try {
          Util.openLink(new URL("file://" + Util.getWorkingDirectory().getAbsolutePath()).toURI());
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
    dirLink.setForeground(new Color(2105599));
    optionsPanel.add(dirLink, gbc);

    panel.add(optionsPanel, "Center");

    JPanel buttonsPanel = new JPanel(new BorderLayout());
    buttonsPanel.add(new JPanel(), "Center");

    JButton saveButton = new JButton("Сохранить");
    saveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        saveSettings();
        setVisible(false);
      }
    });

    JButton doneButton = new JButton("Закрыть");
    doneButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent ae) {
        setVisible(false);
      }
    });

    JPanel buttonPanel = new JPanel();
    buttonPanel.add(saveButton);
    buttonPanel.add(doneButton);
    buttonsPanel.add(buttonPanel, "East");
    buttonsPanel.setBorder(new EmptyBorder(16, 0, 0, 0));

    panel.add(buttonsPanel, "South");

    add(panel);
    panel.setBorder(new EmptyBorder(16, 24, 24, 24));
    pack();
    setLocationRelativeTo(parent);
  }

  private String getSetting(String key, String defaultValue) {
    try {
      File settingsFile = new File(Util.getWorkingDirectory(), "launcher_settings.properties");
      if (settingsFile.exists()) {
        java.util.Properties props = new java.util.Properties();
        props.load(new java.io.FileInputStream(settingsFile));
        return props.getProperty(key, defaultValue);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return defaultValue;
  }

  private void saveSettings() {
    try {
      File settingsFile = new File(Util.getWorkingDirectory(), "launcher_settings.properties");
      java.util.Properties props = new java.util.Properties();
      if (settingsFile.exists()) {
        props.load(new java.io.FileInputStream(settingsFile));
      }
      props.setProperty("minMemory", minMemoryField.getText());
      props.setProperty("maxMemory", maxMemoryField.getText());
      props.setProperty("gamePath", pathField.getText());
      props.store(new java.io.FileOutputStream(settingsFile), "Launcher Settings");

      System.setProperty("minecraft.gamePath", pathField.getText());

      try {
        java.lang.reflect.Field workDirField = Util.class.getDeclaredField("workDir");
        workDirField.setAccessible(true);
        workDirField.set(null, new File(pathField.getText()));
      } catch (Exception e) {
        e.printStackTrace();
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}