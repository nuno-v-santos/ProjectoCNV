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
	private String instanceIp;
	private String instanceId;
	private AmazonEC2 ec2;
	private ArrayList<String> handling;
	private ArrayList<String> handled;
	private int retries = 0;
	private boolean isAlive = true;
	
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
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			// Instance died
			LoadBalancer.instanceList.remove(instanceId);
		}
	};

	HandleServer(AmazonEC2 ec2, String id) {
		this.instanceId = id;
		this.instanceIp = "";
		this.ec2 = ec2;
		this.handling = new ArrayList<String>();
		this.handled = new ArrayList<String>();
		System.out.println("HandleServer for " + id + " created.");

	}

	public void start() {
		ping.start();
	}

	public void run() {
	}

	public void sendRequest(HttpExchange request) {
		System.out.println("a");
		try {

			// waits for instance to be ready
			while (! isAlive) {
				try {
					System.out.println("Instance not ready. Retrying to send request..");
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			// send request to the instance
			URL url = new URL("http://" + this.instanceIp + ":8000/mzrun.html?" + request.getRequestURI().getQuery());
			HttpURLConnection con = (HttpURLConnection) url.openConnection();

			handling.add(request.getRequestURI().getQuery());

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

			handling.remove(request.getRequestURI().getQuery());
			handled.add(request.getRequestURI().getQuery());
		} catch (ConnectException e) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
			sendRequest(request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void updateLoad() {

	}

	public boolean isAlive() {
		return isAlive;
	}
}
