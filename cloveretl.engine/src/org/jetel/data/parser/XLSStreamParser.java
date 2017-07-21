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
package org.jetel.data.parser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.hssf.eventusermodel.FormatTrackingHSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.BOFRecord;
import org.apache.poi.hssf.record.BoolErrRecord;
import org.apache.poi.hssf.record.BoundSheetRecord;
import org.apache.poi.hssf.record.CellRecord;
import org.apache.poi.hssf.record.CellValueRecordInterface;
import org.apache.poi.hssf.record.EOFRecord;
import org.apache.poi.hssf.record.ExtendedFormatRecord;
import org.apache.poi.hssf.record.FormatRecord;
import org.apache.poi.hssf.record.FormulaRecord;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.MulBlankRecord;
import org.apache.poi.hssf.record.NoteRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.RecordFactoryInputStream;
import org.apache.poi.hssf.record.RecordFormatException;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.hssf.record.SharedFormulaRecord;
import org.apache.poi.hssf.record.StringRecord;
import org.apache.poi.hssf.record.crypto.Biff8EncryptionKey;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.DateUtil;
import org.jetel.data.DataField;
import org.jetel.data.DataRecord;
import org.jetel.data.parser.AbstractSpreadsheetParser.CellValueFormatter;
import org.jetel.data.parser.SpreadsheetStreamParser.CellBuffers;
import org.jetel.data.parser.SpreadsheetStreamParser.RecordFieldValueSetter;
import org.jetel.exception.BadDataFormatException;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.JetelException;
import org.jetel.exception.JetelRuntimeException;
import org.jetel.exception.SpreadsheetException;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.util.SpreadsheetUtils;

/**
 * @author lkrejci (info@cloveretl.com) (c) Javlin, a.s. (www.cloveretl.com)
 * 
 * @created 22 Aug 2011
 */
public class XLSStreamParser implements SpreadsheetStreamHandler {
	
	private SpreadsheetStreamParser parent;

	private DirectoryNode workbookDirNode;
	private NPOIFSFileSystem poiFS;
	
	private String fileName;
	private String sheetName;

	private final static Set<Short> ACCEPTED_RECORDS = new HashSet<Short>();
	static {
		ACCEPTED_RECORDS.add(BOFRecord.sid);
		ACCEPTED_RECORDS.add(SSTRecord.sid);
		ACCEPTED_RECORDS.add(NumberRecord.sid);
		ACCEPTED_RECORDS.add(LabelSSTRecord.sid);
		ACCEPTED_RECORDS.add(FormulaRecord.sid);
		ACCEPTED_RECORDS.add(StringRecord.sid);
		ACCEPTED_RECORDS.add(FormulaRecord.sid);
		ACCEPTED_RECORDS.add(BoolErrRecord.sid);
		ACCEPTED_RECORDS.add(EndOfRowRecord.SID);

		ACCEPTED_RECORDS.add(FormatRecord.sid);
		ACCEPTED_RECORDS.add(ExtendedFormatRecord.sid);
	}

	private ParserFinishedFunctionPointer recordReader;
	private ParserFinishedFunctionPointer sheetSetter;
	private ParserFinishedFunctionPointer recordSkipper;

	private MissingRecordAwareFactoryInputStream recordFactory;
	private final RecordFillingHSSFListener recordFillingListener;
	private final FormatTrackingHSSFListenerWithCustomFormatter formatter;
	
	private int currentSheetIndex = -1;

	private int nextRecordStartRow;
	
	private boolean sheetEof;
	private boolean rootEof;
	
	public XLSStreamParser(SpreadsheetStreamParser parent, String password) {
		this.parent = parent;
		recordFillingListener = new RecordFillingHSSFListener(AbstractSpreadsheetParser.USE_DATE1904);
		formatter = new FormatTrackingHSSFListenerWithCustomFormatter(recordFillingListener);
		Biff8EncryptionKey.setCurrentUserPassword(password);
	}

	@Override
	public void init() throws ComponentNotReadyException {
		recordFillingListener.init();
		recordSkipper = new ParserFinishedFunctionPointer() {

			@Override
			public boolean finishedCondition() {
				return !recordFillingListener.skipRecords();
			}

			@Override
			public boolean stopCondition() {
				return !recordFillingListener.isRequestedSheetSet();
			}
		};
		
		recordReader = new ParserFinishedFunctionPointer() {

			@Override
			public boolean finishedCondition() {
				return recordFillingListener.isRecordFinished();
			}

			@Override
			public boolean stopCondition() {
				return !recordFillingListener.isRequestedSheetSet();
			}
		};
		
		sheetSetter = new ParserFinishedFunctionPointer() {

			@Override
			public boolean finishedCondition() {
				// Do not process any more records is requested sheet is set
				return recordFillingListener.isRequestedSheetSet();
			}

			@Override
			public boolean stopCondition() {
				// Never stop processing records for any other reason
				return false;
			}
		};
	}

