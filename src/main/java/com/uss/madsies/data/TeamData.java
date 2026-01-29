package com.uss.madsies.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
    Data class to handle team data more easily
    Has functions to convert to and from spreadsheet data

 */

public class TeamData
{
    public String teamName;
    public double seeding;
    public boolean checkedIn;
    public int score; // Wins * 3
    public int wins;
    public int losses;
    public int map_wins;
    public int map_losses;
    public double omwp;
    public List<String> history = new ArrayList<>(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", "", "", ""));
    public List<Integer> players = new ArrayList<>();
    public int seedingRank = 999;

    /*
        Conversion from spreadsheet data to data class
     */

    public TeamData(List<Object> spreadsheetRow)
    {
        teamName = (String)spreadsheetRow.get(0);
        seeding = Double.parseDouble((String)spreadsheetRow.get(1));
        checkedIn = Boolean.parseBoolean((String) spreadsheetRow.get(2));
        score = Integer.parseInt((String)spreadsheetRow.get(3));
        wins = Integer.parseInt((String)spreadsheetRow.get(4));
        losses = Integer.parseInt((String)spreadsheetRow.get(5));
        map_wins = Integer.parseInt((String)spreadsheetRow.get(6));
        map_losses = Integer.parseInt((String)spreadsheetRow.get(7));
        omwp = Double.parseDouble((String)spreadsheetRow.get(8));
        for(int i = 9; i < spreadsheetRow.size(); i++)
        {
            history.set(i-9, (String) spreadsheetRow.get(i));
        }
    }

    /*
        For team creation in-code
     */

    public TeamData(String teamName, double seeding)
    {
        this.teamName = teamName;
        this.seeding = seeding;
        checkedIn = false;
        score = 0;
        wins = 0;
        losses = 0;
        map_wins = 0;
        map_losses = 0;
        omwp = 0;
    }
    /*
        For testing
     */
    public TeamData(String teamName, double seeding, boolean checkedIn)
    {
        this.teamName = teamName;
        this.seeding = seeding;
        this.checkedIn = checkedIn;
        score = 0;
        wins = 0;
        losses = 0;
        map_wins = 0;
        map_losses = 0;
        omwp = 0;
    }

    public List<Object> convertToSpreadsheetRow()
    {
        List<Object> row = new ArrayList<>();
        row.add(teamName);
        row.add(seeding);
        row.add(checkedIn);
        row.add(score);
        row.add(wins);
        row.add(losses);
        row.add(map_wins);
        row.add(map_losses);
        row.add(omwp);
        row.addAll(history);
        return row;
    }

    /*
        Clears everything bar seeding and name
     */

    public void Clear()
    {
        checkedIn = true;
        score = 0;
        wins = 0;
        losses = 0;
        map_wins = 0;
        map_losses = 0;
        omwp = 0;
        history = new ArrayList<>(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", "", "", "", ""));
    }

    public void addWins(int count)
    {
        this.wins += count;
        this.score += count*3;
    }

    public void setCheckedIn(boolean ci)
    {
        checkedIn = ci;
    }

    public String getName()
    {
        return teamName;
    }

    public boolean hasPlayed(TeamData opponent) {
        return this.history.contains(opponent.teamName);
    }

 }
