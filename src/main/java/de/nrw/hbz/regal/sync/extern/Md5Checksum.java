package de.nrw.hbz.regal.sync.extern;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Copied from
 * http://stackoverflow.com/questions/304268/getting-a-files-md5-checksum
 * -in-java
 * 
 */
public class Md5Checksum {

    @SuppressWarnings("serial")
    class Md5ChecksumException extends RuntimeException {
	public Md5ChecksumException(Throwable cause) {
	    super(cause);
	}
    }

    /**
     * @param file
     *            the file you want to get the checksum from
     * @return checksum as byte array
     */
    public byte[] createChecksum(File file) {
	try {
	    InputStream fis = new FileInputStream(file);

	    byte[] buffer = new byte[1024];
	    MessageDigest complete = MessageDigest.getInstance("MD5");
	    int numRead;

	    do {
		numRead = fis.read(buffer);
		if (numRead > 0) {
		    complete.update(buffer, 0, numRead);
		}
	    } while (numRead != -1);

	    fis.close();
	    return complete.digest();
	} catch (Exception e) {
	    throw new Md5ChecksumException(e);
	}
    }

    /**
     * @param filename
     *            the filename of the file you want to get the checksum from
     * @return checksum as string
     */
    public String getMd5Checksum(String filename) {
	File file = new File(filename);
	return getMd5Checksum(file);
    }

    /**
     * @param file
     *            the file you want to get the checksum from
     * @return the checksum as string
     */
    public String getMd5Checksum(File file) {
	byte[] b = createChecksum(file);
	String result = "";

	for (int i = 0; i < b.length; i++) {
	    result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
	}
	return result;
    }
}