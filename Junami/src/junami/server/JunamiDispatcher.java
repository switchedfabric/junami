package junami.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import junami.common.Filer.FilerAction;
import junami.common.Rx;
import junami.common.S3Filer;
import junami.common.JunamiPacket;

public class JunamiDispatcher extends Thread{
	Socket s;
	InetAddress peerAddr;
	int port, peerPort;
	DatagramSocket ss = null;
	
	JunamiDispatcher (Socket s) {
		this.s = s;
		peerAddr = s.getInetAddress();
		peerPort = s.getPort();
	}
	
	public void run() {
		try {
			JunamiPacket p = new JunamiPacket();
			URI targetURI = null;
			DataInputStream in = null;
			DataOutputStream out = null;
			
			System.out.println("Here we go, picked up a connection from " + peerAddr);
			
			in = new DataInputStream(s.getInputStream());
			out = new DataOutputStream(s.getOutputStream());
	        
	        p.receiveTCP(in);
			try {
				targetURI = new URI((new String(p.getPayload(), 0, p.getSize(), "UTF-8")));
			} catch (URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				s.close();
				return;
			}
			System.out.println("Client said " + p.getPrimitive() + ", targetURI is " + targetURI + ", size is " + p.getExtra()
					+ ", using " + p.getUuid() + " threads");


				
	        if(targetURI.getScheme().equalsIgnoreCase("s3") && p.getPrimitive() == JunamiPacket.TX) {
	        	S3Filer filer = null;
	        	
	        	/*
	        	 * We have our man
	        	 */
				System.out.println("AWS it is");
				
				try {
					filer = new S3Filer(targetURI, FilerAction.RX, (int)p.getExtra());
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}


				CountDownLatch doneSignal = new CountDownLatch((int)p.getUuid());
				Semaphore filerMutex = new Semaphore(1);
				Thread ToAWS[] = new Thread[(int)p.getUuid()];

				for(int i = 0; i < (int)p.getUuid(); i++) {
					try {
						ss = newPort();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						s.close();
						filer.done();
						return;
					}
					
					/*
					 * Start p.uuid threads, and then wait for them
					 */
					ToAWS[i] = new Thread(new Rx(filer, peerAddr, peerPort, p, out, ss, port, doneSignal, filerMutex));
					ToAWS[i].start();
				}
				
				/* 
				 * Wait for all ToAWS'ers to join()
				 */
				try {
					System.out.println("Waiting for Rx()'s...");
					doneSignal.await();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					filer.done();
				}
				
				// filer.put();
				filer.done();
				System.out.println("And we're done");
	        } else if (targetURI.getScheme().equalsIgnoreCase("http")) {
	        	/*
	        	 * Azure, not for now
	        	 */
	        }

	        s.close();
	        
		} catch (IOException e) {
			e.printStackTrace();
        } finally {
        }
	}

	public DatagramSocket newPort() throws Exception {
		boolean done = false;
		DatagramSocket ss = null;

		port = 34090 + 1;
		while(! done) {
			try {
				System.out.println("newPort(): trying " + port);
				ss = new DatagramSocket(port);
				done = true;
			} catch (IOException e) {
				port++;
				if(port > 65000) {
					throw new Exception();
				}
			}
		}
		System.out.println("newPort(): found port " + port);
		return ss;
	}
}
