package com.uss.madsies;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SheetsManagement
{
    private static final String APPLICATION_NAME = "Google Sheets API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES =
            Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    static Sheets service;
    static String ADMIN_SHEET;

    public static void generateService() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentialsRefresh(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }


    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        // Load client secrets.
        InputStream in = Main.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        ADMIN_SHEET = getAdminSheet();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private static Credential getCredentialsRefresh(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        Credential credential = getCredentials(HTTP_TRANSPORT);

        // Attempt to refresh token if expired
        if (credential.getExpiresInSeconds() != null && credential.getExpiresInSeconds() <= 60) {
            boolean success = credential.refreshToken();
            if (!success) {
                System.out.println("Token expired or revoked. Please reauthorize:");
                credential = new AuthorizationCodeInstalledApp(
                        new GoogleAuthorizationCodeFlow.Builder(
                                HTTP_TRANSPORT, JSON_FACTORY,
                                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(
                                        Main.class.getResourceAsStream(CREDENTIALS_FILE_PATH))),
                                SCOPES)
                                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                                .setAccessType("offline")
                                .build(),
                        new LocalServerReceiver.Builder().setPort(8888).build()
                ).authorize("user");
            } else {
                System.out.println("Token successfully refreshed.");
            }
        }

        return credential;
    }

    static String getAdminSheet() throws IOException
    {
        InputStream in = Main.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }

        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.get("admin_sheet").getAsString();
        }
    }

    static String getPublicSheet() throws IOException
    {
        InputStream in = Main.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }

        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.get("public_sheet").getAsString();
        }
    }


    public static void createNewSheet() throws IOException {
        SheetProperties sheetProperties = new SheetProperties();
        int num = getSheetNumber() + 1;
        setSheetNumber(num);
        sheetProperties.setTitle("Match_"+num);

        // Wrap in an AddSheetRequest
        AddSheetRequest addSheetRequest = new AddSheetRequest();
        addSheetRequest.setProperties(sheetProperties);

        // Wrap in a general Request
        Request request = new Request();
        request.setAddSheet(addSheetRequest);

        // Send batchUpdate request
        BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest();
        batchUpdateRequest.setRequests(Collections.singletonList(request));

        service.spreadsheets().batchUpdate(ADMIN_SHEET, batchUpdateRequest).execute();
    }

    public static void deleteSheet(String sheetName) throws IOException
    {
        Spreadsheet spreadsheet = service.spreadsheets().get(ADMIN_SHEET).execute();
        Integer sheetId = null;
        for (Sheet sheet : spreadsheet.getSheets())
        {
            if (sheet.getProperties().getTitle().equals(sheetName)) {
                sheetId = sheet.getProperties().getSheetId();
                break;
            }
        }
        if (sheetId == null) {
            System.out.println("Sheet not found: " + sheetName);
            return;
        }

        // Create DeleteSheetRequest
        DeleteSheetRequest deleteSheetRequest = new DeleteSheetRequest().setSheetId(sheetId);
        Request request = new Request().setDeleteSheet(deleteSheetRequest);
        List<Request> requests = new ArrayList<>();
        requests.add(request);

        BatchUpdateSpreadsheetRequest body = new BatchUpdateSpreadsheetRequest().setRequests(requests);

        service.spreadsheets().batchUpdate(ADMIN_SHEET, body).execute();
    }

    public static int getSheetNumber() throws IOException
    {
        String range = "Datasheet!Z1";
        List<List<Object>> fetchedData = fetchData(ADMIN_SHEET, range);

        return Integer.parseInt(fetchedData.getFirst().getFirst().toString());
    }

    public static void setSheetNumber(int val) throws IOException {
        String range = "Datasheet!Z1";
        List<List<Object>> values = new ArrayList<>();
        values.add(List.of(val));
        writeData(values, ADMIN_SHEET, range);
    }

    public static List<List<Object>> fetchData(String SHEET, String range)
    {
        ValueRange response;
        try {
            response = service.spreadsheets().values()
                    .get(SHEET, range)
                    .execute();
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
            response = new ValueRange();
        }


        List<List<Object>> data = response.getValues();
        if (data == null || data.isEmpty())
        {
            System.out.println("No match data found.");
            return Collections.singletonList(new ArrayList<>());
        }
        return data;
    }

    public static void writeData(List<List<Object>> inputData, String SHEET, String range)
    {
        ValueRange body = new ValueRange().setValues(inputData);
        try
        {
            service.spreadsheets().values().update(SHEET, range, body)
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
        }

    }

    public static void clearData(String range) {
        ClearValuesRequest requestBody = new ClearValuesRequest();

        try {
            Sheets.Spreadsheets.Values.Clear request =
                    service.spreadsheets().values().clear(ADMIN_SHEET, range, requestBody);

            request.execute();
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static boolean readMatchFlag()
    {
        List<List<Object>> data = fetchData(ADMIN_SHEET, "Datasheet!Y1");
        if (data.isEmpty()) return false;
        if (data.getFirst().isEmpty()) return false;
        return Boolean.parseBoolean(data.getFirst().getFirst().toString());
    }

    public static void writeMatchFlag(boolean flag)
    {
        List<List<Object>> data = new ArrayList<>(List.of(List.of(flag)));
        writeData(data, ADMIN_SHEET, "Datasheet!Y1");
    }

    public static void reduceSheetNumber()
    {
        try
        {
            int num = getSheetNumber() - 1;
            setSheetNumber(num);
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
        }

    }
}
