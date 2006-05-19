/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002-05  David Pavlis <david_pavlis@hotmail.com>
*    
*    This library is free software; you can redistribute it and/or
*    modify it under the terms of the GNU Lesser General Public
*    License as published by the Free Software Foundation; either
*    version 2.1 of the License, or (at your option) any later version.
*    
*    This library is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
*    Lesser General Public License for more details.
*    
*    You should have received a copy of the GNU Lesser General Public
*    License along with this library; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/
package org.jetel.data.tape;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetel.data.DataRecord;
import org.jetel.data.Defaults;


/**
 * Data structure for storing records in chunks on local disk (in temporary file).<br>
 * When the data object is closed - the physical representation (the file) is removed - i.e.
 * no permanent storage is performed.<br>
 * Each chunk registers how many records is stored in it. The number
 * of chunks on tape(in file) is not limited. Records can be read only
 * sequentially, but particular chunk can be selected.<br><br>
 * <i>Usage:</i><br>
 * <code>
 * tape=new DataRecordTape();<br>
 * tape.open();<br>
 * tape.addDataChunk();<br>
 * ..loop.. tape.put(..data..);<br>
 * tape.addDataChunk();<br>
 * ..loop.. tape.put(..data..);<br>
 * tape.rewind();<br>
 * ..loop..tape.get();<br>
 * tape.nextChunk();<br>
 * ..loop..tape.get();<br>
 * tape.close();<br>
 * </code>
 * 
 * @author david
 * @since  20.1.2005
 *
 */
public class DataRecordTape {

    private FileChannel tmpFileChannel;
	private File tmpFile;
	private String tmpFileName;

    private boolean deleteOnExit;
    private boolean deleteOnStart;
    
	private List dataChunks;
	
	private ByteBuffer dataBuffer;

	private boolean isClosed;
	
	private DataChunk currentDataChunk;
	private int currentDataChunkIndex;
	
	// size of BUFFER - used for push & shift operations
	private final static int DEFAULT_BUFFER_SIZE = Defaults.DEFAULT_INTERNAL_IO_BUFFER_SIZE; 

	// prefix of temporary file generated by system	
	private final static String TMP_FILE_PREFIX = ".fbufclv";
	//	 suffix of temporary file generated by system
	private final static String TMP_FILE_SUFFIX = ".tmp";
	private final static String TMP_FILE_MODE = "rw";
	private static final String JAVA_IO_TMPDIR_ENV_VAR_NAME = "java.io.tmpdir";

	static Log logger = LogFactory.getLog(DataRecordTape.class);

	/**
	 *  Constructor for the DataRecordTape object
	 *
	 *@param  tmpFileName      Name of the temp file or NULL (the system default temp directory and name will be used)
	 *@param  dataBufferSize   The size of internal in memory buffer.
     *                          If smaller than DEFAULT_BUFFER_SIZE, then default is used
	 */
	public DataRecordTape(String tmpFileName, int dataBufferSize, boolean deleteOnStart, boolean deleteOnExit) {
		this.tmpFileName = tmpFileName;
        this.deleteOnStart = deleteOnStart;
        this.deleteOnExit = deleteOnExit;
		dataChunks=new ArrayList();
		isClosed=false;
		dataBuffer = ByteBuffer.allocateDirect(dataBufferSize > DEFAULT_BUFFER_SIZE ? dataBufferSize : DEFAULT_BUFFER_SIZE);
	}

    /**
     * Constructor.
     * @param tmpFileName
     * @param deleteOnExit
     */
    public DataRecordTape(String tmpFileName, boolean deleteOnStart, boolean deleteOnExit) {
        this(tmpFileName,DEFAULT_BUFFER_SIZE, deleteOnStart, deleteOnExit);
    }

	/**
	 *  Constructor for the DataRecordTape object
	 *
     *@param  tmpFileName      Name of the temp file or NULL (the system default temp directory and name will be used)
	 */
	public DataRecordTape(String tmpFileName) {
		this(tmpFileName,DEFAULT_BUFFER_SIZE, true, true);
	}

	/**
	 * Constructor for DataRecordTape - all parameters defaulted.
	 */
	public DataRecordTape(){
	    this(null,DEFAULT_BUFFER_SIZE, true, true);
	}

