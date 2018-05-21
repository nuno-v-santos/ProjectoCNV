import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;

public class WebServer {

    static ConcurrentHashMap<Long, Double> threadMetrics = new ConcurrentHashMap<>();
    private static AmazonDynamoDB dynamoDB;
    private static String tableName = "Metrics";

    public static void main(String[] args) throws Exception {
        initializeDataBase();
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/mzrun.html", new MazeRunnerHandler());
        server.createContext("/ping", new PingHandler());
        int poolSize = Runtime.getRuntime().availableProcessors() + 1;
        server.setExecutor(Executors.newFixedThreadPool(poolSize));
        System.out.println("Running");
        server.start();
    }

    static class PingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "This was the query:" + t.getRequestURI().getQuery()   + "##";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class MazeRunnerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            ServeRequest r = new ServeRequest(t.getRequestURI().getQuery().hashCode(), t);
            r.start();
        }
    }

    public static void writeToDynamo(Long threadId, String[] args) {
        String metric = threadMetrics.get(threadId).toString();
        String heuristic = Double.toString(calculateHeuristic(args));
        
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("Heuristic", new AttributeValue().withN(heuristic));
        item.put("Metric", new AttributeValue().withN(metric));
        PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
        PutItemResult putItemResult = dynamoDB.putItem(putItemRequest);
        
        System.out.println("Result: " + putItemResult);
        System.out.println("Thread writing metrics: " + args + ":" + metric);
    }

    private static double calculateHeuristic(String[] args) {
        int x0 = Integer.parseInt(args[0]);
        int y0 = Integer.parseInt(args[1]);
        int x1 = Integer.parseInt(args[2]);
        int y1 = Integer.parseInt(args[3]);
        int v = Integer.parseInt(args[4]);
        //int s = Integer.parseInt(args[5]);
        int m = Integer.parseInt(args[6].substring(15, args[6].length()-5));
        double heuristic = Math.sqrt(Math.pow((x1-x0),2) + Math.pow((y1-y0),2) + 500/v + 10*m);
    	System.out.println("Heuristic of query " + args.toString() + " is " + heuristic);
		return heuristic;
	}

	private static void initializeDataBase() throws Exception {
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        try {
            credentialsProvider.getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
            .withCredentials(credentialsProvider)
            .withRegion("us-east-1")
            .build();
    }
}



