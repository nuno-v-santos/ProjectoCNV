import java.util.HashSet;
import java.util.*;
import java.util.Set;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;

public class LoadBalancer {
    private static HashMap<String,HandleServer> instanceList = new HashMap<String,HandleServer>();
    private static AmazonEC2 ec2;

    private static void init() throws Exception {

        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
      ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-1a").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

      try {
            DescribeAvailabilityZonesResult availabilityZonesResult = ec2.describeAvailabilityZones();
            System.out.println("You have access to " + availabilityZonesResult.getAvailabilityZones().size() +
                    " Availability Zones.");
            /* using AWS Ireland. 
             * TODO: Pick the zone where you have your AMI, sec group and keys */
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


    }

   
    public static void newInstance(){
        System.out.println("Starting a new instance.");
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        runInstancesRequest.withImageId("ami-7431ad0b")  
                           .withInstanceType("t2.micro")
                           .withMinCount(1)
                           .withMaxCount(1)
                           .withKeyName("CNV")
                           .withSecurityGroups("CNV-ssh-http");
        RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
        String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
        HandleServer hs = new HandleServer(newInstanceId, ec2);
        hs.start();
        instanceList.put(newInstanceId,hs);
    }

    public void closeInstance(Instance i){
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
            //check args
            //check dynamo for metrics
            //check running instances for their load
            //send to correct instance based on dynamo metric and instance load/creat new instance
            for(String i : instanceList.keySet()) {
                HandleServer hs = instanceList.get(i);
                hs.sendRequest(request);
                break;
            }
        }
    }
}