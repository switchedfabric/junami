package junami.common;

import java.util.Arrays;

public class Block {
	public static final int BLOCKSIZE = 63 * 1024;
	public static final int HOME = 0;
	public static final int DEPARTED = 1;
	public static final int ARRIVED = 2;
	
	/*
	 * Constructors
	 */
	public Block() {
		this.blockNo = 0;
		this.size = BLOCKSIZE;
		Arrays.fill(payload, (byte)0);
	}
	
	public Block(Block copy) {
		this.blockNo = copy.blockNo;
		this.size = copy.size;
		setPayload(copy.payload);
	}
	
	public Block(int blockNo, int size, byte[] payload) {
		this.blockNo = blockNo;
		this.size = size;
		setPayload(payload);
	}
	
	/*
	 * Members
	 */
	private int blockNo;
	public int getBlockNo() {
		return blockNo;
	}
	public void setBlockNo(int blockNo) {
		this.blockNo = blockNo;
	}
	
	private int size;
	public int getSize() {
		return this.size;
	}
	public void setSize(int size) {
		this.size = size;
	}
	
	private byte[] payload = new byte[BLOCKSIZE];
	public byte[] getPayload() {
		return payload;
	}
	public void setPayload(byte[] payload) {
		this.payload = payload;
		System.arraycopy(payload, 0, this.payload, 0, this.size);
	}
}
