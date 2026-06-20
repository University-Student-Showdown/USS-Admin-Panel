package com.uss.madsies.managers;

import com.uss.madsies.Main;
import com.uss.madsies.SeedingTools;
import com.uss.madsies.data.Game;
import com.uss.madsies.data.TeamData;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class TeamsManager
{
    public List<TeamData> teamsInfo = new ArrayList<>();
    public HashMap<String, List<Integer>> teamPlayers = new HashMap<>();
    private final SheetsManager _sheetsManager;

    public TeamsManager(SheetsManager sheetsManager)
    {
        _sheetsManager = sheetsManager;
    }

    public void updateRecords() throws IOException
    {
        int num = _sheetsManager.getSheetNumber();
        String range = "Match_"+num+"!A2:D";

        List<List<Object>> data = _sheetsManager.fetchData(Main.ADMIN_SHEET, range);

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

    public void updateFullRecords() throws IOException
    {
        int num = _sheetsManager.getSheetNumber();
        for (int i = 1; i <= num; i++)
        {
            String range = "Match_"+i+"!A2:D";

            List<List<Object>> data = _sheetsManager.fetchData(Main.ADMIN_SHEET, range);

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
    }

    public void sortTeams(boolean seeding)
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

    public void updateTeamPlayers()
    {
        teamPlayers = new HashMap<>();
        List<List<Object>> seedData = _sheetsManager.fetchData(Main.ADMIN_SHEET, "Seeding!A1:G");

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

    public void addSeedAndCreateTeams(Game game)
    {
        HashMap<String, Double> rankings = calculateSeedingRanks(game);

        updateTeamPlayers();

        for (Map.Entry<String, Double> entry : rankings.entrySet())
        {
            teamsInfo.add(new TeamData(entry.getKey(), entry.getValue()){{players=teamPlayers.get(entry.getKey());}});
        }

        grantSeedingWins(game);
        Main.rewriteData();
    }

    public HashMap<String, Double> calculateSeedingRanks(Game game)
    {
        Main.getFullData();
        List<List<Object>> seedData = _sheetsManager.fetchData(Main.ADMIN_SHEET, "Seeding!A1:H");

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

            double rating = SeedingTools.calculateWeightedSeed(ranks, game);
            rankings.put(name, rating);
            rawRankings.add(new ArrayList<>(Collections.singleton(rating)));
        }

        rawRankings.removeFirst();
        _sheetsManager.writeData(rawRankings, Main.ADMIN_SHEET, "Seeding!I1");

        return rankings;
    }


    /**
     Used before tournament start after all teams have been signed-up
     Gives wins based off of initial seeding to improve week 1 game quality.

     */

    public void grantSeedingWins(Game game)
    {
        List<Integer> thresholds = new ArrayList<>();

        switch (game)
        {
            case Game.OVERWATCH ->
            {
                thresholds.add(24); //List.of(24, 48, 72); //SeedingTools.calcSeedingThresholds(teamsInfo.size());
                thresholds.add(48);
                thresholds.add(72);
            }
            case Game.DEADLOCK, Game.VALORANT, Game.ROCKET_LEAGUE ->
            {
            }
        }

        int count = 0;
        int thresholdCount = thresholds.size();
        for (TeamData t : teamsInfo)
        {
            count++;
            int bucket = thresholdCount;

            for (int i = 0; i < thresholdCount; i++) {
                if (count <= thresholds.get(i)) {
                    bucket = i;
                    break;
                }
            }

            t.setWins(Math.max(thresholdCount - bucket, 0));
            t.losses = Math.max(bucket, 0);
        }
        sortTeams(true);

    }

    public void updateOMWP() {

        Map<String, int[]> teamRecords = new HashMap<>();
        int roundNumber;
        try
        {
            roundNumber = _sheetsManager.getSheetNumber();
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
                if (totalGames == 0 || totalGames < roundNumber/2) continue;

                double winPct = (double) oppWins / totalGames;
                sum += winPct;
                count++;
            }

            team.omwp = BigDecimal.valueOf((count == 0) ? 0 : sum / count).setScale(4, RoundingMode.HALF_UP).doubleValue();
        }
    }

    public void checkAllTeams(boolean in)
    {
        for (TeamData team : teamsInfo)
        {
            team.checkedIn = in;
        }

        Main.rewriteData();
    }

    public void copyNonCheckedIn()
    {
        Main.getFullData(); // Load full data (may have been un-ticked since last check)

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


}
