package org.jetel.ctl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import junit.framework.AssertionFailedError;

import org.jetel.component.CTLRecordTransform;
import org.jetel.component.RecordTransform;
import org.jetel.data.DataRecord;
import org.jetel.data.SetVal;
import org.jetel.data.lookup.LookupTable;
import org.jetel.data.lookup.LookupTableFactory;
import org.jetel.data.sequence.Sequence;
import org.jetel.data.sequence.SequenceFactory;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.ConfigurationStatus;
import org.jetel.exception.TransformException;
import org.jetel.graph.TransformationGraph;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.metadata.DataRecordMetadata;
import org.jetel.test.CloverTestCase;
import org.jetel.util.MiscUtils;
import org.jetel.util.bytes.PackedDecimal;
import org.jetel.util.crypto.Base64;
import org.jetel.util.crypto.Digest;
import org.jetel.util.crypto.Digest.DigestType;
import org.jetel.util.primitive.TypedProperties;
import org.jetel.util.string.StringUtils;

public abstract class CompilerTestCase extends CloverTestCase {

	// ---------- RECORD NAMES -----------
	protected static final String INPUT_1 = "firstInput";
	protected static final String INPUT_2 = "secondInput";
	protected static final String INPUT_3 = "thirdInput";
	protected static final String OUTPUT_1 = "firstOutput";
	protected static final String OUTPUT_2 = "secondOutput";
	protected static final String OUTPUT_3 = "thirdOutput";
	protected static final String OUTPUT_4 = "fourthOutput";
	protected static final String LOOKUP = "lookupMetadata";

	protected static final String NAME_VALUE = "  HELLO  ";
	protected static final Double AGE_VALUE = 20.25;
	protected static final String CITY_VALUE = "Chong'La";

	protected static final Date BORN_VALUE;
	protected static final Long BORN_MILLISEC_VALUE;
	static {
		Calendar c = Calendar.getInstance();
		c.set(2008, 12, 25, 13, 25, 55);
		BORN_VALUE = c.getTime();
		BORN_MILLISEC_VALUE = c.getTimeInMillis();
	}

	protected static final Integer VALUE_VALUE = Integer.MAX_VALUE - 10;
	protected static final Boolean FLAG_VALUE = true;
	protected static final byte[] BYTEARRAY_VALUE = "Abeceda zedla deda".getBytes();

	protected static final BigDecimal CURRENCY_VALUE = new BigDecimal("133.525");
	protected static final int DECIMAL_PRECISION = 7;
	protected static final int DECIMAL_SCALE = 3;
	protected static final int NORMALIZE_RETURN_OK = 0;
	
	public static final int DECIMAL_MAX_PRECISION = 32;
	public static final MathContext MAX_PRECISION = new MathContext(DECIMAL_MAX_PRECISION,RoundingMode.DOWN);

	/** Flag to trigger Java compilation */
	private boolean compileToJava;
	
	protected DataRecord[] inputRecords;
	protected DataRecord[] outputRecords;
	
	protected TransformationGraph graph;

	public CompilerTestCase(boolean compileToJava) {
		this.compileToJava = compileToJava;
	}

	/**
	 * Method to execute tested CTL code in a way specific to testing scenario.
	 * 
	 * @param compiler
	 */
	public abstract void executeCode(ITLCompiler compiler);

	/**
	 * Method which provides access to specified global variable
	 * 
	 * @param varName
	 *            global variable to be accessed
	 * @return
	 * 
	 */
	protected abstract Object getVariable(String varName);

	protected void check(String varName, Object expectedResult) {
		assertEquals(varName, expectedResult, getVariable(varName));
	}
	
	protected void checkEquals(String varName1, String varName2) {
		assertEquals("Comparing " + varName1 + " and " + varName2 + " : ", getVariable(varName1), getVariable(varName2));
	}
	
	protected void checkNull(String varName) {
		assertNull(getVariable(varName));
	}

	private void checkArray(String varName, byte[] expected) {
		byte[] actual = (byte[]) getVariable(varName);
		assertTrue("Arrays do not match; expected: " + byteArrayAsString(expected) + " but was " + byteArrayAsString(actual), Arrays.equals(actual, expected));
	}

	private static String byteArrayAsString(byte[] array) {
		final StringBuilder sb = new StringBuilder("[");
		for (final byte b : array) {
			sb.append(b);
			sb.append(", ");
		}
		sb.delete(sb.length() - 2, sb.length());
		sb.append(']');
		return sb.toString();
	}

	protected void setUp() {
		// set default locale to English to prevent various parsing errors
		Locale.setDefault(Locale.ENGLISH);
		initEngine();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		inputRecords = null;
		outputRecords = null;
		graph = null;
	}

	protected TransformationGraph createEmptyGraph() {
		return new TransformationGraph();
	}

	protected TransformationGraph createDefaultGraph() {
		TransformationGraph g = createEmptyGraph();
		final HashMap<String, DataRecordMetadata> metadataMap = new HashMap<String, DataRecordMetadata>();
		metadataMap.put(INPUT_1, createDefaultMetadata(INPUT_1));
		metadataMap.put(INPUT_2, createDefaultMetadata(INPUT_2));
		metadataMap.put(INPUT_3, createDefaultMetadata(INPUT_3));
		metadataMap.put(OUTPUT_1, createDefaultMetadata(OUTPUT_1));
		metadataMap.put(OUTPUT_2, createDefaultMetadata(OUTPUT_2));
		metadataMap.put(OUTPUT_3, createDefaultMetadata(OUTPUT_3));
		metadataMap.put(OUTPUT_4, createDefault1Metadata(OUTPUT_4));
		metadataMap.put(LOOKUP, createDefaultMetadata(LOOKUP));
		g.addDataRecordMetadata(metadataMap);
		g.addSequence(createDefaultSequence(g, "TestSequence"));
		g.addLookupTable(createDefaultLookup(g, "TestLookup"));
		initDefaultDictionary(g);
		return g;
	}

	private void initDefaultDictionary(TransformationGraph g) {
		try {
			g.getDictionary().init();
			g.getDictionary().setValue("s", "string", null);
			g.getDictionary().setValue("i", "integer", null);
			g.getDictionary().setValue("l", "long", null);
			g.getDictionary().setValue("d", "decimal", null);
			g.getDictionary().setValue("n", "number", null);
			g.getDictionary().setValue("a", "date", null);
			g.getDictionary().setValue("b", "boolean", null);
			g.getDictionary().setValue("y", "byte", null);
			g.getDictionary().setValue("i211", "integer", new Integer(211));
			g.getDictionary().setValue("sVerdon", "string", "Verdon");
			g.getDictionary().setValue("l452", "long", new Long(452));
			g.getDictionary().setValue("d621", "decimal", new BigDecimal(621));
			g.getDictionary().setValue("n9342", "number", new Double(934.2));
			g.getDictionary().setValue("a1992", "date", new GregorianCalendar(1992, GregorianCalendar.AUGUST, 1).getTime());
			g.getDictionary().setValue("bTrue", "boolean", Boolean.TRUE);
			g.getDictionary().setValue("yFib", "byte", new byte[]{1,2,3,5,8,13,21,34,55,89} );
		} catch (ComponentNotReadyException e) {
			throw new RuntimeException("Error init default dictionary", e);
		}
		
	}

	protected Sequence createDefaultSequence(TransformationGraph graph, String name) {
		Sequence seq = SequenceFactory.createSequence(graph, "PRIMITIVE_SEQUENCE", new Object[] { "Sequence0", graph, name }, new Class[] { String.class, TransformationGraph.class, String.class });

		try {
			seq.checkConfig(new ConfigurationStatus());
			seq.init();
		} catch (ComponentNotReadyException e) {
			throw new RuntimeException(e);
		}

		return seq;
	}

	/**
	 * Creates default lookup table of type SimpleLookupTable with 4 records using default metadata and a composite
	 * lookup key Name+Value. Use field City for testing response.
	 * 
	 * @param graph
	 * @param name
	 * @return
	 */
	protected LookupTable createDefaultLookup(TransformationGraph graph, String name) {
		final TypedProperties props = new TypedProperties();
		props.setProperty("id", "LookupTable0");
		props.setProperty("type", "simpleLookup");
		props.setProperty("metadata", LOOKUP);
		props.setProperty("key", "Name;Value");
		props.setProperty("name", name);
		props.setProperty("keyDuplicates", "true");

		/*
		 * The test lookup table is populated from file TestLookup.dat. Alternatively uncomment the populating code
		 * below, however this will most probably break down test_lookup() because free() will wipe away all data and
		 * noone will restore them
		 */
		URL dataFile = getClass().getSuperclass().getResource("TestLookup.dat");
		if (dataFile == null) {
			throw new RuntimeException("Unable to populate testing lookup table. File 'TestLookup.dat' not found by classloader");
		}
		props.setProperty("fileURL", dataFile.getFile());

		LookupTableFactory.init();
		LookupTable lkp = LookupTableFactory.createLookupTable(props);
		lkp.setGraph(graph);

		try {
			lkp.checkConfig(new ConfigurationStatus());
			lkp.init();
			lkp.preExecute();
		} catch (ComponentNotReadyException ex) {
			throw new RuntimeException(ex);
		}

		// ********* POPULATING CODE *************
		/*DataRecord lkpRecord = createEmptyRecord(createDefaultMetadata("lookupResponse"));

		lkpRecord.getField("Name").setValue("Alpha");
		lkpRecord.getField("Value").setValue(1);
		lkpRecord.getField("City").setValue("Andorra la Vella");
		lkp.put(lkpRecord);

		lkpRecord.getField("Name").setValue("Bravo");
		lkpRecord.getField("Value").setValue(2);
		lkpRecord.getField("City").setValue("Bruxelles");
		lkp.put(lkpRecord);

		// duplicate entry 
		lkpRecord.getField("Name").setValue("Charlie");
		lkpRecord.getField("Value").setValue(3);
		lkpRecord.getField("City").setValue("Chamonix");
		lkp.put(lkpRecord);
		lkpRecord.getField("Name").setValue("Charlie");
		lkpRecord.getField("Value").setValue(3);
		lkpRecord.getField("City").setValue("Chomutov");
		lkp.put(lkpRecord);*/

		// ************ END OF POPULATING CODE ************
		 
		return lkp;
	}

	/**
	 * Creates records with default structure
	 * 
	 * @param name
	 *            name for the record to use
	 * @return metadata with default structure
	 */
	protected DataRecordMetadata createDefaultMetadata(String name) {
		DataRecordMetadata ret = new DataRecordMetadata(name);
		ret.addField(new DataFieldMetadata("Name", DataFieldMetadata.STRING_FIELD, "|"));
		ret.addField(new DataFieldMetadata("Age", DataFieldMetadata.NUMERIC_FIELD, "|"));
		ret.addField(new DataFieldMetadata("City", DataFieldMetadata.STRING_FIELD, "|"));

		DataFieldMetadata dateField = new DataFieldMetadata("Born", DataFieldMetadata.DATE_FIELD, "|");
		dateField.setFormatStr("yyyy-MM-dd HH:mm:ss");
		ret.addField(dateField);

		ret.addField(new DataFieldMetadata("BornMillisec", DataFieldMetadata.LONG_FIELD, "|"));
		ret.addField(new DataFieldMetadata("Value", DataFieldMetadata.INTEGER_FIELD, "|"));
		ret.addField(new DataFieldMetadata("Flag", DataFieldMetadata.BOOLEAN_FIELD, "|"));
		ret.addField(new DataFieldMetadata("ByteArray", DataFieldMetadata.BYTE_FIELD, "|"));

		DataFieldMetadata decimalField = new DataFieldMetadata("Currency", DataFieldMetadata.DECIMAL_FIELD, "\n");
		decimalField.setProperty(DataFieldMetadata.LENGTH_ATTR, String.valueOf(DECIMAL_PRECISION));
		decimalField.setProperty(DataFieldMetadata.SCALE_ATTR, String.valueOf(DECIMAL_SCALE));
		ret.addField(decimalField);

		return ret;
	}

	/**
	 * Creates records with default structure
	 * 
	 * @param name
	 *            name for the record to use
	 * @return metadata with default structure
	 */
	protected DataRecordMetadata createDefault1Metadata(String name) {
		DataRecordMetadata ret = new DataRecordMetadata(name);
		ret.addField(new DataFieldMetadata("Field1", DataFieldMetadata.STRING_FIELD, "|"));
		ret.addField(new DataFieldMetadata("Age", DataFieldMetadata.NUMERIC_FIELD, "|"));
		ret.addField(new DataFieldMetadata("City", DataFieldMetadata.STRING_FIELD, "|"));

		return ret;
	}

	/**
	 * Creates new record with specified metadata and sets its field to default values. The record structure will be
	 * created by {@link #createDefaultMetadata(String)}
	 * 
	 * @param dataRecordMetadata
	 *            metadata to use
	 * @return record initialized to default values
	 */
	protected DataRecord createDefaultRecord(DataRecordMetadata dataRecordMetadata) {
		final DataRecord ret = new DataRecord(dataRecordMetadata);
		ret.init();

		SetVal.setString(ret, "Name", NAME_VALUE);
		SetVal.setDouble(ret, "Age", AGE_VALUE);
		SetVal.setString(ret, "City", CITY_VALUE);
		SetVal.setDate(ret, "Born", BORN_VALUE);
		SetVal.setLong(ret, "BornMillisec", BORN_MILLISEC_VALUE);
		SetVal.setInt(ret, "Value", VALUE_VALUE);
		SetVal.setValue(ret, "Flag", FLAG_VALUE);
		SetVal.setValue(ret, "ByteArray", BYTEARRAY_VALUE);
		SetVal.setValue(ret, "Currency", CURRENCY_VALUE);

		return ret;
	}

	/**
	 * Allocates new records with structure prescribed by metadata and sets all its fields to <code>null</code>
	 * 
	 * @param metadata
	 *            structure to use
	 * @return empty record
	 */
	protected DataRecord createEmptyRecord(DataRecordMetadata metadata) {
		DataRecord ret = new DataRecord(metadata);
		ret.init();

		for (int i = 0; i < ret.getNumFields(); i++) {
			SetVal.setNull(ret, i);
		}

		return ret;
	}

