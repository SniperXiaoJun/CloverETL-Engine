/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.data.reader;

import java.io.IOException;

import org.jetel.data.DataRecord;
import org.jetel.data.RecordKey;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.graph.InputPort;

/**
 * Slave reader without duplicates support. Pretends that all runs contain only one record.
 * Doesn't use buffer, supports rewind operation.
 * @author Jan Hadrava, Javlin Consulting (www.javlinconsulting.cz)
 * @author Agata Vackova, Javlin Consulting (www.javlinconsulting.cz)
 *
 */
public class SlaveReader implements InputReader {
	private static final int CURRENT = 0;
	private static final int NEXT = 1;

	private InputPort inPort;
	protected RecordKey key;
	private DataRecord[] rec = new DataRecord[2];
	private boolean firstRun;
	private boolean needsRewind;
	private boolean keepLast;

	private InputOrdering inputOrdering = InputOrdering.UNDEFINED;
	
	/**
	 * Constructor of slave reader object
	 * 
	 * @param inPort input port for reading data
	 * @param key key for comparing records
	 * @param keepLast whether use last (true) or first (false) record from all with the same key  
	 */
	public SlaveReader(InputPort inPort, RecordKey key, boolean keepLast) {
		this.inPort = inPort;
		this.key = key;
		this.keepLast = keepLast;
		this.rec[CURRENT] = new DataRecord(inPort.getMetadata());
		this.rec[NEXT] = new DataRecord(inPort.getMetadata());
		this.rec[CURRENT].init();
		this.rec[NEXT].init();
		this.firstRun = true;
		this.needsRewind = true;
	}
	
	@Override
	public void reset() throws ComponentNotReadyException {
		this.rec[CURRENT] = new DataRecord(inPort.getMetadata());
		this.rec[NEXT] = new DataRecord(inPort.getMetadata());
		this.rec[CURRENT].init();
		this.rec[NEXT].init();
		this.firstRun = true;
		this.needsRewind = true;
	}
	
	@Override
	public void free() {
		inPort = null;
	}
	
	private void swap() {
		DataRecord tmp = rec[CURRENT];
		rec[CURRENT] = rec[NEXT];
		rec[NEXT] = tmp;
	}

	private boolean loadNextRunKeepFirst()  throws InterruptedException, IOException{
		if (inPort == null) {
			rec[CURRENT] = rec[NEXT] = null;
			return false;
		} 
		while(inPort.readRecord(rec[NEXT]) != null) {
			needsRewind = false;
			if (firstRun) {
				firstRun = false;
			}
			//if (key.compare(rec[NEXT], rec[CURRENT]) != 0) {
			int comparison = key.compare(rec[CURRENT], rec[NEXT]);
			if (comparison != 0) {
				inputOrdering = updateOrdering(comparison, inputOrdering);
				swap();
				return true;
			}
		}
		rec[CURRENT] = rec[NEXT] = null;
		return false;
	}

	protected static InputOrdering updateOrdering(int comparison, InputOrdering inputOrdering) {
		if (comparison > 0){
			if (inputOrdering!=InputOrdering.DESCENDING)
				inputOrdering = (inputOrdering==InputOrdering.UNDEFINED ? InputOrdering.DESCENDING : InputOrdering.UNSORTED ) ;
		} 
		if (comparison < 0){
			if (inputOrdering!=InputOrdering.ASCENDING)
				inputOrdering = (inputOrdering==InputOrdering.UNDEFINED ? InputOrdering.ASCENDING : InputOrdering.UNSORTED ) ;
		}
		return inputOrdering;
	}

	private boolean loadNextRunKeepLast() throws InterruptedException, IOException {
		if (inPort == null) {
			rec[CURRENT] = rec[NEXT] = null;
			return false;
		} 
		if (firstRun) {	// first call of this function
			firstRun = false;
			// load first record of the run
			if (inPort.readRecord(rec[NEXT]) == null) {
				rec[CURRENT] = rec[NEXT] = null;
				return false;
			}
		}
		swap();
		while (true) {
		// current record is now the first one from the run to be loaded
		// set current record to the last one from the run to be loaded and next record to the first one
		// from the following run
		needsRewind = false;
			rec[NEXT].reset();
			if (inPort.readRecord(rec[NEXT]) == null) {
				rec[NEXT] = null;
				return true;
			}

			int comparison = key.compare(rec[CURRENT], rec[NEXT]);
			if (comparison != 0) {	// beginning of new run
				inputOrdering = updateOrdering(comparison, inputOrdering);
				return true;
			}
			swap();
		}
	}

	@Override
	public void rewindRun() {
		needsRewind = false;
	}

	@Override
	public DataRecord getSample() {
		if (firstRun) {
			return null;
		}
		return rec[CURRENT];
	}

	@Override
	public DataRecord next() throws IOException {
		if (firstRun || needsRewind) {
			return null;
		}
		needsRewind = true;
		return rec[CURRENT];
	}

	@Override
	public RecordKey getKey() {
		return key;
	}
	
	@Override
	public int compare(InputReader other) {
		DataRecord rec1 = getSample();
		DataRecord rec2 = other.getSample();
//		if (rec1 == null) {
//			return rec2 == null ? 0 : -1;
//		} else if (rec2 == null) {
//			return 1;
//		}
		if (rec1 == null) {
			return 1; // null is greater than any other reader (as in DriverReader)
//			return rec2 == null ? 0 : 1;	
		} else if (rec2 == null) {
			return -1;
		}
		return key.compare(other.getKey(), rec1, rec2);
	}

	@Override
	public boolean hasData() {
		return rec[NEXT] != null || rec[CURRENT] != null;
	}		

	@Override
	public String toString() {
		return getSample().toString();
	}

	@Override
	public boolean loadNextRun() throws InterruptedException, IOException {
		return keepLast ? loadNextRunKeepLast() : loadNextRunKeepFirst();
	}

	@Override
	public InputOrdering getOrdering() {
		return inputOrdering;
	}
}