import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import com.amazonaws.services.ec2.model.Instance;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import com.sun.net.httpserver.HttpExchange;
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

class HandleServer implements Runnable {
	private String instanceIp;
	private String instanceId;
	private AmazonEC2 ec2;


	HandleServer(String id, AmazonEC2 ec2) {
		this.instanceId = id;
		this.ec2 = ec2;
		System.out.println("HandleServer for" + id + " created.");

	}

	public void start() {
	}

	public void run() {
	}

	public void ping() {

	}

	public void sendRequest(HttpExchange request) {
		//http://<ip>:8000/mzrun.html?m=Maze50.maze&x0=1&y0=1&x1=6&y1=6&v=75&s=bfs
		try{
			DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
            List<Reservation> reservations = describeInstancesRequest.getReservations();
            Set<Instance> instances = new HashSet<Instance>();

            for (Reservation reservation : reservations) {
                instances.addAll(reservation.getInstances());
            }

            for (Instance i : instances){
            	if (i.getInstanceId().equals(this.instanceId)){
            		this.instanceIp = i.getPublicIpAddress();
            		break;
            	}
            }

            System.out.println(this.instanceIp);

            //send request to the instance
			URL url = new URL("http://"+ this.instanceIp +":8000/mzrun.html?"+request.getRequestURI().getQuery());
			System.out.println(url.toString());
			
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");

			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			String response = "";

			while ((inputLine = in.readLine()) != null) {
				response += inputLine + "\n";
			}
			in.close();


			//get html response from the instance
			request.sendResponseHeaders(200, response.length());
			OutputStream os = request.getResponseBody();
			os.write(response.getBytes());
			os.close();
		} catch (ConnectException | UnknownHostException e){
			System.out.println("Connect exception");
			try{
				Thread.sleep(10000);
			} catch (InterruptedException ie){
				ie.printStackTrace();
			}
			sendRequest(request);
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void updateLoad(){

	}
}
