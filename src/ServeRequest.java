import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

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
    	  
    	 //run requested mazerunner
    	 Main m = new Main();
    	 String[] args = {"3", "9", "78", "89", "50", "astar", "MazeRunner/Maze100.maze", "Maze100.html"};
    	 try {
			m.main(args);
		} catch (InvalidMazeRunningStrategyException | InvalidCoordinatesException | CantGenerateOutputFileException
				| CantReadMazeInputFileException e) {
			e.printStackTrace();
		}
    	 
    	 //send html response
         System.out.println(name + " going to sleep");
         Thread.sleep(5000);
         System.out.println(name + " awake");
         String response = "This was the query:" + request.getRequestURI().getQuery() 
                               + "##";
         request.sendResponseHeaders(200, response.length());
         OutputStream os = request.getResponseBody();
         os.write(response.getBytes());
         os.close();
      } catch (IOException | InterruptedException e) {
         e.printStackTrace();
         System.out.println("Thread " + name + " interrupted.");
      }
      System.out.println("Thread " + name + " exiting.");
   }
   
   public void start () {
      System.out.println("Thread " + name + " starting.");
      if (t == null) {
         t = new Thread(this, name);
         t.start();
      }
   }
}