	@Override
	public DataRecord parseNext(DataRecord record) throws JetelException {
		recordFillingListener.setRecordRange(nextRecordStartRow, parent.mappingInfo.getStep());
		recordFillingListener.setRecord(record);
		if (!processParsing(recordReader)) {
			if (!recordFillingListener.isRecordStarted() && recordFillingListener.cellBuffers.isEmpty()) {
				return null;
			}
			recordFillingListener.finishRecord();
		}

		nextRecordStartRow += parent.mappingInfo.getStep();
		return record;
	}

	@Override
	public int skip(int nRec) throws JetelException {
		if (nRec <= 0) {
			return 0;
		}
		recordFillingListener.setSkipRecords(nRec);
		processParsing(recordSkipper);

		int skippedRecords = recordFillingListener.getNumberOfSkippedRecords();
		nextRecordStartRow += skippedRecords * parent.mappingInfo.getStep();
		
		return skippedRecords;
	}

	@Override
	public void prepareInput(Object inputSource) throws IOException, ComponentNotReadyException {
		if (inputSource instanceof InputStream) {
			workbookDirNode = new POIFSFileSystem((InputStream) inputSource).getRoot();
		} else {
			poiFS = new NPOIFSFileSystem((File) inputSource);
			workbookDirNode = poiFS.getRoot();
			fileName = ((File) inputSource).getAbsolutePath();
		}
		prepareRecordFactory();
		currentSheetIndex = -1;
	}
	
	@Override
	public void close() throws IOException {
		Biff8EncryptionKey.setCurrentUserPassword(null);
		if (poiFS != null) {
			poiFS.close();
		}
	}

	private void prepareRecordFactory() {
		try {
			RecordFactoryInputStream rfis = new RecordFactoryInputStream(workbookDirNode.createDocumentInputStream("Workbook"), false);
			recordFactory = new MissingRecordAwareFactoryInputStream(rfis);
			currentSheetIndex = -1;
		} catch (IOException e) {
			throw new JetelRuntimeException("Failed to open input stream from workbook", e);
		}
	}

	@Override
	public List<String> getSheetNames() throws IOException {
		HSSFEventFactory factory = new HSSFEventFactory();
		HSSFRequest request = new HSSFRequest();

		SheetNamesHSSFListener listener = new SheetNamesHSSFListener();
		request.addListener(listener, BoundSheetRecord.sid);
		//factory.processWorkbookEvents(request, fs);
		factory.processWorkbookEvents(request, workbookDirNode);

		return listener.getSheetNames();
	}
	
	@Override
	public int getCurrentRecordStartRow() {
		return nextRecordStartRow;
	}

	@Override
	public boolean setCurrentSheet(int sheetNumber) {
		if (sheetNumber >= parent.sheetNames.size()) {
			return false;
		}
		if (currentSheetIndex > sheetNumber) {
			prepareRecordFactory();
		}
		recordFillingListener.setRequestedSheetIndex(sheetNumber);
		sheetName = parent.sheetNames.get(sheetNumber);
		
		sheetEof = false;
		rootEof = false;

		if (processParsing(sheetSetter)) {
			this.nextRecordStartRow = parent.startLine;
			return true;
		}

		return false;
	}

	@Override
	public String[][] getHeader(int startRow, int startColumn, int endRow, int endColumn) throws ComponentNotReadyException {
		RecordFactoryInputStream rfis;
		try {
			rfis = new RecordFactoryInputStream(workbookDirNode.createDocumentInputStream("Workbook"), false);
			MissingRecordAwareFactoryInputStream recordFactory = new MissingRecordAwareFactoryInputStream(rfis);
			HeaderHSSFListener headerListener = new HeaderHSSFListener(currentSheetIndex, startRow, startColumn, endRow, endColumn);
			FormatTrackingHSSFListener formatter = new FormatTrackingHSSFListener(headerListener);
			headerListener.setFormatter(formatter);

			Record recordToProcess = null;
			while (!headerListener.isFinished()) {
				recordToProcess = recordFactory.nextRecord();
				if (recordToProcess == null) {
					break;
				}

				if (ACCEPTED_RECORDS.contains(recordToProcess.getSid())) {
					formatter.processRecord(recordToProcess);
				}
			}

			return headerListener.getHeader();
		} catch (IOException e) {
			throw new ComponentNotReadyException(e);
		}
	}

	private static class SheetNamesHSSFListener implements HSSFListener {

		private final List<String> sheetNames = new ArrayList<String>();

		@Override
		public void processRecord(Record record) {
			if (record.getSid() == BoundSheetRecord.sid) {
				sheetNames.add(((BoundSheetRecord) record).getSheetname());
			}
		}

