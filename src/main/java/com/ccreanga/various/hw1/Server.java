package com.ccreanga.various.hw1;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.*;

import com.google.common.cache.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Server {

    public class Stake{
        private int stake;
        private int customerId;

        public Stake(int stake, int customerId) {
            this.stake = stake;
            this.customerId = customerId;
        }

        public int getStake() {
            return stake;
        }

        public int getCustomerId() {
            return customerId;
        }
    }

    public static void main(String[] args) throws IOException {

        Cache<String, Integer> sessions = CacheBuilder.newBuilder()
                .maximumSize(1000000)
                .concurrencyLevel(16)
                .removalListener(removalNotification -> {

                })
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
        ConcurrentHashMap<Integer,PriorityBlockingQueue<Stake>> highStakes = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer,Stake> customersMaximumStake = new ConcurrentHashMap<>();




        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new MyHandler());

        ExecutorService threadPool = new ThreadPoolExecutor(
                16,
                256,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1));

        server.setExecutor(threadPool); // creates a default executor
        server.start();
    }

    static class MyHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            System.out.println(t.getRequestMethod());
            System.out.println(t.getRequestURI().getPath());
            System.out.println(t.getRequestURI().getQuery());

            String path  = t.getRequestURI().getPath();
            String query  = t.getRequestURI().getQuery();
            if (path.contains("session")){
                int customerId = extractId(path,'/');
                System.out.println(customerId);
            }else if (path.contains("stake")){

            }else if (path.contains("highstakes")){

            }

            String response = "Welcome Real's HowTo test page";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();

            /**
             Request: GET /<customerid>/session
             Response: <sessionkey> <customerid>: 31 bit unsigned integer number <sessionkey>: a string identifying the session (valid for 10 minutes)
             Example: http://localhost:8001/1234/session --> QWER12A
             *
             Request: POST /<betofferid>/stake?sessionkey=<sessionkey>
             Request body: <stake>
             Response: (empty) <stake>: 31 bit unsigned integer number <betofferid>: 31 bit unsigned integer number <sessionkey>: a session key retrieved from the session function
             Example: POST http://localhost:8001/888/stake?sessionkey=QWER12A with post body: 4500
             *
             Request: GET /<betofferid>/highstakes
             Response: CSV of <customerid>=<stake> <betofferid>: 31 bit unsigned integer number <stake>: 31 bit unsigned integer number <customerid>:  31 bit unsigned integer number
             4 Example: http://localhost:8001/888/highstakes -> 1234=4500,57453=1337
             */
        }
    }

    public static Integer extractId(String s, char separator) {
        int i1 = s.indexOf(separator);
        int i2 = s.indexOf(separator,i1+1);
        return Integer.parseInt(s.substring(i1+1,i2));
    }

}
