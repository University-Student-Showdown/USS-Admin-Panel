package com.uss.madsies.view;
import com.uss.madsies.data.Game;
import com.uss.madsies.Main;
import com.uss.madsies.data.MatchUp;

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
    private boolean lastMatchStatus = false;
    private final JFrame frame;
    private final MatchTableModel tableModel;
    private final JTable table;
    private Instant sinceLastRefresh = Instant.now().minus(Duration.ofSeconds(60));
    static List<MatchUp> matches = new ArrayList<>();

    private enum Side {
        LEFT, RIGHT
    }

    public MatchesGUI(Game game) {
        String title = "Current Round: " + game;
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setSize(655, 800);

        Image icon = Toolkit.getDefaultToolkit().getImage(
                MatchesGUI.class.getResource("/images/USS.jpg")
        );
        frame.setIconImage(icon);


        // Table model and JTable
        tableModel = new MatchTableModel();

        table = new JTable(tableModel);

        table.getColumnModel().getColumn(0).setPreferredWidth(25);
        table.getColumnModel().getColumn(1).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setPreferredWidth(35);
        table.getColumnModel().getColumn(3).setPreferredWidth(35);
        table.getColumnModel().getColumn(4).setPreferredWidth(170);
        table.getColumnModel().getColumn(5).setPreferredWidth(25);
        table.setRowHeight(30);

        // Scroll pane for table
        JScrollPane scrollPane = new JScrollPane(table);
        frame.add(scrollPane, BorderLayout.CENTER);


        // Panel for buttons
        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 5, 5));

        JButton fetchButton = new JButton("Sync Matches from Sheet");

        fetchButton.addActionListener(e -> refreshData());

        buttonPanel.add(fetchButton);

        JButton swapButton = new JButton("Swap Teams");
        swapButton.addActionListener(e -> swapSelectedMatches());
        buttonPanel.add(swapButton);

        JButton updateButton = new JButton("Update Sheet & Database");
        updateButton.addActionListener(e -> applyScoreChanges());
        buttonPanel.add(updateButton);

        frame.add(buttonPanel, BorderLayout.EAST);

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

            boolean previous = matchStatus;
            grabMatchStatus();

            if (!previous && matchStatus) {
                refreshData();
                frame.setVisible(matchStatus);
            }

            if (!previous && !matchStatus)
            {
                frame.setVisible(false);
            }

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
        matches = tableModel.getMatches();
        Main.updateAndWriteMatches((ArrayList<MatchUp>) matches);
        JOptionPane.showMessageDialog(frame, "Scores updated!");
        refreshData();
    }

    private void swapTeams( int rowA, Side sideA,int rowB, Side sideB)
    {
        List<MatchUp> list = tableModel.getMatches();

        MatchUp m1 = list.get(rowA);
        MatchUp m2 = list.get(rowB);

        var temp = (sideA == Side.LEFT) ? m1.team1 : m1.team2;

        if (sideA == Side.LEFT) m1.team1 = (sideB == Side.LEFT) ? m2.team1 : m2.team2;
        else                   m1.team2 = (sideB == Side.LEFT) ? m2.team1 : m2.team2;

        if (sideB == Side.LEFT) m2.team1 = temp;
        else                   m2.team2 = temp;

        tableModel.fireTableRowsUpdated(
                Math.min(rowA, rowB),
                Math.max(rowA, rowB)
        );
    }

    private void swapSelectedMatches() {
        int[] rows = table.getSelectedRows();
        if (rows.length != 2) {
            JOptionPane.showMessageDialog(frame, "Select exactly two matches.");
            return;
        }

        Side side1 = askSide("First match: which team?");
        Side side2 = askSide("Second match: which team?");

        if (side1 == null || side2 == null) return;

        swapTeams(rows[0], side1, rows[1], side2);
    }

    private Side askSide(String title) {
        Object choice = JOptionPane.showOptionDialog(
                frame,
                title,
                "Choose Side",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new String[]{"Left", "Right"},
                "Left"
        );

        return switch ((int) choice) {
            case 0 -> Side.LEFT;
            case 1 -> Side.RIGHT;
            default -> null;
        };
    }

    // Table model for matches
    private static class MatchTableModel extends AbstractTableModel {
        private final String[] columns = {"#", "Team 1", "Res", "ult", "Team 2", "#"};
        private List<MatchUp> matches = new ArrayList<>();

        public void setMatches(List<MatchUp> matches) {
            this.matches = matches;
            fireTableDataChanged();
        }

        public List<MatchUp> getMatches() {

            return matches;
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
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            MatchUp m = matches.get(rowIndex);
            return switch (columnIndex)
            {
                case 0 -> m.team1.seedingRank;
                case 1 -> m.team1.teamName;
                case 2 -> m.score1;
                case 3 -> m.score2;
                case 4 -> m.team2.teamName;
                case 5 -> m.team2.seedingRank;
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex)
        {
            return columnIndex == 2 || columnIndex == 3;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            MatchUp m = matches.get(rowIndex);
            if (columnIndex == 2) {
                m.score1 = ((Integer) aValue);
            } else if (columnIndex == 3) {
                m.score2 = ((Integer) aValue);
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0,2,3,5 -> Integer.class;
                default -> String.class;
            };
        }
    }
}
