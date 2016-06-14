package junami.common;

import java.io.FileNotFoundException;
import java.net.URI;
import java.util.Date;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;

public class S3Filer extends Filer {
	/*
	 * Dummy
	 */
	LocalFiler dummy;
	String dummyName;
	
	/*
	 * S3 stuff
	 */
	String scheme, bucket, key;
	TransferManager txMgr;
	
	public S3Filer(URI uri, FilerAction action, long size) throws FileNotFoundException {
		super(uri.toString(), FilerType.S3, action, size);

		if(! uri.getScheme().equalsIgnoreCase("s3")) return;
		
		this.scheme = uri.getScheme();
		this.bucket = uri.getHost();
		// this.userInfo = uri.getUserInfo();
		this.key = uri.getPath();
		
		txMgr = new TransferManager(new BasicAWSCredentials("AKIAJBH7WKBK67W5NLBA", "1+ydZYdVpAY/23l5kalwNjI6eN2JdaZnNAFtUNpH"));
		
		dummyName = "junamid-dummy-" + Thread.currentThread().getId() + "-" + (new Date()).getTime() + ".tmp";
		
		if(action == FilerAction.TX) {
			dummy = new LocalFiler(dummyName, FilerAction.RX, size);
			get();
			dummy.close();
		}
		dummy = new LocalFiler(dummyName, action, size);
	}

	public int getBlock(Block block) {
		return dummy.getBlock(block);
	}

	public boolean putBlock(Block block) {
		return dummy.putBlock(block);
	}

	public String getArrivedBlocks(Integer lastAck) {
		return dummy.getArrivedBlocks(lastAck);
	}
	public boolean get() throws FileNotFoundException {
		/*
		 * Get from S3 using TransferManager
		 */
		try {
			Download download = txMgr.download(bucket, key, dummy.getFp());
			download.waitForCompletion();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new FileNotFoundException();
		}
		return true;
	}

	public boolean put() {
		/*
		 * Close dummy and reopen for transmission (so we can read from it)
		 * Stick it in S3 using TransferManager
		 */
		dummy.close();
		try {
			dummy = new LocalFiler(dummyName, FilerAction.TX, size);
			Upload upload = txMgr.upload(bucket, key, dummy.getFp());
			upload.waitForCompletion();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void done() {
		txMgr.shutdownNow();
		dummy.close();
		// dummy.delete();
	}
}