		public List<String> getSheetNames() {
			return sheetNames;
		}
	}

	// TODO: unify somehow with XLSXStreamParser.RecordFillingContentHandler, there is a lot of duplicated code :-/
	//			- some duplicated code already refactored into CellBuffers class
	private class RecordFillingHSSFListener implements HSSFListener, RecordFieldValueSetter<Record> {

		private final boolean date1904;

		/* shared strings table container */
		private SSTRecord sstRecord;
		/* string formula */
		private boolean outputNextStringRecord;
		private int stringFormulaColumnIndex;

		/* skip functionality */
		private int skipStartRow;
		private boolean skipRecords = false;

		/* requested sheet index */
		private int requestedSheetIndex;

		/* record to fill */
		private DataRecord recordToFill;
		/* row on which starts currently read record */
		private int recordStartRow;
		/* row where current record finishes */
		private int recordEndRow;

		/* actual row which is being parsed */
		private int currentParseRow;
		/* flag indicating whether row where current record finishes was read */
		private boolean recordStarted;
		private boolean recordFinished;

		private int lastColumn = -1;

		private CellBuffers<Record> cellBuffers;
		private FieldValueToFormatSetter fieldValueToFormatSetter = new FieldValueToFormatSetter();

		public RecordFillingHSSFListener(boolean date1904) {
			this.date1904 = date1904;
		}

		public void init() {
			currentParseRow = 0;
			cellBuffers = parent.new CellBuffers<Record>();
			cellBuffers.init(Record.class, this, fieldValueToFormatSetter);
		}

		public void setRequestedSheetIndex(int requestedSheetIndex) {
			this.requestedSheetIndex = requestedSheetIndex;

			cellBuffers.clear();
			
			recordStartRow = parent.startLine;
			currentParseRow = 0;
			lastColumn = -1;
		}

		public boolean isRequestedSheetSet() {
			return requestedSheetIndex == currentSheetIndex;
		}

		public void handleMissingCells(int mappingRow, int first, int last) {
			int[] mappingPart = parent.mapping[mappingRow];
			int[] formatsMappingPart = parent.formatMapping != null ? parent.formatMapping[mappingRow] : null;
			for (int i = first + 1; i < last; i++) {
				setFieldToNull(mappingPart, i);
				setFieldToNull(formatsMappingPart, i);
			}
		}

		private void setFieldToNull(int[] mappingArray, int i) {
			if (mappingArray != null) {
				int cloverFieldIndex;
				cloverFieldIndex = mappingArray[i];
				if (cloverFieldIndex != XLSMapping.UNDEFINED) {
					try {
						recordToFill.getField(cloverFieldIndex).setNull(true);
						if (sheetEof) {
							parent.handleException(new SpreadsheetException("Unexpected end of sheet - expected another data row for field " + 
									recordToFill.getField(cloverFieldIndex).getMetadata().getName() + ". Occurred"), recordToFill, cloverFieldIndex, 
									fileName, sheetName, null, null, null, null);
						}
					} catch (BadDataFormatException e) {
						parent.handleException(new SpreadsheetException("Unexpected end of sheet - expected another data row for field " + 
								recordToFill.getField(cloverFieldIndex).getMetadata().getName() + ". Moreover, cannot set default value or null", e),
								recordToFill, cloverFieldIndex, fileName, sheetName, null, null, null, null);
					}
				}
			}
		}

		public boolean isRecordStarted() {
			return recordStarted;
		}
		
		public boolean isRecordFinished() {
			return recordFinished;
		}
		
		public void finishRecord() {
			for (int i = currentParseRow; i <= recordEndRow; i++) {
				handleMissingCells(i - recordStartRow, lastColumn, parent.mapping[0].length);
			}
		}

		public int getNumberOfSkippedRecords() {
			int result = currentParseRow - skipStartRow;
			return result > 0 ? result / parent.mappingInfo.getStep() : 0;
		}

		public void setSkipRecords(int nRec) {
			if (nRec > 0) {
				skipStartRow = recordStartRow;
				recordToFill = null;
				
				int numberOfRows = nRec * parent.mappingInfo.getStep();
				setRecordRange(recordStartRow + numberOfRows, parent.mappingInfo.getStep());
				
				skipRecords = true;
			}
		}

		public boolean skipRecords() {
			return skipRecords;
		}

		public void setRecordRange(int startRow, int step) {
			recordStartRow = startRow;
			recordEndRow = startRow + parent.mapping.length - 1;
			recordStarted = false;
			recordFinished = false;
		}

		public void setRecord(DataRecord record) {
			this.recordToFill = record;
			cellBuffers.fillRecordFromBuffer(record);
		}

