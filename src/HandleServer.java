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
	private ArrayList<String> handling;
	private ArrayList<String> handled;
	private boolean status = 0;
	private Thread ping = new Thread(){
		public void run(){
			private boolean isAlive = true;
			while(isAlive){
				try{
					if(instanceIp.equals("")){
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
		            }

					URL url = new URL("http://"+ this.instanceIp +":8000/ping");
		       		HttpURLConnection con = (HttpURLConnection) url.openConnection();
			   		con.setRequestMethod("GET");
			   		status = 1;

	           	} catch (ConnectException | UnknownHostException | InterruptedException e){
					if(status == 1){
						LoadBalancer.instanceList.remove(instanceId);
						isAlive=false;
					}

					System.out.println("Connect exception");
				}
				Thread.sleep(10000);
			}			
		}
	}


	HandleServer(AmazonEC2 ec2, String id) {
		this.instanceId = id;
		this.instanceIp = "";
		this.ec2 = ec2;
		this.handling = new ArrayList<String>();
		this.handled = new ArrayList<String>();
		status = 0;
		System.out.println("HandleServer for" + id + " created.");

	}

	public void start() {
		ping.start();
	}


	public void run() {
	}

	
	public void sendRequest(HttpExchange request) {
		try{
			
            //send request to the instance
			URL url = new URL("http://"+ this.instanceIp +":8000/mzrun.html?"+request.getRequestURI().getQuery());
			System.out.println(url.toString());
			
			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			handling.add(request.getRequestURI().getQuery());

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

			handling.remove(request.getRequestURI().getQuery());
			handled.add(request.getRequestURI().getQuery());

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
