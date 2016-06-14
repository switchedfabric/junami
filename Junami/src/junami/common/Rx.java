package junami.common;

import java.io.DataOutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class Rx implements Runnable {
	JunamiPacket hello;
	InetAddress peerAddr;
	DataOutputStream parentOut;
	DatagramSocket ss;
	int port, peerPort;
	CountDownLatch doneSignal;
	Semaphore filerMutex;
	Filer filer;
	Block block;
	Integer lastAck = -1;
	 
	public Rx(Filer filer, InetAddress peerAddr, int peerPort, JunamiPacket hello, DataOutputStream parentOut, DatagramSocket ss, int port, 
			CountDownLatch doneSignal, Semaphore filerMutex) {
		this.filer = filer;
		// this.hello = hello;
		this.peerAddr = peerAddr;
		this.parentOut = parentOut;
		this.ss = ss ;
		this.port = port;
		this.peerPort = peerPort;
		this.doneSignal = doneSignal;
		this.filerMutex = filerMutex;
		this.block = new Block();
	}
	
	public void run() {
		JunamiPacket p = new JunamiPacket();
		
		System.out.println("Rx(" + Thread.currentThread().getId() + "): Peer said PUT: targetURI is " + filer.getName() + ", file size is " + filer.getSize());

		/*
		 * Tell'm what port
		 */
		System.out.println("Rx(" + Thread.currentThread().getId() + "): HELO, your port will be " + port);
		p.setPrimitive(JunamiPacket.HELO);
		p.setExtra(port);
        p.setSize(0);
        p.sendTCP(parentOut);
   	
    	boolean inflight = true;
		while(inflight) {
			System.out.println("Rx(" + Thread.currentThread().getId() + "): waiting for a packet...");
			System.out.println("Rx(" + Thread.currentThread().getId() + "): ----> 1 payload size " + p.payload.length);
			p.receiveUDP(ss);
			System.out.println("Rx(" + Thread.currentThread().getId() + "): ----> 2 payload size " + p.payload.length);
			switch(p.getPrimitive()) {
				case JunamiPacket.TX:
					/*
					 * New block
					 */
					System.out.println("Rx(" + Thread.currentThread().getId() + "): received block " + p.getExtra() + " of size " + p.getSize());
					try {
						filerMutex.acquire();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						inflight = false;
					}
					filer.putBlock(new Block((int)p.getExtra(), p.getSize(), p.getPayload()));
					filerMutex.release();
					System.out.println("Rx(" + Thread.currentThread().getId() + "): ----> 3 payload size " + p.payload.length);
					break;
				case JunamiPacket.ACK: 
					/*
					 * Peer wants an update
					 */
			    	System.out.println("Rx(" + Thread.currentThread().getId() + "): Peer asks for ACK");
					System.out.println("Rx(" + Thread.currentThread().getId() + "): ----> 4a payload size " + p.payload.length);
			    	
			    	try {
						filerMutex.acquire();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						inflight = false;
					}
			    	String acks = filer.getArrivedBlocks(lastAck);
					filerMutex.release();

					System.out.println("Rx(" + Thread.currentThread().getId() + "): ----> 4b payload size " + p.payload.length);
					int acksLen = acks.length(), i = 0;
		    		byte[] acksPayload = acks.getBytes();
			    	do {
			    		JunamiPacket ap = new JunamiPacket();
			    		
						ap.setPrimitive(JunamiPacket.ACK);
				        ap.setSize(java.lang.Math.min(acksLen, Block.BLOCKSIZE));
				        if(acksLen > Block.BLOCKSIZE) {
							System.out.println("Rx(" + Thread.currentThread().getId() + "): ----> 4c payload size " + ap.payload.length);
				        	ap.setPayload(java.util.Arrays.copyOfRange(acksPayload, i, Block.BLOCKSIZE));
							System.out.println("Rx(" + Thread.currentThread().getId() + "): ----> 4d payload size " + ap.payload.length);
				        	i += Block.BLOCKSIZE;
				        	ap.setExtra(1);
				        } else {
							System.out.println("Rx(" + Thread.currentThread().getId() + "): ----> 4e payload size " + ap.payload.length);
				        	ap.setPayload(java.util.Arrays.copyOfRange(acksPayload, i, acksLen));
							System.out.println("Rx(" + Thread.currentThread().getId() + "): ----> 4f payload size " + ap.payload.length);
				        	ap.setExtra(0);
				        }
				        acksLen -= Block.BLOCKSIZE;
				        ap.sendUDP(ss, p.getPeerAddr(), p.getPeerPort());
			    	} while(acksLen > 0);
			    	
					break;
				case JunamiPacket.DONE: 
					/*
					 * Peer is done, tear down
					 */
			    	System.out.println("Rx(" + Thread.currentThread().getId() + "): Peer is done");
					inflight = false;
					break;
			}
			
		}
		
		ss.close();
		doneSignal.countDown();
		return;
	}
}
