import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.*;

class ServeRequest implements Runnable {
	private Thread t;
	private String name;
	private HttpExchange request;
	private Map<Long, String[]> threadArgs;

	ServeRequest(String name, HttpExchange request, Map<Long, String[]> threadArgs2) {
		this.name = name;
		this.request = request;
		this.threadArgs = threadArgs2;
		System.out.println("Thread " + name + " created.");

	}

	public void run() {
		try {

			// run requested mazerunner
			Main m = new Main();
			long threadId = Thread.currentThread().getId();
			
			//String[] args = { "3", "9", "78", "89", "50", "astar", "MazeRunner/Maze100.maze", "Maze100.html" };
			//http://52.73.2.166:8000/test?3&9&4&9&50&astar&Maze100.maze&Maze100.html
			String[] receivedArgs = request.getRequestURI().getQuery().split("&");
			String[] args = new String[8];
			for (String arg : receivedArgs){
				String[] attributes = arg.split("=");
				if (attributes[0].equals("x0")){
					args[0] = attributes[1];
				} else if (attributes[0].equals("y0")){
					args[1] = attributes[1];
				} else if (attributes[0].equals("x1")){
					args[2] = attributes[1];
				} else if (attributes[0].equals("y1")){
					args[3] = attributes[1];
				} else if (attributes[0].equals("v")){
					args[4] = attributes[1];
				} else if (attributes[0].equals("s")){
					args[5] = attributes[1];
				} else if (attributes[0].equals("m")){
					args[6] = "MazeRunner/" + attributes[1];
				}
			}

			args[7] = name;
	
			threadArgs.put(threadId, args);
			System.out.println("Thread " + name + " going to calculate.");
			try {
				m.main(args);
			} catch (InvalidMazeRunningStrategyException | InvalidCoordinatesException | CantGenerateOutputFileException
					| CantReadMazeInputFileException e) {
				e.printStackTrace();
			}

			System.out.println("Thread " + name + " sending response.");

			// send html response
			String outputFile = args[7];
			Path path = Paths.get(outputFile);
			request.sendResponseHeaders(200, Files.size(path));
			OutputStream os = request.getResponseBody();
			os.write(Files.readAllBytes(path));
			os.close();
			Files.delete(path);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void start() {
		System.out.println("Thread " + name + " starting.");
		if (t == null) {
			t = new Thread(this, name);
			t.start();
		}
	}
}
