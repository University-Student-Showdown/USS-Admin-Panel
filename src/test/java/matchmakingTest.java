import com.uss.madsies.data.MatchUp;
import com.uss.madsies.Matchmaker;
import com.uss.madsies.data.TeamData;
import jdk.jfr.Description;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class matchmakingTest
{

    List<TeamData> TEAMS_ALL = Arrays.asList(
            new TeamData("Alpha", 20, true),
            new TeamData("Beta", 30, true),
            new TeamData("Gamma", 40, true),
            new TeamData("Delta", 50, true),
            new TeamData("Elephant", 60, true),
            new TeamData("Falcon", 70, true),
            new TeamData("Hawk", 80, true),
            new TeamData("Iguana", 90, true),
            new TeamData("Jaguar", 100, true),
            new TeamData("Kangaroo", 110, true),
            new TeamData("Lion", 120, true),
            new TeamData("Monkey", 130, true),
            new TeamData("Narwhal", 140, true),
            new TeamData("Owl", 150, true),
            new TeamData("Panda", 160, true),
            new TeamData("Quokka", 170, true),
            new TeamData("Rabbit", 180, true),
            new TeamData("Snake", 190, true),
            new TeamData("Tiger", 200, true),
            new TeamData("Urial", 210, true),
            new TeamData("Viper", 220, true),
            new TeamData("Wolf", 230, true),
            new TeamData("Xerus", 240, true),
            new TeamData("Yak", 250, true),
            new TeamData("Zebra", 260, true)
    );

    @Test
    @Description("Checking if correct amount of games have been created")
    public void testMatchUpNumbers()
    {
        List<TeamData> teams = TEAMS_ALL.subList(0, 4);

        List<MatchUp> games = Matchmaker.createSwissMatchups(teams);

        assertEquals(2, games.size());
    }

    @Test
    @Description("Testing if non-checked-in Teams are included")
    public void testCheckIn()
    {
        List<TeamData> teams = TEAMS_ALL.subList(0, 4);
        teams.getFirst().setCheckedIn(false);
        teams.getLast().setCheckedIn(false);

        List<MatchUp> games = Matchmaker.createSwissMatchups(teams);

        assertEquals(1, games.size());
    }

    @Test
    @Description("Testing if byes are implemented")
    public void testBye()
    {
        List<TeamData> teams = TEAMS_ALL.subList(0, 5);

        List<MatchUp> games = Matchmaker.createSwissMatchups(teams);

        assertEquals(3, games.size());
        assertEquals("BYE", games.getLast().team2.getName()); // Checking position of Bye
    }

    @Test
    @Description("Testing if basic 1stvsNth works")
    public void testMatchups()
    {
        List<TeamData> teams = TEAMS_ALL.subList(0, 8);

        List<MatchUp> games = Matchmaker.createSwissMatchups(teams);

        assertEquals(games.getFirst().team1, teams.getFirst());
        assertEquals(games.getFirst().team2, teams.getLast());
    }

    @Test
    @Description("Testing Grouping management")
    public void testGrouping()
    {
        List<TeamData> teams = TEAMS_ALL.subList(0, 8);
        teams.getFirst().wins = 2;
        teams.get(1).wins = 2;
        teams.get(2).wins = 2;

        List<MatchUp> games = Matchmaker.createSwissMatchups(teams);
        assertEquals(games.get(1).team1, teams.get(0)); // Checking if last place gets dropped a bucket
        assertEquals(4, games.size()); // Make sure all games are played still
    }


}
