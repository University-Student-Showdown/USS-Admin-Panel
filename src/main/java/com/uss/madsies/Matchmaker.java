package com.uss.madsies;

import com.uss.madsies.data.MatchUp;
import com.uss.madsies.data.TeamData;

import java.util.*;

public class Matchmaker {
    /*
        // Pool teams by number of wins
        // Place 1st in pool vs last in pool
        // If odd number in pool, downfloat the weakest to the lower class
        // If odd in total, give a bye to the highest team in the lowest class

        @param sheetData Raw sheet data from Google sheets
        @return List of Matchup objects for optimal matches
     */

    public static List<MatchUp> createSwissMatchups(List<TeamData> teamData) {
        List<MatchUp> matchups = new ArrayList<>();
        List<TeamData> currentPool = new ArrayList<>();
        TeamData downFloat = null;
        int bracketWins = -1;

        for (TeamData team : teamData) {
            if (!team.checkedIn) continue;
            if (bracketWins == -1) bracketWins = team.wins;

            if (team.wins != bracketWins) {
                if (downFloat != null)
                {
                    currentPool.add(0, downFloat);
                }

                downFloat = handlePool(currentPool, matchups, downFloat != null);

                currentPool = new ArrayList<>();
                bracketWins = team.wins;
            }

            currentPool.add(team);
        }

        if (downFloat != null) {
            currentPool.add(0, downFloat);
        }

        downFloat = handlePool(currentPool, matchups, downFloat != null);

        // If something still remains, give it a BYE
        if (downFloat != null) {
            matchups.add(new MatchUp(downFloat, new TeamData("BYE", -1)));
        }

        // Debug output
        for (MatchUp matchup : matchups)
        {
            System.out.println(matchup);
        }

        return matchups;
    }

    /**
     * Handles a pool of teams
     * Returns the downfloat team if odd number, else null.
     *
     * */
    private static TeamData handlePool(List<TeamData> pool, List<MatchUp> matchups, boolean prevDownfloat) {
        if (pool.isEmpty()) return null;

        List<TeamData> workingPool = new ArrayList<>(pool);
        TeamData downFloat = null;

        if (workingPool.size() % 2 != 0) {
            if (prevDownfloat) downFloat = workingPool.remove(1);
            else
            {
                downFloat = workingPool.remove(0);
            }

        }

        Set<TeamData> used = new HashSet<>();
        for (int i = 0; i < workingPool.size(); i++)
        {
            TeamData teamA = workingPool.get(i);
            if (used.contains(teamA)) continue;

            TeamData teamB = null;
            for (int j = workingPool.size() - 1; j > i; j--) {
                TeamData candidate = workingPool.get(j);
                if (used.contains(candidate)) continue;
                if (!teamA.hasPlayed(candidate)) {
                    teamB = candidate;
                    break;
                }
            }

            // fallback
            if (teamB == null) {
                for (int j = workingPool.size() - 1; j > i; j--) {
                    TeamData candidate = workingPool.get(j);
                    if (!used.contains(candidate)) {
                        teamB = candidate;
                        break;
                    }
                }
            }

            if (teamB != null) {
                matchups.add(new MatchUp(teamA, teamB));
                used.add(teamA);
                used.add(teamB);
            }
        }

        return downFloat;
    }


    public static String getMatchupsString(List<MatchUp> matchups)
    {
        StringBuilder sb = new StringBuilder();
        for (MatchUp match : matchups)
        {
            sb.append(match.toString()).append("\n");
        }
        return sb.toString();
    }
}
