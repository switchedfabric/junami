package junami.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class JunamiPacket {
	/*
	 * Primitives are:
	 * 1: Hello from client
	 * 2: Hello from server
	 * 3: Bye from client
	 * 4: Bye from server
	 * 5: Accelerate from server
	 * 6: Decelerate from server
	 * 7: Received report from server
	 */
	public static final int HELO = 1;
	public static final int TX = 2;
	public static final int RX = 3;
	public static final int ACK = 4;
	public static final int DONE = 5;
	
	private int primitive;
	public int getPrimitive() {
		return this.primitive;
	}
	public void setPrimitive(int primitive) {
		this.primitive = primitive;
	}

	/*
	 * UUID: some form of id for the client, server will decide
	 */
	private long uuid;
	public long getUuid() {
		return this.uuid;
	}
	public void setUuid(long uuid) {
		this.uuid = uuid;
	}

	/*
	 * Any values you might want to send
	 */
	private long extra;
	public long getExtra() {
		return this.extra;
	}
	public void setExtra(long extra) {
		this.extra = extra;
	}

	/*
	 * Size of the payload
	 */
	private int size;
	public int getSize() {
		return this.size;
	}
	public void setSize(int size) {
		this.size = size;
	}

	public byte[] payload = null;
	public byte[] getPayload() {
		return this.payload;
	}
	public void setPayload(byte[] payload) {
		this.payload = payload;
	}
	
	private InetAddress peerAddr;
	public InetAddress getPeerAddr() {
		return peerAddr;
	}
	
	private int peerPort;
	public int getPeerPort() {
		return peerPort;
	}
	
	public JunamiPacket() {
		this.payload = new byte[Block.BLOCKSIZE];
	}
	
	public boolean sendTCP(DataOutputStream out) {
		try {
			// System.out.println("\tsendTCP(): primitive: " + primitive); 
			out.writeInt(primitive);
			// System.out.println("\tsendTCP(): uuid: " + uuid); 
			out.writeLong(uuid);
			// System.out.println("\tsendTCP(): extra: " + extra); 
			out.writeLong(extra);
			// System.out.println("\tsendTCP(): sz: " + sz); 
			out.writeInt(size);
			if(size > 0) {
				// System.out.println("\tsendTCP(): payload: " + (new String(payload, 0, sz, "UTF-8"))); 
				out.write(payload, 0, size);
			}
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean receiveTCP(DataInputStream in) {
		try {
			this.primitive = in.readInt();
			// System.out.println("\treceiveTCP(): primitive: " + primitive); 
			this.uuid = in.readLong();
			// System.out.println("\treceiveTCP(): uuid: " + uuid); 
			this.extra = in.readLong();
			// System.out.println("\treceiveTCP(): extra: " + extra); 
			this.size = in.readInt();
			// System.out.println("\treceiveTCP(): sz: " + sz); 
			if(size > 0) {
				int r = 0;
				while(r < size) {
					r += in.read(this.payload, r, size);
					// System.out.println("\treceiveTCP(): payload: " + (new String(payload, 0, sz, "UTF-8")) + ", r: " + r); 
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean sendUDP(DatagramSocket s, InetAddress addr, int port) {
		ByteBuffer pd = null;
		int psz = Integer.SIZE + 2 * Long.SIZE + Integer.SIZE + size;
		pd = ByteBuffer.allocate(psz);
		
		System.out.println("sendUDP(" + Thread.currentThread().getId() + ") send to " + addr.getHostAddress() + ":" + port);
		
		System.out.println("\tsendUDP(" + Thread.currentThread().getId() + "): primitive: " + primitive); 
		pd.putInt(primitive);
		System.out.println("\tsendUDP(" + Thread.currentThread().getId() + "): uuid: " + uuid); 
		pd.putLong(uuid);
		System.out.println("\tsendUDP(" + Thread.currentThread().getId() + "): extra: " + extra); 
		pd.putLong(extra);
		System.out.println("\tsendUDP(" + Thread.currentThread().getId() + "): sz: " + size); 
		pd.putInt(size);
		if(size > 0) {
			System.out.println("\tsendUDP(): payload size  --------------: " + payload.length); 
			pd.put(payload, 0, size);
			System.out.println("\tsendUDP(): payload size  --------------: " + payload.length); 
		}
		
		DatagramPacket p = new DatagramPacket(pd.array(), psz, addr, port);
		try {
			s.send(p);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
		return true;
	}

	public boolean receiveUDP(DatagramSocket s) { // , InetAddress addr, int port) {
		ByteBuffer pd = null;
		int psz = Integer.SIZE + 2 * Long.SIZE + Integer.SIZE + Block.BLOCKSIZE;
		pd = ByteBuffer.allocate(psz);
	
		DatagramPacket p = new DatagramPacket(pd.array(), psz); // , addr, port);
		try {
			s.receive(p);
			peerAddr = p.getAddress();
			peerPort = p.getPort();
			System.out.println("receiveUDP(" + Thread.currentThread().getId() + ") recv from " + peerAddr + ":" + peerPort + ", size " + p.getLength());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
		primitive = pd.getInt();
		System.out.println("\treceiveUDP(" + Thread.currentThread().getId() + "): primitive: " + primitive); 
		uuid = pd.getLong();
		System.out.println("\treceiveUDP(" + Thread.currentThread().getId() + "): uuid: " + uuid); 
		extra = pd.getLong();
		System.out.println("\treceiveUDP(" + Thread.currentThread().getId() + "): extra: " + extra); 
		size = pd.getInt();
		System.out.println("\treceiveUDP(" + Thread.currentThread().getId() + "): sz: " + size + ", buffer has " + pd.remaining() 
				+ ", payload size: " + payload.length); 
		if(size > 0) {
			// System.out.println("\treceiveUDP(): payload: " + (new String(payload, 0, size, "UTF-8"))); 
			try {
				pd.get(payload, 0, size);
			} catch (BufferUnderflowException bue) {
				bue.printStackTrace();
				return false;
			}
		}
		
		return true;
	}
}
