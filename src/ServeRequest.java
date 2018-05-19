import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import com.sun.net.httpserver.HttpExchange;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.*;

class ServeRequest implements Runnable {
	private static final int CACHE_SIZE = 50;
	public static ConcurrentSkipListSet<Integer> requestsCache= new ConcurrentSkipListSet<>();
	private Thread t;
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
			// add() returns false if the element is in the set
			if(! requestsCache.add(hash)) {
				System.out.println("Thread " + hash + " is going to use the cache.");
				Path path = Paths.get(Integer.toString(hash));
				request.sendResponseHeaders(200, Files.size(path));
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
			args[7] = Integer.toString(hash);
			
			// execute maze runner
			System.out.println("Thread " + hash + " going to calculate.");
			try {
				new Main().main(args);
			} catch (InvalidMazeRunningStrategyException | InvalidCoordinatesException | CantGenerateOutputFileException
					| CantReadMazeInputFileException e) {
				e.printStackTrace();
			}

			System.out.println("Thread " + hash + " sending response.");

			// send html response to the client
			Path path = Paths.get(args[7]);
			request.sendResponseHeaders(200, Files.size(path));
			OutputStream os = request.getResponseBody();
			os.write(Files.readAllBytes(path));
			os.close();
			
			// maintain cache size
			if(requestsCache.size() > CACHE_SIZE) {
				int lastUsed = requestsCache.first();
				requestsCache.remove(lastUsed);
				Files.delete(Paths.get(Integer.toString(lastUsed)));
			}
			
			// adds the arguments to dynamo
			WebServer.writeToDynamo(Thread.currentThread().getId(), args);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void start() {
		System.out.println("Thread " + hash + " starting.");
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}
}