		@Override
		public void processRecord(Record record) {
			short sid = record.getSid();

			if (sid == SSTRecord.sid) {
				// this record must be read no matter what, as SST stands for shared strings table
				sstRecord = (SSTRecord) record;
				return;
			}

			if (sid == BOFRecord.sid) {
				if (((BOFRecord) record).getType() == BOFRecord.TYPE_WORKSHEET) {
					currentSheetIndex++;

					// Do not skip any more records! MultiFileReader will continue skipping if too few records were skipped.
					if (skipRecords) {
						skipRecords = false;
					}
				}
				return;
			}

			if (record instanceof EndOfRowRecord) {
				if (currentSheetIndex != requestedSheetIndex) {
					// Ignore these records
					return;
				}
				if (!skipRecords && currentParseRow >= recordStartRow) {
					handleMissingCells(currentParseRow - recordStartRow, lastColumn, parent.mapping[0].length);
				}
				if (skipRecords && currentParseRow >= recordStartRow - 1) {
					// record skipping is stopped when last row of last record to be skipped is read
					skipRecords = false;
				}

				if (currentParseRow >= recordEndRow) {
					// record reading is finished when last row of record is read
					recordFinished = true;
				}

				currentParseRow++;
				lastColumn = -1;

				return;
			}

			if (currentParseRow < recordStartRow || currentSheetIndex != requestedSheetIndex) {
				//not iterested in these records
				return;
			}

			switch (sid) {
			case NumberRecord.sid:
				NumberRecord numrec = (NumberRecord) record;
				processCell(numrec.getColumn(), numrec);
				break;
			case LabelSSTRecord.sid:
				LabelSSTRecord lrec = (LabelSSTRecord) record;
				processCell(lrec.getColumn(), lrec);
				break;

			case FormulaRecord.sid:
				FormulaRecord frec = (FormulaRecord) record;
				if (frec.getCachedResultType() == HSSFCell.CELL_TYPE_STRING) {
					outputNextStringRecord = true;
					stringFormulaColumnIndex = frec.getColumn();
				} else {
					processCell(frec.getColumn(), frec);
				}
				break;
			case StringRecord.sid:
				if (outputNextStringRecord) {
					// String for formula
					StringRecord srec = (StringRecord) record;
					processCell(stringFormulaColumnIndex, srec);
					outputNextStringRecord = false;
				}
				break;

			case BoolErrRecord.sid:
				BoolErrRecord boolRecord = (BoolErrRecord) record;
				if (boolRecord.isBoolean()) {
					processCell(boolRecord.getColumn(), boolRecord);
				}
				break;
			}
		}

		public void processCell(int columnIndex, Record cellRecord) {
			// Record has not started yet
			if (currentParseRow < recordStartRow) {
				return;
			}
			
			if (columnIndex < parent.mappingMinColumn || columnIndex >= parent.mapping[0].length + parent.mappingMinColumn) {
				return;
			}

			// mapping 
			int shiftedColumnIndex = columnIndex - parent.mappingMinColumn;
			int shiftedRowIndex = currentParseRow - recordStartRow;

			handleMissingCells(shiftedRowIndex, lastColumn, shiftedColumnIndex);
			lastColumn = shiftedColumnIndex;

			// if records are being skipped, recordToFill is null, therefore we are interested only with buffering
			cellBuffers.setCellBufferValue(shiftedRowIndex, shiftedColumnIndex, cellRecord);
			if (recordToFill != null) {
				if (parent.mapping[shiftedRowIndex][shiftedColumnIndex] != XLSMapping.UNDEFINED) {
					setFieldValue(parent.mapping[shiftedRowIndex][shiftedColumnIndex], cellRecord);
				}
				if (parent.formatMapping != null) {
					if (parent.formatMapping[shiftedRowIndex][shiftedColumnIndex] != XLSMapping.UNDEFINED) {
						fieldValueToFormatSetter.setFieldValue(parent.formatMapping[shiftedRowIndex][shiftedColumnIndex], cellRecord);
					}
				}
			}
		
		}