	protected void doCompile(String expStr, String testIdentifier) {
		graph = createDefaultGraph();
		DataRecordMetadata[] inMetadata = new DataRecordMetadata[] { graph.getDataRecordMetadata(INPUT_1), graph.getDataRecordMetadata(INPUT_2), graph.getDataRecordMetadata(INPUT_3) };
		DataRecordMetadata[] outMetadata = new DataRecordMetadata[] { graph.getDataRecordMetadata(OUTPUT_1), graph.getDataRecordMetadata(OUTPUT_2), graph.getDataRecordMetadata(OUTPUT_3), graph.getDataRecordMetadata(OUTPUT_4) };

		// prepend the compilation mode prefix
		if (compileToJava) {
			expStr = "//#CTL2:COMPILE\n" + expStr;
		}

		print_code(expStr);

		ITLCompiler compiler = TLCompilerFactory.createCompiler(graph, inMetadata, outMetadata, "UTF-8");
		// *** NOTE: please don't remove this commented code. It is used for debugging
		// ***       Uncomment the code to get the compiled Java code during test execution.
		// ***       Please don't commit this code uncommited.
	
//		try {
//			System.out.println(compiler.convertToJava(expStr, CTLRecordTransform.class, testIdentifier));
//		} catch (ErrorMessageException e) {
//			System.out.println("Error parsing CTL code. Unable to output Java translation.");
//		}
		
		List<ErrorMessage> messages = compiler.compile(expStr, CTLRecordTransform.class, testIdentifier);
		printMessages(messages);
		if (compiler.errorCount() > 0) {
			throw new AssertionFailedError("Error in execution. Check standard output for details.");
		}

		// *** NOTE: please don't remove this commented code. It is used for debugging
		// ***       Uncomment the code to get the compiled Java code during test execution.
		// ***       Please don't commit this code uncommited.

//		CLVFStart parseTree = compiler.getStart();
//		parseTree.dump("");


		executeCode(compiler);
	}

	protected void doCompileExpectError(String expStr, String testIdentifier, List<String> errCodes) {
		graph = createDefaultGraph();
		DataRecordMetadata[] inMetadata = new DataRecordMetadata[] { graph.getDataRecordMetadata(INPUT_1), graph.getDataRecordMetadata(INPUT_2), graph.getDataRecordMetadata(INPUT_3) };
		DataRecordMetadata[] outMetadata = new DataRecordMetadata[] { graph.getDataRecordMetadata(OUTPUT_1), graph.getDataRecordMetadata(OUTPUT_2), graph.getDataRecordMetadata(OUTPUT_3), graph.getDataRecordMetadata(OUTPUT_4) };

		// prepend the compilation mode prefix
		if (compileToJava) {
			expStr = "//#CTL2:COMPILE\n" + expStr;
		}

		print_code(expStr);

		ITLCompiler compiler = TLCompilerFactory.createCompiler(graph, inMetadata, outMetadata, "UTF-8");
		List<ErrorMessage> messages = compiler.compile(expStr, CTLRecordTransform.class, testIdentifier);
		printMessages(messages);

		if (compiler.errorCount() == 0) {
			throw new AssertionFailedError("No errors in parsing. Expected " + errCodes.size() + " errors.");
		}

		if (compiler.errorCount() != errCodes.size()) {
			throw new AssertionFailedError(compiler.errorCount() + " errors in code, but expected " + errCodes.size() + " errors.");
		}

		Iterator<String> it = errCodes.iterator();

		for (ErrorMessage errorMessage : compiler.getDiagnosticMessages()) {
			String expectedError = it.next();
			if (!expectedError.equals(errorMessage.getErrorMessage())) {
				throw new AssertionFailedError("Error : \'" + compiler.getDiagnosticMessages().get(0).getErrorMessage() + "\', but expected: \'" + expectedError + "\'");
			}
		}

		// CLVFStart parseTree = compiler.getStart();
		// parseTree.dump("");

		// executeCode(compiler);
	}

	protected void doCompileExpectError(String testIdentifier, String errCode) {
		doCompileExpectErrors(testIdentifier, Arrays.asList(errCode));
	}

	protected void doCompileExpectErrors(String testIdentifier, List<String> errCodes) {
		URL importLoc = CompilerTestCase.class.getResource(testIdentifier + ".ctl");
		if (importLoc == null) {
			throw new RuntimeException("Test case '" + testIdentifier + ".ctl" + "' not found");
		}

		final StringBuilder sourceCode = new StringBuilder();
		String line = null;
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(importLoc.openStream()));
			while ((line = rd.readLine()) != null) {
				sourceCode.append(line).append("\n");
			}
			rd.close();
		} catch (IOException e) {
			throw new RuntimeException("I/O error occured when reading source file", e);
		}

		doCompileExpectError(sourceCode.toString(), testIdentifier, errCodes);
	}

	/**
	 * Method loads tested CTL code from a file with the name <code>testIdentifier.ctl</code> The CTL code files should
	 * be stored in the same directory as this class.
	 * 
	 * @param Test
	 *            identifier defining CTL file to load code from
	 */
	protected void doCompile(String testIdentifier) {
		URL importLoc = CompilerTestCase.class.getResource(testIdentifier + ".ctl");
		if (importLoc == null) {
			throw new RuntimeException("Test case '" + testIdentifier + ".ctl" + "' not found");
		}

		final StringBuilder sourceCode = new StringBuilder();
		String line = null;
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(importLoc.openStream()));
			while ((line = rd.readLine()) != null) {
				sourceCode.append(line).append("\n");
			}
			rd.close();
		} catch (IOException e) {
			throw new RuntimeException("I/O error occured when reading source file", e);
		}

		doCompile(sourceCode.toString(), testIdentifier);
	}

	protected void printMessages(List<ErrorMessage> diagnosticMessages) {
		for (ErrorMessage e : diagnosticMessages) {
			System.out.println(e);
		}
	}

	/**
	 * Compares two records if they have the same number of fields and identical values in their fields. Does not
	 * consider (or examine) metadata.
	 * 
	 * @param lhs
	 * @param rhs
	 * @return true if records have the same number of fields and the same values in them
	 */
	protected static boolean recordEquals(DataRecord lhs, DataRecord rhs) {
		if (lhs == rhs)
			return true;
		if (rhs == null)
			return false;
		if (lhs == null) {
			return false;
		}
		if (lhs.getNumFields() != rhs.getNumFields()) {
			return false;
		}
		for (int i = 0; i < lhs.getNumFields(); i++) {
			if (lhs.getField(i).isNull()) {
				if (!rhs.getField(i).isNull()) {
					return false;
				}
			} else if (!lhs.getField(i).equals(rhs.getField(i))) {
				return false;
			}
		}
		return true;
	}

	public void print_code(String text) {
		String[] lines = text.split("\n");
		System.out.println("\t:         1         2         3         4         5         ");
		System.out.println("\t:12345678901234567890123456789012345678901234567890123456789");
		for (int i = 0; i < lines.length; i++) {
			System.out.println((i + 1) + "\t:" + lines[i]);
		}
	}
	
//----------------------------- TESTS -----------------------------

	@SuppressWarnings("unchecked")
	public void test_operators_unary_record_allowed() {
		doCompile("test_operators_unary_record_allowed");
		check("value", Arrays.asList(14, 16, 16, 65, 63, 63));
		check("bornMillisec", Arrays.asList(14L, 16L, 16L, 65L, 63L, 63L));
		List<Double> actualAge = (List<Double>) getVariable("age");
		double[] expectedAge = {14.123, 16.123, 16.123, 65.789, 63.789, 63.789};
		for (int i = 0; i < actualAge.size(); i++) {
			assertEquals("age[" + i + "]", expectedAge[i], actualAge.get(i), 0.0001);
		}
		check("currency", Arrays.asList(
				new BigDecimal(BigInteger.valueOf(12500), 3), 
				new BigDecimal(BigInteger.valueOf(14500), 3),
				new BigDecimal(BigInteger.valueOf(14500), 3),
				new BigDecimal(BigInteger.valueOf(65432), 3),
				new BigDecimal(BigInteger.valueOf(63432), 3),
				new BigDecimal(BigInteger.valueOf(63432), 3)
		));
	}

	@SuppressWarnings("unchecked")
	public void test_dynamic_compare() {
		doCompile("test_dynamic_compare");
		
		String varName = "compare";
		List<Integer> compareResult = (List<Integer>) getVariable(varName);
		for (int i = 0; i < compareResult.size(); i++) {
			if ((i % 3) == 0) {
				assertTrue(varName + "[" + i + "]", compareResult.get(i) > 0);
			} else if ((i % 3) == 1) {
				assertEquals(varName + "[" + i + "]", Integer.valueOf(0), compareResult.get(i));
			} else if ((i % 3) == 2) {
				assertTrue(varName + "[" + i + "]", compareResult.get(i) < 0);
			}
		}
		
		varName = "compareBooleans";
		compareResult = (List<Integer>) getVariable(varName);
		assertEquals(varName + "[0]", Integer.valueOf(0), compareResult.get(0));
		assertTrue(varName + "[1]", compareResult.get(1) > 0);
		assertTrue(varName + "[2]", compareResult.get(2) < 0);
		assertEquals(varName + "[3]", Integer.valueOf(0), compareResult.get(3));
	}
	
	private void test_dynamic_get_set_loop(String testIdentifier) {
		doCompile(testIdentifier);
		
		check("recordLength", 9);
		
		check("value", Arrays.asList(654321, 777777, 654321, 654323, 123456, 112567, 112233));
		check("type", Arrays.asList("string", "number", "string", "date", "long", "integer", "boolean", "byte", "decimal"));
		check("asString", Arrays.asList("1000", "1001.0", "1002", "Thu Jan 01 01:00:01 CET 1970", "1004", "1005", "true", null, "1008.000"));
		check("isNull", Arrays.asList(false, false, false, false, false, false, false, true, false));
		check("fieldName", Arrays.asList("Name", "Age", "City", "Born", "BornMillisec", "Value", "Flag", "ByteArray", "Currency"));
		Integer[] indices = new Integer[9];
		for (int i = 0; i < indices.length; i++) {
			indices[i] = i;
		}
		check("fieldIndex", Arrays.asList(indices));
		
		// check dynamic write and read with all data types
		check("booleanVar", true);
		assertTrue("byteVar", Arrays.equals(new BigInteger("1234567890abcdef", 16).toByteArray(), (byte[]) getVariable("byteVar")));
		check("decimalVar", new BigDecimal(BigInteger.valueOf(1000125), 3));
		check("integerVar", 1000);
		check("longVar", 1000000000000L);
		check("numberVar", 1000.5);
		check("stringVar", "hello");
		check("dateVar", new Date(5000));
		
	}

	public void test_dynamic_get_set_loop() {
		test_dynamic_get_set_loop("test_dynamic_get_set_loop");
	}

	public void test_dynamic_get_set_loop_alternative() {
		test_dynamic_get_set_loop("test_dynamic_get_set_loop_alternative");
	}

	public void test_dynamic_invalid() {
		doCompileExpectErrors("test_dynamic_invalid", Arrays.asList(
				"Input record cannot be assigned to",
				"Input record cannot be assigned to"
		));
	}

	public void test_return_constants() {
		// test case for issue 2257
		System.out.println("Return constants test:");
		doCompile("test_return_constants");

		check("skip", RecordTransform.SKIP);
		check("all", RecordTransform.ALL);
		check("ok", NORMALIZE_RETURN_OK);
	}

	public void test_raise_error_terminal() {
		// test case for issue 2337
		doCompile("test_raise_error_terminal");
	}

	public void test_raise_error_nonliteral() {
		// test case for issue CL-2071
		doCompile("test_raise_error_nonliteral");
	}

	public void test_case_unique_check() {
		// test case for issue 2515
		doCompileExpectErrors("test_case_unique_check", Arrays.asList("Duplicate case", "Duplicate case"));
	}

	public void test_case_unique_check2() {
		// test case for issue 2515
		doCompileExpectErrors("test_case_unique_check2", Arrays.asList("Duplicate case", "Duplicate case"));
	}

	public void test_case_unique_check3() {
		doCompileExpectError("test_case_unique_check3", "Default case is already defined");
	}

	public void test_rvalue_for_append() {
		// test case for issue 3956
		doCompile("test_rvalue_for_append");
		check("a", Arrays.asList("1", "2"));
		check("b", Arrays.asList("a", "b", "c"));
		check("c", Arrays.asList("1", "2", "a", "b", "c"));
	}

	public void test_rvalue_for_map_append() {
		// test case for issue 3960
		doCompile("test_rvalue_for_map_append");
		HashMap<Integer, String> map1instance = new HashMap<Integer, String>();
		map1instance.put(1, "a");
		map1instance.put(2, "b");
		HashMap<Integer, String> map2instance = new HashMap<Integer, String>();
		map2instance.put(3, "c");
		map2instance.put(4, "d");
		HashMap<Integer, String> map3instance = new HashMap<Integer, String>();
		map3instance.put(1, "a");
		map3instance.put(2, "b");
		map3instance.put(3, "c");
		map3instance.put(4, "d");
		check("map1", map1instance);
		check("map2", map2instance);
		check("map3", map3instance);
	}

	public void test_global_field_access() {
		// test case for issue 3957
		doCompileExpectError("test_global_field_access", "Unable to access record field in global scope");
	}

	public void test_global_scope() {
		// test case for issue 5006
		doCompile("test_global_scope");
		
		check("len", "Kokon".length());
	}
	
	//TODO Implement
	/*public void test_new() {
		doCompile("test_new");
	}*/

	public void test_parser() {
		System.out.println("\nParser test:");
		doCompile("test_parser");		
	}

	public void test_ref_res_import() {
		System.out.println("\nSpecial character resolving (import) test:");

		URL importLoc = getClass().getSuperclass().getResource("test_ref_res.ctl");
		String expStr = "import '" + importLoc + "';\n";
		doCompile(expStr, "test_ref_res_import");
	}

	public void test_ref_res_noimport() {
		System.out.println("\nSpecial character resolving (no import) test:");
		doCompile("test_ref_res");		
	}
	
	public void test_import() {
		System.out.println("\nImport test:");

		URL importLoc = getClass().getSuperclass().getResource("import.ctl");
		String expStr = "import '" + importLoc + "';\n";
		importLoc = getClass().getSuperclass().getResource("other.ctl");
		expStr += "import '" + importLoc + "';\n" +
				"integer sumInt;\n" +
				"function integer transform() {\n" +
				"	if (a == 3) {\n" +
				"		otherImportVar++;\n" +
				"	}\n" +
				"	sumInt = sum(a, otherImportVar);\n" + 
				"	return 0;\n" +
				"}\n";
		doCompile(expStr, "test_import");
	}
	
	public void test_scope() throws ComponentNotReadyException, TransformException {
		System.out.println("\nMapping test:");
		// String expStr =
		// "function string computeSomething(int n) {\n" +
		// "	string s = '';\n" +
		// "	do  {\n" +
		// "		int i = n--;\n" +
		// "		s = s + '-' + i;\n" +
		// "	} while (n > 0)\n" +
		// "	return s;" +
		// "}\n\n" +
		// "function int transform() {\n" +
		// "	printErr(computeSomething(10));\n" +
		// "   return 0;\n" +
		// "}";
		URL importLoc = getClass().getSuperclass().getResource("samplecode.ctl");
		String expStr = "import '" + importLoc + "';\n";

		// "function int getIndexOfOffsetStart(string encodedDate) {\n" +
		// "int offsetStart;\n" +
		// "int actualLastMinus;\n" +
		// "int lastMinus = -1;\n" +
		// "if ( index_of(encodedDate, '+') != -1 )\n" +
		// "	return index_of(encodedDate, '+');\n" +
		// "do {\n" +
		// "	actualLastMinus = index_of(encodedDate, '-', lastMinus+1);\n" +
		// "	if ( actualLastMinus != -1 )\n" +
		// "		lastMinus = actualLastMinus;\n" +
		// "} while ( actualLastMinus != -1 )\n" +
		// "return lastMinus;\n" +
		// "}\n" +
		// "function int transform() {\n" +
		// "	getIndexOfOffsetStart('2009-04-24T08:00:00-05:00');\n" +
		// " 	return 0;\n" +
		// "}\n";

		doCompile(expStr, "test_scope");

	}

