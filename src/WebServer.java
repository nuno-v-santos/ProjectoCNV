import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.Arrays;
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

public class WebServer {
	// associates the id of a thread with the metrics
	public static ConcurrentHashMap<Long, Double> threadMetrics = new ConcurrentHashMap<>();
	private static AmazonDynamoDB dynamoDB;
	private static String tableName = "Metrics";
	private static final int CONCURRENT_THREADS = 30;
	private static final int PORT = 8000;
	
	public static void main(String[] args) throws Exception {
		initializeDataBase();
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.createContext("/mzrun.html", new MazeRunnerHandler());
		server.createContext("/ping", new PingHandler());
		server.setExecutor(Executors.newFixedThreadPool(CONCURRENT_THREADS));
		System.out.println("Running");
		server.start();
	}

	static class PingHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			String response = "This was the query:" + t.getRequestURI().getQuery();
			t.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}
	}

	static class MazeRunnerHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			new ServeRequest(t.getRequestURI().getQuery().hashCode(), t).start();
		}
	}

	public static void writeToDynamo(Long threadId, String[] args) {
		String metric = threadMetrics.get(threadId).toString();
		String heuristic = Double.toString(calculateHeuristic(args));
		
		Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
		item.put("Heuristic", new AttributeValue().withN(heuristic));
		item.put("Metric", new AttributeValue().withN(metric));

		dynamoDB.putItem(new PutItemRequest(tableName, item));
		
		System.out.println("Thread writing metrics: " + Arrays.toString(args) + ":" + metric);
	}

	private static double calculateHeuristic(String[] args) {
		int x0 = Integer.parseInt(args[0]), y0 = Integer.parseInt(args[1]);
		int x1 = Integer.parseInt(args[2]), y1 = Integer.parseInt(args[3]);
		int v = Integer.parseInt(args[4]);
		int m = Integer.parseInt(args[6].substring(15, args[6].length()-5));
		
		double heuristic = Math.sqrt(Math.pow((x1-x0),2) + Math.pow((y1-y0),2) + 500/v + 10*m);
		System.out.println("Heuristic of query " + Arrays.toString(args) + " is " + heuristic);
		return heuristic;
	}

	private static void initializeDataBase() throws Exception {
		ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
		try {
			credentialsProvider.getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. ", e);
		}
		dynamoDB = AmazonDynamoDBClientBuilder.standard()
			.withCredentials(credentialsProvider)
			.withRegion("us-east-1")
			.build();
	}
}



