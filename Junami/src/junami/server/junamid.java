package junami.server;

import java.io.IOException;
import java.net.*;

import gnu.getopt.Getopt;


public class junamid {
	public static void main(String argv[]) {
		int masterPort = 34090;
	
		System.out.println("Junamid running...");

		/*
		 * Parse command line arguments
		 */
		Getopt g = new Getopt("junami", argv, "p:h?");
		 //
		 int c;
		 while ((c = g.getopt()) != -1)
		   {
		     switch(c)
		       {
		          case 'p':
			            masterPort = Integer.parseInt(g.getOptarg());
			            break;
		          case 'h':
		          case '?':
		        	  	break; // getopt() already printed an error
		          default:
		        	  	System.out.print("getopt() returned " + c + "\n");
		       }
		   }

		/*
		 * Typical TCP server:
		 */
		ServerSocket listener;
		try {
			listener = new ServerSocket(masterPort);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		try {
			while(true) {
				System.out.println("Waiting for new connections...");
				Thread t = new JunamiDispatcher(listener.accept());
				t.start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				listener.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("Done");
		}
	}
}