//-------------------------- Data Types Tests ---------------------

	public void test_type_void() {
		doCompileExpectErrors("test_type_void", Arrays.asList("Syntax error on token 'void'",
				"Variable 'voidVar' is not declared",
				"Variable 'voidVar' is not declared",
				"Syntax error on token 'void'"));
	}
	
	public void test_type_integer() {
		doCompile("test_type_integer");
		check("i", 0);
		check("j", -1);
		check("field", VALUE_VALUE);
		checkNull("nullValue");
		check("varWithInitializer", 123);
		checkNull("varWithNullInitializer");
	}
	
	public void test_type_integer_edge() {
		String testExpression = 
			"integer minInt;\n"+
			"integer maxInt;\n"+			
			"function integer transform() {\n" +
				"minInt=" + Integer.MIN_VALUE + ";\n" +
				"printErr(minInt, true);\n" + 
				"maxInt=" + Integer.MAX_VALUE + ";\n" +
				"printErr(maxInt, true);\n" + 
				"return 0;\n" +
			"}\n";
		doCompile(testExpression, "test_int_edge");
		check("minInt", Integer.MIN_VALUE);
		check("maxInt", Integer.MAX_VALUE);
	}
	
	public void test_type_long() {
		doCompile("test_type_long");
		check("i", Long.valueOf(0));
		check("j", Long.valueOf(-1));
		check("field", BORN_MILLISEC_VALUE);
		check("def", Long.valueOf(0));
		checkNull("nullValue");
		check("varWithInitializer", 123L);
		checkNull("varWithNullInitializer");
	}
	
	public void test_type_long_edge() {
		String expStr = 
			"long minLong;\n"+
			"long maxLong;\n"+			
			"function integer transform() {\n" +
				"minLong=" + (Long.MIN_VALUE) + "L;\n" +
				"printErr(minLong);\n" +
				"maxLong=" + (Long.MAX_VALUE) + "L;\n" +
				"printErr(maxLong);\n" +
				"return 0;\n" +
			"}\n";

		doCompile(expStr,"test_long_edge");
		check("minLong", Long.MIN_VALUE);
		check("maxLong", Long.MAX_VALUE);
	}
	
	public void test_type_decimal() {
		doCompile("test_type_decimal");
		check("i", new BigDecimal(0, MAX_PRECISION));
		check("j", new BigDecimal(-1, MAX_PRECISION));
		check("field", CURRENCY_VALUE);
		check("def", new BigDecimal(0, MAX_PRECISION));
		checkNull("nullValue");
		check("varWithInitializer", new BigDecimal("123.35", MAX_PRECISION));
		checkNull("varWithNullInitializer");
		check("varWithInitializerNoDist", new BigDecimal(123.35, MAX_PRECISION));
	}
	
	public void test_type_decimal_edge() {
		String testExpression = 
			"decimal minLong;\n"+
			"decimal maxLong;\n"+
			"decimal minLongNoDist;\n"+
			"decimal maxLongNoDist;\n"+
			"decimal minDouble;\n"+
			"decimal maxDouble;\n"+
			"decimal minDoubleNoDist;\n"+
			"decimal maxDoubleNoDist;\n"+
			
			"function integer transform() {\n" +
				"minLong=" + String.valueOf(Long.MIN_VALUE) + "d;\n" +
				"printErr(minLong);\n" + 
				"maxLong=" + String.valueOf(Long.MAX_VALUE) + "d;\n" +
				"printErr(maxLong);\n" + 
				"minLongNoDist=" + String.valueOf(Long.MIN_VALUE) + "L;\n" +
				"printErr(minLongNoDist);\n" + 
				"maxLongNoDist=" + String.valueOf(Long.MAX_VALUE) + "L;\n" +
				"printErr(maxLongNoDist);\n" +
				// distincter will cause the double-string be parsed into exact representation within BigDecimal
				"minDouble=" + String.valueOf(Double.MIN_VALUE) + "D;\n" +
				"printErr(minDouble);\n" + 
				"maxDouble=" + String.valueOf(Double.MAX_VALUE) + "D;\n" +
				"printErr(maxDouble);\n" +
				// no distincter will cause the double-string to be parsed into inexact representation within double
				// then to be assigned into BigDecimal (which will extract only MAX_PRECISION digits)
				"minDoubleNoDist=" + String.valueOf(Double.MIN_VALUE) + ";\n" +
				"printErr(minDoubleNoDist);\n" + 
				"maxDoubleNoDist=" + String.valueOf(Double.MAX_VALUE) + ";\n" +
				"printErr(maxDoubleNoDist);\n" +
				"return 0;\n" +
			"}\n";
		
		doCompile(testExpression, "test_decimal_edge");
		
		check("minLong", new BigDecimal(String.valueOf(Long.MIN_VALUE), MAX_PRECISION));
		check("maxLong", new BigDecimal(String.valueOf(Long.MAX_VALUE), MAX_PRECISION));
		check("minLongNoDist", new BigDecimal(String.valueOf(Long.MIN_VALUE), MAX_PRECISION));
		check("maxLongNoDist", new BigDecimal(String.valueOf(Long.MAX_VALUE), MAX_PRECISION));
		// distincter will cause the MIN_VALUE to be parsed into exact representation (i.e. 4.9E-324)
		check("minDouble", new BigDecimal(String.valueOf(Double.MIN_VALUE), MAX_PRECISION));
		check("maxDouble", new BigDecimal(String.valueOf(Double.MAX_VALUE), MAX_PRECISION));
		// no distincter will cause MIN_VALUE to be parsed into double inexact representation and extraction of
		// MAX_PRECISION digits (i.e. 4.94065.....E-324)
		check("minDoubleNoDist", new BigDecimal(Double.MIN_VALUE, MAX_PRECISION));
		check("maxDoubleNoDist", new BigDecimal(Double.MAX_VALUE, MAX_PRECISION));
	}
	
	public void test_type_number() {
		doCompile("test_type_number");
		
		check("i", Double.valueOf(0));
		check("j", Double.valueOf(-1));
		check("field", AGE_VALUE);
		check("def", Double.valueOf(0));
		checkNull("nullValue");
		checkNull("varWithNullInitializer");
	}
	
	public void test_type_number_edge() {
		String testExpression = 
			"number minDouble;\n" +
			"number maxDouble;\n"+		
			"function integer transform() {\n" +
				"minDouble=" + Double.MIN_VALUE + ";\n" +
				"printErr(minDouble);\n" +
				"maxDouble=" + Double.MAX_VALUE + ";\n" +
				"printErr(maxDouble);\n" +
				"return 0;\n" +
			"}\n";
		doCompile(testExpression, "test_number_edge");
		check("minDouble", Double.valueOf(Double.MIN_VALUE));
		check("maxDouble", Double.valueOf(Double.MAX_VALUE));
	}

	public void test_type_string() {
		doCompile("test_type_string");
		check("i","0");
		check("helloEscaped", "hello\\nworld");
		check("helloExpanded", "hello\nworld");
		check("fieldName", NAME_VALUE);
		check("fieldCity", CITY_VALUE);
		check("escapeChars", "a\u0101\u0102A");
		check("doubleEscapeChars", "a\\u0101\\u0102A");
		check("specialChars", "špeciálne značky s mäkčeňom môžu byť");
		check("dQescapeChars", "a\u0101\u0102A");
		//TODO:Is next test correct?
		check("dQdoubleEscapeChars", "a\\u0101\\u0102A");
		check("dQspecialChars", "špeciálne značky s mäkčeňom môžu byť");
		check("empty", "");
		check("def", "");
		checkNull("varWithNullInitializer");
}
	
	public void test_type_string_long() {
		int length = 1000;
		StringBuilder tmp = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			tmp.append(i % 10);
		}
		String testExpression = 
			"string longString;\n" +
			"function integer transform() {\n" +
				"longString=\"" + tmp + "\";\n" +
				"printErr(longString);\n" +
				"return 0;\n" +
			"}\n";
		doCompile(testExpression, "test_string_long");
		
		check("longString", String.valueOf(tmp));
	}
	
	public void test_type_date() {
		doCompile("test_type_date");
		check("d3", new GregorianCalendar(2006, GregorianCalendar.AUGUST, 1).getTime());
		check("d2", new GregorianCalendar(2006, GregorianCalendar.AUGUST, 2, 15, 15, 3).getTime());
		check("d1", new GregorianCalendar(2006, GregorianCalendar.JANUARY, 1, 1, 2, 3).getTime());
		check("field", BORN_VALUE);
		checkNull("nullValue");
		check("minValue", new GregorianCalendar(1970, GregorianCalendar.JANUARY, 1, 1, 0, 0).getTime());
		checkNull("varWithNullInitializer");
	}
	
	public void test_type_boolean() {
		doCompile("test_type_boolean");
		check("b1", true);
		check("b2", false);
		check("b3", false);
		checkNull("nullValue");
		checkNull("varWithNullInitializer");
	}
	
	public void test_type_boolean_compare() {
		doCompileExpectErrors("test_type_boolean_compare", Arrays.asList(
				"Operator '>' is not defined for types 'boolean' and 'boolean'", 
				"Operator '>=' is not defined for types 'boolean' and 'boolean'",
				"Operator '<' is not defined for types 'boolean' and 'boolean'",
				"Operator '<=' is not defined for types 'boolean' and 'boolean'",
				"Operator '<' is not defined for types 'boolean' and 'boolean'",
				"Operator '>' is not defined for types 'boolean' and 'boolean'",
				"Operator '>=' is not defined for types 'boolean' and 'boolean'",
				"Operator '<=' is not defined for types 'boolean' and 'boolean'"));
	}
	
	@SuppressWarnings("unchecked")
	public void test_type_list() {
		doCompile("test_type_list");
		check("intList", Arrays.asList(1, 2, 3, 4, 5, 6));
		check("intList2", Arrays.asList(1, 2, 3));
		check("stringList", Arrays.asList(
				"first", "replaced", "third", "fourth",
				"fifth", "sixth", "seventh", "extra"));
		assertEquals((List<String>) getVariable("stringList"), (List<String>) getVariable("stringListCopy"));
	}
	
	@SuppressWarnings("unchecked")
	public void test_type_map() {
		doCompile("test_type_map");
		Map<String, Integer> testMap = (Map<String, Integer>) getVariable("testMap");
		assertEquals(Integer.valueOf(1), testMap.get("zero"));
		assertEquals(Integer.valueOf(2), testMap.get("one"));
		assertEquals(Integer.valueOf(3), testMap.get("two"));
		assertEquals(Integer.valueOf(4), testMap.get("three"));
		assertEquals(4, testMap.size());

		Map<Date, String> dayInWeek = (Map<Date, String>) getVariable("dayInWeek");
		Calendar c = Calendar.getInstance();
		c.set(2009, Calendar.MARCH, 2, 0, 0, 0);
		c.set(Calendar.MILLISECOND, 0);
		assertEquals("Monday", dayInWeek.get(c.getTime()));

		Map<Date, String> dayInWeekCopy = (Map<Date, String>) getVariable("dayInWeekCopy");
		c.set(2009, Calendar.MARCH, 3, 0, 0, 0);
		c.set(Calendar.MILLISECOND, 0);
		assertEquals("Tuesday", ((Map<Date, String>) getVariable("tuesday")).get(c.getTime()));
		assertEquals("Tuesday", dayInWeekCopy.get(c.getTime()));

		c.set(2009, Calendar.MARCH, 4, 0, 0, 0);
		c.set(Calendar.MILLISECOND, 0);
		assertEquals("Wednesday", ((Map<Date, String>) getVariable("wednesday")).get(c.getTime()));
		assertEquals("Wednesday", dayInWeekCopy.get(c.getTime()));
		assertFalse(dayInWeek.equals(dayInWeekCopy));
	}
	
	public void test_type_record_list() {
		doCompile("test_type_record_list");
		
		check("resultInt", 6);
		check("resultString", "string");
		check("resultInt2", 10);
		check("resultString2", "string2");
	}

	public void test_type_record_list_global() {
		doCompile("test_type_record_list_global");
		
		check("resultInt", 6);
		check("resultString", "string");
		check("resultInt2", 10);
		check("resultString2", "string2");
	}

	public void test_type_record_map() {
		doCompile("test_type_record_map");
		
		check("resultInt", 6);
		check("resultString", "string");
		check("resultInt2", 10);
		check("resultString2", "string2");
	}

	public void test_type_record_map_global() {
		doCompile("test_type_record_map_global");
		
		check("resultInt", 6);
		check("resultString", "string");
		check("resultInt2", 10);
		check("resultString2", "string2");
	}

	
	public void test_type_record() {
		doCompile("test_type_record");

		// expected result
		DataRecord expected = createDefaultRecord(createDefaultMetadata("expected"));

		// simple copy
		assertTrue(recordEquals(expected, inputRecords[0]));
		assertTrue(recordEquals(expected, (DataRecord) getVariable("copy")));

		// copy and modify
		expected.getField("Name").setValue("empty");
		expected.getField("Value").setValue(321);
		Calendar c = Calendar.getInstance();
		c.set(1987, Calendar.NOVEMBER, 13, 0, 0, 0);
		c.set(Calendar.MILLISECOND, 0);
		expected.getField("Born").setValue(c.getTime());
		assertTrue(recordEquals(expected, (DataRecord) getVariable("modified")));

		// 2x modified copy
		expected.getField("Name").setValue("not empty");
		assertTrue(recordEquals(expected, (DataRecord)getVariable("modified2")));
		
		// modified by reference
		expected.getField("Value").setValue(654321);
		assertTrue(recordEquals(expected, (DataRecord)getVariable("modified3")));
		assertTrue(recordEquals(expected, (DataRecord)getVariable("reference")));
		assertTrue(getVariable("modified3") == getVariable("reference"));
		
		// output record
		assertTrue(recordEquals(expected, outputRecords[1]));
		
		// null record
		expected.setToNull();
		assertTrue(recordEquals(expected, (DataRecord)getVariable("nullRecord")));
	}
	
