package com.uss.madsies;

import com.uss.madsies.data.Game;
import com.uss.madsies.data.TeamData;
import com.uss.madsies.managers.RoundManager;
import com.uss.madsies.managers.SheetsManager;
import com.uss.madsies.managers.TeamsManager;
import com.uss.madsies.view.GUIView;
import com.uss.madsies.view.MatchesGUI;

import javax.swing.*;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.List;

public class Main {

    public static String ADMIN_SHEET; // Stored privately
    public static String PUBLIC_SHEET ;
    static Game GAME;
    public static RoundManager roundManager;
    public static SheetsManager sheetsManager;
    public static TeamsManager teamManager;

    public static void main(String... args) throws IOException, GeneralSecurityException
    {
        // Build a new authorized API client service.
        sheetsManager = new SheetsManager();
        sheetsManager.generateService();
        GAME = Game.valueOf(sheetsManager.getGame());
        ADMIN_SHEET = sheetsManager.getAdminSheet();
        PUBLIC_SHEET = sheetsManager.getPublicSheet();

        teamManager = new TeamsManager(sheetsManager);
        roundManager = new RoundManager(sheetsManager, teamManager);

        getFullData();

        // Initialise GUI
        SwingUtilities.invokeLater(() -> {
            GUIView view = new GUIView(GAME, roundManager, teamManager);
            view.show();
            view.setMatchStatus(roundManager.isCurrentMatch);
        });
        SwingUtilities.invokeLater(() -> new MatchesGUI(GAME, roundManager, teamManager));
    }


    /**
     *  For initial startup, sets up data and sorts by seed
     */

    public static void genericSetup() throws IOException {
        getFullData();
        teamManager.sortTeams(true);
        rewriteData();
    }

    /**
     * Orders standings by correct order and writes back to disk
     */

    public static void fixStandings() throws IOException
    {
        teamManager.updateOMWP();
        teamManager.sortTeams(false);
        rewriteData();
    }

    /*
        Writes necessary information to the public view sheet, ran at the end of each week
     */

    public static void updatePublicStandings()
    {
        teamManager.sortTeams(false);
        List<List<Object>> sheetData = new ArrayList<>();
        int i = 1;
        for (TeamData t : teamManager.teamsInfo)
        {
            sheetData.add(Arrays.asList(i, t.teamName, t.score, t.wins, t.losses, t.omwp));
            i++;
        }

        sheetsManager.writeData(sheetData, PUBLIC_SHEET, "Standings!B5");
    }

    /*
        Wipes all data, also loads new teams if more have signed-up
     */

    public static void wipeData()
    {
        for (TeamData t : teamManager.teamsInfo)
        {
            t.Clear();
        }
        teamManager.teamsInfo.clear();
        try
        {
            int num = sheetsManager.getSheetNumber();
            for (int i = num; i > 0; i--)
            {
                sheetsManager.deleteSheet("Match_"+i);
            }
            sheetsManager.setSheetNumber(0);
            sheetsManager.clearData("Datasheet!A2:Y");
            rewriteData();
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
        }
    }

    /*
        Rewrites the standings page of admin sheet.
     */

    public static void rewriteData()
    {
        List<List<Object>> sheetData = new ArrayList<>();
        for (TeamData teamData : teamManager.teamsInfo)
        {
            sheetData.add(teamData.convertToSpreadsheetRow());
        }
        if (teamManager.teamsInfo.isEmpty()) sheetData.add(new ArrayList<>(List.of("")));
        sheetsManager.writeData(sheetData, ADMIN_SHEET, "Datasheet!A2:Y");
    }

    /*
        Loads all data from sheets into the program
     */

    public static void getFullData()
    {
        String range = "Datasheet!A2:ZZ";
        List<List<Object>> sheetData = sheetsManager.fetchData(ADMIN_SHEET, range);

        teamManager.updateTeamPlayers();

        List<TeamData> data = new ArrayList<>();
        for (List<Object> row : sheetData)
        {
            if (row.isEmpty()) continue;
            if (row.getFirst().toString().isEmpty()) continue;
            data.add(new TeamData(row){{players=teamManager.teamPlayers.get(row.getFirst());}});
        }
        teamManager.teamsInfo = data;

        // Janky Seeding fix -- Sorts by seed, grabs the order, sorts by score
        teamManager.sortTeams(true);
        teamManager.sortTeams(false);
    }

}