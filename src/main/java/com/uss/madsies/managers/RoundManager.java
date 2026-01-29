package com.uss.madsies.managers;

import com.uss.madsies.Main;
import com.uss.madsies.Matchmaker;
import com.uss.madsies.data.MatchUp;
import com.uss.madsies.data.TeamData;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class RoundManager
{
    public List<MatchUp> matches = new ArrayList<>();
    private final SheetsManager _sheetsManager;
    private final TeamsManager _teamsManager;

    public boolean isCurrentMatch = false;
    private final String _ADMIN_SHEET; // Stored privately

    public RoundManager(SheetsManager sheetsManager, TeamsManager teamsManager)
    {
        _ADMIN_SHEET = Main.ADMIN_SHEET;
        _sheetsManager = sheetsManager;
        _teamsManager = teamsManager;

        isCurrentMatch = _sheetsManager.readMatchFlag();
        if (isCurrentMatch) loadCurrentMatch();
    }

    // Do this when matches are needed to be generated
    public void generateRound() throws IOException
    {
        if (isCurrentMatch) {
            throw new RuntimeException("Round is already currently running..");
        }

        Main.getFullData();
        matches = Matchmaker.createSwissMatchups(_teamsManager.teamsInfo);
        _sheetsManager.createNewSheet();
        writeMatchupSheet(matches);

        _sheetsManager.writeMatchFlag(true);
        isCurrentMatch = true;
    }

    public void copyRound()
    {
        StringSelection stringSelection = new StringSelection(Matchmaker.getMatchupsString(matches));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }

    public void cancelRound() throws IOException
    {
        if (!isCurrentMatch)
        {
            throw new RuntimeException("How has this been ran?");
        }
        matches.clear();
        int num = _sheetsManager.getSheetNumber();
        _sheetsManager.deleteSheet("Match_"+num);

        _sheetsManager.writeMatchFlag(false);
        isCurrentMatch = false;
        _sheetsManager.reduceSheetNumber(); // -1
    }

    // Do this when all data is filled and all matches are done
    public void endRound() throws IOException
    {
        if (!isCurrentMatch)
        {
            throw new RuntimeException("Round was not running..");
        }

        _teamsManager.updateRecords();
        updateHistory(matches, _sheetsManager.getSheetNumber());
        _teamsManager.updateOMWP();
        _teamsManager.sortTeams(false);
        Main.rewriteData();

        _sheetsManager.writeMatchFlag(false);
        isCurrentMatch = false;
    }

    /**
     * Loads the current round if the client is closed whilst a round is in progress
     */

    public void loadCurrentMatch() {
        try {
            int num = _sheetsManager.getSheetNumber();
            List<List<Object>> data = _sheetsManager.fetchData(_ADMIN_SHEET, "Match_" + num + "!A1:D");

            for (List<Object> row : data) {
                if (row.isEmpty()) continue;

                String teamA = row.get(0).toString();
                String teamB = row.get(1).toString();

                if (Objects.equals(teamA, "BYE")) {
                    matches.add(new MatchUp(
                            new TeamData("BYE", -1),
                            getTeamFromName(teamB)
                    ));
                    continue;
                }

                if (Objects.equals(teamB, "BYE")) {
                    matches.add(new MatchUp(
                            getTeamFromName(teamA),
                            new TeamData("BYE", -1)
                    ));
                    continue;
                }

                TeamData t1 = getTeamFromName(teamA);
                TeamData t2 = getTeamFromName(teamB);

                if (t1 == null || t2 == null) continue;

                matches.add(new MatchUp(t1, t2));
            }
        }
        catch (Exception e)
        {
            System.out.println("LOAD ERROR:"+ e.getMessage());
        }
    }

    public void refreshCurrentMatch() {
        isCurrentMatch = _sheetsManager.readMatchFlag();
    }

    public void grabLiveMatch() throws IOException
    {
        if (!isCurrentMatch) return;
        int num = _sheetsManager.getSheetNumber();
        String range = "Match_"+num+"!A2:D";

        List<List<Object>> data = _sheetsManager.fetchData(_ADMIN_SHEET, range);

        int match = 0;
        for(List<Object> row : data)
        {
            if (row.size() < 4) continue;
            if (match >= matches.size()) continue;

            matches.get(match).score1 = Integer.parseInt(row.get(2).toString());
            matches.get(match).score2 = Integer.parseInt(row.get(3).toString());
            match++;
        }
        System.out.println(matches);
    }

    public List<MatchUp> getMatches()
    {
        return matches;
    }

    public void updateAndWriteMatches(ArrayList<MatchUp> newMatches)
    {
        matches = newMatches;
        try {
            int num = _sheetsManager.getSheetNumber();
            String range = "Match_"+num+"!A1";
            List<List<Object>> values = new ArrayList<>();

            // Headers for sheet (Readability)
            values.add(Arrays.asList("Team A", "Team B", "Team A Score", "Team B Score", "Team A Seed", "Team B Seed"));

            // Data
            for (MatchUp match : matches) {
                values.add(Arrays.asList(match.team1.teamName, match.team2.teamName, match.score1, match.score2, match.team1.seedingRank, match.team2.seedingRank));
            }
            _sheetsManager.writeData(values, _ADMIN_SHEET, range);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void writeMatchupSheet(List<MatchUp> matches) throws IOException
    {
        int num = _sheetsManager.getSheetNumber();
        String range = "Match_"+num+"!A1";
        List<List<Object>> values = new ArrayList<>();

        // Headers for sheet (Readability)
        values.add(Arrays.asList("Team A", "Team B", "Team A Score", "Team B Score", "Team A Seed", "Team B Seed"));

        // Data
        for(MatchUp match : matches)
        {
            // Auto filling in bye scores
            int scoreA = 0;
            int scoreB = 0;
            if (match.team1.teamName.equals("BYE")) scoreB = 2;
            if (match.team2.teamName.equals("BYE")) scoreA = 2;

            values.add(Arrays.asList(match.team1.teamName, match.team2.teamName, scoreA, scoreB, match.team1.seedingRank, match.team2.seedingRank));
        }

        _sheetsManager.writeData(values, _ADMIN_SHEET, range);
    }



    public TeamData getTeamFromName(String name)
    {
        for (TeamData team : _teamsManager.teamsInfo) {
            if (team.teamName.equals(name)) return team;

        }
        return null;
    }

    public void updateHistory(List<MatchUp> matches, int round)
    {
        for (MatchUp m : matches) {
            addOpponent(m.team1.teamName, m.team2.teamName, round);
            addOpponent(m.team2.teamName, m.team1.teamName, round);
        }
    }

    private void addOpponent( String team, String opponent, int round)
    {
        for (TeamData t : _teamsManager.teamsInfo)
        {
            String teamName = t.teamName;
            if (teamName.equals(team))
            {
                t.history.set(round - 1, opponent);
                return;
            }
        }
    }

    public void copyMissingMatches() throws IOException {
        // Go through matches in current round, print names of teams of unfinished games
        int num = _sheetsManager.getSheetNumber();
        String range = "Match_"+num+"!A2:D";
        List<List<Object>> data = _sheetsManager.fetchData(_ADMIN_SHEET, range);

        StringBuilder sb = new StringBuilder();
        sb.append("Matches without a score:\n");

        for (List<Object> row : data)
        {
            if (row.get(2).equals("0") && row.get(3).equals("0"))
            {
                sb.append(row.get(0)).append(" vs ").append(row.get(1)).append("\n");
            }
        }

        StringSelection stringSelection = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }

}