//------------------------ Operator Tests ---------------------------
	public void test_variables() {
		doCompile("test_variables");

		check("b1", true);
		check("b2", true);
		check("b4", "hi");
		check("i", 2);
	}

	public void test_operator_plus() {
		doCompile("test_operator_plus");

		check("iplusj", 10 + 100);
		check("lplusm", Long.valueOf(Integer.MAX_VALUE) + Long.valueOf(Integer.MAX_VALUE / 10));
		check("mplusl", getVariable("lplusm"));
		check("mplusi", Long.valueOf(Integer.MAX_VALUE) + 10);
		check("iplusm", getVariable("mplusi"));
		check("nplusm1", Double.valueOf(0.1D + 0.001D));
		check("nplusj", Double.valueOf(100 + 0.1D));
		check("jplusn", getVariable("nplusj"));
		check("m1plusm", Double.valueOf(Long.valueOf(Integer.MAX_VALUE) + 0.001d));
		check("mplusm1", getVariable("m1plusm"));
		check("dplusd1", new BigDecimal("0.1", MAX_PRECISION).add(new BigDecimal("0.0001", MAX_PRECISION), MAX_PRECISION));
		check("dplusj", new BigDecimal(100, MAX_PRECISION).add(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
		check("jplusd", getVariable("dplusj"));
		check("dplusm", new BigDecimal(Long.valueOf(Integer.MAX_VALUE), MAX_PRECISION).add(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
		check("mplusd", getVariable("dplusm"));
		check("dplusn", new BigDecimal("0.1").add(new BigDecimal(0.1D, MAX_PRECISION)));
		check("nplusd", getVariable("dplusn"));
		check("spluss1", "hello world");
		check("splusj", "hello100");
		check("jpluss", "100hello");
		check("splusm", "hello" + Long.valueOf(Integer.MAX_VALUE));
		check("mpluss", Long.valueOf(Integer.MAX_VALUE) + "hello");
		check("splusm1", "hello" + 0.001D);
		check("m1pluss", 0.001D + "hello");
		check("splusd1", "hello" + new BigDecimal("0.0001"));
		check("d1pluss", new BigDecimal("0.0001", MAX_PRECISION) + "hello");
	}

	public void test_operator_minus() {
		doCompile("test_operator_minus");

		check("iminusj", 10 - 100);
		check("lminusm", Long.valueOf(Integer.MAX_VALUE / 10) - Long.valueOf(Integer.MAX_VALUE));
		check("mminusi", Long.valueOf(Integer.MAX_VALUE - 10));
		check("iminusm", 10 - Long.valueOf(Integer.MAX_VALUE));
		check("nminusm1", Double.valueOf(0.1D - 0.001D));
		check("nminusj", Double.valueOf(0.1D - 100));
		check("jminusn", Double.valueOf(100 - 0.1D));
		check("m1minusm", Double.valueOf(0.001D - Long.valueOf(Integer.MAX_VALUE)));
		check("mminusm1", Double.valueOf(Long.valueOf(Integer.MAX_VALUE) - 0.001D));
		check("dminusd1", new BigDecimal("0.1", MAX_PRECISION).subtract(new BigDecimal("0.0001", MAX_PRECISION), MAX_PRECISION));
		check("dminusj", new BigDecimal("0.1", MAX_PRECISION).subtract(new BigDecimal(100, MAX_PRECISION), MAX_PRECISION));
		check("jminusd", new BigDecimal(100, MAX_PRECISION).subtract(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
		check("dminusm", new BigDecimal("0.1", MAX_PRECISION).subtract(new BigDecimal(Long.valueOf(Integer.MAX_VALUE), MAX_PRECISION), MAX_PRECISION));
		check("mminusd", new BigDecimal(Long.valueOf(Integer.MAX_VALUE), MAX_PRECISION).subtract(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
		check("dminusn", new BigDecimal("0.1", MAX_PRECISION).subtract(new BigDecimal(0.1D, MAX_PRECISION), MAX_PRECISION));
		check("nminusd", new BigDecimal(0.1D, MAX_PRECISION).subtract(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
	}

	public void test_operator_multiply() {
		doCompile("test_operator_multiply");

		check("itimesj", 10 * 100);
		check("ltimesm", Long.valueOf(Integer.MAX_VALUE) * (Long.valueOf(Integer.MAX_VALUE / 10)));
		check("mtimesl", getVariable("ltimesm"));
		check("mtimesi", Long.valueOf(Integer.MAX_VALUE) * 10);
		check("itimesm", getVariable("mtimesi"));
		check("ntimesm1", Double.valueOf(0.1D * 0.001D));
		check("ntimesj", Double.valueOf(0.1) * 100);
		check("jtimesn", getVariable("ntimesj"));
		check("m1timesm", Double.valueOf(0.001d * Long.valueOf(Integer.MAX_VALUE)));
		check("mtimesm1", getVariable("m1timesm"));
		check("dtimesd1", new BigDecimal("0.1", MAX_PRECISION).multiply(new BigDecimal("0.0001", MAX_PRECISION), MAX_PRECISION));
		check("dtimesj", new BigDecimal("0.1", MAX_PRECISION).multiply(new BigDecimal(100, MAX_PRECISION)));
		check("jtimesd", getVariable("dtimesj"));
		check("dtimesm", new BigDecimal("0.1", MAX_PRECISION).multiply(new BigDecimal(Long.valueOf(Integer.MAX_VALUE), MAX_PRECISION), MAX_PRECISION));
		check("mtimesd", getVariable("dtimesm"));
		check("dtimesn", new BigDecimal("0.1", MAX_PRECISION).multiply(new BigDecimal(0.1, MAX_PRECISION), MAX_PRECISION));
		check("ntimesd", getVariable("dtimesn"));
	}

	public void test_operator_divide() {
		doCompile("test_operator_divide");

		check("idividej", 10 / 100);
		check("ldividem", Long.valueOf(Integer.MAX_VALUE / 10) / Long.valueOf(Integer.MAX_VALUE));
		check("mdividei", Long.valueOf(Integer.MAX_VALUE / 10));
		check("idividem", 10 / Long.valueOf(Integer.MAX_VALUE));
		check("ndividem1", Double.valueOf(0.1D / 0.001D));
		check("ndividej", Double.valueOf(0.1D / 100));
		check("jdividen", Double.valueOf(100 / 0.1D));
		check("m1dividem", Double.valueOf(0.001D / Long.valueOf(Integer.MAX_VALUE)));
		check("mdividem1", Double.valueOf(Long.valueOf(Integer.MAX_VALUE) / 0.001D));
		check("ddivided1", new BigDecimal("0.1", MAX_PRECISION).divide(new BigDecimal("0.0001", MAX_PRECISION), MAX_PRECISION));
		check("ddividej", new BigDecimal("0.1", MAX_PRECISION).divide(new BigDecimal(100, MAX_PRECISION), MAX_PRECISION));
		check("jdivided", new BigDecimal(100, MAX_PRECISION).divide(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
		check("ddividem", new BigDecimal("0.1", MAX_PRECISION).divide(new BigDecimal(Long.valueOf(Integer.MAX_VALUE), MAX_PRECISION), MAX_PRECISION));
		check("mdivided", new BigDecimal(Long.valueOf(Integer.MAX_VALUE), MAX_PRECISION).divide(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
		check("ddividen", new BigDecimal("0.1", MAX_PRECISION).divide(new BigDecimal(0.1D, MAX_PRECISION), MAX_PRECISION));
		check("ndivided", new BigDecimal(0.1D, MAX_PRECISION).divide(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
	}
	
	public void test_operator_modulus() {
		doCompile("test_operator_modulus");
		check("imoduloj", 10 % 100);
		check("lmodulom", Long.valueOf(Integer.MAX_VALUE / 10) % Long.valueOf(Integer.MAX_VALUE));
		check("mmoduloi", Long.valueOf(Integer.MAX_VALUE % 10));
		check("imodulom", 10 % Long.valueOf(Integer.MAX_VALUE));
		check("nmodulom1", Double.valueOf(0.1D % 0.001D));
		check("nmoduloj", Double.valueOf(0.1D % 100));
		check("jmodulon", Double.valueOf(100 % 0.1D));
		check("m1modulom", Double.valueOf(0.001D % Long.valueOf(Integer.MAX_VALUE)));
		check("mmodulom1", Double.valueOf(Long.valueOf(Integer.MAX_VALUE) % 0.001D));
		check("dmodulod1", new BigDecimal("0.1", MAX_PRECISION).remainder(new BigDecimal("0.0001", MAX_PRECISION), MAX_PRECISION));
		check("dmoduloj", new BigDecimal("0.1", MAX_PRECISION).remainder(new BigDecimal(100, MAX_PRECISION), MAX_PRECISION));
		check("jmodulod", new BigDecimal(100, MAX_PRECISION).remainder(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
		check("dmodulom", new BigDecimal("0.1", MAX_PRECISION).remainder(new BigDecimal(Long.valueOf(Integer.MAX_VALUE), MAX_PRECISION), MAX_PRECISION));
		check("mmodulod", new BigDecimal(Long.valueOf(Integer.MAX_VALUE), MAX_PRECISION).remainder(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
		check("dmodulon", new BigDecimal("0.1", MAX_PRECISION).remainder(new BigDecimal(0.1D, MAX_PRECISION), MAX_PRECISION));
		check("nmodulod", new BigDecimal(0.1D, MAX_PRECISION).remainder(new BigDecimal("0.1", MAX_PRECISION), MAX_PRECISION));
	}
	
	public void test_operators_unary() {
		doCompile("test_operators_unary");

		// postfix operators
		// int
		check("intPlusOrig", Integer.valueOf(10));
		check("intPlusPlus", Integer.valueOf(10));
		check("intPlus", Integer.valueOf(11));
		check("intMinusOrig", Integer.valueOf(10));
		check("intMinusMinus", Integer.valueOf(10));
		check("intMinus", Integer.valueOf(9));
		// long
		check("longPlusOrig", Long.valueOf(10));
		check("longPlusPlus", Long.valueOf(10));
		check("longPlus", Long.valueOf(11));
		check("longMinusOrig", Long.valueOf(10));
		check("longMinusMinus", Long.valueOf(10));
		check("longMinus", Long.valueOf(9));
		// double
		check("numberPlusOrig", Double.valueOf(10.1));
		check("numberPlusPlus", Double.valueOf(10.1));
		check("numberPlus", Double.valueOf(11.1));
		check("numberMinusOrig", Double.valueOf(10.1));
		check("numberMinusMinus", Double.valueOf(10.1));
		check("numberMinus", Double.valueOf(9.1));
		// decimal
		check("decimalPlusOrig", new BigDecimal("10.1"));
		check("decimalPlusPlus", new BigDecimal("10.1"));
		check("decimalPlus", new BigDecimal("11.1"));
		check("decimalMinusOrig", new BigDecimal("10.1"));
		check("decimalMinusMinus", new BigDecimal("10.1"));
		check("decimalMinus", new BigDecimal("9.1"));
		// prefix operators
		// integer
		check("plusIntOrig", Integer.valueOf(10));
		check("plusPlusInt", Integer.valueOf(11));
		check("plusInt", Integer.valueOf(11));
		check("minusIntOrig", Integer.valueOf(10));
		check("minusMinusInt", Integer.valueOf(9));
		check("minusInt", Integer.valueOf(9));
		check("unaryInt", Integer.valueOf(-10));
		// long
		check("plusLongOrig", Long.valueOf(10));
		check("plusPlusLong", Long.valueOf(11));
		check("plusLong", Long.valueOf(11));
		check("minusLongOrig", Long.valueOf(10));
		check("minusMinusLong", Long.valueOf(9));
		check("minusLong", Long.valueOf(9));
		check("unaryLong", Long.valueOf(-10));
		// double
		check("plusNumberOrig", Double.valueOf(10.1));
		check("plusPlusNumber", Double.valueOf(11.1));
		check("plusNumber", Double.valueOf(11.1));
		check("minusNumberOrig", Double.valueOf(10.1));
		check("minusMinusNumber", Double.valueOf(9.1));
		check("minusNumber", Double.valueOf(9.1));
		check("unaryNumber", Double.valueOf(-10.1));
		// decimal
		check("plusDecimalOrig", new BigDecimal("10.1"));
		check("plusPlusDecimal", new BigDecimal("11.1"));
		check("plusDecimal", new BigDecimal("11.1"));
		check("minusDecimalOrig", new BigDecimal("10.1"));
		check("minusMinusDecimal", new BigDecimal("9.1"));
		check("minusDecimal", new BigDecimal("9.1"));
		check("unaryDecimal", new BigDecimal("-10.1"));
		
		// record values
		assertEquals(101, ((DataRecord) getVariable("plusPlusRecord")).getField("Value").getValue());
		assertEquals(101, ((DataRecord) getVariable("recordPlusPlus")).getField("Value").getValue());
		assertEquals(101, ((DataRecord) getVariable("modifiedPlusPlusRecord")).getField("Value").getValue());
		assertEquals(101, ((DataRecord) getVariable("modifiedRecordPlusPlus")).getField("Value").getValue());
		
		//record as parameter
		assertEquals(99, ((DataRecord) getVariable("minusMinusRecord")).getField("Value").getValue());
		assertEquals(99, ((DataRecord) getVariable("recordMinusMinus")).getField("Value").getValue());
		assertEquals(99, ((DataRecord) getVariable("modifiedMinusMinusRecord")).getField("Value").getValue());
		assertEquals(99, ((DataRecord) getVariable("modifiedRecordMinusMinus")).getField("Value").getValue());
		
		// logical not
		check("booleanValue", true);
		check("negation", false);
		check("doubleNegation", true);
	}
	
	public void test_operators_unary_record() {
		doCompileExpectErrors("test_operators_unary_record", Arrays.asList(
				"Illegal argument to ++/-- operator",
				"Illegal argument to ++/-- operator",
				"Illegal argument to ++/-- operator",
				"Illegal argument to ++/-- operator",
				"Input record cannot be assigned to",
				"Input record cannot be assigned to",
				"Input record cannot be assigned to",
				"Input record cannot be assigned to"
		));
	}
	
	public void test_operator_equal() {
		doCompile("test_operator_equal");

		check("eq0", true);
		check("eq1", true);
		check("eq1a", true);
		check("eq1b", true);
		check("eq1c", false);
		check("eq2", true);
		check("eq3", true);
		check("eq4", true);
		check("eq5", true);
		check("eq6", false);
		check("eq7", true);
		check("eq8", false);
		check("eq9", true);
		check("eq10", false);
		check("eq11", true);
		check("eq12", false);
		check("eq13", true);
		check("eq14", false);
		check("eq15", false);
		check("eq16", true);
		check("eq17", false);
		check("eq18", false);
		check("eq19", false);
		// byte
		check("eq20", true);
		check("eq21", true);
		check("eq22", false);
		check("eq23", false);
		check("eq24", true);
		check("eq25", false);
		check("eq20c", true);
		check("eq21c", true);
		check("eq22c", false);
		check("eq23c", false);
		check("eq24c", true);
		check("eq25c", false);
		check("eq26", true);
		check("eq27", true);
	}
	
	public void test_operator_non_equal(){
		doCompile("test_operator_non_equal");
		check("inei", false);
		check("inej", true);
		check("jnei", true);
		check("jnej", false);
		check("lnei", false);
		check("inel", false);
		check("lnej", true);
		check("jnel", true);
		check("lnel", false);
		check("dnei", false);
		check("ined", false);
		check("dnej", true);
		check("jned", true);
		check("dnel", false);
		check("lned", false);
		check("dned", false);
	}
	
	public void test_operator_in() {
		doCompile("test_operator_in");

		check("a", Integer.valueOf(1));
		check("haystack", Collections.EMPTY_LIST);
		check("needle", Integer.valueOf(2));
		check("b1", true);
		check("b2", false);
		check("h2", Arrays.asList(2.1D, 2.0D, 2.2D));
		check("b3", true);
		check("h3", Arrays.asList("memento", "mori", "memento mori"));
		check("n3", "memento mori");
		check("b4", true);
	}
	
	public void test_operator_greater_less() {
		doCompile("test_operator_greater_less");

		check("eq1", true);
		check("eq2", true);
		check("eq3", true);
		check("eq4", false);
		check("eq5", true);
		check("eq6", false);
		check("eq7", true);
		check("eq8", true);
		check("eq9", true);
	}
	
	public void test_operator_ternary(){
		doCompile("test_operator_ternary");

		// simple use
		check("trueValue", true);
		check("falseValue", false);
		check("res1", Integer.valueOf(1));
		check("res2", Integer.valueOf(2));
		// nesting in positive branch
		check("res3", Integer.valueOf(1));
		check("res4", Integer.valueOf(2));
		check("res5", Integer.valueOf(3));
		// nesting in negative branch
		check("res6", Integer.valueOf(2));
		check("res7", Integer.valueOf(3));
		// nesting in both branches
		check("res8", Integer.valueOf(1));
		check("res9", Integer.valueOf(1));
		check("res10", Integer.valueOf(2));
		check("res11", Integer.valueOf(3));
		check("res12", Integer.valueOf(2));
		check("res13", Integer.valueOf(4));
		check("res14", Integer.valueOf(3));
		check("res15", Integer.valueOf(4));
	}
	
	public void test_operators_logical(){
		doCompile("test_operators_logical");
		//TODO: please double check this.
		check("res1", false);
		check("res2", false);
		check("res3", true);
		check("res4", true);
		check("res5", false);
		check("res6", false);
		check("res7", true);
		check("res8", false);
	}
	
	public void test_regex(){
		doCompile("test_regex");
		check("eq0", false);
		check("eq1", true);
		check("eq2", false);
		check("eq3", true);
		check("eq4", false);
		check("eq5", true);
	}
	
	public void test_if() {
		doCompile("test_if");

		// if with single statement
		check("cond1", true);
		check("res1", true);

		// if with mutliple statements (block)
		check("cond2", true);
		check("res21", true);
		check("res22", true);

		// else with single statement
		check("cond3", false);
		check("res31", false);
		check("res32", true);

		// else with multiple statements (block)
		check("cond4", false);
		check("res41", false);
		check("res42", true);
		check("res43", true);

		// if with block, else with block
		check("cond5", false);
		check("res51", false);
		check("res52", false);
		check("res53", true);
		check("res54", true);

		// else-if with single statement
		check("cond61", false);
		check("cond62", true);
		check("res61", false);
		check("res62", true);

		// else-if with multiple statements
		check("cond71", false);
		check("cond72", true);
		check("res71", false);
		check("res72", true);
		check("res73", true);

		// if-elseif-else test
		check("cond81", false);
		check("cond82", false);
		check("res81", false);
		check("res82", false);
		check("res83", true);

		// if with single statement + inactive else
		check("cond9", true);
		check("res91", true);
		check("res92", false);

		// if with multiple statements + inactive else with block
		check("cond10", true);
		check("res101", true);
		check("res102", true);
		check("res103", false);
		check("res104", false);

		// if with condition
		check("i", 0);
		check("j", 1);
		check("res11", true);
	}
	
	public void test_switch() {
		doCompile("test_switch");
		// simple switch
		check("cond1", 1);
		check("res11", false);
		check("res12", true);
		check("res13", false);

		// switch, no break
		check("cond2", 1);
		check("res21", false);
		check("res22", true);
		check("res23", true);

		// default branch
		check("cond3", 3);
		check("res31", false);
		check("res32", false);
		check("res33", true);

		// no default branch => no match
		check("cond4", 3);
		check("res41", false);
		check("res42", false);
		check("res43", false);

		// multiple statements in a single case-branch
		check("cond5", 1);
		check("res51", false);
		check("res52", true);
		check("res53", true);
		check("res54", false);

		// single statement shared by several case labels
		check("cond6", 1);
		check("res61", false);
		check("res62", true);
		check("res63", true);
		check("res64", false);
	}
	
	public void test_int_switch(){
		doCompile("test_int_switch");
		
		// simple switch
		check("cond1", 1);
		check("res11", true);
		check("res12", false);
		check("res13", false);

		// first case is not followed by a break
		check("cond2", 1);
		check("res21", true);
		check("res22", true);
		check("res23", false);

		// first and second case have multiple labels
		check("cond3", 12);
		check("res31", false);
		check("res32", true);
		check("res33", false);

		// first and second case have multiple labels and no break after first group
		check("cond4", 11);
		check("res41", true);
		check("res42", true);
		check("res43", false);

		// default case intermixed with other case labels in the second group
		check("cond5", 11);
		check("res51", true);
		check("res52", true);
		check("res53", true);

		// default case intermixed, with break
		check("cond6", 16);
		check("res61", false);
		check("res62", true);
		check("res63", false);

		// continue test
		check("res7", Arrays.asList(
		/* i=0 */false, false, false,
		/* i=1 */true, true, false,
		/* i=2 */true, true, false,
		/* i=3 */false, true, false,
		/* i=4 */false, true, false,
		/* i=5 */false, false, true));

		// return test
		check("res8", Arrays.asList("0123", "123", "23", "3", "4", "3"));
	}
	
	public void test_non_int_switch(){
		doCompile("test_non_int_switch");
		
		// simple switch
		check("cond1", "1");
		check("res11", true);
		check("res12", false);
		check("res13", false);

		// first case is not followed by a break
		check("cond2", "1");
		check("res21", true);
		check("res22", true);
		check("res23", false);

		// first and second case have multiple labels
		check("cond3", "12");
		check("res31", false);
		check("res32", true);
		check("res33", false);

		// first and second case have multiple labels and no break after first group
		check("cond4", "11");
		check("res41", true);
		check("res42", true);
		check("res43", false);

		// default case intermixed with other case labels in the second group
		check("cond5", "11");
		check("res51", true);
		check("res52", true);
		check("res53", true);

		// default case intermixed, with break
		check("cond6", "16");
		check("res61", false);
		check("res62", true);
		check("res63", false);

		// continue test
		check("res7", Arrays.asList(
		/* i=0 */false, false, false,
		/* i=1 */true, true, false,
		/* i=2 */true, true, false,
		/* i=3 */false, true, false,
		/* i=4 */false, true, false,
		/* i=5 */false, false, true));

		// return test
		check("res8", Arrays.asList("0123", "123", "23", "3", "4", "3"));		
	}
	
	public void test_while() {
		doCompile("test_while");
		// simple while
		check("res1", Arrays.asList(0, 1, 2));
		// continue
		check("res2", Arrays.asList(0, 2));
		// break
		check("res3", Arrays.asList(0));
	}

	public void test_do_while() {
		doCompile("test_do_while");
		// simple while
		check("res1", Arrays.asList(0, 1, 2));
		// continue
		check("res2", Arrays.asList(0, null, 2));
		// break
		check("res3", Arrays.asList(0));
	}
	
	public void test_for() {
		doCompile("test_for");
		
		// simple loop
		check("res1", Arrays.asList(0,1,2));
		// continue
		check("res2", Arrays.asList(0,null,2));
		// break
		check("res3", Arrays.asList(0));
		// empty init
		check("res4", Arrays.asList(0,1,2));
		// empty update
		check("res5", Arrays.asList(0,1,2));
		// empty final condition
		check("res6", Arrays.asList(0,1,2));
		// all conditions empty
		check("res7", Arrays.asList(0,1,2));
	}

	public void test_for1() {
		//5125: CTL2: "for" cycle is EXTREMELY memory consuming
		doCompile("test_for1");

		checkEquals("counter", "COUNT");
	}

	public void test_foreach() {
		doCompile("test_foreach");
		check("intRes", Arrays.asList(VALUE_VALUE));
		check("longRes", Arrays.asList(BORN_MILLISEC_VALUE));
		check("doubleRes", Arrays.asList(AGE_VALUE));
		check("decimalRes", Arrays.asList(CURRENCY_VALUE));
		check("booleanRes", Arrays.asList(FLAG_VALUE));
		check("stringRes", Arrays.asList(NAME_VALUE, CITY_VALUE));
		check("dateRes", Arrays.asList(BORN_VALUE));
	}
	
	public void test_return(){
		doCompile("test_return");
		check("lhs", Integer.valueOf(1));
		check("rhs", Integer.valueOf(2));
		check("res", Integer.valueOf(3));
	}
	
	public void test_return_incorrect() {
		doCompileExpectError("test_return_incorrect", "Can't convert from 'string' to 'integer'");
	}
	
	public void test_overloading() {
		doCompile("test_overloading");
		check("res1", Integer.valueOf(3));
		check("res2", "Memento mori");
	}
	
	
	public void test_overloading_incorrect() {
		doCompileExpectErrors("test_overloading_incorrect", Arrays.asList(
				"Duplicate function 'integer sum(integer, integer)'",
				"Duplicate function 'integer sum(integer, integer)'"));
	}
	
	//Test case for 4038
	public void test_function_parameter_without_type() {
		doCompileExpectError("test_function_parameter_without_type", "Syntax error on token ')'");
	}
	
	public void test_duplicate_import() {
		URL importLoc = getClass().getSuperclass().getResource("test_duplicate_import.ctl");
		String expStr = "import '" + importLoc + "';\n";
		expStr += "import '" + importLoc + "';\n";
		
		doCompile(expStr, "test_duplicate_import");
	}
	
	/*TODO:
	 * public void test_invalid_import() {
		URL importLoc = getClass().getResource("test_duplicate_import.ctl");
		String expStr = "import '/a/b/c/d/e/f/g/h/i/j/k/l/m';\n";
		expStr += expStr;
		
		doCompileExpectError(expStr, "test_invalid_import", Arrays.asList("TODO: Unknown error"));
		//doCompileExpectError(expStr, "test_duplicate_import", Arrays.asList("TODO: Unknown error"));
	}	 */

	
	public void test_built_in_functions(){
		doCompile("test_built_in_functions");
		
		check("notNullValue", Integer.valueOf(1));
		checkNull("nullValue");
		check("isNullRes1", false);
		check("isNullRes2", true);
		assertEquals("nvlRes1", getVariable("notNullValue"), getVariable("nvlRes1"));
		check("nvlRes2", Integer.valueOf(2));
		assertEquals("nvl2Res1", getVariable("notNullValue"), getVariable("nvl2Res1"));
		check("nvl2Res2", Integer.valueOf(2));
		check("iifRes1", Integer.valueOf(2));
		check("iifRes2", Integer.valueOf(1));
	}
	
	public void test_mapping(){
		doCompile("test_mapping");
		// simple mappings
		assertEquals("Name", NAME_VALUE, outputRecords[0].getField("Name").getValue().toString());
		assertEquals("Age", AGE_VALUE, outputRecords[0].getField("Age").getValue());
		assertEquals("City", CITY_VALUE, outputRecords[0].getField("City").getValue().toString());
		assertEquals("Born", BORN_VALUE, outputRecords[0].getField("Born").getValue());
		
		// * mapping
		assertTrue(recordEquals(inputRecords[1], outputRecords[1]));
	}

	public void test_mapping_null_values() {
		doCompile("test_mapping_null_values");

		assertTrue(recordEquals(inputRecords[2], outputRecords[0]));
	}

	public void test_copyByName() {
		doCompile("test_copyByName");
		assertEquals("Field1", null, outputRecords[3].getField("Field1").getValue());
		assertEquals("Age", AGE_VALUE, outputRecords[3].getField("Age").getValue());
		assertEquals("City", CITY_VALUE, outputRecords[3].getField("City").getValue().toString());
	}

	public void test_copyByName_assignment() {
		doCompile("test_copyByName_assignment");
		assertEquals("Field1", null, outputRecords[3].getField("Field1").getValue());
		assertEquals("Age", AGE_VALUE, outputRecords[3].getField("Age").getValue());
		assertEquals("City", CITY_VALUE, outputRecords[3].getField("City").getValue().toString());
	}

	public void test_copyByName_assignment1() {
		doCompile("test_copyByName_assignment1");
		assertEquals("Field1", null, outputRecords[3].getField("Field1").getValue());
		assertEquals("Age", null, outputRecords[3].getField("Age").getValue());
		assertEquals("City", null, outputRecords[3].getField("City").getValue());
	}

	public void test_sequence(){
		doCompile("test_sequence");
		check("intRes", Arrays.asList(0,1,2));
		check("longRes", Arrays.asList(Long.valueOf(0),Long.valueOf(1),Long.valueOf(2)));
		check("stringRes", Arrays.asList("0","1","2"));
		check("intCurrent", Integer.valueOf(2));
		check("longCurrent", Long.valueOf(2));
		check("stringCurrent", "2");
	}
	
	//TODO: If this test fails please double check whether the test is correct?
	public void test_lookup(){
        doCompile("test_lookup");
		check("alphaResult", Arrays.asList("Andorra la Vella","Andorra la Vella"));
		check("bravoResult", Arrays.asList("Bruxelles","Bruxelles"));
		check("charlieResult", Arrays.asList("Chamonix","Chomutov","Chamonix","Chomutov"));
		check("countResult", Arrays.asList(2,2));
	}
	
//------------------------- ContainerLib Tests---------------------
	
	public void test_containerlib_append() {
		doCompile("test_containerlib_append");
		
		check("appendElem", Integer.valueOf(10));
		check("appendList", Arrays.asList(1, 2, 3, 4, 5, 10));
	}

	@SuppressWarnings("unchecked")
	public void test_containerlib_clear() {
		doCompile("test_containerlib_clear");

		assertTrue(((List<Integer>) getVariable("clearList")).isEmpty());
	}
	
	public void test_containerlib_copy() {
		doCompile("test_containerlib_copy");

		check("copyList", Arrays.asList(1, 2, 3, 4, 5));
		check("returnedList", Arrays.asList(1, 2, 3, 4, 5));
	}

	public void test_containerlib_insert() {
		doCompile("test_containerlib_insert");

		check("insertElem", Integer.valueOf(7));
		check("insertIndex", Integer.valueOf(3));
		check("insertList", Arrays.asList(1, 2, 3, 7, 4, 5));
		check("insertList1", Arrays.asList(7, 8, 11, 10, 11));
		check("insertList2", Arrays.asList(7, 8, 10, 9, 11));
	}
	
	public void test_containerlib_isEmpty() {
		doCompile("test_containerlib_isEmpty");
		
		check("emptyMap", true);
		check("fullMap", false);
		check("emptyList", true);
		check("fullList", false);
	}

	public void test_containerlib_poll() {
		doCompile("test_containerlib_poll");

		check("pollElem", Integer.valueOf(1));
		check("pollList", Arrays.asList(2, 3, 4, 5));
	}

	public void test_containerlib_pop() {
		doCompile("test_containerlib_pop");

		check("popElem", Integer.valueOf(5));
		check("popList", Arrays.asList(1, 2, 3, 4));
	}

	@SuppressWarnings("unchecked")
	public void test_containerlib_push() {
		doCompile("test_containerlib_push");

		check("pushElem", Integer.valueOf(6));
		check("pushList", Arrays.asList(1, 2, 3, 4, 5, 6));
		
		// there is hardly any way to get an instance of DataRecord
		// hence we just check if the list has correct size
		// and if its elements have correct metadata
		List<DataRecord> recordList = (List<DataRecord>) getVariable("recordList");
		List<DataRecordMetadata> mdList = Arrays.asList(
				graph.getDataRecordMetadata(OUTPUT_1),
				graph.getDataRecordMetadata(INPUT_2),
				graph.getDataRecordMetadata(INPUT_1)
		);
		assertEquals(mdList.size(), recordList.size());
		for (int i = 0; i < mdList.size(); i++) {
			assertEquals(mdList.get(i), recordList.get(i).getMetadata());
		}
	}

	public void test_containerlib_remove() {
		doCompile("test_containerlib_remove");

		check("removeElem", Integer.valueOf(3));
		check("removeIndex", Integer.valueOf(2));
		check("removeList", Arrays.asList(1, 2, 4, 5));
	}

	public void test_containerlib_reverse() {
		doCompile("test_containerlib_reverse");

		check("reverseList", Arrays.asList(5, 4, 3, 2, 1));
	}

	public void test_containerlib_sort() {
		doCompile("test_containerlib_sort");

		check("sortList", Arrays.asList(1, 1, 2, 3, 5));
	}
//---------------------- StringLib Tests ------------------------	
	
	public void test_stringlib_cache() {
		doCompile("test_stringlib_cache");
		check("rep1", "The cat says meow. All cats say meow.");
		check("rep2", "The cat says meow. All cats say meow.");
		check("rep3", "The cat says meow. All cats say meow.");
		
		check("find1", Arrays.asList("to", "to", "to", "tro", "to"));
		check("find2", Arrays.asList("to", "to", "to", "tro", "to"));
		check("find3", Arrays.asList("to", "to", "to", "tro", "to"));
		
		check("split1", Arrays.asList("one", "two", "three", "four", "five"));
		check("split2", Arrays.asList("one", "two", "three", "four", "five"));
		check("split3", Arrays.asList("one", "two", "three", "four", "five"));
		
		check("chop01", "ting soming choping function");
		check("chop02", "ting soming choping function");
		check("chop03", "ting soming choping function");
		
		check("chop11", "testing end of lines cutting");
		check("chop12", "testing end of lines cutting");
		
	}
	
	public void test_stringlib_charAt() {
		doCompile("test_stringlib_charAt");
		String input = "The QUICk !!$  broWn fox 	juMPS over the lazy DOG	";
		String[] expected = new String[input.length()];
		for (int i = 0; i < expected.length; i++) {
			expected[i] = String.valueOf(input.charAt(i));
		}
		check("chars", Arrays.asList(expected));
	}
	
	public void test_stringlib_concat() {
		doCompile("test_stringlib_concat");
		
		final SimpleDateFormat format = new SimpleDateFormat();
		format.applyPattern("yyyy MMM dd");
				
		check("concat", "");
		check("concat1", "ello hi   ELLO 2,today is " + format.format(new Date()));
	}
	
	public void test_stringlib_countChar() {
		doCompile("test_stringlib_countChar");
		check("charCount", 3);
	}
	
	public void test_stringlib_cut() {
		doCompile("test_stringlib_cut");
		check("cutInput", Arrays.asList("a", "1edf", "h3ijk"));
	}
	
	public void test_stringlib_editDistance() {
		doCompile("test_stringlib_editDistance");
		check("dist", 1);
		check("dist1", 1);
		check("dist2", 0);
		check("dist5", 1);
		check("dist3", 1);
		check("dist4", 0);
		check("dist6", 4);
		check("dist7", 5);
		check("dist8", 0);
		check("dist9", 0);
	}
	
	public void test_stringlib_find() {
		doCompile("test_stringlib_find");
		check("findList", Arrays.asList("The quick br", "wn f", "x jumps ", "ver the lazy d", "g"));
	}
	
	public void test_stringlib_join() {
		doCompile("test_stringlib_join");
		//check("joinedString", "Bagr,3,3.5641,-87L,CTL2");
		check("joinedString1", "3=0.1\"80=5455.987\"-5=5455.987");
		check("joinedString2", "5.0♫54.65♫67.0♫231.0");
		//check("joinedString3", "5☺54☺65☺67☺231☺80=5455.987☺-5=5455.987☺3=0.1☺CTL2☺42");
	}
	
	public void test_stringlib_left() {
		doCompile("test_stringlib_left");
		check("lef", "The q");
		check("padded", "The q   ");
		check("notPadded", "The q");
		check("lef2", "The quick brown fox jumps over the lazy dog");
	}
	
	public void test_stringlib_length() {
		doCompile("test_stringlib_length");
		check("lenght1", new BigDecimal(50));
		check("lenghtByte", 18);
		
		check("stringLength", 8);
		check("listLength", 8);
		check("mapLength", 3);
		check("recordLength", 9);
	}
	
	public void test_stringlib_lowerCase() {
		doCompile("test_stringlib_lowerCase");
		check("lower", "the quick !!$  brown fox jumps over the lazy dog bagr  ");
	}
	
	public void test_stringlib_matches() {
		doCompile("test_stringlib_matches");
		check("matches1", true);
		check("matches2", true);
		check("matches3", false);
		check("matches4", true);
		check("matches5", false);
	}
	
	public void test_stringlib_metaphone() {
		doCompile("test_stringlib_metaphone");
		check("metaphone1", "XRS");
		check("metaphone2", "KWNTLN");
		check("metaphone3", "KWNT");
	}
	
	public void test_stringlib_nysiis() {
		doCompile("test_stringlib_nysiis");
		check("nysiis1", "CAP");
		check("nysiis2", "CAP");
	}
	
	public void test_stringlib_replace() {
		doCompile("test_stringlib_replace");
		
		final SimpleDateFormat format = new SimpleDateFormat();
		format.applyPattern("yyyy MMM dd");
		
		check("rep", format.format(new Date()).replaceAll("[lL]", "t"));
		check("rep1", "The cat says meow. All cats say meow.");
		
	}
	
	public void test_stringlib_right() {
		doCompile("test_stringlib_right");
		check("righ", "y dog");
		check("rightNotPadded", "y dog");
		check("rightPadded", "y dog");
		check("padded", "   y dog");
		check("notPadded", "y dog");
		check("short", "Dog");
		check("shortNotPadded", "Dog");
		check("shortPadded", "     Dog");
	}
	
	public void test_stringlib_soundex() {
		doCompile("test_stringlib_soundex");
		check("soundex1", "W630");
		check("soundex2", "W643");
	}
	
	public void test_stringlib_split() {
		doCompile("test_stringlib_split");
		check("split1", Arrays.asList("The quick br", "wn f", "", " jumps " , "ver the lazy d", "g"));
	}
	
	public void test_stringlib_substring() {
		doCompile("test_stringlib_substring");
		check("subs", "UICk ");
	}
	
	public void test_stringlib_trim() {
		doCompile("test_stringlib_trim");
		check("trim1", "im  The QUICk !!$  broWn fox juMPS over the lazy DOG");
	}
	
	public void test_stringlib_upperCase() {
		doCompile("test_stringlib_upperCase");
		check("upper", "THE QUICK !!$  BROWN FOX JUMPS OVER THE LAZY DOG BAGR	");
	}

	public void test_stringlib_isFormat() {
		doCompile("test_stringlib_isFormat");
		check("test", "test");
		check("isBlank", Boolean.FALSE);
		check("blank", "");
		checkNull("nullValue");
		check("isBlank2", true);
		check("isAscii1", true);
		check("isAscii2", false);
		check("isNumber", false);
		check("isNumber1", false);
		check("isNumber2", true);
		check("isNumber3", true);
		check("isNumber4", false);
		check("isNumber5", true);
		check("isNumber6", true);
		check("isInteger", false);
		check("isInteger1", false);
		check("isInteger2", false);
		check("isInteger3", true);
		check("isLong", true);
		check("isLong1", false);
		check("isLong2", false);
		check("isDate", true);
		check("isDate1", false);
		// "kk" allows hour to be 1-24 (as opposed to HH allowing hour to be 0-23)
		check("isDate2", true);
		check("isDate3", true);
		check("isDate4", false);
		check("isDate5", true);
		check("isDate6", true);
		check("isDate7", false);
		// illegal month: 15
		check("isDate9", false);
		check("isDate10", false);
		check("isDate11", false);
		check("isDate12", true);
		check("isDate13", false);
		// 24 is an illegal value for pattern HH (it allows only 0-23)
		check("isDate14", false);
		// empty string: invalid
		check("isDate15", false);
	}
	
	public void test_stringlib_removeBlankSpace() {
		String expStr = 
			"string r1;\n" +
			"function integer transform() {\n" +
				"r1=removeBlankSpace(\"" + StringUtils.specCharToString(" a	b\nc\rd   e \u000Cf\r\n") +	"\");\n" +
				"printErr(r1);\n" +
				"return 0;\n" +
			"}\n";
		doCompile(expStr, "test_removeBlankSpace");
		check("r1", "abcdef");
	}
	
	public void test_stringlib_removeNonPrintable() {
		doCompile("test_stringlib_removeNonPrintable");
		check("nonPrintableRemoved", "AHOJ");
	}
	
	public void test_stringlib_getAlphanumericChars() {
		String expStr = 
			"string an1;\n" +
			"string an2;\n" +
			"string an3;\n" +
			"string an4;\n" +
			"function integer transform() {\n" +
				"an1=getAlphanumericChars(\"" + StringUtils.specCharToString(" a	1b\nc\rd \b  e \u000C2f\r\n") + "\");\n" +
				"printErr(an1);\n" +
				"an2=getAlphanumericChars(\"" + StringUtils.specCharToString(" a	1b\nc\rd \b  e \u000C2f\r\n") + "\",true,true);\n" +
				"printErr(an2);\n" +
				"an3=getAlphanumericChars(\"" + StringUtils.specCharToString(" a	1b\nc\rd \b  e \u000C2f\r\n") + "\",true,false);\n" +
				"printErr(an3);\n" +
				"an4=getAlphanumericChars(\"" + StringUtils.specCharToString(" a	1b\nc\rd \b  e \u000C2f\r\n") + "\",false,true);\n" +
				"printErr(an4);\n" +
				"return 0;\n" +
			"}\n";
		doCompile(expStr, "test_getAlphanumericChars");

		check("an1", "a1bcde2f");
		check("an2", "a1bcde2f");
		check("an3", "abcdef");
		check("an4", "12");
	}
	
	public void test_stringlib_indexOf(){
        doCompile("test_stringlib_indexOf");
        check("index",2);
		check("index1",9);
		check("index2",0);
		check("index3",-1);
		check("index4",6);
	}
	
	public void test_stringlib_removeDiacritic(){
        doCompile("test_stringlib_removeDiacritic");
        check("test","tescik");
    	check("test1","zabicka");
	}
	
	public void test_stringlib_translate(){
        doCompile("test_stringlib_translate");
        check("trans","hippi");
		check("trans1","hipp");
		check("trans2","hippi");
		check("trans3","");
		check("trans4","y lanuaX nXXd thX lXttXr X");
	}
    
	public void test_stringlib_chop() {
		doCompile("test_stringlib_chop");
		check("s1", "hello");
		check("s6", "hello");
		check("s5", "hello");
		check("s2", "hello");
		check("s7", "helloworld");
		check("s3", "hello ");
		check("s4", "hello");
	}
	
//-------------------------- MathLib Tests ------------------------
	public void test_bitwise_or() {
		doCompile("test_bitwise_or");
		check("resultInt1", 1);
		check("resultInt2", 1);
		check("resultInt3", 3);
		check("resultInt4", 3);
		check("resultLong1", 1l);
		check("resultLong2", 1l);
		check("resultLong3", 3l);
		check("resultLong4", 3l);
	}

	public void test_bitwise_and() {
		doCompile("test_bitwise_and");
		check("resultInt1", 0);
		check("resultInt2", 1);
		check("resultInt3", 0);
		check("resultInt4", 1);
		check("resultLong1", 0l);
		check("resultLong2", 1l);
		check("resultLong3", 0l);
		check("resultLong4", 1l);
	}

	public void test_bitwise_xor() {
		doCompile("test_bitwise_xor");
		check("resultInt1", 1);
		check("resultInt2", 0);
		check("resultInt3", 3);
		check("resultInt4", 2);
		check("resultLong1", 1l);
		check("resultLong2", 0l);
		check("resultLong3", 3l);
		check("resultLong4", 2l);
	}

	public void test_bitwise_lshift() {
		doCompile("test_bitwise_lshift");
		check("resultInt1", 2);
		check("resultInt2", 4);
		check("resultInt3", 10);
		check("resultInt4", 20);
		check("resultLong1", 2l);
		check("resultLong2", 4l);
		check("resultLong3", 10l);
		check("resultLong4", 20l);
	}

	public void test_bitwise_rshift() {
		doCompile("test_bitwise_rshift");
		check("resultInt1", 2);
		check("resultInt2", 0);
		check("resultInt3", 4);
		check("resultInt4", 2);
		check("resultLong1", 2l);
		check("resultLong2", 0l);
		check("resultLong3", 4l);
		check("resultLong4", 2l);
	}

	public void test_bitwise_negate() {
		doCompile("test_bitwise_negate");
		check("resultInt", -59081717);
		check("resultLong", -3321654987654105969L);
	}

	public void test_set_bit() {
		doCompile("test_set_bit");
		check("resultInt1", 0x2FF);
		check("resultInt2", 0xFB);
		check("resultLong1", 0x4000000000000l);
		check("resultLong2", 0xFFDFFFFFFFFFFFFl);
		check("resultBool1", true);
		check("resultBool2", false);
		check("resultBool3", true);
		check("resultBool4", false);
	}	
	
	public void test_mathlib_abs() {
		doCompile("test_mathlib_abs");
		check("absIntegerPlus", new Integer(10));
		check("absIntegerMinus", new Integer(1));
		check("absLongPlus", new Long(10));
		check("absLongMinus", new Long(1));
		check("absDoublePlus", new Double(10.0));
		check("absDoubleMinus", new Double(1.0));
		check("absDecimalPlus", new BigDecimal(5.0));
		check("absDecimalMinus", new BigDecimal(5.0));
	}
	
	public void test_mathlib_ceil() {
		doCompile("test_mathlib_ceil");
		check("ceil1", -3.0);
		
		check("intResult", Arrays.asList(2.0, 3.0));
		check("longResult", Arrays.asList(2.0, 3.0));
		check("doubleResult", Arrays.asList(3.0, -3.0));
		check("decimalResult", Arrays.asList(3.0, -3.0));
	}
	
	public void test_mathlib_e() {
		doCompile("test_mathlib_e");
		check("varE", Math.E);
	}
	
	public void test_mathlib_exp() {
		doCompile("test_mathlib_exp");
		check("ex", Math.exp(1.123));
	}
	
	public void test_mathlib_floor() {
		doCompile("test_mathlib_floor");
		check("floor1", -4.0);
		
		check("intResult", Arrays.asList(2.0, 3.0));
		check("longResult", Arrays.asList(2.0, 3.0));
		check("doubleResult", Arrays.asList(2.0, -4.0));
		check("decimalResult", Arrays.asList(2.0, -4.0));
	}
	
	public void test_mathlib_log() {
		doCompile("test_mathlib_log");
		check("ln", Math.log(3));
	}
	
	public void test_mathlib_log10() {
		doCompile("test_mathlib_log10");
		check("varLog10", Math.log10(3));
	}
	
	public void test_mathlib_pi() {
		doCompile("test_mathlib_pi");
		check("varPi", Math.PI);
	}
	
	public void test_mathlib_pow() {
		doCompile("test_mathlib_pow");
		check("power1", Math.pow(3,1.2));
		check("power2", Double.NaN);
		
		check("intResult", Arrays.asList(8d, 8d, 8d, 8d));
		check("longResult", Arrays.asList(8d, 8d, 8d, 8d));
		check("doubleResult", Arrays.asList(8d, 8d, 8d, 8d));
		check("decimalResult", Arrays.asList(8d, 8d, 8d, 8d));
	}
	
	public void test_mathlib_round() {
		doCompile("test_mathlib_round");
		check("round1", -4l);
		
		check("intResult", Arrays.asList(2l, 3l));
		check("longResult", Arrays.asList(2l, 3l));
		check("doubleResult", Arrays.asList(2l, 4l));
		check("decimalResult", Arrays.asList(2l, 4l));
	}
	
	public void test_mathlib_sqrt() {
		doCompile("test_mathlib_sqrt");
		check("sqrtPi", Math.sqrt(Math.PI));
		check("sqrt9", Math.sqrt(9));
	}

//-------------------------DateLib tests----------------------
	
	public void test_datelib_cache() {
		doCompile("test_datelib_cache");
		
		check("b11", true);
		check("b12", true);
		check("b21", true);
		check("b22", true);
		check("b31", true);
		check("b32", true);
		check("b41", true);
		check("b42", true);		
		
		checkEquals("date3", "date3d");
		checkEquals("date4", "date4d");
		checkEquals("date7", "date7d");
		checkEquals("date8", "date8d");
	}
	
	public void test_datelib_trunc() {
		doCompile("test_datelib_trunc");
		check("truncDate", new GregorianCalendar(2004, 00, 02).getTime());
	}
	
	public void test_datelib_truncDate() {
		doCompile("test_datelib_truncDate");
		Calendar cal = Calendar.getInstance();
		cal.setTime(BORN_VALUE);
		int[] portion = new int[]{cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND),cal.get(Calendar.MILLISECOND)};
    	cal.clear();
    	cal.set(Calendar.HOUR_OF_DAY, portion[0]);
    	cal.set(Calendar.MINUTE, portion[1]);
    	cal.set(Calendar.SECOND, portion[2]);
    	cal.set(Calendar.MILLISECOND, portion[3]);
        check("truncBornDate", cal.getTime());
	}
	
	public void test_datelib_today() {
		doCompile("test_datelib_today");
		Date expectedDate = new Date();
		//the returned date does not need to be exactly the same date which is in expectedData variable
		//let say 1000ms is tolerance for equality
		assertTrue("todayDate", Math.abs(expectedDate.getTime() - ((Date) getVariable("todayDate")).getTime()) < 1000);
	}
	
	public void test_datelib_zeroDate() {
		doCompile("test_datelib_zeroDate");
		check("zeroDate", new Date(0));
	}
	
	public void test_datelib_dateDiff() {
		doCompile("test_datelib_dateDiff");
		Calendar cal = Calendar.getInstance();
		
		cal.setTime(BORN_VALUE);
		long diffYears = cal.get(Calendar.YEAR);
		cal.setTime(new Date());
		diffYears -= cal.get(Calendar.YEAR);
		check("ddiff", diffYears);
		
		long[] results = {1, 12, 52, 365, 8760, 525600, 31536000, 31536000000L};
		String[] vars = {"ddiffYears", "ddiffMonths", "ddiffWeeks", "ddiffDays", "ddiffHours", "ddiffMinutes", "ddiffSeconds", "ddiffMilliseconds"};
		
		for (int i = 0; i < results.length; i++) {
			check(vars[i], results[i]);
		}
	}
	
	public void test_datelib_dateAdd() {
		doCompile("test_datelib_dateAdd");
		check("datum", new Date(BORN_MILLISEC_VALUE + 100));
	}
	
	public void test_datelib_extractTime() {
		doCompile("test_datelib_extractTime");
		Calendar cal = Calendar.getInstance();
		cal.setTime(BORN_VALUE);
		int[] portion = new int[]{cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND),cal.get(Calendar.MILLISECOND)};
    	cal.clear();
    	cal.set(Calendar.HOUR_OF_DAY, portion[0]);
    	cal.set(Calendar.MINUTE, portion[1]);
    	cal.set(Calendar.SECOND, portion[2]);
    	cal.set(Calendar.MILLISECOND, portion[3]);
    	check("bornExtractTime", cal.getTime());
	}
	
	public void test_datelib_extractDate() {
		doCompile("test_datelib_extractDate");
		
		Calendar cal = Calendar.getInstance();
		cal.setTime(BORN_VALUE);
		int[] portion = new int[]{cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH), cal.get(Calendar.YEAR)};
    	cal.clear();
    	cal.set(Calendar.DAY_OF_MONTH, portion[0]);
    	cal.set(Calendar.MONTH, portion[1]);
    	cal.set(Calendar.YEAR, portion[2]);
    	check("bornExtractDate", cal.getTime());
	}
//-----------------Convert Lib tests-----------------------
	public void test_convertlib_cache() {
		// set default locale to en.US so the date is formatted uniformly on all systems
		Locale.setDefault(Locale.US);

		doCompile("test_convertlib_cache");
		Calendar cal = Calendar.getInstance();
		cal.set(2000, 6, 20, 0, 0, 0);
		cal.set(Calendar.MILLISECOND, 0);
		
		Date checkDate = cal.getTime();
		
		final SimpleDateFormat format = new SimpleDateFormat();
		format.applyPattern("yyyy MMM dd");
		
		check("sdate1", format.format(new Date()));
		check("sdate2", format.format(new Date()));
		
		check("date01", checkDate);
		check("date02", checkDate);
		check("date03", checkDate);
		check("date04", checkDate);
		check("date11", checkDate);
		check("date12", checkDate);
		check("date13", checkDate);
	}
	
	public void test_convertlib_base64byte() {
		doCompile("test_convertlib_base64byte");
		assertTrue(Arrays.equals((byte[])getVariable("base64input"), Base64.decode("The quick brown fox jumps over the lazy dog")));
	}
	
	public void test_convertlib_bits2str() {
		doCompile("test_convertlib_bits2str");
		check("bitsAsString1", "00000000");
		check("bitsAsString2", "11111111");
		check("bitsAsString3", "010100000100110110100000");
	}
	
	public void test_convertlib_bool2num() {
		doCompile("test_convertlib_bool2num");
		check("resultTrue", 1);
		check("resultFalse", 0);
	}
	
	public void test_convertlib_byte2base64() {
		doCompile("test_convertlib_byte2base64");
		check("inputBase64", Base64.encodeBytes("Abeceda zedla deda".getBytes()));
	}
	
	public void test_convertlib_byte2hex() {
		doCompile("test_convertlib_byte2hex");
		check("hexResult", "41626563656461207a65646c612064656461");
	}
	
	public void test_convertlib_date2long() {
		doCompile("test_convertlib_date2long");
		check("bornDate", BORN_MILLISEC_VALUE);
		check("zeroDate", 0l);
	}
	
	public void test_convertlib_date2num() {
		doCompile("test_convertlib_date2num");
		Calendar cal = Calendar.getInstance();
		cal.setTime(BORN_VALUE);
		check("yearDate", 1987);
		check("monthDate", 5);
		check("secondDate", 0);
		check("yearBorn", cal.get(Calendar.YEAR));
		check("monthBorn", cal.get(Calendar.MONTH) + 1); //Calendar enumerates months from 0, not 1;
		check("secondBorn", cal.get(Calendar.SECOND));
		check("yearMin", 1970);
		check("monthMin", 1);
		check("weekMin", 1);
		check("weekMinCs", 1);
		check("dayMin", 1);
		check("hourMin", 1); //TODO: check!
		check("minuteMin", 0);
		check("secondMin", 0);
		check("millisecMin", 0);
	}
	
	public void test_convertlib_date2str() {
		doCompile("test_convertlib_date2str");
		check("inputDate", "1987:05:12");
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd");
		check("bornDate", sdf.format(BORN_VALUE));

		SimpleDateFormat sdfCZ = new SimpleDateFormat("yyyy:MMMM:dd", MiscUtils.createLocale("cs.CZ"));
		check("czechBornDate", sdfCZ.format(BORN_VALUE));

		SimpleDateFormat sdfEN = new SimpleDateFormat("yyyy:MMMM:dd", MiscUtils.createLocale("en"));
		check("englishBornDate", sdfEN.format(BORN_VALUE));
	}
	
	public void test_convertlib_decimal2double() {
		doCompile("test_convertlib_decimal2double");
		check("toDouble", 0.007d);
	}
	
	public void test_convertlib_decimal2integer() {
		doCompile("test_convertlib_decimal2integer");
		check("toInteger", 0);
		check("toInteger2", -500);
		check("toInteger3", 1000000);
	}
	
	public void test_convertlib_decimal2long() {
		doCompile("test_convertlib_decimal2long");
		check("toLong", 0l);
		check("toLong2", -500l);
		check("toLong3", 10000000000l);
	}
	
	public void test_convertlib_double2integer() {
		doCompile("test_convertlib_double2integer");
		check("toInteger", 0);
		check("toInteger2", -500);
		check("toInteger3", 1000000);
	}
	
	public void test_convertlib_double2long() {
		doCompile("test_convertlib_double2long");
		check("toLong", 0l);
		check("toLong2", -500l);
		check("toLong3", 10000000000l);
	}

	public void test_convertlib_getFieldName() {
		doCompile("test_convertlib_getFieldName");
		check("fieldNames",Arrays.asList("Name", "Age", "City", "Born", "BornMillisec", "Value", "Flag", "ByteArray", "Currency"));
	}

	public void test_convertlib_getFieldType() {
		doCompile("test_convertlib_getFieldType");
		check("fieldTypes",Arrays.asList(DataFieldMetadata.STRING_TYPE, DataFieldMetadata.NUMERIC_TYPE, DataFieldMetadata.STRING_TYPE,
				DataFieldMetadata.DATE_TYPE, DataFieldMetadata.LONG_TYPE, DataFieldMetadata.INTEGER_TYPE, DataFieldMetadata.BOOLEAN_TYPE,
				DataFieldMetadata.BYTE_TYPE, DataFieldMetadata.DECIMAL_TYPE));
	}
	
	public void test_convertlib_hex2byte() {
		doCompile("test_convertlib_hex2byte");
		assertTrue(Arrays.equals((byte[])getVariable("fromHex"), BYTEARRAY_VALUE));
	}
	
	public void test_convertlib_long2date() {
		doCompile("test_convertlib_long2date");
		check("fromLong1", new Date(0));
		check("fromLong2", new Date(50000000000L));
		check("fromLong3", new Date(-5000L));
	}
	
	public void test_convertlib_long2integer() {
		doCompile("test_convertlib_long2integer");
		check("fromLong1", 10);
		check("fromLong2", -10);
	}
	
	
	public void test_convertlib_long2packDecimal() {
		doCompile("test_convertlib_long2packDecimal");
		assertTrue(Arrays.equals((byte[])getVariable("packedLong"), new byte[] {5, 0, 12}));
	}
	
	public void test_convertlib_md5() {
		doCompile("test_convertlib_md5");
		assertTrue(Arrays.equals((byte[])getVariable("md5Hash1"), Digest.digest(DigestType.MD5, "The quick brown fox jumps over the lazy dog")));
		assertTrue(Arrays.equals((byte[])getVariable("md5Hash2"), Digest.digest(DigestType.MD5, BYTEARRAY_VALUE)));
	}

	public void test_convertlib_num2bool() {
		doCompile("test_convertlib_num2bool");
		check("integerTrue", true);
		check("integerFalse", false);
		check("longTrue", true);
		check("longFalse", false);
		check("doubleTrue", true);
		check("doubleFalse", false);
		check("decimalTrue", true);
		check("decimalFalse", false);
	}
	
	public void test_convertlib_num2str() {
		System.out.println("num2str() test:");
		doCompile("test_convertlib_num2str");

		check("intOutput", Arrays.asList("16", "10000", "20", "10", "1.235E3", "12 350 001 Kcs"));
		check("longOutput", Arrays.asList("16", "10000", "20", "10", "1.235E13", "12 350 001 Kcs"));
		check("doubleOutput", Arrays.asList("16.16", "0x1.028f5c28f5c29p4", "1.23548E3", "12 350 001,1 Kcs"));
		check("decimalOutput", Arrays.asList("16.16", "1235.44", "12 350 001,1 Kcs"));
	}

	public void test_convertlib_packdecimal2long() {
		doCompile("test_convertlib_packDecimal2long");
		check("unpackedLong", PackedDecimal.parse(BYTEARRAY_VALUE));
	}

	public void test_convertlib_sha() {
		doCompile("test_convertlib_sha");
		assertTrue(Arrays.equals((byte[])getVariable("shaHash1"), Digest.digest(DigestType.SHA, "The quick brown fox jumps over the lazy dog")));
		assertTrue(Arrays.equals((byte[])getVariable("shaHash2"), Digest.digest(DigestType.SHA, BYTEARRAY_VALUE)));
	}

	public void test_convertlib_str2bits() {
		doCompile("test_convertlib_str2bits");
		//TODO: uncomment -> test will pass, but is that correct?
		assertTrue(Arrays.equals((byte[]) getVariable("textAsBits1"), new byte[] {0/*, 0, 0, 0, 0, 0, 0, 0*/}));
		assertTrue(Arrays.equals((byte[]) getVariable("textAsBits2"), new byte[] {-1/*, 0, 0, 0, 0, 0, 0, 0*/}));
		assertTrue(Arrays.equals((byte[]) getVariable("textAsBits3"), new byte[] {10, -78, 5/*, 0, 0, 0, 0, 0*/}));
	}

	public void test_convertlib_str2bool() {
		doCompile("test_convertlib_str2bool");
		check("fromTrueString", true);
		check("fromFalseString", false);
	}

	public void test_convertlib_str2date() {
		doCompile("test_convertlib_str2date");
		
		Calendar cal = Calendar.getInstance();
		cal.set(2050, 4, 19, 0, 0, 0);
		cal.set(Calendar.MILLISECOND, 0);
		
		Date checkDate = cal.getTime();
		
		check("date1", checkDate);
		check("date2", checkDate);
	}

	public void test_convertlib_str2decimal() {
		doCompile("test_convertlib_str2decimal");
		check("parsedDecimal1", new BigDecimal("100.13"));
		check("parsedDecimal2", new BigDecimal("123123123.123"));
		check("parsedDecimal3", new BigDecimal("-350000.01"));
	}

	public void test_convertlib_str2double() {
		doCompile("test_convertlib_str2double");
		check("parsedDouble1", 100.13);
		check("parsedDouble2", 123123123.123);
		check("parsedDouble3", -350000.01);
	}

	public void test_convertlib_str2integer() {
		doCompile("test_convertlib_str2integer");
		check("parsedInteger1", 123456789);
		check("parsedInteger2", 123123);
		check("parsedInteger3", -350000);
		check("parsedInteger4", 419);
	}

	public void test_convertlib_str2long() {
		doCompile("test_convertlib_str2long");
		check("parsedLong1", 1234567890123L);
		check("parsedLong2", 123123123456789L);
		check("parsedLong3", -350000L);
		check("parsedLong4", 133L);
	}

	public void test_convertlib_toString() {
		doCompile("test_convertlib_toString");
		check("integerString", "10");
		check("longString", "110654321874");
		check("doubleString", "1.547874E-14");
		check("decimalString", "-6847521431.1545874");
		check("listString", "[not ALI A, not ALI B, not ALI D..., but, ALI H!]");
		check("mapString", "{1=Testing, 2=makes, 3=me, 4=crazy :-)}");
	}
	
	public void test_convertlib_str2byte() {
		doCompile("test_convertlib_str2byte");

		checkArray("utf8Hello", new byte[] { 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 33 });
		checkArray("utf8Horse", new byte[] { 80, -59, -103, -61, -83, 108, 105, -59, -95, 32, -59, -66, 108, 117, -59, -91, 111, 117, -60, -115, 107, -61, -67, 32, 107, -59, -81, -59, -120, 32, 112, -60, -101, 108, 32, -60, -113, -61, -95, 98, 108, 115, 107, -61, -87, 32, -61, -77, 100, 121 });
		checkArray("utf8Math", new byte[] { -62, -67, 32, -30, -123, -109, 32, -62, -68, 32, -30, -123, -107, 32, -30, -123, -103, 32, -30, -123, -101, 32, -30, -123, -108, 32, -30, -123, -106, 32, -62, -66, 32, -30, -123, -105, 32, -30, -123, -100, 32, -30, -123, -104, 32, -30, -126, -84, 32, -62, -78, 32, -62, -77, 32, -30, -128, -96, 32, -61, -105, 32, -30, -122, -112, 32, -30, -122, -110, 32, -30, -122, -108, 32, -30, -121, -110, 32, -30, -128, -90, 32, -30, -128, -80, 32, -50, -111, 32, -50, -110, 32, -30, -128, -109, 32, -50, -109, 32, -50, -108, 32, -30, -126, -84, 32, -50, -107, 32, -50, -106, 32, -49, -128, 32, -49, -127, 32, -49, -126, 32, -49, -125, 32, -49, -124, 32, -49, -123, 32, -49, -122, 32, -49, -121, 32, -49, -120, 32, -49, -119 });

		checkArray("utf16Hello", new byte[] { -2, -1, 0, 72, 0, 101, 0, 108, 0, 108, 0, 111, 0, 32, 0, 87, 0, 111, 0, 114, 0, 108, 0, 100, 0, 33 });
		checkArray("utf16Horse", new byte[] { -2, -1, 0, 80, 1, 89, 0, -19, 0, 108, 0, 105, 1, 97, 0, 32, 1, 126, 0, 108, 0, 117, 1, 101, 0, 111, 0, 117, 1, 13, 0, 107, 0, -3, 0, 32, 0, 107, 1, 111, 1, 72, 0, 32, 0, 112, 1, 27, 0, 108, 0, 32, 1, 15, 0, -31, 0, 98, 0, 108, 0, 115, 0, 107, 0, -23, 0, 32, 0, -13, 0, 100, 0, 121 });
		checkArray("utf16Math", new byte[] { -2, -1, 0, -67, 0, 32, 33, 83, 0, 32, 0, -68, 0, 32, 33, 85, 0, 32, 33, 89, 0, 32, 33, 91, 0, 32, 33, 84, 0, 32, 33, 86, 0, 32, 0, -66, 0, 32, 33, 87, 0, 32, 33, 92, 0, 32, 33, 88, 0, 32, 32, -84, 0, 32, 0, -78, 0, 32, 0, -77, 0, 32, 32, 32, 0, 32, 0, -41, 0, 32, 33, -112, 0, 32, 33, -110, 0, 32, 33, -108, 0, 32, 33, -46, 0, 32, 32, 38, 0, 32, 32, 48, 0, 32, 3, -111, 0, 32, 3, -110, 0, 32, 32, 19, 0, 32, 3, -109, 0, 32, 3, -108, 0, 32, 32, -84, 0, 32, 3, -107, 0, 32, 3, -106, 0, 32, 3, -64, 0, 32, 3, -63, 0, 32, 3, -62, 0, 32, 3, -61, 0, 32, 3, -60, 0, 32, 3, -59, 0, 32, 3, -58, 0, 32, 3, -57, 0, 32, 3, -56, 0, 32, 3, -55 });

		checkArray("macHello", new byte[] { 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 33 });
		checkArray("macHorse", new byte[] { 80, -34, -110, 108, 105, -28, 32, -20, 108, 117, -23, 111, 117, -117, 107, -7, 32, 107, -13, -53, 32, 112, -98, 108, 32, -109, -121, 98, 108, 115, 107, -114, 32, -105, 100, 121 });

		checkArray("asciiHello", new byte[] { 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 33 });

		checkArray("isoHello", new byte[] { 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 33 });
		checkArray("isoHorse", new byte[] { 80, -8, -19, 108, 105, -71, 32, -66, 108, 117, -69, 111, 117, -24, 107, -3, 32, 107, -7, -14, 32, 112, -20, 108, 32, -17, -31, 98, 108, 115, 107, -23, 32, -13, 100, 121 });

		checkArray("cpHello", new byte[] { 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 33 });
		checkArray("cpHorse", new byte[] { 80, -8, -19, 108, 105, -102, 32, -98, 108, 117, -99, 111, 117, -24, 107, -3, 32, 107, -7, -14, 32, 112, -20, 108, 32, -17, -31, 98, 108, 115, 107, -23, 32, -13, 100, 121 });
	}

	public void test_convertlib_byte2str() {
		doCompile("test_convertlib_byte2str");

		String hello = "Hello World!";
		String horse = "Příliš žluťoučký kůň pěl ďáblské ódy";
		String math = "½ ⅓ ¼ ⅕ ⅙ ⅛ ⅔ ⅖ ¾ ⅗ ⅜ ⅘ € ² ³ † × ← → ↔ ⇒ … ‰ Α Β – Γ Δ € Ε Ζ π ρ ς σ τ υ φ χ ψ ω";

		check("utf8Hello", hello);
		check("utf8Horse", horse);
		check("utf8Math", math);

		check("utf16Hello", hello);
		check("utf16Horse", horse);
		check("utf16Math", math);

		check("macHello", hello);
		check("macHorse", horse);

		check("asciiHello", hello);

		check("isoHello", hello);
		check("isoHorse", horse);

		check("cpHello", hello);
		check("cpHorse", horse);

	}

	public void test_conditional_fail() {
		doCompile("test_conditional_fail");
		check("result", 3);
	}
	
	
	public void test_expression_statement(){
		// test case for issue 4174
        doCompileExpectErrors("test_expression_statement", Arrays.asList("Syntax error, statement expected","Syntax error, statement expected"));
	}

	public void test_dictionary_read() {
		doCompile("test_dictionary_read");
		check("s", "Verdon");
		check("i", Integer.valueOf(211));
		check("l", Long.valueOf(226));
		check("d", BigDecimal.valueOf(239483061));
		check("n", Double.valueOf(934.2));
		check("a", new GregorianCalendar(1992, GregorianCalendar.AUGUST, 1).getTime());
		check("b", true);
		byte[] y = (byte[]) getVariable("y");
		assertEquals(10, y.length);
		assertEquals(89, y[9]);
		
		check("sNull", null);
		check("iNull", null);
		check("lNull", null);
		check("dNull", null);
		check("nNull", null);
		check("aNull", null);
		check("bNull", null);
		check("yNull", null);
	}

	public void test_dictionary_write() {
		doCompile("test_dictionary_write");
		assertEquals(832, graph.getDictionary().getValue("i") );
		assertEquals("Guil", graph.getDictionary().getValue("s"));
		assertEquals(Long.valueOf(540), graph.getDictionary().getValue("l"));
		assertEquals(BigDecimal.valueOf(621), graph.getDictionary().getValue("d"));
		assertEquals(934.2, graph.getDictionary().getValue("n"));
		assertEquals(new GregorianCalendar(1992, GregorianCalendar.DECEMBER, 2).getTime(), graph.getDictionary().getValue("a"));
		assertEquals(true, graph.getDictionary().getValue("b"));
		byte[] y = (byte[]) graph.getDictionary().getValue("y");
		assertEquals(2, y.length);
		assertEquals(18, y[0]);
		assertEquals(-94, y[1]);
		
	}

	public void test_dictionary_write_null() {
		doCompile("test_dictionary_write_null");
		assertEquals(null, graph.getDictionary().getValue("s"));
		assertEquals(null, graph.getDictionary().getValue("sVerdon"));
		assertEquals(null, graph.getDictionary().getValue("i") );
		assertEquals(null, graph.getDictionary().getValue("i211") );
		assertEquals(null, graph.getDictionary().getValue("l"));
		assertEquals(null, graph.getDictionary().getValue("l452"));
		assertEquals(null, graph.getDictionary().getValue("d"));
		assertEquals(null, graph.getDictionary().getValue("d621"));
		assertEquals(null, graph.getDictionary().getValue("n"));
		assertEquals(null, graph.getDictionary().getValue("n9342"));
		assertEquals(null, graph.getDictionary().getValue("a"));
		assertEquals(null, graph.getDictionary().getValue("a1992"));
		assertEquals(null, graph.getDictionary().getValue("b"));
		assertEquals(null, graph.getDictionary().getValue("bTrue"));
		assertEquals(null, graph.getDictionary().getValue("y"));
		assertEquals(null, graph.getDictionary().getValue("yFib"));
	}
	
	public void test_dictionary_invalid_key(){
        doCompileExpectErrors("test_dictionary_invalid_key", Arrays.asList("Dictionary entry 'invalid' does not exist"));
	}
	
	public void test_dictionary_string_to_int(){
        doCompileExpectErrors("test_dictionary_string_to_int", Arrays.asList("Type mismatch: cannot convert from 'string' to 'integer'","Type mismatch: cannot convert from 'string' to 'integer'"));
	}
	
	public void test_utillib_sleep() {
		long time = System.currentTimeMillis();
		doCompile("test_utillib_sleep");
		assertTrue("sleep() function didn't pause execution", System.currentTimeMillis() - time > 1000);
	}
	
	public void test_utillib_random_uuid() {
		doCompile("test_utillib_random_uuid");
		assertNotNull(getVariable("uuid"));
	}
	
	public void test_stringlib_validUrl() {
		doCompile("test_stringlib_url");
		check("urlValid", Arrays.asList(true, true, false));
		check("protocol", Arrays.asList("http", "https", null));
		check("userInfo", Arrays.asList("", "chuck:norris", null));
		check("host", Arrays.asList("example.com", "server.javlin.eu", null));
		check("port", Arrays.asList(-1, 12345, -2));
		check("path", Arrays.asList("", "/backdoor/trojan.cgi", null));
		check("query", Arrays.asList("", "hash=SHA560;god=yes", null));
		check("ref", Arrays.asList("", "autodestruct", null));
	}
	
	public void test_stringlib_escapeUrl() {
		doCompile("test_stringlib_escapeUrl");
		check("escaped", "http://example.com/foo%20bar%5E");
		check("unescaped", "http://example.com/foo bar^");
	}
	
}