		@Override
		public void setFieldValue(int cloverFieldIndex, Record cellRecord) {
			if (!recordStarted) {
				recordStarted = true;
			}
			
			DataField field = recordToFill.getField(cloverFieldIndex);
			short sid = cellRecord.getSid();

			switch (field.getType()) {
			case DataFieldMetadata.DATE_FIELD:
			case DataFieldMetadata.DATETIME_FIELD:
				double date;

				switch (sid) {
				case NumberRecord.sid:
					date = ((NumberRecord) cellRecord).getValue();
					break;
				case FormulaRecord.sid:
					date = ((FormulaRecord) cellRecord).getValue();
					break;
				default:
					notifyExceptionHandler(cloverFieldIndex, field, cellRecord, "Date");
					return;
				}

				field.setValue(DateUtil.getJavaDate(date, date1904)); // TODO: really use this method?
				break;
			case DataFieldMetadata.BYTE_FIELD:
			case DataFieldMetadata.STRING_FIELD:
				String value;
				switch (sid) {
				case NumberRecord.sid:
					value = formatter.formatNumberDateCell((NumberRecord) cellRecord, field.getMetadata().getLocaleStr());
					break;
				case FormulaRecord.sid:
					FormulaRecord frec = (FormulaRecord) cellRecord;
					switch (frec.getCachedResultType()) {
					case HSSFCell.CELL_TYPE_NUMERIC:
						value = formatter.formatNumberDateCell(frec, field.getMetadata().getLocaleStr());
						break;
					case HSSFCell.CELL_TYPE_BOOLEAN:
						value = String.valueOf(frec.getCachedBooleanValue());
						break;
					case HSSFCell.CELL_TYPE_ERROR:
						value = String.valueOf(frec.getCachedErrorValue());
						break;
					default: // HSSFCell.CELL_TYPE_BLANK
						value = "";
						break;
					}
					break;
				case LabelSSTRecord.sid:
					value = sstRecord.getString(((LabelSSTRecord) cellRecord).getSSTIndex()).getString();
					break;
				case StringRecord.sid:
					value = ((StringRecord) cellRecord).getString();
					break;
				case BoolErrRecord.sid:
					value = Boolean.toString(((BoolErrRecord) cellRecord).isBoolean());
					break;
				default:
					notifyExceptionHandler(cloverFieldIndex, field, cellRecord, "String");
					return;
				}

				field.fromString(value);
				break;
			case DataFieldMetadata.DECIMAL_FIELD:
			case DataFieldMetadata.INTEGER_FIELD:
			case DataFieldMetadata.LONG_FIELD:
			case DataFieldMetadata.NUMERIC_FIELD:
				double number;

				switch (sid) {
				case NumberRecord.sid:
					number = ((NumberRecord) cellRecord).getValue();
					break;
				case FormulaRecord.sid:
					number = ((FormulaRecord) cellRecord).getValue();
					break;
				case LabelSSTRecord.sid:
					try {
						number = Double.parseDouble(sstRecord.getString(((LabelSSTRecord) cellRecord).getSSTIndex()).getString());
						break;
					} catch (NumberFormatException e) {
						notifyExceptionHandler(cloverFieldIndex, field, cellRecord, "Number");
						return;
					}
				case StringRecord.sid:
					try {
						number = Double.parseDouble(((StringRecord) cellRecord).getString());
						break;
					} catch (NumberFormatException e) {
						notifyExceptionHandler(cloverFieldIndex, field, cellRecord, "Number");
						return;
					}
				default:
					notifyExceptionHandler(cloverFieldIndex, field, cellRecord, "Number");
					return;
				}

				field.setValue(number);				
				break;
			case DataFieldMetadata.BOOLEAN_FIELD:
				if (sid == BoolErrRecord.sid) {
					field.setValue(((BoolErrRecord) cellRecord).getBooleanValue());
				} else if (sid == FormulaRecord.sid) {
					FormulaRecord frec = (FormulaRecord) cellRecord;
					int resultType = frec.getCachedResultType();
					if (resultType == HSSFCell.CELL_TYPE_BOOLEAN) {
						field.setValue(frec.getCachedBooleanValue());
					}				
				} else {
					notifyExceptionHandler(cloverFieldIndex, field, cellRecord, "Boolean");
				}
				break;
			}
		}
		
