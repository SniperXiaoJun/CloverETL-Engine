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
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormatSymbols;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetel.data.DataField;
import org.jetel.data.DataRecord;
import org.jetel.data.RecordKey;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.JetelException;
import org.jetel.exception.PolicyType;
import org.jetel.exception.SpreadsheetParserExceptionHandler;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.metadata.DataRecordMetadata;
import org.jetel.test.CloverTestCase;
import org.jetel.util.file.FileUtils;

/**
 * @author tkramolis (info@cloveretl.com)
 *         (c) Javlin, a.s. (www.cloveretl.com)
 *
 * @created 29 Jul 2011
 */
public class SpreadsheetParserTest extends CloverTestCase {
	
	private static final String XLS_FILE = "data/xls/Szinkron Comedy 2011 03v2.xls";
	private static final String XLSX_FILE = "data/xls/Szinkron Comedy 2011 03v2.xlsx";
	
	private DataRecordMetadata stringMetadata;
	private DataRecordMetadata metadata2;
	
	private String mapping1; 
	private String mapping2;
	private String mapping3;
	private String mapping4;
	private String mapping5;
	private String mapping6;
	private String mapping7;
	private String mapping8;
	private String mapping9;
	private boolean mappingsInitialized;
	
	private void initMappings() throws IOException {
		if (mappingsInitialized) {
			mappingsInitialized = false;
			return;
		}
		
		URL currentDir = new File(".").toURI().toURL();
		mapping1 = FileUtils.getStringFromURL(currentDir, "data/xls/mapping1_multirow.xlsx.xml", "UTF-8");
		mapping2 = FileUtils.getStringFromURL(currentDir, "data/xls/mapping2_multirow.xlsx.xml", "UTF-8");
		mapping3 = FileUtils.getStringFromURL(currentDir, "data/xls/mapping3_Customers_02.xls.xml", "UTF-8");
		mapping4 = FileUtils.getStringFromURL(currentDir, "data/xls/mapping4_Customers_03.xml", "UTF-8");
		mapping5 = FileUtils.getStringFromURL(currentDir, "data/xls/mapping5_Customers_03_Horizontal.xml", "UTF-8");
		mapping6 = FileUtils.getStringFromURL(currentDir, "data/xls/mapping6_excel-types-nocurrency.xml", "UTF-8");
		mapping7 = FileUtils.getStringFromURL(currentDir, "data/xls/mapping7_empty.xml", "UTF-8");
		mapping8 = FileUtils.getStringFromURL(currentDir, "data/xls/mapping8_excel-types-nocurrency.xml", "UTF-8");
		mapping9 = FileUtils.getStringFromURL(currentDir, "data/xls/mapping9_excel-types-nocurrency.xml", "UTF-8");
	}
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		initEngine();

		initMappings();
		
		String[] cloverFieldNames = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T"};
		
		stringMetadata = new DataRecordMetadata("md", DataRecordMetadata.DELIMITED_RECORD);
		for (int i = 0; i < cloverFieldNames.length; i++) {
			stringMetadata.addField(new DataFieldMetadata(cloverFieldNames[i], DataFieldMetadata.STRING_FIELD, ";"));
		}
		
