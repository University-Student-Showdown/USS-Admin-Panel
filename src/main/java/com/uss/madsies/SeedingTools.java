package com.uss.madsies;

import com.uss.madsies.data.Game;

import java.util.ArrayList;
import java.util.List;

public class SeedingTools
{
    public static final double HIGH_THRESHOLD_PERCENT = 25;
    public static final double MID_THRESHOLD_PERCENT = 50;
    public static final double LOW_THRESHOLD_PERCENT = 75;

    /**
     *  Sorts Team rankings with highest rank first
     *
     * @param data The array of player ranks to convert
     * @return Sorted array of player data
     */

    public static List<Integer> sortByRank(List<Integer> data)
    {
        if (data == null || data.isEmpty()) return new ArrayList<>();
        data.sort(Integer::compareTo);
        return data.reversed();
    }

    /**
        Intakes array of ranks, and gives it a seeding rank

        OW:
        Highest rank *2.0, *1.5, *1.25 and base for others

        RL:
        *1.25, *1.125, base for last.

        @param data The array of player ranks to convert
        @return The teams calculated seeding score
     */
    public static float calculateWeightedSeed(List<Integer> data, Game game)
    {
        int WEIGHT_CAP = 0;
        float weightedScore = 0;
        float flatWeightedScore = 0;
        data = sortByRank(data);
        if (game == Game.OVERWATCH)
        {
            WEIGHT_CAP = 4000;
            weightedScore = (float) Math.floor
                    (data.get(0)*2.0f + data.get(1) * 1.5f + data.get(2) * 1.25f + data.get(3)+data.get(4)) / 6.75f;

            flatWeightedScore = (data.get(0) + data.get(1) + data.get(2) + data.get(3) + data.get(4)) / 5.0f;
        }
        else
        {
            WEIGHT_CAP = 0;
            weightedScore = (float) Math.floor
                    (data.get(0)*1f + data.get(1) * 1f + data.get(2)) / 3.0f;

            flatWeightedScore = (float) Math.floor((data.get(0) + data.get(1) + data.get(2)) / 3.0f);
        }

        if (flatWeightedScore >= WEIGHT_CAP)
        {
            return flatWeightedScore;
        }

        if (weightedScore >= WEIGHT_CAP) weightedScore = WEIGHT_CAP-1;

        return (float) Math.ceil(weightedScore);
    }

    /*
     *  Tiebreaker for teams that have equal seeding score
     *  The team with the highest ranked 1st player is chosen, if a tie, 2nd player
     *  If all players have identical ranks, the 1st team is chosen in tiebreak.
     *
     * @param team1 First team in comparison
     * @param team2 Team to compare to
     * @return If the left team should be higher seed

     */
    public static int seedTiebreaker(List<Integer> team1, List<Integer> team2)
    {
        List<Integer> sortedTeam1 = sortByRank(team1);
        List<Integer> sortedTeam2 = sortByRank(team2);
        if (sortedTeam1.size() != sortedTeam2.size()) return 0;

        for (int idx = 0; idx < sortedTeam1.size(); idx++)
        {
            if (sortedTeam1.get(idx) > sortedTeam2.get(idx)) return -1;
            if (sortedTeam2.get(idx) > sortedTeam1.get(idx)) return 1;
        }
        return 0;
    }

    /**
     *  Returns a list of cutoffs of teams that gain seeding points
     *
     *  @param teamCount Number of teams in tournament
     *  @return List of the cut-off points of seed-based point boundaries
     */

    public static List<Integer> calcSeedingThresholds(int teamCount)
    {
        List<Integer> thresholds = calcSeedingThreshold_light(teamCount);
        thresholds.add(ceilToNextQuad(LOW_THRESHOLD_PERCENT / 100.0 * teamCount));
        return thresholds;
    }

    public static List<Integer> calcSeedingThreshold_light(int teamCount)
    {
        List<Integer> thresholds = new ArrayList<>();
        thresholds.add(ceilToNextQuad(HIGH_THRESHOLD_PERCENT / 100.0 * teamCount));
        thresholds.add(ceilToNextQuad(MID_THRESHOLD_PERCENT / 100.0 * teamCount));
        return thresholds;
    }

    /*
        Ensures no overflow games for the top group for the first week at least, stops massive stomps
     */
    private static int ceilToNextQuad(double value)
    {
        int ceil = (int) Math.ceil(value);
        return (ceil % 4 == 0) ? ceil : ceil + (4 - (ceil % 4));
    }


}
