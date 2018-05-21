import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.net.httpserver.HttpExchange;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.*;

class ServeRequest implements Runnable {
	private static final int CACHE_SIZE = 2;
	private static final String OUTPUT_PATH = "outputs/A";

	private static ConcurrentLinkedQueue<Integer> requestsCache= new ConcurrentLinkedQueue<>();
	private int hash;
	private HttpExchange request;


	public ServeRequest(int hash, HttpExchange request) {
		this.hash = hash;
		this.request = request;
		System.out.println("Thread " + hash + " created.");
	}

	public void run() {
		try {
			// if the hash is in cache send response immediately
			if(requestsCache.contains(hash)) {
				System.out.println("Thread " + hash + " is going to use the cache.");
				Path path = Paths.get(OUTPUT_PATH + hash);
				request.sendResponseHeaders(HttpURLConnection.HTTP_OK, Files.size(path));
				OutputStream os = request.getResponseBody();
				os.write(Files.readAllBytes(path));
				os.close();

				//this will place the hash as the most recently used
				requestsCache.remove(hash);
				requestsCache.add(hash);
				return;
			}

			// prepare the arguments for the maze runner
			// receivedArgs = {m=Maze50.maze, x0=1, y0=1, x1=6, y1=6, v=75, s=bfs
			// args = {1, 1, 6, 6, 75, bfs, MazeRunner/Maze50.maze, hash };
			String[] receivedArgs = request.getRequestURI().getQuery().split("&"), args = new String[8];
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
			args[7] = OUTPUT_PATH + hash;

			// execute maze runner
			System.out.println("Thread " + hash + " going to calculate.");
			try {
				Main.main(args);
			} catch (InvalidMazeRunningStrategyException | InvalidCoordinatesException | CantGenerateOutputFileException | CantReadMazeInputFileException e) {
				String response = "Invalid query: " + e.getMessage();
				request.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length());
				OutputStream os = request.getResponseBody();
				os.write(response.getBytes());
				os.close();
				return;
			}

			System.out.println("Thread " + hash + " sending response.");

			// send html response to the client
			Path path = Paths.get(args[7]);
			request.sendResponseHeaders(HttpURLConnection.HTTP_OK, Files.size(path));
			OutputStream os = request.getResponseBody();
			os.write(Files.readAllBytes(path));
			os.close();

			requestsCache.add(hash);
			// maintain cache size
			if(requestsCache.size() > CACHE_SIZE) {
				int lastUsed = requestsCache.peek();
				requestsCache.remove(lastUsed);
				Files.delete(Paths.get(OUTPUT_PATH + lastUsed));
			}

			System.out.println("Cache contents:");
			for(Integer i : requestsCache) System.out.println("\t" + i);

			// adds the arguments to dynamo
			WebServer.writeToDynamo(Thread.currentThread().getId(), args);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void start() {
		new Thread(this).start();
	}
}

