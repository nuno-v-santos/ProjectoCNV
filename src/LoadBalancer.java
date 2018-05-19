import java.util.HashSet;
import java.util.*;
import java.util.Set;
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
    private static List<Instance> InstanceList = new ArrayList<>();
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
    }

    private static void firstLoad(){
        /*
         * Amazon EC2
         *
         * The AWS EC2 client allows you to create, delete, and administer
         * instances programmatically.
         *
         * In this sample, we use an EC2 client to get a list of all the
         * availability zones, and all instances sorted by reservation id, then 
         * create an instance, list existing instances again, wait a minute and 
         * the terminate the started instance.
         */
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
            System.out.println("Starting a new instance.");
            RunInstancesRequest runInstancesRequest =
               new RunInstancesRequest();

            /* TODO: configure to use your AMI, key and security group */
            runInstancesRequest.withImageId("ami-0053ce7f")
                               .withInstanceType("t2.micro")
                               .withMinCount(1)
                               .withMaxCount(1)
                               .withKeyName("CNV-lab-AWS")
                               .withSecurityGroups("CNV-ssh+http");
            RunInstancesResult runInstancesResult =
               ec2.runInstances(runInstancesRequest);
            InstanceList.add(runInstancesResult.getReservation().getInstances().get(0));
            runInstancesResult =
               ec2.runInstances(runInstancesRequest);
            InstanceList.add(runInstancesResult.getReservation().getInstances().get(0));
            
            describeInstancesRequest = ec2.describeInstances();
            reservations = describeInstancesRequest.getReservations();
            instances = new HashSet<Instance>();

            for (Reservation reservation : reservations) {
                instances.addAll(reservation.getInstances());
            }

            System.out.println("You have " + instances.size() + " Amazon EC2 instance(s) running.");
            System.out.println("Waiting 1 minute. See your instance in the AWS console...");
            Thread.sleep(60000);
            TerminateInstancesRequest termInstanceReq;
            System.out.println("Terminating the instances.");
            for(Instance i : InstanceList){
                termInstanceReq = new TerminateInstancesRequest();
                termInstanceReq.withInstanceIds(i.getInstanceId());
                ec2.terminateInstances(termInstanceReq);
            }

            
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }


    public static void main(String[] args) throws Exception {

        System.out.println("===========================================");
        System.out.println("Welcome to the AWS Java SDK!");
        System.out.println("===========================================");

        init();
        firstLoad();
        System.out.println("First Load Finished");
        System.out.println("Receiving Requests...");

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/mzrun.html", new MazeRunnerHandler());
        server.start();

        
    }

    //thread ping for each instance
    //response updates load in each instance

    static class MazeRunnerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            //check args
            //check dynamo for metrics
            //check running instances for their load
            //send to correct instance based on dynamo metric and instance load/creat new instance
        }
    }
}

