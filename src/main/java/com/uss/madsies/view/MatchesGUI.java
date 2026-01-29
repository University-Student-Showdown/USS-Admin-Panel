package com.uss.madsies.view;
import com.uss.madsies.Game;
import com.uss.madsies.Main;
import com.uss.madsies.MatchUp;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MatchesGUI {
    private boolean matchStatus = false;
    private final JFrame frame;
    private final MatchTableModel tableModel;
    private final JTable table;
    private Instant sinceLastRefresh = Instant.now().minus(Duration.ofSeconds(10));
    static List<MatchUp> matches = new ArrayList<>();

    public MatchesGUI(Game game) {
        String title = "Current Round: " + game;
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Table model and JTable
        tableModel = new MatchTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(30);

        // Scroll pane for table
        JScrollPane scrollPane = new JScrollPane(table);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Panel for buttons
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        JButton updateButton = new JButton("Update Scores");
        JButton fetchButton = new JButton("Reload Matches");
        updateButton.addActionListener(e -> applyScoreChanges());
        fetchButton.addActionListener(e -> refreshData());
        buttonPanel.add(updateButton);
        buttonPanel.add(fetchButton);

        frame.add(buttonPanel, BorderLayout.EAST);

        frame.setSize(600, 400);
        frame.setVisible(matchStatus);

        // Start background data loop
        startTitleRefreshTimer(game);
    }

    private void refreshData()
    {
        try {
            Main.grabLiveMatch();
            tableModel.setMatches(Main.getMatches());
            sinceLastRefresh = Instant.now();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void startTitleRefreshTimer(Game game) {
        Timer timer = new Timer(500, e -> {
            long secondsAgo = Duration
                    .between(sinceLastRefresh, Instant.now())
                    .getSeconds();

            frame.setTitle("Current Round: " + game +
                    " | Last refresh: " + secondsAgo + "s ago");

            grabMatchStatus();
            frame.setVisible(matchStatus);

            if (secondsAgo >= 60) {
                refreshData();
            }
        });
        timer.start();
    }


    private void grabMatchStatus() {
        this.matchStatus = Main.isCurrentMatch;
    }

    private void applyScoreChanges() {
        for (int i = 0; i < matches.size(); i++) {
            MatchUp m = matches.get(i);
            int homeScore = (int) tableModel.getValueAt(i, 1);
            int awayScore = (int) tableModel.getValueAt(i, 2);
            m.score1 = (homeScore);
            m.score2 = (awayScore);
        }
        JOptionPane.showMessageDialog(frame, "Scores updated!");
    }

    // Table model for matches
    private static class MatchTableModel extends AbstractTableModel {
        private final String[] columns = {"Team 1", "Home Score", "Away Score", "Team 2"};
        private List<MatchUp> matches = new ArrayList<>();

        public void setMatches(List<MatchUp> matches) {
            this.matches = matches;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return matches.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MatchUp m = matches.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> m.team1.teamName;
                case 1 -> m.score1;
                case 2 -> m.score2;
                case 3 -> m.team2.teamName;
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1 || columnIndex == 2; // allow editing scores
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            MatchUp m = matches.get(rowIndex);
            if (columnIndex == 1) {
                m.score1 = ((Integer) aValue);
            } else if (columnIndex == 2) {
                m.score2 = ((Integer) aValue);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 1, 2 -> Integer.class;
                default -> String.class;
            };
        }
    }
}