	/**
	 *  Opens buffer, creates temporary file.
	 *
	 *@exception  IOException  Description of Exception
	 *@since                   September 17, 2002
	 */
	public void open() throws IOException {
        if(tmpFileName == null)
            tmpFile = File.createTempFile(TMP_FILE_PREFIX, TMP_FILE_SUFFIX);
        else {
            tmpFile = new File(tmpFileName);
            if(deleteOnStart && tmpFile.exists()) {
                if (!tmpFile.delete()) {
                    throw new IOException("Can't delete TMP file: " + tmpFile.getAbsoluteFile());
                }
            }
            if(!deleteOnStart && !tmpFile.exists()) {
                throw new IOException("Temp file does not exist.");
            }
        }
        if(deleteOnExit) tmpFile.deleteOnExit();
        
		// we want the temp file be deleted on exit
		tmpFileChannel = new RandomAccessFile(tmpFile, TMP_FILE_MODE).getChannel();
       	currentDataChunkIndex=-1;
		currentDataChunk=null;
	}


	/**
	 *  Closes buffer, removes temporary file (is exists)
	 *
	 *@exception  IOException  Description of Exception
	 *@since                   September 17, 2002
	 */
	public void close() throws IOException {
		isClosed=true;
        if(deleteOnExit) {
            clear();
            if (!tmpFile.delete()) {
                throw new IOException("Can't delete TMP file: " + tmpFile.getAbsoluteFile());
            }
        }
        tmpFileChannel.close();
	}

	
	/**
	 * Flushes tape content to disk.
	 * 
	 * @throws IOException
	 */
	public void flush(boolean force) throws IOException {
	    dataBuffer.flip();
	    tmpFileChannel.write(dataBuffer);
	    dataBuffer.clear();
	    //currentDataChunk.flushBuffer();
	    if (force){
	        tmpFileChannel.force(true);
	    }
	}

	/**
	 *  Rewinds the buffer and makes first chunk active. Next get operation returns first record stored in
	 * first chunk.
	 *
	 *@since    September 19, 2002
	 */
	public void rewind() {
	    if (dataChunks.size()==0){
	        return;
	    }
	    try{
	    tmpFileChannel.position(0);
	    }catch(IOException ex){
	        ex.printStackTrace();
	    }
	    currentDataChunkIndex=0;
	    currentDataChunk=(DataChunk)dataChunks.get(0);
	    try{
	        currentDataChunk.rewind();
	    }catch(IOException ex){
	        throw new RuntimeException("Can't rewind DataChunk !");
	    }
	}


	/**
	 *  Clears the tape. All DataChunks are destroyed. Underlying 
	 * tmp file is truncated.
	 */
	public void clear() throws IOException{
		dataChunks.clear();
		tmpFileChannel.truncate(0);
		tmpFileChannel.position(0);
		currentDataChunkIndex=-1;
		currentDataChunk=null;
	}

	/**
	 * Adds new data chunk to the tape and opens it.
	 * @return true if successful, otherwise false
	 */
	public boolean addDataChunk() {
	    if (currentDataChunk==null){
	            // add new data chunk
	            DataChunk chunk=new DataChunk(tmpFileChannel,dataBuffer);
	            dataChunks.add(chunk);
	            currentDataChunkIndex=0;
	            currentDataChunk=chunk;
	    }else{
	        try{
	            // set file position to the end of file
	            tmpFileChannel.position(tmpFileChannel.size());
	            // add new data chunk
	            DataChunk chunk=new DataChunk(tmpFileChannel,dataBuffer);
	            dataChunks.add(chunk);
	            currentDataChunkIndex++;
	            currentDataChunk=chunk;
	        }catch(IOException ex){
	            return false;
	        }
	    }
	    dataBuffer.clear();
	    return true;
	}

	/**
	 * Sets next data chunk active. Next get() operation will return first record
	 * from the newly activated chunk. This method must be called after rewind()
	 * method, otherwise the result is not guaranteed.
	 * 
	 * @return true if next data chunk has been activated, otherwise false
	 */
	public boolean nextDataChunk(){
	    if (currentDataChunkIndex!=-1 && currentDataChunkIndex+1 < dataChunks.size()){
	        currentDataChunkIndex++;
	        currentDataChunk=(DataChunk)dataChunks.get(currentDataChunkIndex);
	        try{
		        currentDataChunk.rewind();
		    }catch(IOException ex){
		        throw new RuntimeException("Can't rewind DataChunk !");
		    }
	        return true;
	    }else{
	        currentDataChunkIndex=-1;
			currentDataChunk=null;
	        return false;
	    }
	}
	
	
	public boolean setDataChunk(int order){
	    if (order<dataChunks.size()){
	        currentDataChunk=(DataChunk)dataChunks.get(order);
	        currentDataChunkIndex=order;
	        
	        try{
	            currentDataChunk.rewind();
	        }catch(IOException ex){
	            throw new RuntimeException("Can't rewind DataChunk !");
	        }
	        return true;
	    }else{
	        currentDataChunkIndex=-1;
			currentDataChunk=null;
	        return false;
	    }
	    
	}
	