		private void notifyExceptionHandler(int cloverFieldIndex, DataField field, Record record, String expectedType) {
			try {
				field.setNull(true);
			} catch (BadDataFormatException e) {
				// TODO maybe throw IllegalStateException here - do we require fields to be nullable or can they hold defaults?
			}
			int sid = record.getSid();
			String cellType;
			String actualValue;
			String cellFormat = null;
			switch(sid) {
			case NumberRecord.sid:
				cellType = "Numeric";
				actualValue = formatter.formatNumberDateCell((NumberRecord) record, field.getMetadata().getLocaleStr());
				cellFormat = formatter.getFormatString((NumberRecord) record);
				break;
			case FormulaRecord.sid:
				cellType = "Formula";
				FormulaRecord frec = (FormulaRecord) record;
				int fresultType = frec.getCachedResultType();
				// We don't check for STRING formulas here as these are always "translated" to StringRecord instead
				switch (fresultType) {
				case HSSFCell.CELL_TYPE_NUMERIC:
					actualValue = formatter.formatNumberDateCell(frec, field.getMetadata().getLocaleStr());
					cellFormat = formatter.getFormatString((NumberRecord) record);
					break;
				case HSSFCell.CELL_TYPE_BOOLEAN:
					actualValue = String.valueOf(frec.getCachedBooleanValue());
					break;
				case HSSFCell.CELL_TYPE_ERROR:
					actualValue = String.valueOf(frec.getCachedErrorValue());
					break;
				default: // HSSFCell.CELL_TYPE_BLANK
					actualValue = "";
					break;
				}
				break;
			case LabelSSTRecord.sid:
				cellType = "String";
				actualValue = sstRecord.getString(((LabelSSTRecord) record).getSSTIndex()).getString();
				break;
			case StringRecord.sid:
				cellType = "String";
				actualValue = ((StringRecord) record).getString();
				break;
			case BoolErrRecord.sid:
				cellType = "Boolean";
				BoolErrRecord boolErrRecord = (BoolErrRecord) record;
				if (boolErrRecord.isBoolean()) {
					actualValue = String.valueOf(boolErrRecord.getBooleanValue());
				} else {
					actualValue =  String.valueOf(boolErrRecord.getErrorValue());
				}
				break;
			default:
				cellType = "Unknown";
				actualValue = null;
				break;
			}
			CellRecord cellRecord = "Unknown".equals(cellType) ? null : (CellRecord) record;
			String cellCoordinates = record == null ? "Unknown" : SpreadsheetUtils.getColumnReference(cellRecord.getColumn()) + String.valueOf(cellRecord.getRow());
			parent.handleException(new SpreadsheetException("Cannot get " + expectedType + " value from cell of type " + cellType + " in " + cellCoordinates), 
					recordToFill, cloverFieldIndex, fileName, sheetName, cellCoordinates, actualValue, cellType, cellFormat);
		}

		
		private class FieldValueToFormatSetter implements RecordFieldValueSetter<Record> {

			@Override
			public void setFieldValue(int cloverFieldIndex, Record cellRecord) {
				DataField field = recordToFill.getField(cloverFieldIndex);
				String format = formatter.getFormatString((CellValueRecordInterface) cellRecord);
				field.fromString(format);
			}
		
		}
	}

	private static class HeaderHSSFListener implements HSSFListener {

		private SSTRecord sstRecord;

		private final int sheetIndex;
		private final int firstRow;
		private final int lastRow;
		private final int firstColumn;
		private final int lastColumn;

		private FormatTrackingHSSFListener formatter;

		private final List<List<String>> header;

		private int lastFilledColumn;
		private int currentParseSheet = -1;
		private int currentParseRow = 0;

		private boolean outputNextStringRecord = false;
		private int stringFormulaColumnIndex;

		private boolean finished = false;

		public HeaderHSSFListener(int sheetIndex, int firstRow, int firstColumn, int lastRow, int lastColumn) {
			this.sheetIndex = sheetIndex;
			this.firstRow = firstRow;
			this.lastRow = lastRow;
			this.firstColumn = firstColumn;
			this.lastColumn = lastColumn;

			this.header = new ArrayList<List<String>>(lastRow - firstRow);
			this.lastFilledColumn = firstColumn - 1;
		}

		public void setFormatter(FormatTrackingHSSFListener formatter) {
			this.formatter = formatter;
		}

		public boolean isFinished() {
			return finished;
		}

		public String[][] getHeader() {
			String[][] toReturn = new String[header.size()][];
			for (int i = 0; i < header.size(); i++) {
				toReturn[i] = header.get(i).toArray(new String[header.get(i).size()]);
			}
			return toReturn;
		}

		@Override
		public void processRecord(Record record) {
			short sid = record.getSid();

			if (sid == SSTRecord.sid) {
				// contains shared strings table
				sstRecord = (SSTRecord) record;
				return;
			}

			if (sid == BOFRecord.sid) {
				if (((BOFRecord) record).getType() == BOFRecord.TYPE_WORKSHEET) {
					currentParseSheet++;
					if (currentParseSheet > sheetIndex) {
						finished = true;
					}
				}
				return;
			}

			if (currentParseSheet < sheetIndex) {
				return;
			}
			
			if (record instanceof EndOfRowRecord) {
				currentParseRow++;
				if (currentParseRow >= lastRow) {
					finished = true;
				}
				if (currentParseRow > firstRow && lastFilledColumn < firstColumn) {
					List<String> emptyList = Collections.emptyList();
					header.add(emptyList);
				}
				lastFilledColumn = firstColumn - 1;
				return;
			}

			if (currentParseRow < firstRow) {
				return;
			}

			switch (sid) {
			case NumberRecord.sid:
				NumberRecord numrec = (NumberRecord) record;
				processCell(numrec.getColumn(), numrec);
				break;
			case LabelSSTRecord.sid:
				LabelSSTRecord lrec = (LabelSSTRecord) record;
				processCell(lrec.getColumn(), lrec);
				break;

			case FormulaRecord.sid:
				FormulaRecord frec = (FormulaRecord) record;
				if (frec.getCachedResultType() == HSSFCell.CELL_TYPE_STRING) {
					outputNextStringRecord = true;
					stringFormulaColumnIndex = frec.getColumn();
				} else {
					processCell(frec.getColumn(), frec);
				}
				break;
			case StringRecord.sid:
				if (outputNextStringRecord) { // String for formula
					StringRecord srec = (StringRecord) record;
					processCell(stringFormulaColumnIndex, srec);
					outputNextStringRecord = false;
				}
				break;

			case BoolErrRecord.sid:
				BoolErrRecord boolRecord = (BoolErrRecord) record;
				if (boolRecord.isBoolean()) {
					processCell(boolRecord.getColumn(), boolRecord);
				}
				break;
			}
		}

