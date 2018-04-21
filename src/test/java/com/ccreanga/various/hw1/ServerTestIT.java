package com.ccreanga.various.hw1;


import com.google.common.io.CharStreams;

import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ServerTestIT {

    public static void main(String[] args) throws Exception {
        String session1 = login("10");
        String session2 = login("20");

        putStake(session1,"1","100");
        putStake(session1,"2","100");
        putStake(session1,"3","100");
        putStake(session2,"1","90");
        putStake(session2,"2","100");
        putStake(session2,"3","110");
        System.out.println(highestStakes("1"));
        System.out.println(highestStakes("2"));
        System.out.println(highestStakes("3"));
    }

    private static String login(String customerId) throws Exception{
        URL url = new URL("http://localhost:8000/"+customerId+"/session");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.connect();
        String session;
        try (Reader reader = new InputStreamReader(con.getInputStream())) {
            session = CharStreams.toString(reader);
        }
        con.disconnect();
        return session;
    }
    private static void putStake(String sessionId,String betOfferId,String stake) throws Exception{

        URL url = new URL("http://localhost:8000/"+betOfferId+"/stake?sessionkey="+sessionId);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput( true );
        con.setRequestProperty( "Content-Type", "application/x-www-form-urlencoded");
        con.setRequestProperty( "charset", "utf-8");
        con.setRequestProperty( "Content-Length", ""+stake.length());

        con.setUseCaches( false );
        con.getOutputStream().write(stake.getBytes());
        con.getOutputStream().close();
        con.getResponseCode();
//        System.out.println(con.getResponseCode());
        con.disconnect();

    }
    private static String highestStakes(String betOfferId) throws Exception{
        URL url = new URL("http://localhost:8000/"+betOfferId+"/highstakes");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.connect();
        String highestStakes;
        try (Reader reader = new InputStreamReader(con.getInputStream())) {
            highestStakes = CharStreams.toString(reader);
        }
        con.disconnect();
        return highestStakes;
    }


}
