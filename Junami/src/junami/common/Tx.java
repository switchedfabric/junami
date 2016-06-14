package junami.common;

import java.io.*;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class Tx implements Runnable {
	Filer filer;
	InetAddress peer;
	int port;
	CountDownLatch doneSignal;
	Semaphore filerMutex;
	
	public Tx(Filer filer, InetAddress peer, int port, CountDownLatch doneSignal, Semaphore filerMutex) {
		this.filer = filer;
		this.peer = peer;
		this.port = port;
		this.doneSignal = doneSignal;
		this.filerMutex = filerMutex;
	}
	
	public void run() {
		Block block = new Block();
		JunamiPacket p = new JunamiPacket();
		boolean done = false;
		
		/*
		 * Open a socket
		 */
		DatagramSocket s;
		try {
			s = new DatagramSocket();
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			doneSignal.countDown();
			return;
		}
		
		/*
		 * Set static stuff for the packets
		 */
		int i = 0;
		while(! done) {
			int r;
			
			/*
			 * Synchronize the call to getBlock()
			 */
			try {
				System.out.println("Tx(" + Thread.currentThread().getId() + "): wait for filerMutex.acquire()");
				filerMutex.acquire();
				System.out.println("Tx(" + Thread.currentThread().getId() + "): got it");
				r = filer.getBlock(block);
				filerMutex.release();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				s.close();
				doneSignal.countDown();
				return;
			}

			System.out.println("Tx(" + Thread.currentThread().getId() + "): filer.getBlock() returned " + r);

			if(r == -1) {
				/*
				 * Error condition
				 */
				System.out.println("Tx(" + Thread.currentThread().getId() + "): some error after getBlock()");
				done = true;
				continue;
			}
			if(r == 0) {
				/*
				 * All transmitted & acknowledged
				 */
				System.out.println("Tx(" + Thread.currentThread().getId() + "): all done");
				p.setPrimitive(JunamiPacket.DONE);
				p.setSize(0);

				/* 
				 * Transmit it			
				 */
				if(! p.sendUDP(s, peer, port)) {
					doneSignal.countDown();
					return;
				}
				
				done = true;
				continue;
			}
			if(r == 1) {
				/*
				 * Block available to transmit
				 */
				System.out.println("Tx(" + Thread.currentThread().getId() + "): transmit block " + block.getBlockNo() + " of size " + block.getSize());
				/*
				 * Construct datagram
				 */
				p.setPrimitive(JunamiPacket.TX);
				p.setExtra(block.getBlockNo());
				p.setPayload(block.getPayload());
				p.setSize(block.getSize());

				/* 
				 * Transmit it			
				 */
				if(! p.sendUDP(s, peer, port)) {
					doneSignal.countDown();
					return;
				}
			}
			
			/*
			 * The remaining case (r == -2, no blocks to transmit but some acknowledgments outstanding)
			 * is handled by default
			 * Maybe ask peer for an update, instead of waiting for one
			 */
			
			/*
			 * At some interval, check for return traffic
			 */
			if(r == 2 || (++i % 100 == 0)) {
				i = 0;
				System.out.println("Tx(" + Thread.currentThread().getId() + "): Waiting for some feedback...");

				p.setPrimitive(JunamiPacket.ACK);
				p.setSize(0);
				if(! p.sendUDP(s, peer, port)) {
					doneSignal.countDown();
					return;
				}

				if(p.receiveUDP(s)) { // , server, port)) {
					/*
					 * Check if peer responded with acks, and mark them if so
					 */
					if(p.getPrimitive() == JunamiPacket.ACK) {
						try {
							String acks = "";
							do {
								acks = acks.concat(new String(p.getPayload(), 0, p.getSize(), "UTF-8"));
							} while(p.getExtra() == 1);
							
							try {
								System.out.println("Tx(" + Thread.currentThread().getId() + "): wait for filerMutex.acquire() for setBlockArrived()");
								filerMutex.acquire();
								System.out.println("Tx(" + Thread.currentThread().getId() + "): got it");
								filer.setBlockArrived(acks);
								filerMutex.release();
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								s.close();
								doneSignal.countDown();
								return;
							}
						} catch (Exception e) {
							e.printStackTrace();
							doneSignal.countDown();
							return;
						}
					} else {
						/*
						 * Out of order packet received
						 */
					}
				}
			}
			
			
			/* 
			 * Pace yourself (Tsunami-UDP uses an inter-packet delay)
			 */
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		s.close();
		System.out.println("Tx(" + Thread.currentThread().getId() + "): done");
		doneSignal.countDown();
		return;
	}

}
