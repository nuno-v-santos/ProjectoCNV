import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantGenerateOutputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantReadMazeInputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidCoordinatesException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidMazeRunningStrategyException;

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

			// run requested mazerunner
			Main m = new Main();
			//String[] args = { "3", "9", "78", "89", "50", "astar", "MazeRunner/Maze100.maze", "Maze100.html" };
			//http://52.73.2.166:8000/test?3&9&4&9&50&astar&Maze100.maze&Maze100.html
			String[] args = request.getRequestURI().getQuery().split("&");
			args[6] = "MazeRunner/" + args[6];
			try {
				// Create a stream to hold the output
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PrintStream ps = new PrintStream(baos);
				// IMPORTANT: Save the old System.out!
				PrintStream old = System.out;
				// Tell Java to use your special stream
				System.setOut(ps);
				m.main(args);
				// Put things back
				System.out.flush();
				System.setOut(old);

				List<String> lines = Arrays.asList(Arrays.toString(args), baos.toString());
				Path file = Paths.get("metrica.txt");
				Files.write(file, lines, Charset.forName("UTF-8"));
				Files.write(file, lines, Charset.forName("UTF-8"), StandardOpenOption.APPEND);
			} catch (InvalidMazeRunningStrategyException | InvalidCoordinatesException | CantGenerateOutputFileException
					| CantReadMazeInputFileException e) {
				e.printStackTrace();
			}

			// send html response
			String outputFile = args[7];
			Path path = Paths.get(outputFile);
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