package com.ccreanga.various.hw1;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.Striped;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

public class Server {

    static Striped<ReadWriteLock> stripedLock = Striped.lazyWeakReadWriteLock(1024);
    static ReentrantLock stakeLock = new ReentrantLock();
    static ConcurrentHashMap<Integer, PriorityBlockingQueue<Stake>> highStakes = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, Integer> customersMaximumStake = new ConcurrentHashMap<>();
    static Cache<String, Integer> sessions = CacheBuilder.newBuilder()
            .maximumSize(1000000)
            .concurrencyLevel(16)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    static Cache<Integer, String> customersSession = CacheBuilder.newBuilder()
            .maximumSize(1000000)
            .concurrencyLevel(16)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();



    public static void main(String[] args) throws IOException {


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
        public void handle(HttpExchange httpExchange) throws IOException {
            System.out.println(httpExchange.getRequestMethod()+" "+httpExchange.getRequestURI().getPath());

            String path = httpExchange.getRequestURI().getPath();
            String method = httpExchange.getRequestMethod();
            System.out.println(path);
            String query = httpExchange.getRequestURI().getQuery();
            if (method.equals("GET")) {
                if (path.contains("session")) {
                    int customerId = extractId(path, '/');
                    String response;
                    Lock customerLock = stripedLock.get(customerId).readLock();
                    customerLock.lock();
                    try {
                        String session = customersSession.getIfPresent(customerId);
                        if (session != null) {
                            System.out.printf("already created %s %n", customerId);
                            response = session;
                        } else {
                            System.out.printf("creating session %s %n", customerId);
                            response = UUID.randomUUID().toString().replaceAll("-", "");
                            customersSession.put(customerId, response);
                            sessions.put(response, customerId);
                        }
                    } finally {
                        customerLock.unlock();
                    }
                    httpExchange.sendResponseHeaders(200, response.length());
                    OutputStream os = httpExchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();

                } else if (path.contains("highstakes")) {
                    int betOfferId = extractId(path, '/');
                    PriorityBlockingQueue<Stake> queue = highStakes.get(betOfferId);
                    if (queue == null) {
                        httpExchange.sendResponseHeaders(404, 0);
                        return;
                    }
                    StringBuilder sb = new StringBuilder(256);
                    for (int i = 0; i < 20; i++) {
                        Stake stake = queue.poll();
                        if (stake == null)
                            break;
                        sb.append(stake.getCustomerId()).append("=").append(stake.getStake()).append(",");
                    }

                    String response = sb.substring(0, sb.length() - 1);
                    httpExchange.sendResponseHeaders(200, response.length());
                    OutputStream os = httpExchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
            }else if  (method.equals("POST")) {

                if (path.contains("stake")) {
                    int betOfferId = extractId(path, '/');
                    String sessionId = paramValue(query, "sessionkey");
                    Integer customerId = sessions.getIfPresent(sessionId);
                    if (customerId == null) {
                        System.out.printf("not authorized %s %n", customerId);
                        httpExchange.sendResponseHeaders(401, 0);
                        httpExchange.close();
                    } else {
                        int currentStake = 0;
                        try (Reader reader = new InputStreamReader(httpExchange.getRequestBody())) {
                            currentStake = Integer.parseInt(CharStreams.toString(reader));
                        }
                        System.out.printf("customerId %s stake %s %n", customerId, currentStake);
                        Lock customerLock = stripedLock.get(customerId).readLock();
                        customerLock.lock();
                        try {
                            Integer previousMaximumStake = customersMaximumStake.get(betOfferId + "-" + customerId);
                            if ((previousMaximumStake == null) || (currentStake > previousMaximumStake)) {
                                customersMaximumStake.put(betOfferId + "-" + customerId, currentStake);
                                stakeLock.lock();
                                PriorityBlockingQueue<Stake> queue = highStakes.get(betOfferId);
                                try {
                                    if (queue == null) {
                                        queue = new PriorityBlockingQueue<>(128, Comparator.comparingInt(Stake::getStake).reversed());
                                        highStakes.put(betOfferId,queue);
                                    }
                                } finally {
                                    stakeLock.unlock();
                                }
                                queue.add(new Stake(currentStake, customerId));
                            }

                        } finally {
                            customerLock.unlock();
                        }
                        httpExchange.sendResponseHeaders(200, 0);
                        httpExchange.close();
                    }


                }

            }
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
        int i2 = s.indexOf(separator, i1 + 1);
        return Integer.parseInt(s.substring(i1 + 1, i2));
    }

    public static String paramValue(String query, String name) {
        int i1 = query.indexOf(name);
        return query.substring(i1 + name.length() + 1);
    }

}