		public void processCell(int columnIndex, Record cellRecord) {
			if (columnIndex < firstColumn || columnIndex >= lastColumn) {
				return;
			}
			
			int headerRow = currentParseRow - firstRow;
			List<String> row;
			if (header.size() > headerRow) {
				row = header.get(headerRow);
			} else {
				row = new ArrayList<String>();
				header.add(headerRow, row);
			}

			for (int i = lastFilledColumn + 1; i < columnIndex; i++) {
				row.add(null);
			}
			
			lastFilledColumn = columnIndex;

			row.add(getStringValue(cellRecord));
		}

		private String getStringValue(Record record) {
			switch (record.getSid()) {
			case NumberRecord.sid:
				return formatter.formatNumberDateCell((NumberRecord) record);
			case FormulaRecord.sid:
				FormulaRecord frec = (FormulaRecord) record;
				int fresultType = frec.getCachedResultType();
				switch (fresultType) {
				case HSSFCell.CELL_TYPE_NUMERIC:
					return formatter.formatNumberDateCell(frec);
				case HSSFCell.CELL_TYPE_BOOLEAN:
					return String.valueOf(frec.getCachedBooleanValue());
				case HSSFCell.CELL_TYPE_ERROR:
					return String.valueOf(frec.getCachedErrorValue());
				default: // HSSFCell.CELL_TYPE_BLANK
					return "";
				}
			case LabelSSTRecord.sid:
				return sstRecord.getString(((LabelSSTRecord) record).getSSTIndex()).getString();
			case StringRecord.sid:
				return ((StringRecord) record).getString();
			case BoolErrRecord.sid:
				return Boolean.toString(((BoolErrRecord) record).isBoolean());
			default:
				return null;
				// throw new IllegalStateException("Cannot get String value from type " +
				// cellTypeToString(record.getSid()) + " cell");
			}
		}
	}

	private boolean processParsing(ParserFinishedFunctionPointer pointer) {
		Record recordToProcess = null;

		while (!pointer.finishedCondition()) {
			if (pointer.stopCondition()) {
				return false;
			}

			recordToProcess = recordFactory.nextRecord();
			if (recordToProcess == null) {
				return false;
			}

			if (ACCEPTED_RECORDS.contains(recordToProcess.getSid())) {
				formatter.processRecord(recordToProcess);
			} else if (recordToProcess.getSid() == EOFRecord.sid) {
				if (!rootEof) {
					rootEof = true;
				} else {
					if (recordFillingListener.isRequestedSheetSet()) {
						sheetEof = true;
						return false;
					}
				}
			}
		}

		return true;
	}

	/**
	 * RecordFactoryInputStream wrapper, which reports EndOfRow. Based on MissingRecordAwareHSSFListener.
	 * Note that MulRKRecords are already expanded by RecordFactoryInputStream. 
	 * 
	 * @author lkrejci (info@cloveretl.com)
	 *         (c) Javlin, a.s. (www.cloveretl.com)
	 *
	 */
	private static class MissingRecordAwareFactoryInputStream {
		private final RecordFactoryInputStream recordFactory;

		private int lastCellRow;
		private int lastCellColumn;
		private int endOfRowsCount;
		private Record recordToProcess = null;
		private boolean firstCell;

		public MissingRecordAwareFactoryInputStream(RecordFactoryInputStream recordFactory) {
			this.recordFactory = recordFactory;
		}

		public Record nextRecord() {
			Record toReturn = getNext();
			if (toReturn != null) {
				return toReturn;
			}

			prepareNext(recordFactory.nextRecord());

			return getNext();
		}

		private Record getNext() {
			Record toReturn = null;
			if (endOfRowsCount > 0) {
				toReturn = new EndOfRowRecord();
				endOfRowsCount--;
			} else if (recordToProcess != null) {
				toReturn = recordToProcess;
				recordToProcess = null;
			}
			return toReturn;
		}

