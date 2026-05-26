package net.distrocraft.app.ui;

import net.distrocraft.app.agent.StandaloneAgent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AppGui extends JFrame {

    private final JTextField hostField;
    private final JTextField portField;
    private final JSpinner   threadsSpinner;
    private final JTextField labelField;
    private final JButton    connectBtn;
    private final JButton    disconnectBtn;
    private final JButton    resourcesBtn;
    private final JLabel     statusLabel;
    private final JLabel     doneLabel;
    private final JLabel     failLabel;
    private final JTextArea  logArea;

    private final Map<String, Integer> extraResources = new LinkedHashMap<>();

    private StandaloneAgent agent;
    private Timer           statsTimer;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    public AppGui() {
        super("Distrocraft Client");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });

        hostField      = new JTextField("localhost", 16);
        portField      = new JTextField("25566", 6);
        threadsSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2), 1, 64, 1));
        labelField     = new JTextField(System.getProperty("user.name", "player"), 14);
        connectBtn     = new JButton("Connect");
        disconnectBtn  = new JButton("Disconnect");
        disconnectBtn.setEnabled(false);
        resourcesBtn   = new JButton("Resources\u2026");

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.anchor = GridBagConstraints.WEST;

        addRow(formPanel, gbc, 0, "Host:", hostField, "Port:", portField);
        addRow(formPanel, gbc, 1, "Threads:", threadsSpinner, "Label:", labelField);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        btnPanel.add(connectBtn);
        btnPanel.add(disconnectBtn);
        btnPanel.add(resourcesBtn);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4;
        formPanel.add(btnPanel, gbc);

        statusLabel = new JLabel("Status: idle");
        doneLabel   = new JLabel("Done: 0");
        failLabel   = new JLabel("Failed: 0");

        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        Color sepColor = UIManager.getColor("Separator.foreground");
        statsPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0,
                sepColor != null ? sepColor : new Color(0x99, 0x99, 0x99)));
        statsPanel.add(statusLabel);
        statsPanel.add(new JSeparator(SwingConstants.VERTICAL));
        statsPanel.add(doneLabel);
        statsPanel.add(failLabel);

        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setLineWrap(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new EmptyBorder(4, 4, 4, 4));

        setLayout(new BorderLayout(0, 0));
        add(formPanel,  BorderLayout.NORTH);
        add(statsPanel, BorderLayout.CENTER);
        add(logScroll,  BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(480, 360));
        setLocationRelativeTo(null);

        connectBtn.addActionListener(e -> connect());
        disconnectBtn.addActionListener(e -> disconnect());
        resourcesBtn.addActionListener(e -> showResourcesDialog());

        statsTimer = new Timer(500, e -> refreshStats());
        statsTimer.start();
    }

    private void addRow(JPanel p, GridBagConstraints gbc, int row,
                        String l1, Component c1, String l2, Component c2) {
        gbc.gridy = row; gbc.gridwidth = 1;
        gbc.gridx = 0; p.add(new JLabel(l1), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; p.add(c1, gbc);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 2; p.add(new JLabel(l2), gbc);
        gbc.gridx = 3; p.add(c2, gbc);
    }

    private void connect() {
        String host   = hostField.getText().trim();
        int    port   = parsePort();
        int    threads = (int)(Integer) threadsSpinner.getValue();
        String label  = labelField.getText().trim();

        if (host.isEmpty()) { JOptionPane.showMessageDialog(this, "Host is required"); return; }
        if (port <= 0)      { JOptionPane.showMessageDialog(this, "Invalid port"); return; }

        Map<String, Integer> caps = new LinkedHashMap<>();
        caps.put("threads", threads);
        caps.putAll(extraResources);
        agent = new StandaloneAgent(host, port, threads, label, caps);
        agent.onStatus(s -> SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + s)));
        agent.onLog(s    -> SwingUtilities.invokeLater(() -> appendLog(s)));
        agent.start();

        connectBtn.setEnabled(false);
        disconnectBtn.setEnabled(true);
        hostField.setEditable(false);
        portField.setEditable(false);
        labelField.setEditable(false);
    }

    private void disconnect() {
        if (agent != null) { agent.stop(); agent = null; }
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        hostField.setEditable(true);
        portField.setEditable(true);
        labelField.setEditable(true);
        statusLabel.setText("Status: idle");
    }

    private void refreshStats() {
        if (agent != null) {
            doneLabel.setText("Done: " + agent.getTasksDone());
            failLabel.setText("Failed: " + agent.getTasksFailed());
        }
    }

    private void appendLog(String msg) {
        String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + msg + "\n";
        logArea.append(line);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showResourcesDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        java.util.List<JTextField> valFields = new java.util.ArrayList<>();
        // Pre-populate with existing extra resources
        if (extraResources.isEmpty()) {
            extraResources.put("ram_mb", 2048);
        }
        java.util.List<String> keys = new java.util.ArrayList<>(extraResources.keySet());
        int row = 0;
        for (String key : keys) {
            gbc.gridx = 0; gbc.gridy = row;
            panel.add(new JLabel(key), gbc);
            JTextField f = new JTextField(String.valueOf(extraResources.get(key)), 8);
            gbc.gridx = 1;
            panel.add(f, gbc);
            valFields.add(f);
            row++;
        }

        int result = JOptionPane.showConfirmDialog(this, panel, "Resource Limits",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            extraResources.clear();
            for (int i = 0; i < keys.size() && i < valFields.size(); i++) {
                try {
                    extraResources.put(keys.get(i), Integer.parseInt(valFields.get(i).getText()));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private int parsePort() {
        try { return Integer.parseInt(portField.getText().trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private void shutdown() {
        if (agent != null) agent.stop();
        statsTimer.stop();
        dispose();
        System.exit(0);
    }
}
