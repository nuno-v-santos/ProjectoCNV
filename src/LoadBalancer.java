import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;

public class LoadBalancer {
    private static final String IMAGE_ID = "ami-b7039dc8";
    private static final String AWS_KEY = "CNV";
    private static final String AWS_SECURITY_GROUP = "CNV-ssh-http";
    private static final int MAX_EQUAL_REQUESTS_PER_SERVER = 10;
    private static final int CACHE_SIZE = 1024;
	private static final String TABLE_NAME = "Metrics";

	public static ConcurrentHashMap<String, HandleServer> instanceList = new ConcurrentHashMap<>();
	public static ConcurrentHashMap<Integer, HandleServer> requestsCache = new  ConcurrentHashMap<>();
    public static ConcurrentHashMap<HandleServer, Double> serverLoad = new  ConcurrentHashMap<>();

	private static AmazonEC2 ec2;
	private static AmazonDynamoDB dynamoDB;
	
	private static void init() throws Exception {

		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
					+ "Please make sure that your credentials file is at the correct "
					+ "location (~/.aws/credentials), and is in valid format.", e);
		}
		ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-1a")
				.withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

		try {
			DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
			System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size()
					+ " Availability Zones.");

			DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
			List<Reservation> reservations = describeInstancesRequest.getReservations();
			Set<Instance> instances = new HashSet<Instance>();

			for (Reservation reservation : reservations) {
				instances.addAll(reservation.getInstances());
			}

			System.out.println("You have " + instances.size() + " Amazon EC2 instance(s) running.");

		} catch (AmazonServiceException ase) {
			System.out.println("Caught Exception: " + ase.getMessage());
			System.out.println("Reponse Status Code: " + ase.getStatusCode());
			System.out.println("Error Code: " + ase.getErrorCode());
			System.out.println("Request ID: " + ase.getRequestId());
		}
		
		// init database
		
		dynamoDB = AmazonDynamoDBClientBuilder
				.standard()
				.withCredentials(new AWSStaticCredentialsProvider(credentials))
				.withRegion("us-east-1")
				.build();

		try {
			// Create a table with a primary hash key named 'name', which holds a string
			CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(TABLE_NAME)
					.withKeySchema(new KeySchemaElement().withAttributeName("Heuristic").withKeyType(KeyType.HASH))
					.withAttributeDefinitions(
							new AttributeDefinition().withAttributeName("Heuristic").withAttributeType(ScalarAttributeType.N))
					.withProvisionedThroughput(
							new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

			// Create table if it does not exist yet
			TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
			// wait for the table to move into ACTIVE state
			TableUtils.waitUntilActive(dynamoDB, TABLE_NAME);

			// Describe our new table
			DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(TABLE_NAME);
			TableDescription tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
			System.out.println("Table Description: " + tableDescription);
		} catch (AmazonServiceException ase) {
			System.out.println("Caught an AmazonServiceException, which means your request made it "
					+ "to AWS, but was rejected with an error response for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
			System.out.println("HTTP Status Code: " + ase.getStatusCode());
			System.out.println("AWS Error Code:   " + ase.getErrorCode());
			System.out.println("Error Type:       " + ase.getErrorType());
			System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			System.out.println("Caught an AmazonClientException, which means the client encountered "
					+ "a serious internal problem while trying to communicate with AWS, "
					+ "such as not being able to access the network.");
			System.out.println("Error Message: " + ace.getMessage());
		}

	}

	public static void newInstance() {
		System.out.println("Starting a new instance.");
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
		runInstancesRequest.withImageId(IMAGE_ID).withInstanceType("t2.micro").withMinCount(1).withMaxCount(1)
				.withKeyName(AWS_KEY).withSecurityGroups(AWS_SECURITY_GROUP);
		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
		String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
		HandleServer hs = new HandleServer(ec2, newInstanceId);
		hs.start();
		instanceList.put(newInstanceId, hs);
        serverLoad.put(hs,0.0);
	}

	public static void closeInstance(String id) {
		TerminateInstancesRequest termInstanceReq;
		System.out.println("Terminating the instance: " + id);
		termInstanceReq = new TerminateInstancesRequest();
		termInstanceReq.withInstanceIds(id);
		ec2.terminateInstances(termInstanceReq);
	}

	public static void main(String[] args) throws Exception {

		System.out.println("===========================================");
		System.out.println("Welcome to the AWS Java SDK!");
		System.out.println("===========================================");

		init();

		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/mzrun.html", new RedirectHandler());
		server.createContext("/ping", new PingHandler());
		int poolSize = Runtime.getRuntime().availableProcessors() + 1;
        server.setExecutor(Executors.newFixedThreadPool(poolSize));
		server.start();

		newInstance();
        autoscaler.start();

		System.out.println("Receiving Requests...");
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

	public static class RedirectHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange request) throws IOException {
			// verify if it is in local cache
			int requestHash = request.getRequestURI().getQuery().hashCode();
			HandleServer hs = requestsCache.get(requestHash);
			if(hs != null && hs.handling.size() < MAX_EQUAL_REQUESTS_PER_SERVER) {
				if(hs.isAlive()) {
					System.out.println("Sending request to cache");
					hs.sendRequest(request,getMetric(request.getRequestURI().getQuery()));
					return;
				} else {
					System.out.println("The instance died - removing from cache: " + requestHash);
					requestsCache.remove(requestHash);
				}
			}

			// choose instance
			Double metric = getMetric(request.getRequestURI().getQuery());
            Double min = Double.MAX_VALUE;
            for (Map.Entry<HandleServer, Double> entry: serverLoad.entrySet()) {
            	if(entry.getValue() < min) {
            		min = entry.getValue();
            	    hs = entry.getKey();
            	}            	
            }
            System.out.println("Sending request");
            hs.sendRequest(request, metric);

            System.out.println("Adding to cache. Cache size = " + requestsCache.size());
            requestsCache.put(requestHash, hs);
            for(int hash : requestsCache.keySet()) {
            	System.out.println("\t" + hash);
            }
            System.out.println("Added to cache. Cache size = " + requestsCache.size());
            if(requestsCache.size() > CACHE_SIZE) {
				requestsCache.remove(requestsCache.keys().nextElement());
			}
		}
	}

    private static double calculateHeuristic(String query) {
    	String[] args = query.split("&");
    	int x0=0, y0=0, x1=0, y1=0, v=0, m=0;
    	double heuristic;
    	for (String arg : args){
			String[] attributes = arg.split("=");
			if (attributes[0].equals("x0")){
				x0 = Integer.parseInt(attributes[1]);
			} else if (attributes[0].equals("y0")){
				y0 = Integer.parseInt(attributes[1]);
			} else if (attributes[0].equals("x1")){
				x1 = Integer.parseInt(attributes[1]);
			} else if (attributes[0].equals("y1")){
				y1 = Integer.parseInt(attributes[1]);
			} else if (attributes[0].equals("v")){
				v = Integer.parseInt(attributes[1]);
			} else if (attributes[0].equals("m")){
				m = Integer.parseInt(attributes[1].substring(4, attributes[1].length()-5));
			}
		}
    	heuristic = Math.sqrt(Math.pow((x1-x0),2) + Math.pow((y1-y0),2) + 500/v + 10*m);
    	System.out.println("Heuristic of query " + query + " is " + heuristic);
		return heuristic;
	}

    public static Double getMetric(String query) {
        //Check if table empty
        ScanRequest scanRequest = new ScanRequest(TABLE_NAME);
        if (dynamoDB.scan(scanRequest).getCount() == 0) {
        	System.out.println("Dynamo is empty");
        	return .0;
        }
        double heuristic = calculateHeuristic(query);
        
        // Check if its there
        HashMap<String,AttributeValue> key_to_get = new HashMap<String,AttributeValue>();
        key_to_get.put("Heuristic", new AttributeValue().withN(Double.toString(heuristic)));
        GetItemRequest r = new GetItemRequest()
                .withKey(key_to_get)
                .withTableName(TABLE_NAME);
        Map<String, AttributeValue> returned_item = dynamoDB.getItem(r).getItem();
        if (returned_item != null) {
        	for (String key : returned_item.keySet()) {
                System.out.format("Dynamo found match %s: %s\n", key, returned_item.get(key).toString());
                return Double.parseDouble(returned_item.get(key).getN());
        	}
        }
        
        // weighted average between lower and higher values of dynamo
        // get higher
        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        Condition condition =  new Condition()
                .withComparisonOperator(ComparisonOperator.GT.toString())
                .withAttributeValueList(new AttributeValue().withN(Double.toString(heuristic)));
        scanFilter.put("Heuristic", condition);
        scanRequest = new ScanRequest(TABLE_NAME).withScanFilter(scanFilter);
        ScanResult scanResultGT = dynamoDB.scan(scanRequest);

        //get smaller
        scanFilter = new HashMap<String, Condition>();
        condition =  new Condition()
                .withComparisonOperator(ComparisonOperator.LT.toString())
                .withAttributeValueList(new AttributeValue().withN(Double.toString(heuristic)));
        scanFilter.put("Heuristic", condition);
        scanRequest = new ScanRequest(TABLE_NAME).withScanFilter(scanFilter);
        ScanResult scanResultLT = dynamoDB.scan(scanRequest);

        System.out.println("Results LT -> " + scanResultLT);
        System.out.println("Results GT -> " + scanResultGT);

        double lowerHeuristic = .0, lowerMetric = .0, higherHeuristic = .0, higherMetric = .0;
        if (scanResultGT.getCount() != 0 && scanResultLT.getCount() != 0) {
            Map<String, AttributeValue> lower = scanResultLT.getItems().get(0);
            for (Map.Entry<String, AttributeValue> pair : lower.entrySet()) {
                lowerHeuristic = Double.parseDouble(pair.getKey());
                lowerMetric = Double.parseDouble(pair.getValue().getN());
            }
            Map<String, AttributeValue> higher =  scanResultGT.getItems().get(0);
            for (Map.Entry<String, AttributeValue> pair : higher.entrySet()) {
            	higherHeuristic = Double.parseDouble(pair.getKey());
                higherMetric = Double.parseDouble(pair.getValue().getN());
            }
            // weighted average formula
            return lowerMetric + (higherMetric - lowerMetric) * ((heuristic - lowerHeuristic) / (higherHeuristic - lowerHeuristic));
        } else if (scanResultLT.getCount() != 0) {
            AttributeValue lower = (AttributeValue) scanResultLT.getItems().get(0).values().toArray()[0];
            return Double.parseDouble(lower.getN());
        } else if (scanResultGT.getCount() != 0){
            AttributeValue higher = (AttributeValue) scanResultGT.getItems().get(0).values().toArray()[0];
        	return Double.parseDouble(higher.getN());
        }
        return .0;
    }

    public static Thread autoscaler = new Thread(){
        public void run(){
            while(true){

                //auto-scaler decrease rules
                int toleratedFreeInstances = 1;         //number of free instances possible
                for(String i : instanceList.keySet()) {
                    HandleServer hs = instanceList.get(i);
                    if (hs.isFree()){ //if instance is not busy
                        if (toleratedFreeInstances > 0){
                            toleratedFreeInstances--;
                        } else {
                            hs.kill();
                        }
                    }
                }

                //auto-scaler increase rules
                int freeToReceive = 0;         //number of instances that have no requests or have only a small one
                for(String i : instanceList.keySet()) {
                    HandleServer hs = instanceList.get(i);
                    if (hs.readyForRequest())
                        freeToReceive++;
                }
                if (freeToReceive == 0){
                    newInstance();
                }

                try{
                    Thread.sleep(30000);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    };
}