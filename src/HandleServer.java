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
	private static final double THRESHOLD_VALUE = 18788059;  //TODO: choose value here
	private String instanceIp;
	private String instanceId;
	private AmazonEC2 ec2;
	public ArrayList<HttpExchange> handling;
	private ArrayList<HttpExchange> handled;
	private int retries = 0;
	private int status = 0;  //0:no ip  1:ip but not ready  2:running   3:dead
	private String load;
	
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
					System.out.println("Failed ping. IP not defined yet.");
				} else {
					try {
						URL url = new URL("http://" + instanceIp + ":8000/ping");
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						con.setRequestMethod("GET");
						if (con.getResponseCode() == 200){
							System.out.println("Sucessfull ping for " + instanceIp);
							status = 2;
						} else {
							System.out.println("Failed ping " + instanceIp);
						}
					} catch (IOException e) {
						if(retries++ > MAX_RETRIES && status == 2) {
							status = 3;
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

	public HandleServer(AmazonEC2 ec2, String id) {
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
		status = 3;
	}

	public void start() {
		ping.start();
	}

	public void run() {
	}

	public void sendRequest(HttpExchange request, Double metric) {
		try {
			handling.add(request);

			LoadBalancer.serverLoad.put((HandleServer)this, LoadBalancer.serverLoad.get((HandleServer)this) + metric);

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

			LoadBalancer.serverLoad.put((HandleServer)this, LoadBalancer.serverLoad.get((HandleServer)this) - metric);

		} catch (IOException e) {
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
		return status != 3;
	}

	public boolean isFree(){
		return handling.size() == 0;
	}

	public boolean readyForRequest(){
		System.out.println("Calculating ready for request");
		if (handling.size() == 0)   //if it doesnt have requests
			return true;
		if (Integer.parseInt(load.split(";")[0]) <= THRESHOLD_VALUE)  //if it has one request but is a small one
			return true;
		return false;
	}
}
