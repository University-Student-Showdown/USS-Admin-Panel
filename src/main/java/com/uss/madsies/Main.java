package com.uss.madsies;

import com.uss.madsies.data.Game;
import com.uss.madsies.data.MatchUp;
import com.uss.madsies.data.TeamData;
import com.uss.madsies.view.GUIView;
import com.uss.madsies.view.MatchesGUI;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    static String ADMIN_SHEET; // Stored privately
    static String PUBLIC_SHEET ;
    static List<TeamData> teamsInfo = new ArrayList<>();
    static List<MatchUp> matches = new ArrayList<>();
    public static boolean isCurrentMatch = false;
    static HashMap<String, List<Integer>> teamPlayers = new HashMap<>();
    static Game GAME;

    public static void main(String... args) throws IOException, GeneralSecurityException
    {
        // Build a new authorized API client service.
        SheetsManagement.generateService();
        GAME = Game.valueOf(SheetsManagement.getGame());
        ADMIN_SHEET = SheetsManagement.getAdminSheet();
        PUBLIC_SHEET = SheetsManagement.getPublicSheet();

        getFullData();

        isCurrentMatch = SheetsManagement.readMatchFlag();
        if (isCurrentMatch) loadCurrentMatch();

        // Initialise GUI
        SwingUtilities.invokeLater(() -> {
            GUIView view = new GUIView(GAME);
            view.show();
            view.setMatchStatus(isCurrentMatch);
        });
        SwingUtilities.invokeLater(() -> new MatchesGUI(GAME));
    }

    /**
     *  Creates new sheet and writes matchups for admins to submit match scores
     *
     * @param matches Current list of matches in round
     */
    public static void writeMatchupSheet(List<MatchUp> matches) throws IOException
    {
        int num = SheetsManagement.getSheetNumber();
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

        SheetsManagement.writeData(values, ADMIN_SHEET, range);
    }

    public static void grabLiveMatch() throws IOException
    {
        if (!isCurrentMatch) return;
        int num = SheetsManagement.getSheetNumber();
        String range = "Match_"+num+"!A2:D";

        List<List<Object>> data = SheetsManagement.fetchData(ADMIN_SHEET, range);

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

    public static List<MatchUp> getMatches()
    {
        return matches;
    }

    public static void updateAndWriteMatches(ArrayList<MatchUp> newMatches)
    {
        matches = newMatches;
        try {
            int num = SheetsManagement.getSheetNumber();
            String range = "Match_"+num+"!A1";
            List<List<Object>> values = new ArrayList<>();

            // Headers for sheet (Readability)
            values.add(Arrays.asList("Team A", "Team B", "Team A Score", "Team B Score", "Team A Seed", "Team B Seed"));

            // Data
            for (MatchUp match : matches) {
                values.add(Arrays.asList(match.team1.teamName, match.team2.teamName, match.score1, match.score2, match.team1.seedingRank, match.team2.seedingRank));
            }
            SheetsManagement.writeData(values, ADMIN_SHEET, range);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
        Reads data from match sheet and updates win/losses accordingly
     */
    public static void updateRecords() throws IOException
    {
        int num = SheetsManagement.getSheetNumber();
        String range = "Match_"+num+"!A2:D";

        List<List<Object>> data = SheetsManagement.fetchData(ADMIN_SHEET, range);

        Map<String, TeamData> teamMap = new HashMap<>();
        for (TeamData t : teamsInfo)
        {
            teamMap.put(t.teamName, t);
        }

        for (List<Object> row : data)
        {
            if (row.size() < 4) continue; // skip incomplete rows

            String teamA = row.get(0).toString();
            String teamB = row.get(1).toString();

            int scoreA = Integer.parseInt(row.get(2).toString());
            int scoreB = Integer.parseInt(row.get(3).toString());

            if (Objects.equals(teamA, "BYE"))
            {
                teamMap.get(teamB).addWins(1);
                teamMap.get(teamB).map_wins += 2;
                continue;
            }
            else if (Objects.equals(teamB, "BYE"))
            {
                teamMap.get(teamA).addWins(1);
                teamMap.get(teamA).map_wins += 2;
                continue;
            }
            else if (scoreA > scoreB) {
                teamMap.get(teamA).addWins(1);
                teamMap.get(teamB).losses++;
            } else if (scoreB > scoreA) {
                teamMap.get(teamB).addWins(1);
                teamMap.get(teamA).losses++;
            }
            teamMap.get(teamA).map_wins += scoreA;
            teamMap.get(teamB).map_wins += scoreB;
            teamMap.get(teamA).map_losses += scoreB;
            teamMap.get(teamB).map_losses += scoreA;
        }

    }

    /**
     *  For initial startup, sets up data and sorts by seed
     */

    public static void genericSetup() throws IOException {
        getFullData();
        sortTeams(true);
        rewriteData();
    }

    /**
     * Orders standings by correct order and writes back to disk
     */

    public static void fixStandings() throws IOException
    {
        updateOMWP();
        sortTeams(false);
        rewriteData();
    }

    // Do this when matches are needed to be generated
    public static void generateRound() throws IOException
    {
        if (isCurrentMatch) {
            throw new RuntimeException("Round is already currently running..");
        }

        getFullData();
        matches = Matchmaker.createSwissMatchups(teamsInfo);
        SheetsManagement.createNewSheet();
        writeMatchupSheet(matches);

        SheetsManagement.writeMatchFlag(true);
        isCurrentMatch = true;
    }

    /**
     * Cancels round, reverting any completed matches as if the round was never started.
     *
     * @throws IOException
     */
    public static void cancelRound() throws IOException
    {
        if (!isCurrentMatch)
        {
            throw new RuntimeException("How has this been ran?");
        }
        matches.clear();
        int num = SheetsManagement.getSheetNumber();
        SheetsManagement.deleteSheet("Match_"+num);

        SheetsManagement.writeMatchFlag(false);
        isCurrentMatch = false;
        SheetsManagement.reduceSheetNumber(); // -1
    }

    // Do this when all data is filled and all matches are done
    public static void endRound() throws IOException
    {
        if (!isCurrentMatch)
        {
            throw new RuntimeException("Round was not running..");
        }

        updateRecords();
        updateHistory(matches, SheetsManagement.getSheetNumber());
        updateOMWP();
        sortTeams(false);
        rewriteData();

        SheetsManagement.writeMatchFlag(false);
        isCurrentMatch = false;
    }

    /*
        Writes necessary information to the public view sheet, ran at the end of each week
     */

    public static void updatePublicStandings()
    {
        sortTeams(false);
        List<List<Object>> sheetData = new ArrayList<>();
        int i = 1;
        for (TeamData t : teamsInfo)
        {
            sheetData.add(Arrays.asList(i, t.teamName, t.score, t.wins, t.losses, t.omwp));
            i++;
        }

        SheetsManagement.writeData(sheetData, PUBLIC_SHEET, "Standings!B5");
    }

    public static void copyRound()
    {
        StringSelection stringSelection = new StringSelection(Matchmaker.getMatchupsString(matches));
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }

    public static void copyNonCheckedIn()
    {
        getFullData(); // Load full data (may have been un-ticked since last check)

        StringBuilder sb = new StringBuilder();
        sb.append("Teams that have not Checked in:\n");
        for (TeamData t : teamsInfo)
        {
            if (!t.checkedIn)
            {
                sb.append(t.teamName).append("\n");
            }
        }
        StringSelection stringSelection = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }

    public static void wipeData()
    {
        for (TeamData t : teamsInfo)
        {
            t.Clear();
        }
        teamsInfo.clear();
        try
        {
            int num = SheetsManagement.getSheetNumber();
            for (int i = num; i > 0; i--)
            {
                SheetsManagement.deleteSheet("Match_"+i);
            }
            SheetsManagement.setSheetNumber(0);
            SheetsManagement.clearData("Datasheet!A2:Y");
            rewriteData();
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
        }

    }

    public static void rewriteData()
    {
        List<List<Object>> sheetData = new ArrayList<>();
        for (TeamData teamData : teamsInfo)
        {
            sheetData.add(teamData.convertToSpreadsheetRow());
        }
        if (teamsInfo.isEmpty()) sheetData.add(new ArrayList<>(List.of("")));
        SheetsManagement.writeData(sheetData, ADMIN_SHEET, "Datasheet!A2:Y");
    }

    public static void updateHistory(List<MatchUp> matches, int round)
    {
        for (MatchUp m : matches) {
            addOpponent(m.team1.teamName, m.team2.teamName, round);
            addOpponent(m.team2.teamName, m.team1.teamName, round);
        }
    }

    public static void copyMissingMatches() throws IOException {
        // Go through matches in current round, print names of teams of unfinished games
        int num = SheetsManagement.getSheetNumber();
        String range = "Match_"+num+"!A2:D";
        List<List<Object>> data = SheetsManagement.fetchData(ADMIN_SHEET, range);

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

    private static void addOpponent( String team, String opponent, int round)
    {
        for (TeamData t : teamsInfo)
        {
            String teamName = t.teamName;
            if (teamName.equals(team))
            {
                t.history.set(round - 1, opponent);
                return;
            }
        }
    }

    public static void updateOMWP() {

        Map<String, int[]> teamRecords = new HashMap<>();
        int roundNumber;
        try
        {
            roundNumber = SheetsManagement.getSheetNumber();
        }
        catch (IOException e)
        {
            roundNumber = 0;
        }

        for (TeamData team : teamsInfo)
        {
            teamRecords.put(team.teamName, new int[]{team.wins, team.losses});
        }

        for (TeamData team : teamsInfo)
        {
            double sum = 0;
            int count = 0;

            for (String opp : team.history)
            {
                if (!teamRecords.containsKey(opp)) continue;

                int[] rec = teamRecords.get(opp);
                int oppWins = rec[0];
                int oppLosses = rec[1];


                int totalGames = oppWins + oppLosses;
                if (totalGames == 0 || totalGames < roundNumber+1) continue;

                double winPct = (double) oppWins / totalGames;
                sum += winPct;
                count++;
            }

            team.omwp = BigDecimal.valueOf((count == 0) ? 0 : sum / count).setScale(4, RoundingMode.HALF_UP).doubleValue();

        }
    }

    public static void checkAllTeams(boolean in)
    {
        for (TeamData team : teamsInfo)
        {
            team.checkedIn = in;
        }

        rewriteData();


    }

    public static void getFullData()
    {
        String range = "Datasheet!A2:ZZ";
        List<List<Object>> sheetData = SheetsManagement.fetchData(ADMIN_SHEET, range);

        updateTeamPlayers();

        List<TeamData> data = new ArrayList<>();
        for (List<Object> row : sheetData)
        {
            if (row.isEmpty()) continue;
            if (row.getFirst().toString().isEmpty()) continue;
            data.add(new TeamData(row){{players=teamPlayers.get(row.getFirst());}});
        }
        teamsInfo = data;

        // Janky Seeding fix -- Sorts by seed, grabs the order, sorts by score
        sortTeams(true);
        sortTeams(false);
    }

    public static void sortTeams(boolean seeding)
    {
        /*
            Placing order Wins -> OMWP -> Map Wins -> Map Losses (Inv) -> H2H
         */
        if (!seeding)
        {
            teamsInfo.sort(Comparator.comparingInt((TeamData t) -> t.wins)
                    .thenComparingDouble((TeamData t) -> t.omwp)
                    .thenComparingInt((TeamData t) -> t.map_wins).reversed()
                    .thenComparingInt((TeamData t) -> t.map_losses).reversed()
                    .thenComparingDouble((TeamData t) -> t.seeding).reversed());
        }
        else {
            updateTeamPlayers();
            teamsInfo.sort(
                    Comparator.comparingDouble((TeamData t) -> t.seeding)
                            .reversed()
                    .thenComparing((a,b) -> SeedingTools.seedTiebreaker(a.players, b.players))
                            .thenComparing((t) -> t.teamName.toLowerCase())
                );

            int rank = 1;
            for (TeamData team : teamsInfo)
            {
                team.seedingRank = rank++;
            }

        }
    }

    public static void addSeedAndCreateTeams()
    {
        HashMap<String, Double> rankings = calculateSeedingRanks();

        updateTeamPlayers();

        for (Map.Entry<String, Double> entry : rankings.entrySet())
        {
            teamsInfo.add(new TeamData(entry.getKey(), entry.getValue()){{players=teamPlayers.get(entry.getKey());}});
        }

        grantSeedingWins();
        rewriteData();
    }

    public static HashMap<String, Double> calculateSeedingRanks()
    {
        getFullData();
        List<List<Object>> seedData = SheetsManagement.fetchData(ADMIN_SHEET, "Seeding!A1:G");

        HashMap<String, Double> rankings = new HashMap<>();
        List<List<Object>> rawRankings = new ArrayList<>();
        rawRankings.add(new ArrayList<>());
        for (List<Object> row : seedData)
        {
            if (row.isEmpty()) continue;
            String name = row.getFirst().toString();
            if (name.isEmpty()) continue;
            ArrayList<Integer> ranks = (ArrayList<Integer>) new ArrayList<>(row.subList(2, row.size()))
                    .stream().map(o -> Integer.parseInt(o.toString())).collect(Collectors.toList());

            double rating = SeedingTools.calculateWeightedSeed(ranks);
            rankings.put(name, rating);
            rawRankings.add(new ArrayList<>(Collections.singleton(rating)));
        }

        rawRankings.removeFirst();
        SheetsManagement.writeData(rawRankings, ADMIN_SHEET, "Seeding!I1");

        return rankings;
    }

    public static void updateTeamPlayers()
    {
        teamPlayers = new HashMap<>();
        List<List<Object>> seedData = SheetsManagement.fetchData(ADMIN_SHEET, "Seeding!A1:G");

        for (List<Object> row : seedData) {
            if (row.isEmpty()) continue;
            String name = row.getFirst().toString();
            if (name.isEmpty()) continue;
            try {
                teamPlayers.put(name, new ArrayList<>(row.subList(2, row.size()))
                        .stream().map(o -> Integer.parseInt(o.toString())).collect(Collectors.toList()));
            }
            catch (Exception e)
            {
                System.out.println(e);
            }

        }
    }

    /**
        Used before tournament start after all teams have been signed-up
        Gives wins based off of initial seeding to improve week 1 game quality.

     */

    public static void grantSeedingWins()
    {
        List<Integer> thresholds = List.of(24, 48, 72);//SeedingTools.calcSeedingThresholds(teamsInfo.size());

        sortTeams(true);
        int count = 0;
        for (TeamData t : teamsInfo)
        {
            count++;
            if (count <= thresholds.getFirst())
            {
                t.addWins(3);
                continue;
            }
            if (count <= thresholds.get(1))
            {
                t.addWins(2);
                t.losses += 1;
                continue;
            }
            if (count <= thresholds.get(2)) {
                t.addWins(1);
                t.losses += 2;
            }
            if (count > thresholds.get(2))
            {
                t.losses += 3;
            }
        }
    }

    public static TeamData getTeamFromName(String name)
    {
        for (TeamData team : teamsInfo) {
            if (team.teamName.equals(name)) return team;

        }
        return null;
    }

    /**
     * Loads the current round if the client is closed whilst a round is in progress
     */

    public static void loadCurrentMatch() {
        try {
            int num = SheetsManagement.getSheetNumber();
            List<List<Object>> data = SheetsManagement.fetchData(ADMIN_SHEET, "Match_" + num + "!A1:D");

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
    
}