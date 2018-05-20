import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private static final String IMAGE_ID = "ami-7431ad0b";
    private static final String AWS_KEY = "CNV";
    private static final String AWS_SECURITY_GROUP = "CNV-ssh-http";
	public static ConcurrentHashMap<String, HandleServer> instanceList = new ConcurrentHashMap<>();
	private static AmazonEC2 ec2;
	private static final String tableName = "Metrics";
	private static AmazonDynamoDB dynamoDB;
	public static ConcurrentHashMap<Integer, HandleServer> requestsCache = new  ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, HandleServer> serverLoad = new  ConcurrentHashMap<>();
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
		runInstancesRequest.withImageId(IMAGE_ID).withInstanceType("t2.micro").withMinCount(1).withMaxCount(1)
				.withKeyName(AWS_KEY).withSecurityGroups(AWS_SECURITY_GROUP);
		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
		String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
		HandleServer hs = new HandleServer(ec2, newInstanceId);
		hs.start();
		instanceList.put(newInstanceId, hs);
        serverLoad.put("0;"+newInstanceId, hs);

	}

	public static void closeInstance(String i) {
		TerminateInstancesRequest termInstanceReq;
		System.out.println("Terminating the instance: " + i);
		termInstanceReq = new TerminateInstancesRequest();
		termInstanceReq.withInstanceIds(i);
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
        autoscaler.start();

		System.out.println("Receiving Requests...");
	}

	public static class RedirectHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange request) throws IOException {
			// verify if it is in local cache
			int requestHash = request.getRequestURI().getQuery().hashCode();
			HandleServer hs = requestsCache.get(requestHash);
			if(hs != null) {
				if(hs.isAlive() && hs.handling.size() < 10) {
					hs.sendRequest(request,calculateHeuristic(request.getRequestURI().getQuery().split("&")));
					return;
				} else {
					requestsCache.remove(requestHash);
				}
			}
			
			// choose instance
			Double d = getMetric(request);

            ArrayList<String> loads = new  ArrayList<String>();
            loads.addAll(serverLoad.keySet());
            Collections.sort(loads, new LoadComparator());
            hs = serverLoad.get(loads.get(0));
            hs.sendRequest(request,d);
            
            requestsCache.put(requestHash, hs);
            if(requestsCache.size() > CACHE_SIZE) {
				requestsCache.remove(requestsCache.keys().nextElement());
			}
		}
	}

    public static double calculateHeuristic(String[] args) {
        int x0 = Integer.parseInt(args[1]);
        int y0 = Integer.parseInt(args[2]);
        int x1 = Integer.parseInt(args[3]);
        int y1 = Integer.parseInt(args[4]);
        int v = Integer.parseInt(args[5]);
        //int s = Integer.parseInt(args[6]);
        int m = Integer.parseInt(args[0].substring(4, args[0].length()-5));
        return Math.sqrt((x1-x0)^2 + (y1-y0)^2) * 1/v * m ;
    }

    public static Double getMetric(HttpExchange request) {
    	//peter did
		return null;       
    }

    public static Thread autoscaler = new Thread(){
        public void run(){
            while(true){

                //auto-scaler decrease rules
                int toleratedFreeInstances = 1;         //number of free instances possible
                for(String i : instanceList.keySet()) {
                    HandleServer hs = instanceList.get(i);
                    if (!hs.isBusy()){ //if instance is not busy
                        if (toleratedFreeInstances <= 0){
                            hs.kill();
                        } else {
                            toleratedFreeInstances--;
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
                    Thread.sleep(5000);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    };

    private static class LoadComparator implements Comparator<String> {
        public LoadComparator() {
        }
        @Override
        public int compare(String load1, String load2) {
            if (Integer.parseInt(load2.split(";")[0]) < Integer.parseInt(load1.split(";")[0]))
                return -1;
            else
                return 1;
        }
		
    }
}