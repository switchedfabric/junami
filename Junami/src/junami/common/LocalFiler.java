package junami.common;

import java.io.*;
import java.util.*;

public class LocalFiler extends Filer  {
	private String fileName;
	private FileInputStream fis;
	private FileOutputStream fos;
	private File fp;
	
	public LocalFiler(String fileName, FilerAction action, long size) throws FileNotFoundException {
		super(fileName, FilerType.LOCAL, action, size);
		
		this.fileName = fileName;
		
		try {
			if(action == FilerAction.TX) {
				/*
				 * Check the file exists and you can read it
				 */
				fis = new FileInputStream(fileName);
				fp = new File(fileName);
				this.size = fp.length();
				System.out.println("LocalFiler(" + Thread.currentThread().getId() + "): opened file " + fileName + " for reading");
			} else if(action == FilerAction.RX) {
				/*
				 * Check if the file is writable
				 */
				fos = new FileOutputStream(fileName);
				fp = new File(fileName);
				System.out.println("LocalFiler(" + Thread.currentThread().getId() + "): opened file " + fileName + " for writing");
			} else {
				throw new FileNotFoundException();
			}
		} catch(FileNotFoundException e) {
			e.printStackTrace();
			throw e;
		}
		
		/*
		 * Prepare status area to record transmission of blocks, can't do in superclass because we don't always know what the size is 
		 */
		numBlocks = (int)((this.size / (Block.BLOCKSIZE)) + 1);
		System.out.println("LocalFiler(" + Thread.currentThread().getId() + "): numBlocks = " + numBlocks);
		// blocks = new ArrayList<Block>(numBlocks);
		// blocks.ensureCapacity(numBlocks);
		blocks = new Block[numBlocks];
		transmitStatus = new long[numBlocks];
		Arrays.fill(transmitStatus, Block.HOME);
		tail = head = 0;
		System.out.println("LocalFiler(" + Thread.currentThread().getId() + "): Actual sizes: blocks: " + blocks.length + ", transmitStatus: " + transmitStatus.length);
	}
	
	/*
	 * Returns
	 * -1: Error
	 *  0: All done
	 *  1: Block to transmit
	 *  2: All transmitted but acknowledgments outstanding
	 */
	public int getBlock(Block block) {
		long now = new Date().getTime(); 
		int r = findForTx(block);

		System.out.println("getBlock(" + Thread.currentThread().getId() + "): findForTx returned " + r); 

		if(r == -1) { 
			return -1; 
		}
		else if(r == 0) {
			/*
			 * No blocks left
			 */
			return 0;
		}
		else if (r == 1) {
			/*
			 * Read block from the file
			 */
			try {
				byte[] _payload = new byte[Block.BLOCKSIZE];
				
				System.out.println("getBlock(" + Thread.currentThread().getId() + "): Reading " + Block.BLOCKSIZE + " bytes, blockNo " 
						+ block.getBlockNo() + " from file " + fileName);
				block.setSize(fis.read(_payload, 0, Block.BLOCKSIZE));
				block.setPayload(_payload);
				if(block.getSize() == -1) {
					/*
					 * is.read() == -1: nothing to read
					 */
					return 0;
				}
				System.out.println("getBlock(" + Thread.currentThread().getId() + "): got " + block.getSize() + " bytes"); 
			} catch (IOException e) {
				/*
				 * Some error occurred, maybe handle this more gracefully
				 */
				e.printStackTrace();
				return -1;
			}
			transmitStatus[block.getBlockNo()] = now;
			// blocks.add(block.getBlockNo(), new Block(block));
			blocks[block.getBlockNo()] = new Block(block);
			return 1;
		}
		else if(r == 2) {
			return 2;
		}
		
		/*
		 * This should never happen
		 */
		return -1;
	}

	public boolean putBlock(Block block) {
		/*
		 * Maintain a list of unwritten blocks, flush if you can to keep the list as small as possible
		 */
		System.out.println("putBlock(" + Thread.currentThread().getId() + "): putting block " + block.getBlockNo() 
				+ " into blocks (length: " + blocks.length + "), transmitStatus size: " + transmitStatus.length); 
		transmitStatus[block.getBlockNo()] = Block.ARRIVED;
		// blocks.add(block.getBlockNo(), new Block(block));
		blocks[block.getBlockNo()] = new Block(block);
		
		/*
		 * Check if contiguous from the start, then write out and free (yeah, C!!)
		 */
		System.out.println("putBlock(" + Thread.currentThread().getId() + "): flushing block " + tail + ".." + block.getBlockNo()); 
		while(tail <= block.getBlockNo() && transmitStatus[tail] == Block.ARRIVED) {
			// System.out.println("putBlock(" + Thread.currentThread().getId() + "): flushed block " + tail + " (" + blocks.get(tail).getBlockNo() + ") to disk"); 
			System.out.println("putBlock(" + Thread.currentThread().getId() + "): flushed block " + tail + " (" + blocks[tail].getBlockNo() + ") to disk"); 
			try {
				// fos.write(blocks.get(tail).getPayload(), 0, blocks.get(tail).getSize());
				fos.write(blocks[tail].getPayload(), 0, blocks[tail].getSize());
				// blocks.set(tail, null);
				blocks[tail] = null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
			tail++;
		}
		return true;
	}
	
	public void delete() {
		fp.delete();
	}

	public String getFileName() {
		return fileName;
	}
	
	public File getFp() {
		return fp;
	}
	
	public void close() {
		try {
			if(action == FilerAction.TX) fis.close();
			else if(action == FilerAction.RX) {
				System.out.println("LocalFiler(" + Thread.currentThread().getId() + "): flushing and closing " + fileName);
				fos.flush();
				fos.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
