package com.uss.madsies.view;

import com.uss.madsies.data.Game;
import com.uss.madsies.Main;
import com.uss.madsies.managers.RoundManager;
import com.uss.madsies.managers.TeamsManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class GUIView
{
    private final JFrame frame;
    private final JButton cancelMatches;
    public JButton endMatches;
    public JButton updatePublicBoard;
    public JButton generateMatches;
    public JButton copyMatches;
    public JButton copyUnfinished;
    public TextArea errorText;
    private final RoundManager _roundManager;
    private final TeamsManager _teamsManager;

    public boolean matchStatus;

    public void setMatchStatus(boolean ms)
    {
        this.matchStatus = ms;
    }

    public GUIView(Game game, RoundManager roundManager, TeamsManager teamsManager) {
        _roundManager = roundManager;
        _teamsManager = teamsManager;
        matchStatus = _roundManager.isCurrentMatch;
        String title = "USS Admin Control Panel - %s";
        frame = new JFrame(String.format(title, game.toString()));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(3,1,10,10));

        errorText = new TextArea();
        errorText.setEditable(false);

        Image icon = Toolkit.getDefaultToolkit().getImage(
                GUIView.class.getResource("/images/USS.jpg")
        );

        frame.setIconImage(icon);

        JPanel sortingPanel = new JPanel(new GridLayout(2, 3, 15, 15));
        sortingPanel.setBorder(BorderFactory.createTitledBorder("Sorting"));

        JButton sortSeeding = new JButton("Rank by Seed");
        sortSeeding.addActionListener(a -> {
            try {
                Main.genericSetup();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        JButton sortPlacement = new JButton("Fix Rankings");
        sortPlacement.addActionListener(a -> {
            try {
                Main.fixStandings();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, e.getMessage());
            }
        });

        JButton reset = new JButton("Reset Datasheet");
        reset.addActionListener(a -> {
            int result = JOptionPane.showConfirmDialog(
                    frame,                     // parent component
                    "Are you sure you want to reset the sheet data?", // message
                    "YOU ARE DELETING ALL DATA.",          // title
                    JOptionPane.YES_NO_OPTION, // options
                    JOptionPane.WARNING_MESSAGE // icon
            );
            if (result == JOptionPane.YES_OPTION)
            {
                Main.wipeData();
                _teamsManager.addSeedAndCreateTeams();
            }}
            );

        JButton setAllCheckIn = new JButton("Set All Check In");
        setAllCheckIn.addActionListener(a -> _teamsManager.checkAllTeams(true));

        JButton setAllCheckOut = new JButton("Set All Check Out");
        setAllCheckOut.addActionListener(a -> _teamsManager.checkAllTeams(false));

        sortingPanel.add(sortSeeding);
        sortingPanel.add(sortPlacement);
        sortingPanel.add(reset);
        sortingPanel.add(setAllCheckIn);
        sortingPanel.add(setAllCheckOut);

        cancelMatches = new JButton("Cancel Matches");
        endMatches = new JButton("End Matches");
        generateMatches = new JButton("Generate Matches");
        copyMatches = new JButton("Copy Matches to Clipboard");
        copyUnfinished = new JButton("Copy Unfinished Matches to Clipboard");
        copyMatches.addActionListener(a -> {_roundManager.copyRound();JOptionPane.showMessageDialog(frame, "Copied to clipboard");});
        generateMatches.addActionListener(a -> {
            try {
                _roundManager.generateRound();
                endMatches.setVisible(true);
                cancelMatches.setVisible(true);
                copyMatches.setVisible(true);
                copyUnfinished.setVisible(true);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } catch (RuntimeException ex)
            {
                JOptionPane.showMessageDialog(frame, ex.getMessage());
            }
        });

        copyUnfinished.addActionListener(a -> {
                try {
                    _roundManager.copyMissingMatches();
                    JOptionPane.showMessageDialog(frame, "Copied to clipboard");
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(frame, e.getMessage());
                }
        });

        endMatches.addActionListener(a -> {
        int result = JOptionPane.showConfirmDialog(
                frame,                     // parent component
                "Are you sure all scores have been submitted?", // message
                "Confirm Completed?",          // title
                JOptionPane.YES_NO_OPTION, // options
                JOptionPane.WARNING_MESSAGE // icon
        );

        if (result == JOptionPane.YES_OPTION) {
            try {
                _roundManager.endRound();
                endMatches.setVisible(false);
                cancelMatches.setVisible(false);
                copyMatches.setVisible(false);
                copyUnfinished.setVisible(false);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, ex.getMessage());
            }
        }
         });

        cancelMatches.addActionListener(a -> {
            int result = JOptionPane.showConfirmDialog(
                    frame,                     // parent component
                    "Are you sure you want to cancel the round?", // message
                    "Are you sure?",          // title
                    JOptionPane.YES_NO_OPTION, // options
                    JOptionPane.WARNING_MESSAGE // icon
            );
            if (result == JOptionPane.YES_OPTION) {
                try {
                    _roundManager.cancelRound();
                    endMatches.setVisible(false);
                    cancelMatches.setVisible(false);
                    copyMatches.setVisible(false);
                    copyUnfinished.setVisible(false);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, ex.getMessage());
                }
            }
        });

        if (!matchStatus) {
            endMatches.setVisible(false);
            cancelMatches.setVisible(false);
            copyMatches.setVisible(false);
            copyUnfinished.setVisible(false);
        }
        else
        {
            endMatches.setVisible(true);
            cancelMatches.setVisible(true);
            copyMatches.setVisible(true);
            copyUnfinished.setVisible(true);
        }

        JPanel matchPanel = new JPanel(new GridLayout(2,3,15,15));
        matchPanel.setBorder(BorderFactory.createTitledBorder("Matches"));
        matchPanel.add(generateMatches);
        matchPanel.add(cancelMatches);
        matchPanel.add(endMatches);
        matchPanel.add(copyMatches);
        matchPanel.add(copyUnfinished);

        updatePublicBoard = new JButton("Update Public Sheet");
        updatePublicBoard.addActionListener(a -> Main.updatePublicStandings());

        JButton copyUnreadyTeams = new JButton("Copy List of non-checked in teams");
        copyUnreadyTeams.addActionListener(a ->
        {_teamsManager.copyNonCheckedIn();JOptionPane.showMessageDialog(frame, "Copied to clipboard");});

        JPanel publicPanel = new JPanel(new GridLayout(1,2,15,15));
        publicPanel.setBorder(BorderFactory.createTitledBorder("Public"));
        publicPanel.add(updatePublicBoard);
        publicPanel.add(copyUnreadyTeams);


        sortSeeding.setVisible(true);
        frame.add(sortingPanel);
        frame.add(matchPanel);
        frame.add(publicPanel);

        frame.setSize(1280, 720);
        frame.setLocationRelativeTo(null);

        refreshTimer();

    }

    private void refreshTimer() {
        Timer timer = new Timer(2000, e -> {
            _roundManager.refreshCurrentMatch();
            setMatchStatus(_roundManager.isCurrentMatch);

            if (!matchStatus) {
                endMatches.setVisible(false);
                cancelMatches.setVisible(false);
                copyMatches.setVisible(false);
                copyUnfinished.setVisible(false);
            }
            else
            {
                endMatches.setVisible(true);
                cancelMatches.setVisible(true);
                copyMatches.setVisible(true);
                copyUnfinished.setVisible(true);
            }
        });
        timer.start();
    }

    public void show()
    {
        frame.setVisible(true);
    }
}
