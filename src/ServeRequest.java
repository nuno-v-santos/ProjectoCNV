import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantGenerateOutputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantReadMazeInputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidCoordinatesException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidMazeRunningStrategyException;

class ServeRequest implements Runnable {
	private Thread t;
	private String name;
	private HttpExchange request;
	private Map<Long, String> threadArgs;

	ServeRequest(String name, HttpExchange request, Map<Long, String> threadArgs) {
		this.name = name;
		this.request = request;
		this.threadArgs = threadArgs;
		System.out.println("Thread " + name + " created.");
	}

	public void run() {
		try {

			// run requested mazerunner
			Main m = new Main();
			long threadId = Thread.currentThread().getId();
			
			//String[] args = { "3", "9", "78", "89", "50", "astar", "MazeRunner/Maze100.maze", "Maze100.html" };
			//http://52.73.2.166:8000/test?3&9&4&9&50&astar&Maze100.maze&Maze100.html
			String[] args = request.getRequestURI().getQuery().split("&");
			args[6] = "MazeRunner/" + args[6];
			threadArgs.put(threadId, Arrays.toString(args));
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
		System.out.println("Thread " + name + " ending.");
	}
}