		private void prepareNext(Record record) {
			if (record == null) {
				return;
			}

			int thisRow;
			int thisColumn;

			if (record instanceof CellValueRecordInterface) {
				CellValueRecordInterface valueRec = (CellValueRecordInterface) record;
				thisRow = valueRec.getRow();
				thisColumn = valueRec.getColumn();
			} else {
				thisRow = -1;
				thisColumn = -1;

				switch (record.getSid()) {
				// the BOFRecord can represent either the beginning of a sheet or the workbook
				case BOFRecord.sid:
					BOFRecord bof = (BOFRecord) record;
					if (bof.getType() == BOFRecord.TYPE_WORKBOOK || bof.getType() == BOFRecord.TYPE_WORKSHEET) {
						// Reset the row and column counts - new workbook / worksheet
						resetCounts();
					}
					break;

				case StringRecord.sid:
					// If the previous record was a FormulaRecord of type STRING, the resultant value is stored here.
					// We need to process the record to see if we expected this record or not.
					recordToProcess = record;
					return;
				case SharedFormulaRecord.sid:
					// SharedFormulaRecord occurs after the first FormulaRecord of the cell range.
					// There are probably (but not always) more cell records after this
					// - so don't fire off the EndOfRowRecord yet
					recordToProcess = record;
					return;
				case MulBlankRecord.sid:
					// These appear in the middle of the cell records, to
					// specify that the next bunch are empty but styled
					// Expand this out into multiple blank cells
					MulBlankRecord mbr = (MulBlankRecord) record;
					thisRow = mbr.getRow();
					thisColumn = mbr.getLastColumn();
					break;
				case NoteRecord.sid:
					NoteRecord nrec = (NoteRecord) record;
					thisRow = nrec.getRow();
					thisColumn = nrec.getColumn();
					break;
				}
			}
			recordToProcess = record;

			// If we're on cells, and this cell isn't in the same row as the last one,
			// then fire the dummy end-of-row records
			if (thisRow > lastCellRow) {
				if (lastCellRow > -1) {
					endOfRowsCount = thisRow - lastCellRow;
				} else if (firstCell) {
					endOfRowsCount = thisRow;
					firstCell = false;
				}
				
			}
//			if (thisRow > lastCellRow) {
//				endOfRowsCount = thisRow - (lastCellRow >= 0 ? lastCellRow : 0);
//			}

			// If we've just finished with the cells, then fire the final dummy end-of-row record
			if (lastCellRow != -1 && lastCellColumn != -1 && thisRow == -1) {
				endOfRowsCount++;
				lastCellRow = -1;
				lastCellColumn = -1;
			}

			// If we've moved onto a new row, the ensure we re-set the column counter
			if (thisRow != lastCellRow) {
				lastCellColumn = -1;
			}

			// Update cell and row counts as needed
			if (thisColumn != -1) {
				lastCellColumn = thisColumn;
				lastCellRow = thisRow;
			}
		}

		private void resetCounts() {
			lastCellRow = -1;
			lastCellColumn = -1;
			firstCell = true;
		}
	}

	private interface ParserFinishedFunctionPointer {
		/**
		 * @return true when parser should not process any more records job was completed
		 */
		public boolean finishedCondition();
		/**
		 * @return true when parser should not process any more records job and job is not completed 
		 */
		public boolean stopCondition();
	}

	private static class EndOfRowRecord extends Record {

		public static final short SID = -1;

		@Override
		public final short getSid() {
			return -1;
		}

		@Override
		public int serialize(int offset, byte[] data) {
			throw new RecordFormatException("Cannot serialize a dummy record");
		}

		@Override
		public final int getRecordSize() {
			throw new RecordFormatException("Cannot serialize a dummy record");
		}
	}
	
	
	public static class FormatTrackingHSSFListenerWithCustomFormatter extends FormatTrackingHSSFListener {
		
		private CellValueFormatter formatter = new CellValueFormatter();
		
		public FormatTrackingHSSFListenerWithCustomFormatter(HSSFListener childListener) {
			super(childListener);
		}
	
		/**
		 * Formats the given numeric of date Cell's contents as a String, in as
		 * close as we can to the way that Excel would do so. Uses the various
		 * format records to manage this.
		 *
		 * Copy of superclass formatNumberDateCell(CellValueRecordInterface) method and adjusted to use custom CellValueFormater.
		 */
		public String formatNumberDateCell(CellValueRecordInterface cell, String locale) {
			double value;
			if (cell instanceof NumberRecord) {
				value = ((NumberRecord) cell).getValue();
			} else if (cell instanceof FormulaRecord) {
				value = ((FormulaRecord) cell).getValue();
			} else {
				throw new IllegalArgumentException("Unsupported CellValue Record passed in " + cell);
			}

			// Get the built in format, if there is one
			int formatIndex = getFormatIndex(cell);
			String formatString = getFormatString(cell);

//			if (formatString == null) {
//				return _defaultFormat.format(value);
//			}

			return formatter.formatRawCellContents(value, formatIndex, formatString, locale);
		}
		
	}
	
}