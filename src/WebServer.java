import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class WebServer {

    public static int request_counter = 0;
    public static Map<Long, String> threadArgs = new HashMap<>();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/test", new MyHandler());
        server.setExecutor(null); // creates a default executor
        System.out.println("Running");
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            ServeRequest r = new ServeRequest("" + request_counter, t, threadArgs);
            r.start();
            request_counter++;
        }
    }

	public void addMetric(Long id, Double metric) {
		//Write metrics
		List<String> argsAndMetric = Arrays.asList(threadArgs.get(id), metric.toString());

		Path file = Paths.get("metrics.txt");
		try {
			Files.write(file, argsAndMetric, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
		} catch (IOException e) {
			try {
				Files.createFile(file);
				Files.write(file, argsAndMetric, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		
	}

}


