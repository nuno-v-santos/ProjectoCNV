import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class WebServer {

    public static int request_counter = 0;
    public static Map<Long, String> threadArgs = new HashMap<>();
    public static ByteArrayOutputStream newOut = new ByteArrayOutputStream();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/test", new MyHandler());
        server.setExecutor(null); // creates a default executor
        System.out.println("Running");
        
        // Redirecting out
        
		PrintStream new_ps = new PrintStream(newOut);
		PrintStream old = System.out;
		System.setOut(new_ps);
		
        server.start();

    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            ServeRequest r = new ServeRequest("" + request_counter, t, threadArgs, newOut);
            r.start();
            request_counter++;
        }
    }

}