	public int getNumChunks(){
	    return dataChunks.size();
	}
	
	/**
	 * Stores data in current/active chunk. Must not be mixed with calls to
	 * get() method, otherwise the result is not guaranteed.
	 * 
	 * @param data buffer containig record's data
	 * @return
	 */
	public boolean put(ByteBuffer data) throws IOException{
	    try{
	        currentDataChunk.put(data);
	    }catch(NullPointerException ex){
	        throw new RuntimeException("No DataChunk has been created !");
	    }
	    return true;
	}
	
	/**
	 * Stores data record in current/active chunk.
	 * 
	 * @param data
	 * @return
	 * @throws IOException
	 */
	public boolean put(DataRecord data) throws IOException{
	    try{
	        currentDataChunk.put(data);
	    }catch(NullPointerException ex){
	        throw new RuntimeException("No DataChunk has been created !");
	    }
	    return true;
	}
	
	/**
	 * Fills buffer passed as an argument with data read from current data chunk.
	 * Must not be mixed with put() method calls, otherwise the result is not guaranteed.<br>
	 * The normal way of using this method is:<br>
	 * while(get(data)){
	 *   ... do something ...
	 * }
	 * @param data	buffer into which store data
	 * @return	true if success, otherwise false (chunk contains no more data).
	 */
	public boolean get(ByteBuffer data) throws IOException{
	    if (currentDataChunk!=null){
	        return currentDataChunk.get(data);
	    }else{
	        return false;
	    }
	}
	
	/**
	 * Reads data record from current chunk
	 * 
	 * @param data
	 * @return
	 * @throws IOException
	 */
	public boolean get(DataRecord data) throws IOException{
	    if (currentDataChunk!=null){
	        return currentDataChunk.get(data);
	    }else{
	        return false;
	    }
	}

	/* Returns String containing short summary of chunks stored on tape.
	 * 
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString(){
	    StringBuffer buffer=new StringBuffer(160);
	    int index=0;
	    for (Iterator i=dataChunks.iterator();i.hasNext();){
	        buffer.append("Chunk #").append(index++);
	        buffer.append(((DataChunk)i.next()).toString());
	        buffer.append("\\n");
	    }
	    return buffer.toString();
	}
	
	public void testConsistency(){
	    ByteBuffer buffer=ByteBuffer.allocateDirect(2048);
	    logger.info("Testing consistency...");
	    rewind();
	    for(int i=0;i<getNumChunks();i++){
	        int counter=0;
	        try{
	            while(get(buffer)){
	                counter++;
	                buffer.clear();
	            }
	        }catch(Exception ex){
	            logger.error("Problem with chunk: "+i+" record "+counter);
	            ex.printStackTrace();
	        }
	        if(!nextDataChunk()) break;
	    }
	    logger.info("OK");
	}
	
	/**
	 * Helper class for storing data chunks. 
	 * @author david
	 * @since  20.1.2005
	 *
	 * TODO To change the template for this generated type comment go to
	 * Window - Preferences - Java - Code Style - Code Templates
	 */
	private static class DataChunk{
	    //	size of integer variable used to keep record length
	    private final static int LEN_SIZE_SPECIFIER = 4;
	    ByteBuffer dataBuffer;
	    FileChannel tmpFileChannel;
	    long offsetStart;
	    long length;
	    long position;
	    int recordsRead;
	    int nRecords;
	    boolean canRead;
	    
	    private DataChunk(FileChannel channel,ByteBuffer buffer){
	        tmpFileChannel=channel;
	        canRead=false;
	        dataBuffer=buffer;
	        try{
	            offsetStart=channel.position();
	        }catch(IOException ex){
	            throw new RuntimeException(ex);
	        }
	        length=0;
	        nRecords=recordsRead=0;
	        dataBuffer.clear();
	    }
	    
	    long getLength(){
	        return length;
	    }
	    
	    int getNumRecords(){
	        return nRecords;
	    }
	    
	    void rewind() throws IOException{
	        tmpFileChannel.position(offsetStart);
	        canRead=true;
	        recordsRead=0;
	        dataBuffer.clear();
	        tmpFileChannel.read(dataBuffer);
	        dataBuffer.flip();
	        position=0;
	    }
	    
