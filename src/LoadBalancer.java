import java.util.HashSet;
import java.util.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	public static ConcurrentHashMap<String, HandleServer> instanceList = new ConcurrentHashMap<>();
	private static AmazonEC2 ec2;
	private static final String tableName = "Metrics";
	private static AmazonDynamoDB dynamoDB;
	private static ConcurrentHashMap<Integer, HandleServer> requestsCache = new  ConcurrentHashMap<>();
	private static final int CACHE_SIZE = 1024;
	
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
			CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
					.withKeySchema(new KeySchemaElement().withAttributeName("Heuristic").withKeyType(KeyType.HASH))
					.withAttributeDefinitions(
							new AttributeDefinition().withAttributeName("Heuristic").withAttributeType(ScalarAttributeType.S))
					.withProvisionedThroughput(
							new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

			// Create table if it does not exist yet
			TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
			// wait for the table to move into ACTIVE state
			TableUtils.waitUntilActive(dynamoDB, tableName);

			// Describe our new table
			DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
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
		runInstancesRequest.withImageId("ami-7431ad0b").withInstanceType("t2.micro").withMinCount(1).withMaxCount(1)
				.withKeyName("CNV").withSecurityGroups("CNV-ssh-http");
		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
		String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
		HandleServer hs = new HandleServer(ec2, newInstanceId);
		hs.start();
		instanceList.put(newInstanceId, hs);
	}

	public void closeInstance(Instance i) {
		TerminateInstancesRequest termInstanceReq;
		System.out.println("Terminating the instance:" + i);
		termInstanceReq = new TerminateInstancesRequest();
		termInstanceReq.withInstanceIds(i.getInstanceId());
		ec2.terminateInstances(termInstanceReq);
	}

	public static void main(String[] args) throws Exception {

		System.out.println("===========================================");
		System.out.println("Welcome to the AWS Java SDK!");
		System.out.println("===========================================");

		init();

		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/mzrun.html", new RedirectHandler());
		server.start();

		newInstance();

		System.out.println("Receiving Requests...");
	}

	static class RedirectHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange request) throws IOException {
			// verify if it is in local cache
			int requestHash = request.getRequestURI().getQuery().hashCode();
			HandleServer hs = requestsCache.get(requestHash);
			if(hs != null) {
				if(hs.isAlive()) {
					hs.sendRequest(request);
					return;
				} else {
					requestsCache.remove(requestHash);
				}
			}
			
			// choose instance
			Double d = getMetric(request.getRequestURI().getQuery());
			for (String i : instanceList.keySet()) {
				hs = instanceList.get(i);
				hs.sendRequest(request);
				// add to cache
				requestsCache.put(requestHash, hs);
				
				// if limit is reached delete random entry
				if(requestsCache.size() > CACHE_SIZE) {
					requestsCache.remove(requestsCache.keys().nextElement());
				}
				break;
			}
		}

		private static Double getMetric(String requestQuery) {
			String[] receivedArgs = requestQuery.split("&");
			String[] args = new String[8];
			for (String arg : receivedArgs) {
				String[] attributes = arg.split("=");
				if (attributes[0].equals("x0")) {
					args[0] = attributes[1];
				} else if (attributes[0].equals("y0")) {
					args[1] = attributes[1];
				} else if (attributes[0].equals("x1")) {
					args[2] = attributes[1];
				} else if (attributes[0].equals("y1")) {
					args[3] = attributes[1];
				} else if (attributes[0].equals("v")) {
					args[4] = attributes[1];
				} else if (attributes[0].equals("s")) {
					args[5] = attributes[1];
				} else if (attributes[0].equals("m")) {
					args[6] = "MazeRunner/" + attributes[1];
				}
			}
			// Scan items for movies with a year attribute greater than 1985
			HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
			Condition condition =  new Condition()
					.withComparisonOperator(ComparisonOperator.GT.toString())
					.withAttributeValueList(new AttributeValue().withN("1985"));
			scanFilter.put("year", condition);
			ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
			ScanResult scanResult = dynamoDB.scan(scanRequest);
			System.out.println("Result: " + scanResult);
			return 1.;
		}
	}
}