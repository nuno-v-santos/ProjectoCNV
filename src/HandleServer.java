import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import com.amazonaws.services.ec2.model.Instance;
import com.sun.net.httpserver.HttpExchange;

class HandleServer implements Runnable {
	private Instance instance;


	HandleServer(Instance i) {
		this.instance = i;
		System.out.println("HandleServer for" + i + " created.");

	}

	public void start() {
	}

	public void run() {
		try {

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void ping() {

	}

	public void sendRequest(HttpExchange request) {
		//http://<ip>:8000/mzrun.html?m=Maze50.maze&x0=1&y0=1&x1=6&y1=6&v=75&s=bfs
		URL url = new URL("http://"+instance.getPublicDnsName()+":8000/mzrun.html?"+request.getRequestURI().getQuery());
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");

		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		request.sendResponseHeaders(200, response.toString().lenght);
		OutputStream os = request.getResponseBody();
		os.write(response.toString().getBytes());
		os.close();
	}

	public void updateLoad(){

	}
}
