import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class ServeRequest implements Runnable {
	private Thread t;
	private String name;
	private HttpExchange request;

	ServeRequest(String name, HttpExchange request) {
		this.name = name;
		this.request = request;
		System.out.println("Thread " + name + " created.");
	}

	public void run() {
      try {
         System.out.println(name + " going to sleep");
         String mazeRunner = "pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main";
         String[] args = {"3", "9", "78", "89", "50", "astar", "Maze100.maze", "Maze100.html"};
         String outputFile = args[7];
         Path path = Paths.get("./MazeRunner/" + outputFile);
         
         request.sendResponseHeaders(200, Files.size(path));
         OutputStream os = request.getResponseBody();
         
         os.write(Files.readAllBytes(path));
         os.close();
      } catch (IOException e) {
         e.printStackTrace();
         System.out.println("Thread " + name + " interrupted.");
      }
      System.out.println("Thread " + name + " exiting.");
   }

	public void start() {
		System.out.println("Thread " + name + " starting.");
		if (t == null) {
			t = new Thread(this, name);
         t.start();
      }
   }
}