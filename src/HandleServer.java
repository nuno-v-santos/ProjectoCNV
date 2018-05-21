import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.amazonaws.services.ec2.model.Instance;
import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;

class HandleServer implements Runnable {
	private static final double THRESHOLD_VALUE = 18788059;
	private static final int TIME_BETWEEN_RETRIES = 3333;
	private static final int TIME_BETWEEN_PINGS = 10000;
	private static final int INSTANCE_CACHE_SIZE = 2;
	private static final int RETRY_INTERVAL = 10000; // interval to retry sending request
	private static final int MAX_RETRIES = 3;
	
	private String instanceIp = "";
	private String instanceId = "";
	private AmazonEC2 ec2;
	public ArrayList<HttpExchange> handling;
	private ArrayList<HttpExchange> handled;
	private int retries = 0;
	private int status = 0;  //0:no ip  1:ip but not ready  2:running   3:dead
	private double load = 0;
	
	public Thread ping = new Thread() {
		public void run() {
			while (status < 3) {
				// we assume the instance will be alive once
				// if the ip address is not defined yet
				if (status == 0) {
					DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
					List<Reservation> reservations = describeInstancesRequest.getReservations();
					Set<Instance> instances = new HashSet<Instance>();

					for (Reservation reservation : reservations) {
						instances.addAll(reservation.getInstances());
					}

					for (Instance i : instances) {
						if (i.getInstanceId().equals(instanceId)) {
							instanceIp = i.getPublicIpAddress();
							if (instanceIp != null && !instanceIp.equals(""))
								status = 1;
							break;
						}
					}
				} else {
					try {
						URL url = new URL("http://" + instanceIp + ":8000/ping");
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						con.setConnectTimeout(TIME_BETWEEN_RETRIES);
						con.setRequestMethod("GET");
						if (con.getResponseCode() == HttpURLConnection.HTTP_OK){
							status = 2;
							retries = 0;
							try {
								Thread.sleep(TIME_BETWEEN_PINGS);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						} else if (status == 2){
							throw new IOException();
						}
					} catch (IOException e) {
						if(++retries > MAX_RETRIES && status == 2) {
							status = 3;
						}
						try {
							Thread.sleep(TIME_BETWEEN_RETRIES);
						} catch (InterruptedException ie) {
							ie.printStackTrace();
						}
					}
				}
			}

			kill();
			this.interrupt();
		}
	};

	public HandleServer(AmazonEC2 ec2, String id) {
		this.instanceId = id;
		this.ec2 = ec2;
		this.handling = new ArrayList<HttpExchange>();
		this.handled = new ArrayList<HttpExchange>(INSTANCE_CACHE_SIZE); ;
		System.out.println("HandleServer for " + id + " created.");

	}
	
	public void kill() {
		System.out.println("Instance " + instanceId + " terminated");
		status = 3;
		LoadBalancer.instanceList.remove(instanceId);
		LoadBalancer.serverLoad.remove((HandleServer)this);
		LoadBalancer.closeInstance(instanceId);
		for (HttpExchange r : handling){
			System.out.println("Sending unserved" + r);
			LoadBalancer.send(r);
		}
		Thread.currentThread().interrupt();
		
	}

	public void start() {
		ping.start();
	}

	public void run() {
	}

	public void sendRequest(HttpExchange request, Double metric) {
		try {
			handling.add(request);
			
			load += metric;
			LoadBalancer.serverLoad.put(this, load);

			// send request to the instance
			URL url = new URL("http://" + this.instanceIp + ":8000/mzrun.html?" + request.getRequestURI().getQuery());

			HttpURLConnection con = (HttpURLConnection) url.openConnection();			

			con.setRequestMethod("GET");

			// read response from the instance
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			String response = "";
			while ((inputLine = in.readLine()) != null) {
				response += inputLine + "\n";
			}
			in.close();

			// send response of the instance to the client
			request.sendResponseHeaders(200, response.length());
			OutputStream os = request.getResponseBody();
			os.write(response.getBytes());
			os.close();

			handling.remove(request);
			if(handled.size() < INSTANCE_CACHE_SIZE){
				handled.add(request);
			} else{
				LoadBalancer.requestsCache.remove(handled.get(0).getRequestURI().getQuery().hashCode());
				handled.remove(0);
				handled.add(request);
			}
			load -= metric;
			LoadBalancer.serverLoad.put(this, load);

		} catch (IOException e) {
			System.out.println("Retrying to send request");
			handling.remove(request);
			load -= metric;
			LoadBalancer.serverLoad.put((HandleServer)this, load);
			try {
				Thread.sleep(RETRY_INTERVAL);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
			sendRequest(request,metric);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean isAlive() {
		return status != 3;
	}

	public boolean isFree(){
		return handling.size() == 0;
	}

	public boolean readyForRequest(){
		System.out.println("Instance " + instanceId + " has load " + load);
		return load <= THRESHOLD_VALUE;
	}
}