		metadata2 = new DataRecordMetadata("m", DataRecordMetadata.DELIMITED_RECORD);
		metadata2.addField(new DataFieldMetadata("_1", DataFieldMetadata.INTEGER_FIELD, ";"));
		metadata2.addField(new DataFieldMetadata("_2", DataFieldMetadata.INTEGER_FIELD, ";"));
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		stringMetadata = null;
	}

	public void testSpreadsheetParsers() throws Exception {
		String sheetName = "0";
		
		/* Old ones */
		XLSParser xlsxParser = new XLSXDataParser(stringMetadata);
		xlsxParser.setSheetNumber(sheetName);
		xlsxParser.init();
		xlsxParser.preExecute();
		xlsxParser.setDataSource(new FileInputStream(XLSX_FILE));
//		xlsxParser.setExceptionHandler(new SpreadsheetParserExceptionHandler(PolicyType.LENIENT));

		XLSParser xlsParser = new JExcelXLSDataParser(stringMetadata);
		xlsParser.setSheetNumber(sheetName);
		xlsParser.init();
		xlsParser.preExecute();
		xlsParser.setDataSource(new FileInputStream(XLS_FILE));
		//xlsParser.setExceptionHandler(new SpreadsheetParserExceptionHandler(PolicyType.LENIENT));
		
		/* New ones */
		AbstractSpreadsheetParser xlsxStreamParser = new SpreadsheetStreamParser(stringMetadata, null, null);
		xlsxStreamParser.setSheet(sheetName);
		xlsxStreamParser.init();
		xlsxStreamParser.preExecute();
		xlsxStreamParser.setDataSource(new FileInputStream(XLSX_FILE));
		xlsxStreamParser.setExceptionHandler(new SpreadsheetParserExceptionHandler(PolicyType.LENIENT));
		
		AbstractSpreadsheetParser xlsStreamParser = new SpreadsheetStreamParser(stringMetadata, null, null);
		xlsStreamParser.setSheet(sheetName);
		xlsStreamParser.init();
		xlsStreamParser.preExecute();
		xlsStreamParser.setDataSource(new FileInputStream(XLS_FILE));
		xlsStreamParser.setExceptionHandler(new SpreadsheetParserExceptionHandler(PolicyType.LENIENT));
		
		AbstractSpreadsheetParser inMemoryParser1 = new SpreadsheetDOMParser(stringMetadata, null, null);
		inMemoryParser1.setSheet(sheetName);
		inMemoryParser1.init();
		inMemoryParser1.preExecute();
		inMemoryParser1.setDataSource(new FileInputStream(XLSX_FILE));
		inMemoryParser1.setExceptionHandler(new SpreadsheetParserExceptionHandler(PolicyType.LENIENT));
	
		AbstractSpreadsheetParser inMemoryParser2 = new SpreadsheetDOMParser(stringMetadata, null, null);
		inMemoryParser2.setSheet(sheetName);
		inMemoryParser2.init();
		inMemoryParser2.preExecute();
		inMemoryParser2.setDataSource(new FileInputStream(XLS_FILE));
		inMemoryParser2.setExceptionHandler(new SpreadsheetParserExceptionHandler(PolicyType.LENIENT));
		
		assertEqualOutput(stringMetadata, xlsParser, xlsxParser, xlsxStreamParser, xlsStreamParser, inMemoryParser1, inMemoryParser2);
	}
	
	private void assertEqualOutput(DataRecordMetadata metadata, Parser... parsers) throws JetelException, IOException, ComponentNotReadyException {
		RecordKey recordKey = new RecordKey(metadata.getFieldNamesArray(), metadata);
		recordKey.setEqualNULLs(true);
		recordKey.init();
		
		DataRecord[] records = new DataRecord[parsers.length];
		for (int i = 0; i < parsers.length; i++) {
			records[i] = new DataRecord(metadata);
			records[i].init();
		}
		
		boolean run = true;
		int recNum = 0;
		while (run) {
			for (int i = 0; i < parsers.length; i++) {
				DataRecord returnedRecord = parsers[i].getNext(records[i]);
				//System.out.println(returnedRecord);
				if (returnedRecord == null) {
					records[i] = null;
					run = false;
				}
			}

			for (int i = 0; i < parsers.length-1; i++) {
				assertTrue(
						"Unequal record #" + recNum + " (0-based)\n" +
						"Parser " + i + " (" + parsers[i].getClass().getSimpleName() + "):\n" + records[i] +
						"Parser " + (i+1) + " (" + parsers[i+1].getClass().getSimpleName() + "):\n" + records[i+1],
						recordKey.compare(records[i], records[i+1]) == 0);
			}
			recNum++;
		}

		List<Integer> eof = new ArrayList<Integer>();
		List<Integer> notEof = new ArrayList<Integer>();
		for (int i = 0; i < parsers.length; i++) {
			if (records[i] == null) {
				eof.add(i);
			} else {
				notEof.add(i);
			}
		}
		assertTrue("\nParsers with EOF: " + eof + "\n Parsers without EOF: " + notEof, notEof.isEmpty());
		
		for (int i = 0; i < parsers.length; i++) {
			parsers[i].postExecute();
			parsers[i].free();
		}
	}
	
	private List<AbstractSpreadsheetParser> prepareParsers(DataRecordMetadata metadata, XLSMapping mapping, String sheet,
			String inputFile) throws Exception {
		List<AbstractSpreadsheetParser> parsers = new ArrayList<AbstractSpreadsheetParser>();
		parsers.add(new SpreadsheetStreamParser(metadata, mapping, null));
		parsers.add(new SpreadsheetDOMParser(metadata, mapping, null));
		for (AbstractSpreadsheetParser spreadsheetParser : parsers) {
			spreadsheetParser.setSheet(sheet);
			spreadsheetParser.init();
			spreadsheetParser.preExecute();
			spreadsheetParser.setDataSource(new FileInputStream(inputFile));
		}
		return parsers;
	}
	
	private List<AbstractSpreadsheetParser> prepareParsers(DataRecordMetadata metadata, XLSMapping mapping, String sheet,
			String xlsFile, String xlsxFile) throws Exception {
		List<AbstractSpreadsheetParser> toReturn = new ArrayList<AbstractSpreadsheetParser>();
		toReturn.addAll(prepareParsers(metadata, mapping, sheet, xlsFile));
		toReturn.addAll(prepareParsers(metadata, mapping, sheet, xlsxFile));
		return toReturn;
	}

	public void testGetHeader() throws Exception {
		String[][] expected = new String[4][];
		expected[0] = new String[] {"Időszak", "2011. március"};
		expected[1] = new String[] {};
		expected[2] = new String[] {};
		expected[3] = new String[] {"Formátum", "Epizód darabszám", "Összes hossz", "Percdíj", "Össz ktg"};
		for (AbstractSpreadsheetParser parser : prepareParsers(stringMetadata, null, "0", XLS_FILE, XLSX_FILE)) {
			String[][] actual = parser.getHeader(6, 1, 10, 6);
			assertEqual(expected, actual, parser);
		}

		expected = new String[1][];
		expected[0] = new String[] {"Műsor címe", "Formátum", "Epizód darabszám", "Összes hossz"};
		for (AbstractSpreadsheetParser parser : prepareParsers(stringMetadata, null, "0", XLS_FILE, XLSX_FILE)) {
			String[][] actual = parser.getHeader(9, 0, 10, 4);
			assertEqual(expected, actual, parser);
		}
	}

	private void assertEqual(String[][] expected, String[][] actual, AbstractSpreadsheetParser parser) {
		assertEquals(parser.getClass().getSimpleName(), expected.length, actual.length);
		for (int i = 0; i < expected.length; i++) {
			String[] expectedRow = expected[i];
			String[] actualRow = actual[i];
			assertEquals(parser.getClass().getSimpleName() + " row " + i,
					expectedRow.length, actualRow.length);
			for (int j = 0; j < actualRow.length; j++) {
				assertEquals(parser.getClass().getSimpleName() + " row " + i + " column " + j,
						expectedRow[j], actualRow[j]);
			}
		}
	}
	
	public void testGetSheetNames() throws Exception {
		List<String> expected = Arrays.asList(new String[] {"Telj igaz Comedy", "Comedy"});
		for (AbstractSpreadsheetParser parser : prepareParsers(stringMetadata, null, "0", XLS_FILE, XLSX_FILE)) {
			assertEquals(parser.getClass().getSimpleName(), expected, parser.getSheetNames());
		}
	}
	
	public void testXlsxParsersWithMapping() throws Exception {
		AbstractSpreadsheetParser parser;
		for (int parserIndex = 0; parserIndex < 2; parserIndex++) {
//			System.out.println("Parser index: " + parserIndex);

			parser = getParser(parserIndex, stringMetadata, XLSMapping.parse(mapping1, stringMetadata, true, getParserType(parserIndex)));
			parser.setSheet("0");
			parser.init();
			parser.preExecute();
			parser.setDataSource(new FileInputStream("data/xls/multirow.xlsx")); // resolves name mapping
			
			SpreadsheetParserExceptionHandler exceptionHandler = new SpreadsheetParserExceptionHandler(PolicyType.LENIENT);
			parser.setExceptionHandler(exceptionHandler);
			
			DataRecord record = new DataRecord(stringMetadata);
			record.init();
			
			for (int i = 2; i <= 7; i++) {
				parser.parseNext(record);
//				System.out.println(record);
				assertRecordContent(record, "A"+i+"Value", "B"+i+"Value", "C"+(i+1)+"Value", i < 4 ? "D"+(i+1)+"Value" : null);
			}
			parser.parseNext(record);
			assertRecordContent(record, null, "B8Value", "C9Value");

			assertNull(parser.parseNext(record));
			
			parser.close();

			parser = getParser(parserIndex, stringMetadata, XLSMapping.parse(mapping2, stringMetadata, true, getParserType(parserIndex)));
			parser.setSheet("0");
			parser.init();
			parser.preExecute();
			parser.setDataSource(new FileInputStream("data/xls/multirow.xlsx"));
			
			exceptionHandler = new SpreadsheetParserExceptionHandler(PolicyType.LENIENT);
			parser.setExceptionHandler(exceptionHandler);
	
			parser.parseNext(record);
			assertRecordContent(record, "A5Value", "B5Value", "C7Value");
			parser.parseNext(record);
			assertRecordContent(record, "A7Value", "B7Value", "C9Value");

			assertNull(parser.parseNext(record));
			
			parser.close();
		}
	}

	public void testXlsParsersWithMapping() throws Exception {
		AbstractSpreadsheetParser parser;
		for (int parserIndex = 0; parserIndex < 2; parserIndex++) {
			parser = getParser(parserIndex, stringMetadata, XLSMapping.parse(mapping3, stringMetadata, true, getParserType(parserIndex)));

//			System.out.println("Parser index: " + parserIndex + ", type: " + parser.getClass().getSimpleName());
			
			parser.setSheet("0");
			parser.init();
			parser.preExecute();
			parser.setDataSource(new FileInputStream("data/xls/Customers_02.xls"));
			
			SpreadsheetParserExceptionHandler exceptionHandler = new SpreadsheetParserExceptionHandler(PolicyType.LENIENT);
			parser.setExceptionHandler(exceptionHandler);
			
			DataRecord record = new DataRecord(stringMetadata);
			record.init();
			
			parser.parseNext(record);
			assertRecordContent(record, "12", "New York", "6th Avenue");
			parser.parseNext(record);
			assertRecordContent(record, "23", "London", "Baker Street");
			parser.parseNext(record);
			assertRecordContent(record, "123", "Princeton", "Dirac Street");
			parser.parseNext(record);
			assertRecordContent(record, "1234", "Washington", "Constitution Ave");
			
			assertNull(parser.parseNext(record));
			
			parser.close();
		}
	}
	
	public void testParsersWithMoreComplexVerticalMapping() throws Exception {
		testParsersWithMoreComplexMapping("data/xls/Customers_03.xls", mapping4, false);
	}
	
	public void testParsersWithMoreComplexHorizontalMapping() throws Exception {
		testParsersWithMoreComplexMapping("data/xls/Customers_03_Horizontal.xls", mapping5, true);
	}
	
	private void testParsersWithMoreComplexMapping(String xlsFile, String mapping, boolean excludeStreamParser) throws Exception {
		AbstractSpreadsheetParser parser;
		for (int inputSourceType = 0; inputSourceType < 2; inputSourceType++) {
			for (int skip = 0; skip < 3; skip++) {
				for (String file : Arrays.asList(xlsFile, xlsFile+"x")) {
					for (int parserIndex = 0; parserIndex < 2; parserIndex++) {
						parser = getParser(parserIndex, stringMetadata, XLSMapping.parse(mapping, stringMetadata, true, getParserType(parserIndex)));
						if (parser instanceof SpreadsheetStreamParser && excludeStreamParser) {
							continue;
						}
			
						//System.out.println("File: " + file + ", Parser type: " + parser.getClass().getSimpleName() + ", Skip: " + skip);
						
						parser.setSheet("0");
						parser.init();
						parser.preExecute();
						parser.setDataSource(inputSourceType == 0 ? new FileInputStream(file) : new File(file));
						
						SpreadsheetParserExceptionHandler exceptionHandler = new SpreadsheetParserExceptionHandler(PolicyType.LENIENT);
						parser.setExceptionHandler(exceptionHandler);
						
						DataRecord record = new DataRecord(stringMetadata);
						record.init();
						
						parser.skip(skip);
						
						if (skip <= 0) {
							parser.parseNext(record);
							assertRecordContent(record, "12", "New York", "NY, US", "6th Avenue", "Spam");
						}
						if (skip <= 1) {
							parser.parseNext(record);
							assertRecordContent(record, "23", "London", "UK", "Baker Street", "Ham");
						}
						if (skip <= 2) {
							parser.parseNext(record);
							assertRecordContent(record, "123", "Princeton", "NJ, US", "Dirac Street", "Eggs");
						}
						parser.parseNext(record);
						assertRecordContent(record, "1234", "Washington", "US", "Constitution Ave", "More spam");
						parser.parseNext(record);
						assertRecordContent(record, "12345", "Boston", "MA, US", "Cambridge St", "Spam & eggs");
						assertNull(parser.parseNext(record));
						
						parser.close();
						
						// !!! TODO !!! input file closed test
					}
				}
			}
		}
	}
	
	private AbstractSpreadsheetParser getParser(int parserIndex, DataRecordMetadata metadata, XLSMapping mappingInfo) {
		switch (parserIndex) {
			case 0: return new SpreadsheetStreamParser(metadata, mappingInfo, null);
			case 1: return new SpreadsheetDOMParser(metadata, mappingInfo, null);
		}
		throw new IllegalArgumentException("parserIndex");
	}
	
	public String getParserType(int parserIndex) {
		switch (parserIndex) {
		case 0: return "Stream parser:";
		case 1: return "In-mem parser:";
		}
		return null;
	}
	
	private static void assertRecordContent(DataRecord record, Object... fields) {
		for (int i = 0; i < fields.length; i++) {
			DataField field = record.getField(i);
			DataField expectedField = field.duplicate();
			expectedField.setValue(fields[i]);
			if (!field.isNull()) {
				assertEquals("Unexpected value in field \"" + field.getMetadata().getName() + "\":", expectedField, field);
			} else {
				assertNull("Field \""+ field.getMetadata().getName() + "\" is null, expected value: " + fields[i], fields[i]);
			}
		}
	}
	
	
	public void testCellFormatReading() throws Exception {
		AbstractSpreadsheetParser parser;
		String xlsFile = "data/xls/excel-types-nocurrency.xls";
		for (String file : Arrays.asList(xlsFile, xlsFile+"x")) {
			for (int parserIndex = 0; parserIndex < 2; parserIndex++) {
				parser = getParser(parserIndex, stringMetadata, XLSMapping.parse(mapping8, stringMetadata, true, getParserType(parserIndex)));
	
				//System.out.println("File: " + file + ", Parser type: " + parser.getClass().getSimpleName());
				
				parser.setSheet("0");
				parser.init();
				parser.preExecute();
				parser.setDataSource(new FileInputStream(file));
				
				SpreadsheetParserExceptionHandler exceptionHandler = new SpreadsheetParserExceptionHandler(PolicyType.LENIENT);
				parser.setExceptionHandler(exceptionHandler);
				
				DataRecord record = new DataRecord(stringMetadata);
				record.init();
				
				String general = "GENERAL";
				String number = "#,##0.00";
				String currency = "_-* #,##0.00\" Kč\"_-;\\-* #,##0.00\" Kč\"_-;_-* \\-??\" Kč\"_-;_-@_-";
				String date = "D\". \"MMMM\\ YYYY;@";
				String time = "H:MM:SS\\ AM/PM;@";
				String percent = "0.00%";
				String fraction = "#\\ ?/2";
				String math = "0.00E+00";
				String text = "@";
				String special = "[<=9999999]###\\ ##\\ ##;##\\ ##\\ ##\\ ##";
				
				DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols();
				char gs = decimalSymbols.getGroupingSeparator();
				char ds = decimalSymbols.getDecimalSeparator();
				DateFormatSymbols dateSymbols = new DateFormatSymbols();
				String june = dateSymbols.getMonths()[5];
				String january = dateSymbols.getMonths()[0];
				
				assertNotNull(parser.parseNext(record));
				//System.out.println(record);
				assertRecordContent(record, "něco", general, "56"+gs+"895"+ds+"00", number, "56"+ds+"00 Kč", currency, "5\". \""+june+" 2005", date, "5:30:00 AM", time, "10"+ds+"00%", percent, "1"+ds+"00", fraction, "5"+ds+"00E+00", math, "text", text, "sd", special);

				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, "další", general, "1"+ds+"20", number, "-852"+ds+"00 Kč", currency, "1\". \""+january+" 1970", date, "11:12:00 PM", time, "5"+ds+"80%", percent, "1"+ds+"50", fraction, "1"+ds+"00E+01", math, "neoc", text, "sgwrert", special);
				
				assertNotNull(parser.parseNext(record));
				
				for (int i = 3; i < 10; i++) {
					assertNotNull(parser.parseNext(record));
				}
				
				assertNull(parser.parseNext(record));
				
				parser.close();
			}
		}
	}
	
	public void testCellFormatReadingWithStrangeMapping() throws Exception {
		AbstractSpreadsheetParser parser;
		String xlsFile = "data/xls/excel-types-nocurrency.xls";
		for (String file : Arrays.asList(xlsFile, xlsFile+"x")) {
			for (int parserIndex = 0; parserIndex < 2; parserIndex++) {
				parser = getParser(parserIndex, stringMetadata, XLSMapping.parse(mapping6, stringMetadata, true, getParserType(parserIndex)));
	
				//System.out.println("File: " + file + ", Parser type: " + parser.getClass().getSimpleName());
				
				parser.setSheet("0");
				parser.init();
				parser.preExecute();
				parser.setDataSource(new FileInputStream(file));
				
				SpreadsheetParserExceptionHandler exceptionHandler = new SpreadsheetParserExceptionHandler(PolicyType.LENIENT);
				parser.setExceptionHandler(exceptionHandler);
				
				DataRecord record = new DataRecord(stringMetadata);
				record.init();
				
				String general = "GENERAL";
				String text = "@";
				
				parser.parseNext(record);
				//System.out.println(record);
				assertRecordContent(record, "něco", general, null, null, null, null, null, null, null, "text", text, null);

				parser.parseNext(record);
				assertRecordContent(record, "další", general, null, null, null, null, null, null, null, "neoc", text, null);
				
				parser.parseNext(record);
				assertRecordContent(record, null, null, null, null, null, null, null, null, null, "dále", text, null);
				
				for (int i = 3; i < 10; i++) {
					assertNotNull(parser.parseNext(record));
				}
				
				// Oh yeah! Correct empty record, because none of its cell is beyond end of sheet.
				parser.parseNext(record);
				assertTrue(record.isNull());
				
				assertNull(parser.parseNext(record));
				
				parser.close();
			}
		}
	}
	
	public void testSheetWithEmptyRows() throws Exception {
		AbstractSpreadsheetParser parser;
		String xlsFile = "data/xls/empty.xls";
		for (String file : Arrays.asList(xlsFile, xlsFile+"x")) {
			for (int parserIndex = 0; parserIndex < 2; parserIndex++) {
				parser = getParser(parserIndex, metadata2, XLSMapping.parse(mapping7, metadata2, true, getParserType(parserIndex)));
		
				//System.out.println("File: " + file + ", Parser type: " + parser.getClass().getSimpleName());
				
				parser.setSheet("0");
				parser.init();
				parser.preExecute();
				parser.setDataSource(new FileInputStream(file));
				
				SpreadsheetParserExceptionHandler exceptionHandler = new SpreadsheetParserExceptionHandler(PolicyType.LENIENT);
				parser.setExceptionHandler(exceptionHandler);
				
				DataRecord record = new DataRecord(metadata2);
				record.init();
				
				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, null, 2);

				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, null, null);

				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, null, null);
				
				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, 1, 2);
				
				assertFalse(exceptionHandler.isExceptionThrowed());
				
				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, 1, null);
				
				// last record is half beyond the end of sheet
				assertTrue(exceptionHandler.isExceptionThrowed());

				assertNull(parser.parseNext(record));
				
				parser.close();
			}
		}
	}
	
	public void testRecordsPartiallyBeyondEndOfSheet() throws Exception {
		AbstractSpreadsheetParser parser;
		String xlsFile = "data/xls/excel-types-nocurrency.xls";
		for (String file : Arrays.asList(xlsFile, xlsFile+"x")) {
			for (int parserIndex = 0; parserIndex < 2; parserIndex++) {
				parser = getParser(parserIndex, stringMetadata, XLSMapping.parse(mapping9, stringMetadata, true, getParserType(parserIndex)));
	
				//System.out.println("File: " + file + ", Parser type: " + parser.getClass().getSimpleName());
				
				parser.setSheet("0");
				parser.init();
				parser.preExecute();
				parser.setDataSource(new FileInputStream(file));
				
				SpreadsheetParserExceptionHandler exceptionHandler = new SpreadsheetParserExceptionHandler(PolicyType.LENIENT);
				parser.setExceptionHandler(exceptionHandler);
				
				DataRecord record = new DataRecord(stringMetadata);
				record.init();
				
				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, "něco", "2.00%");

				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, "další", "1.00%");
				assertFalse(exceptionHandler.isExceptionThrowed());
				
				assertNotNull(parser.parseNext(record));
				assertTrue(record.isNull());
				assertTrue(exceptionHandler.isExceptionThrowed());

				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, "bž", null);
				
				for (int i = 0; i < 5; i++) {
					assertNotNull(parser.parseNext(record));
				}
				
				assertNotNull(parser.parseNext(record));
				assertRecordContent(record, "ireu tiorewitopoehj goifdsgoeůhg eiognboienhgoienpo", null);
				
				assertNull(parser.parseNext(record));
				
				parser.close();
			}
		}
	}
	
}