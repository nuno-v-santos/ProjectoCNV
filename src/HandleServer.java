import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
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
	private static final int MAX_RETRIES = 3;
	private static final int TIME_BETWEEN_PINGS = 10000;
	private static final int CACHE_SIZE = 2;
	private String instanceIp;
	private String instanceId;
	private AmazonEC2 ec2;
	public ArrayList<HttpExchange> handling;
	private ArrayList<HttpExchange> handled;
	private int retries = 0;
	private boolean isAlive = true;
	private String load;
	
	public Thread ping = new Thread() {
		public void run() {
			while (isAlive) {
				// we assume the instance will be alive once
				// if the ip address is not defined yet
				if (instanceIp == null || instanceIp.equals("")) {
					DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
					List<Reservation> reservations = describeInstancesRequest.getReservations();
					Set<Instance> instances = new HashSet<Instance>();

					for (Reservation reservation : reservations) {
						instances.addAll(reservation.getInstances());
					}

					for (Instance i : instances) {
						if (i.getInstanceId().equals(instanceId)) {
							instanceIp = i.getPublicIpAddress();
							break;
						}
					}
					System.out.println("Failed ping. IP not defined yet.");
				} else {
					// send ping

					try {
						URL url = new URL("http://" + instanceIp + ":8000/ping");
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						con.setRequestMethod("GET");
						System.out.println("Sucessfull ping for " + instanceIp);
					} catch (IOException e) {
						if(retries++ < MAX_RETRIES) {
							isAlive = false;
						}

					}
				}

				try {
					Thread.sleep(TIME_BETWEEN_PINGS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			// Instance died
			kill();
		}
	};

	HandleServer(AmazonEC2 ec2, String id) {
		this.instanceId = id;
		this.instanceIp = "";
		this.ec2 = ec2;
		this.handling = new ArrayList<HttpExchange>();
		this.handled = new ArrayList<HttpExchange>(CACHE_SIZE); 
		this.load = "0;"+id;
		System.out.println("HandleServer for " + id + " created.");

	}
	
	public void kill() {
		for (HttpExchange r : handling){
			// LoadBalancer.RedirectHandler.handle(r); how do i do this?
		}
		LoadBalancer.instanceList.remove(instanceId);
		LoadBalancer.closeInstance(instanceId);
		isAlive = false;
		
	}

	public void start() {
		ping.start();
	}

	public void run() {
	}

	public void sendRequest(HttpExchange request, Double metric) {
		try {
			handling.add(request);
			LoadBalancer.serverLoad.remove(load);
			Double newload = Integer.parseInt(load.split(";")[0]) + metric;
			load = newload + load.split(";")[1];
			LoadBalancer.serverLoad.put(load,(HandleServer)this);

			System.out.println("Received request");
			/*
			// waits for instance to be ready
			while (instanceIp == null || instanceIp.equals("")) {
				try {
					System.out.println("Instance not ready. Retrying to send request..");
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}*/

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
			if(handled.size() < CACHE_SIZE){
				handled.add(request);
			}
			else{
				LoadBalancer.requestsCache.remove(handled.get(0).getRequestURI().getQuery().hashCode());
				handled.remove(0);
				handled.add(request);
			}

			LoadBalancer.serverLoad.remove(load);
			newload = Integer.parseInt(load.split(";")[0]) - metric;
			load = newload + load.split(";")[1];
			LoadBalancer.serverLoad.put(load,(HandleServer)this);

		} catch (ConnectException e) {
			System.out.println("Retrying to send request");
			handling.remove(request);
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
			sendRequest(request,metric);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void updateLoad() {

	}

	public boolean isAlive() {
		return isAlive;
	}

	public boolean isBusy(){
		return handling.size() != 0;
	}

	public boolean readyForRequest(){
		if (handling.size() == 0)   //if it doesnt have requests
			return true;
		if (handling.size() == 1 && LoadBalancer.getMetric(handling.get(0).getRequestURI().getQuery()) == 0)  //if it has one request but is a small one
			return true;
		return false;
	}
}
