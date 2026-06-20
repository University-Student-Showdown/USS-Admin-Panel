package com.uss.madsies;

import com.uss.madsies.data.MatchUp;
import com.uss.madsies.data.TeamData;

import java.util.*;

public class Matchmaker {

    public static List<MatchUp> createRoundRobinRound(List<TeamData> teamData, int round)
    {
        List<MatchUp> matchups = new ArrayList<>();

        List<TeamData> teams = teamData.stream()
                .sorted(Comparator.comparing(t -> t.teamName.toLowerCase()))
                .toList();

        // Add BYE if odd
        if (teams.size() % 2 != 0) {
            teams.add(new TeamData("BYE", -1));
        }

        int n = teams.size();

        // rotated arrangement
        List<TeamData> arrangement = new ArrayList<>(Collections.nCopies(n, null));

        // First fixed
        arrangement.set(0, teams.get(0));

        // Rotate rest
        for (int i = 1; i < n; i++) {
            int pos = 1 + ((i - 1 + round) % (n - 1));
            arrangement.set(pos, teams.get(i));
        }

        // Pair opposites
        for (int i = 0; i < n / 2; i++) {
            TeamData a = arrangement.get(i);
            TeamData b = arrangement.get(n - 1 - i);

            matchups.add(new MatchUp(a, b));
        }

        return matchups;
    }


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
        int teamCount = teamData.size();
        int currentSize = 0;

        for (TeamData team : teamData) {
            if (!team.checkedIn) continue;
            if (bracketWins == -1) bracketWins = team.wins;

            if (team.wins != bracketWins) {
                if (downFloat != null)
                {
                    currentPool.add(0, downFloat);
                }

                downFloat = handlePool(currentPool, matchups, downFloat != null);

                currentSize = currentPool.size() + 1;

                currentPool = new ArrayList<>();
                bracketWins = team.wins;
            }

            currentPool.add(team);
        }

        // Complainer fix, if a team is dropping below 25% and at least 12of the teams, they get a bye
        if (downFloat != null) {
            if (currentSize > teamCount/4 && currentSize >= 12)
            {
                matchups.add(new MatchUp(downFloat, new TeamData("BYE", -1)));
            }
            else
            {
                currentPool.add(0, downFloat);
            }

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
