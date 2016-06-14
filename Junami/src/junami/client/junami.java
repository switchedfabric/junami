package junami.client;

import gnu.getopt.Getopt;

import java.io.*;
import java.net.*;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import org.apache.logging.log4j.LogManager;

import junami.common.Filer.FilerAction;
import junami.common.LocalFiler;
import junami.common.JunamiPacket;
import junami.common.Tx;

public class junami {
	public static void main(String argv[]) throws UnknownHostException, IOException {
		InetAddress serverAddress;
		LocalFiler filer;
		int port = 34090, noThreads = 1;
		URI sourceURI = null, targetURI = null;
		String serverName = "localhost";
		
		final org.apache.logging.log4j.Logger l = LogManager.getLogger();
				
		l.info("Junami (named after Tsunami-UDP)");
		
		/*
		 * Parse command line arguments
		 */
		Getopt g = new Getopt("junami", argv, "p:s:n:vh?");
		//
		int c;
		while ((c = g.getopt()) != -1)
		{
		    switch(c)
		    {
		        case 'p':
			            port = Integer.parseInt(g.getOptarg());
			            break;
		        case 'n':
		            noThreads = Integer.parseInt(g.getOptarg());
		            break;
		        case 's':
			            serverName = g.getOptarg();
			            break;
		        case 'h':
		        case '?':
		        	  	break; // getopt() already printed an error
		        default:
		        	  	/*
		        	  	 * This is the case where we have the source and the target 
		        	  	 */
		        	  	System.out.print("getopt() returned " + c + "\n");
		    }
		}
		boolean first = true;
		for (int i = g.getOptind(); i < argv.length ; i++) {
			// System.out.println("Non option argv element: " + argv[i] + "\n");
			if(first) {
				try {
					sourceURI = new URI(argv[i]);
				} catch (URISyntaxException e) {
					try {
						sourceURI = makeURIFromLocalFileName(argv[i], e);
					} catch (URISyntaxException ee) {
						ee.printStackTrace();
					}
				}
				first = false;
			} else {
				try {
					targetURI = new URI(argv[i]);
				} catch (URISyntaxException e) {
					try {
						targetURI = makeURIFromLocalFileName(argv[i], e);
					} catch (URISyntaxException ee) {
						ee.printStackTrace();
					}
				}
		    }
		}

		serverAddress = InetAddress.getByName(serverName);

		/*
		 * Go through source/destination combinations
		 */
		if(! sourceURI.getScheme().equalsIgnoreCase("file")) {
			System.out.print("We can only copy from local disk at the moment, sorry " + sourceURI.getScheme());
			return;
		}
		if(sourceURI.getScheme().equalsIgnoreCase("s3") || sourceURI.getScheme().equalsIgnoreCase("http")) {
			System.out.print("We can't copy from the cloud yet, sorry");
			return;
		}
		if(! targetURI.getScheme().equalsIgnoreCase("s3")) {
			System.out.print("We can only copy to S3 at the moment, sorry");
			return;
		} else {
			try {
				filer = new LocalFiler(sourceURI.getHost() + ":" + sourceURI.getPath(), FilerAction.TX, 0);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return;
			}

			System.out.println("junami connecting to " + serverName + ":" + port + " to copy " + filer.getFileName() 
					+ " to " + targetURI.toString() + " (" + filer.getSize() + " bytes) using " + noThreads + " threads...");

			/*
			 * Start a session on Junamid
			 */
			Socket s = new Socket(serverAddress, port);
			DataInputStream in = new DataInputStream(s.getInputStream());
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			
			/*
			 * Get you port from the server
			 */
			JunamiPacket p = new JunamiPacket();
			p.setPrimitive(JunamiPacket.TX);
			p.setPayload(targetURI.toString().getBytes("UTF-8"));
			System.out.println("targetURI: " + targetURI.toString() + ", payload: " + (new String(p.getPayload(), "UTF-8")));
			p.setSize(targetURI.toString().length());
			p.setExtra(filer.getSize());
			p.setUuid(noThreads);
			
			p.sendTCP(out);

			/*
			 * Start your engines
			 */
			CountDownLatch doneSignal = new CountDownLatch(noThreads);
			Semaphore filerMutex = new Semaphore(1);
			Thread Tx[] = new Thread[noThreads];

			for(int i = 0; i < noThreads; i++) {
				int threadPort;
				
				if (p.receiveTCP(in)) {
					threadPort = (int)p.getExtra(); 
				    System.out.println("junamid for this session at " + serverName + ", your very own port is " + threadPort);
					Tx[i] = new Thread(new Tx(filer, serverAddress, threadPort, doneSignal, filerMutex));
					Tx[i].start();
				}
				else break;
			}

			/* 
			 * Wait for all Tx'ers to join()
			 */
			try {
				doneSignal.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			filer.close();
			s.close();
		}

		System.out.println("Done");
	}
	
	private static URI makeURIFromLocalFileName(String localFileName, URISyntaxException e) throws URISyntaxException {
		URI uri = null;
		
		if(localFileName.contains(":")) {
			String t = localFileName.replace('\\', '/');
			uri = new URI("file", t.substring(0, 1), t.substring(2), null);
		}
		else throw e;
		return uri;
	}
}