	    /**
		 *  Stores one data record into buffer / file.
		 *
		 *@param  recordBuffer             ByteBuffer containing record's data
		 *@exception  IOException  In case of IO failure
		 *@since                   September 17, 2002
		 */
		 void put(ByteBuffer recordBuffer) throws IOException {
			int recordSize = recordBuffer.remaining();
			
			// check that internal buffer has enough space
			if ((recordSize + LEN_SIZE_SPECIFIER) > dataBuffer.remaining()){
					flushBuffer();
				}
			
			try {
				dataBuffer.putInt(recordSize);
				dataBuffer.put(recordBuffer);
			} catch (BufferOverflowException ex) {
				throw new RuntimeException("Input Buffer is not big enough to accomodate data record !");
			}
			
			length+=(recordSize+ LEN_SIZE_SPECIFIER);
			nRecords++;
		}

		 /**
		  * Stores one data record into buffer / file.
		  * 
		 * @param data	DataRecord to be stored
		 * @throws IOException
		 */
		void put(DataRecord data) throws IOException {
				int recordSize = data.getSizeSerialized();
				
				// check that internal buffer has enough space
				if ((recordSize + LEN_SIZE_SPECIFIER) > dataBuffer.remaining()){
						flushBuffer();
					}
				
				try {
					dataBuffer.putInt(recordSize);
					data.serialize(dataBuffer);
				} catch (BufferOverflowException ex) {
					throw new RuntimeException("Input Buffer is not big enough to accomodate data record !");
				}
				
				length+=(recordSize+ LEN_SIZE_SPECIFIER);
				nRecords++;
			}

		/**
		 *  Returns next record from the buffer - FIFO order.
		 *
		 *@param  recordBuffer             ByteBuffer into which store data
		 *@return                  ByteBuffer populated with record's data or NULL if
		 *      no more record can be retrieved
		 *@exception  IOException  Description of Exception
		 *@since                   September 17, 2002
		 */
		 boolean get(ByteBuffer recordBuffer) throws IOException {
			int recordSize;
			if(!canRead){
				throw new RuntimeException("Buffer has not been rewind !");
			}
			
			if (nRecords > 0 && recordsRead>=nRecords){
			    return false;
			}
			//	check that internal buffer has enough data to read data size
			if (LEN_SIZE_SPECIFIER > dataBuffer.remaining()){
			    reloadBuffer();
			    if(LEN_SIZE_SPECIFIER > dataBuffer.remaining()) return false;
			}
			recordSize = dataBuffer.getInt();
			position+=LEN_SIZE_SPECIFIER;
			
			//	check that internal buffer has enough data to read data record
			if (recordSize > dataBuffer.remaining()){
			    reloadBuffer();
			    if(recordSize > dataBuffer.remaining()) return false;
			}
			int oldLimit = dataBuffer.limit();
			dataBuffer.limit(dataBuffer.position() + recordSize);
            recordBuffer.clear();
			recordBuffer.put(dataBuffer);
			recordBuffer.flip();
			dataBuffer.limit(oldLimit);
			
			position+=recordSize;
			recordsRead++;
			return true;
		}

		 /**
		  * Returns next record from the buffer - FIFO order.
		 * @param data	DataRecord into which load the data
		 * @return
		 * @throws IOException
		 */
		boolean get(DataRecord data) throws IOException {
				int recordSize;
				if(!canRead){
					throw new RuntimeException("Buffer has not been rewind !");
				}
				
				if (nRecords > 0 && recordsRead>=nRecords){
				    return false;
				}
				//	check that internal buffer has enough data to read data size
				if (LEN_SIZE_SPECIFIER > dataBuffer.remaining()) {
				    reloadBuffer();
                    if(LEN_SIZE_SPECIFIER > dataBuffer.remaining()) return false;
				}
				recordSize = dataBuffer.getInt();
				position+=LEN_SIZE_SPECIFIER;
				
				//	check that internal buffer has enough data to read data record
				if (recordSize > dataBuffer.remaining()){
				    reloadBuffer();
                    if(recordSize > dataBuffer.remaining()) return false;
				}
				data.deserialize(dataBuffer);
				
				position+=recordSize;
				recordsRead++;
				return true;
			}
		 
		/**
		 *  Flushes in memory buffer into TMP file
		 *
		 *@exception  IOException  Description of Exception
		 *@since                   September 17, 2002
		 */
		private void flushBuffer() throws IOException {
			dataBuffer.flip();
			tmpFileChannel.write(dataBuffer);
			dataBuffer.clear();
		}
		
		private void reloadBuffer() throws IOException {
			dataBuffer.compact();
			tmpFileChannel.read(dataBuffer);
			dataBuffer.flip();
		}


		public boolean isEmpty(){
			return (length==0);
		}
	    
		public String toString(){
		    return "start: "+offsetStart+" #records: "+nRecords+" length: "+length;
		}
	}
	
}

