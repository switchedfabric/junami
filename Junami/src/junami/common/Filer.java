package junami.common;

import java.util.ArrayList;
import java.util.Date;

public class Filer {
	public enum FilerType { LOCAL, S3, AZURE };
	public enum FilerAction {TX, RX};
	
	protected FilerType type;
	protected FilerAction action;
	protected long size;
	protected int tail, head, numBlocks;
	protected long timeout;
	protected long[] transmitStatus;
	// protected ArrayList<Block> blocks;
	protected Block[] blocks;
	
	protected String name;
	
	public Filer(String name, FilerType type, FilerAction action, long size) {
		this.name = name;
		this.type = type;
		this.action = action;
		this.size = size;
		this.timeout = 5 * 1000;  // 5 seconds timeout, adjust as needed
	}
	
	public FilerType getType() {
		return type;
	}

	public FilerAction getAction() {
		return action;
	}

	public long getSize() {
		return size;
	}

	public String getName() {
		return name;
	}

	public int getBlock(Block block) {
		return 0;
	}

	public boolean putBlock(Block block) {
		return true;
	}
	
	/*
	 * Returns
	 * -1: Error
	 *  0: All done
	 *  1: Block to transmit
	 *  2: All transmitted but acknowledgments outstanding
	 */
	protected int findForTx(Block block) {
		long now = new Date().getTime();
		
		System.out.println("findForTx(" + Thread.currentThread().getId() + "): starting, tail = " + tail + ", head = " + head);
		
		for(int j = 0; j < head; j++) {
			// System.out.println("findForTx(" + Thread.currentThread().getId() + "): transmitStatus[" + j + "] = " + transmitStatus[j]);
		}

		/*
		 * Advance tail if you can
		 */
		while(tail < head && transmitStatus[tail] == Block.ARRIVED) {
			System.out.println("findForTx(" + Thread.currentThread().getId() + "): Setting block " + tail + " to null because it's received"); 
			// blocks.set(tail, null);
			blocks[tail] = null;
			tail++;
		}
		
		/*
		 * Find the first, unacknowledged block before the head
		 */
		int i = tail;
		while(i < head) {
			if(transmitStatus[i] > 2 && (now - transmitStatus[i]) > timeout) {
				System.out.println("findForTx(" + Thread.currentThread().getName() + "): block " + i + " can do with a retx"); 
				block.setBlockNo(i);
				return 2;
			}
			i++;
		}

		/*
		 * Advance head for the next block
		 */
		if(head < numBlocks) {
			System.out.println("findForTx(" + Thread.currentThread().getName() + "): block " + head + " is new"); 
			block.setBlockNo(head++);
			return 1;
		}
		
		/*
		 * If tail < head, that is, there is are unacknowledged blocks, we'll return 0
		 */
		if(tail < head) {
			System.out.println("findForTx(" + Thread.currentThread().getName() + "): tail < head? tail = " + tail + ", head = " + head);
			return 2;
		} else {
			return 0;
		}
	}

	protected void setBlockArrived(Block block) {
		setBlockArrived(block.getBlockNo());
	}

	protected void setBlockArrived(int b) {
		System.out.println("setBlockArrived(" + Thread.currentThread().getId() + "): block " + b + " has arrived");
		transmitStatus[b] = Block.ARRIVED;
	}

	protected void setBlockArrived(String list) {
		System.out.println("setBlockArrived(" + Thread.currentThread().getId() + "): got [" + list + "]");
		for(String ackGroup: list.split(":")) {
			System.out.println("setBlockArrived(" + Thread.currentThread().getId() + "): split -> [" + ackGroup + "]");
			if(ackGroup.contains("-")) {
				System.out.println("setBlockArrived(" + Thread.currentThread().getId() + "): split -> [" + ackGroup + "] is a range");
				String[] range = list.split("-");
				int start = Integer.parseInt(range[0]);
				int end = Integer.parseInt(range[1]);
				System.out.println("setBlockArrived(" + Thread.currentThread().getId() + "): split -> [" + ackGroup + "] is a range from " + start + " to " + end);
				for(int i = start; i <= end; i++) {
					setBlockArrived(i);
				}
			} else {
				setBlockArrived(Integer.parseInt(ackGroup));
			}
		}

	}

	protected String getArrivedBlocks(Integer lastAck) {
		String acks = "";
		int prev = -2;
		int i = lastAck + 1;
		int run = 0;
		boolean first = true;

		System.out.println("getArrivedBlocks(" + Thread.currentThread().getId() + "): i = " + i + ", numBlocks = " + numBlocks);
		while(i < numBlocks) {
			// System.out.println("getArrivedBlocks(" + Thread.currentThread().getId() + "): transmitStatus[" + i + "] = " + transmitStatus[i] 
			//		+ ", prev = " + prev + ", i = " + i);
			if(transmitStatus[i] == Block.ARRIVED) {
				if(prev == i - 1) {
					run++;
				} else {
					if(first) {
						acks = String.valueOf(i);
						// System.out.println("getArrivedBlocks(" + Thread.currentThread().getId() + "): acks A = [" + acks + "]");
						first = false;
					}
					else {
						acks = acks.concat(":" + String.valueOf(i));
						// System.out.println("getArrivedBlocks(" + Thread.currentThread().getId() + "): acks B = [" + acks + "]");
					}
				}
				prev = i;
			} else {
				if(run == 1) {
					acks = acks.concat(":" + prev);
					// System.out.println("getArrivedBlocks(" + Thread.currentThread().getId() + "): acks C = [" + acks + "]");
				} else if(run > 1) {
					acks = acks.concat("-" + prev);
					// System.out.println("getArrivedBlocks(" + Thread.currentThread().getId() + "): acks D = [" + acks + "]");
				}
				run = 0;
			}
			i++;
		}

		if(run == 1) {
			acks = acks.concat(":" + prev);
			// System.out.println("getArrivedBlocks(" + Thread.currentThread().getId() + "): acks E = [" + acks + "]");
		} else if(run > 1) {
			acks = acks.concat("-" + prev);
			// System.out.println("getArrivedBlocks(" + Thread.currentThread().getId() + "): acks F = [" + acks + "]");
		}
		
		lastAck = i;
		System.out.println("getArrivedBlocks(" + Thread.currentThread().getId() + ") returns [" + acks + "]");
		return acks;
	}
}
