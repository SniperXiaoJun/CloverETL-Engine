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
package org.jetel.component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetel.data.DataField;
import org.jetel.data.DataRecord;
import org.jetel.data.Defaults;
import org.jetel.data.IntegerDataField;
import org.jetel.data.parser.AbstractSpreadsheetParser;
import org.jetel.data.parser.SpreadsheetDOMParser;
import org.jetel.data.parser.SpreadsheetStreamParser;
import org.jetel.data.parser.XLSMapping;
import org.jetel.data.parser.XLSMapping.SpreadsheetOrientation;
import org.jetel.exception.BadDataFormatException;
import org.jetel.exception.ComponentNotReadyException;
import org.jetel.exception.ConfigurationProblem;
import org.jetel.exception.ConfigurationStatus;
import org.jetel.exception.JetelException;
import org.jetel.exception.PolicyType;
import org.jetel.exception.SpreadsheetException;
import org.jetel.exception.SpreadsheetParserExceptionHandler;
import org.jetel.exception.XMLConfigurationException;
import org.jetel.graph.Node;
import org.jetel.graph.OutputPort;
import org.jetel.graph.Result;
import org.jetel.graph.TransformationGraph;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.metadata.DataRecordMetadata;
import org.jetel.util.MultiFileReader;
import org.jetel.util.SpreadsheetUtils.SpreadsheetAttitude;
import org.jetel.util.SynchronizeUtils;
import org.jetel.util.file.FileUtils;
import org.jetel.util.property.ComponentXMLAttributes;
import org.jetel.util.property.PropertyRefResolver;
import org.jetel.util.property.RefResFlag;
import org.jetel.util.string.StringUtils;
import org.w3c.dom.Element;

/**
 * @author lkrejci (info@cloveretl.com)
 *         (c) Javlin, a.s. (www.cloveretl.com)
 *
 * @created 11 Aug 2011
 */
public class SpreadsheetReader extends Node {
	
    protected static Log LOGGER = LogFactory.getLog(SpreadsheetReader.class);

    public static final String COMPONENT_TYPE = "SPREADSHEET_READER";
    protected final static String DEFAULT_SHEET_VALUE = "0";
    
    public static final String XML_ATTITUDE_ATTRIBUTE = "attitude";
    public static final String XML_PASSWORD_ATTRIBUTE = "password";
    public static final String XML_FILE_URL_ATTRIBUTE = "fileURL";
    public static final String XML_SHEET_ATTRIBUTE = "sheet";
    public static final String XML_CHARSET_ATTRIBUTE = "charset"; // TODO remove
    public static final String XML_DATA_POLICY_ATTRIBUTE = "dataPolicy";
    public static final String XML_MAPPING_ATTRIBUTE = "mapping";
    public static final String XML_MAPPING_URL_ATTRIBUTE = "mappingURL";
    public static final String XML_NUM_RECORDS_ATTRIBUTE = "numRecords";
    public static final String XML_SKIP_RECORDS_ATTRIBUTE = "skipRecords";
    public static final String XML_NUM_SOURCE_RECORDS_ATTRIBUTE = "numSourceRecords";
    public static final String XML_SKIP_SOURCE_RECORDS_ATTRIBUTE = "skipSourceRecords";
    public static final String XML_NUM_SHEET_RECORDS_ATTRIBUTE = "numSheetRecords";
    public static final String XML_SKIP_SHEET_RECORDS_ATTRIBUTE = "skipSheetRecords";
    public static final String XML_MAX_ERROR_COUNT_ATTRIBUTE = "maxErrorCount";
    public static final String XML_INCREMENTAL_FILE_ATTRIBUTE = "incrementalFile";
    public static final String XML_INCREMENTAL_KEY_ATTRIBUTE = "incrementalKey";
	
    private final static int OUTPUT_PORT = 0;
	private final static int INPUT_PORT = 0;
	private final static int ERROR_PORT = 1;
    
    public static Node fromXML(TransformationGraph graph, Element nodeXML) throws XMLConfigurationException {
        SpreadsheetReader spreadsheetReader = null;
        ComponentXMLAttributes xattribs = new ComponentXMLAttributes(nodeXML, graph);

        try {
        	spreadsheetReader = new SpreadsheetReader(xattribs.getString(XML_ID_ATTRIBUTE));
        	
        	if (xattribs.exists(XML_PASSWORD_ATTRIBUTE)) {
        		spreadsheetReader.setPassword(xattribs.getString(XML_PASSWORD_ATTRIBUTE));
        	}
        	spreadsheetReader.setFileUrl(xattribs.getStringEx(XML_FILE_URL_ATTRIBUTE, RefResFlag.SPEC_CHARACTERS_OFF));
        	spreadsheetReader.setCharset(xattribs.getString(XML_CHARSET_ATTRIBUTE, null));
        	spreadsheetReader.setPolicyType(xattribs.getString(XML_DATA_POLICY_ATTRIBUTE, null));
        	
        	if (xattribs.exists(XML_ATTITUDE_ATTRIBUTE)) {
        		spreadsheetReader.setParserAttitude(SpreadsheetAttitude.valueOfIgnoreCase(xattribs.getString(XML_ATTITUDE_ATTRIBUTE)));
        	}
        	
        	String mappingURL = xattribs.getStringEx(XML_MAPPING_URL_ATTRIBUTE, null, RefResFlag.SPEC_CHARACTERS_OFF);
			String mapping = xattribs.getString(XML_MAPPING_ATTRIBUTE, null);
			if (mappingURL != null) {
				spreadsheetReader.setMappingURL(mappingURL);
			} else if (mapping != null) {
				spreadsheetReader.setMapping(mapping);
			}
			
			if (xattribs.exists(XML_SHEET_ATTRIBUTE)) {
				spreadsheetReader.setSheet(xattribs.getString(XML_SHEET_ATTRIBUTE));
			}
            if (xattribs.exists(XML_NUM_RECORDS_ATTRIBUTE)) {
            	spreadsheetReader.setNumRecords(xattribs.getInteger(XML_NUM_RECORDS_ATTRIBUTE));
            }
            if (xattribs.exists(XML_SKIP_RECORDS_ATTRIBUTE)) {
            	spreadsheetReader.setSkipRecords(xattribs.getInteger(XML_SKIP_RECORDS_ATTRIBUTE));
            }
			if (xattribs.exists(XML_NUM_SOURCE_RECORDS_ATTRIBUTE)){
				spreadsheetReader.setNumSourceRecords(xattribs.getInteger(XML_NUM_SOURCE_RECORDS_ATTRIBUTE));
			}
			if (xattribs.exists(XML_SKIP_SOURCE_RECORDS_ATTRIBUTE)){
				spreadsheetReader.setSkipSourceRecords(xattribs.getInteger(XML_SKIP_SOURCE_RECORDS_ATTRIBUTE));
			}
			if (xattribs.exists(XML_NUM_SHEET_RECORDS_ATTRIBUTE)){
				spreadsheetReader.setNumSheetRecords(xattribs.getInteger(XML_NUM_SHEET_RECORDS_ATTRIBUTE));
			}
			if (xattribs.exists(XML_SKIP_SHEET_RECORDS_ATTRIBUTE)){
				spreadsheetReader.setSkipSheetRecords(xattribs.getInteger(XML_SKIP_SHEET_RECORDS_ATTRIBUTE));
			}
            if (xattribs.exists(XML_MAX_ERROR_COUNT_ATTRIBUTE)) {
            	spreadsheetReader.setMaxErrorCount(xattribs.getInteger(XML_MAX_ERROR_COUNT_ATTRIBUTE));
            }
            
            if (xattribs.exists(XML_INCREMENTAL_FILE_ATTRIBUTE)) {
            	spreadsheetReader.setIncrementalFile(xattribs.getStringEx(XML_INCREMENTAL_FILE_ATTRIBUTE, RefResFlag.SPEC_CHARACTERS_OFF));
            }
            if (xattribs.exists(XML_INCREMENTAL_KEY_ATTRIBUTE)) {
            	spreadsheetReader.setIncrementalKey(xattribs.getString(XML_INCREMENTAL_KEY_ATTRIBUTE));
            }
        } catch (Exception ex) {
            throw new XMLConfigurationException(COMPONENT_TYPE + ":"
                    + xattribs.getString(XML_ID_ATTRIBUTE, " unknown ID ") + ":" + ex.getMessage(), ex);
        }

        return spreadsheetReader;
    }

    private String password = null;
	private String fileURL;
	private String charset;
	
	private SpreadsheetAttitude parserAttitude = SpreadsheetAttitude.STREAM;
	private AbstractSpreadsheetParser parser;
    private MultiFileReader reader;
	private PolicyType policyType = PolicyType.STRICT;
	
	private String mapping;
	private String mappingURL;
	
	private String sheet = null;
	
	private int numRecords = -1;
	private int skipRecords = -1;
	private int numSourceRecords = -1;
	private int skipSourceRecords = -1;
	private int numSheetRecords = -1;
	private int skipSheetRecords = -1;
	private int maxErrorCount = -1;
	
    private String incrementalFile;
    private String incrementalKey;
    
    private boolean logging = false;
    
    private int firstOptional = -1;
    private int lastOptional = -1;
    private OutputPort errorPort;
    

	public SpreadsheetReader(String id) {
		super(id);
	}
	
	@Override
	public String getType() {
		return COMPONENT_TYPE;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	
	public void setFileUrl(String fileURL) {
		this.fileURL = fileURL;
	}
	
	public void setCharset(String charset) {
		this.charset = charset;
	}
	
	public void setParserAttitude(SpreadsheetAttitude parserAttitude) {
        this.parserAttitude = parserAttitude;
    }
	
	public void setPolicyType(String strPolicyType) {
        setPolicyType(PolicyType.valueOfIgnoreCase(strPolicyType));
    }

    public void setPolicyType(PolicyType policyType) {
        this.policyType = policyType;
    }

	private void setMappingURL(String mappingURL) {
		this.mappingURL = mappingURL;
	}
    
	public void setMapping(String mapping) {
		this.mapping = mapping;
	}

	public void setSheet(String sheet) {
		this.sheet = sheet;
	}

	public void setNumRecords(int numRecords) {
		this.numRecords = numRecords;
	}

	public void setSkipRecords(int skipRecords) {
		this.skipRecords = skipRecords;
	}

	public void setNumSourceRecords(int numSourceRecords) {
		this.numSourceRecords = numSourceRecords;
	}

	public void setSkipSourceRecords(int skipSourceRecords) {
		this.skipSourceRecords = skipSourceRecords;
	}

	public void setNumSheetRecords(int numSheetRecords) {
		this.numSheetRecords = numSheetRecords;
	}

	public void setSkipSheetRecords(int skipSheetRecords) {
		this.skipSheetRecords = skipSheetRecords;
	}
	
	public void setMaxErrorCount(int maxErrorCount) {
		this.maxErrorCount = maxErrorCount;
	}

	public void setIncrementalFile(String incrementalFile) {
		this.incrementalFile = incrementalFile;
	}

	public void setIncrementalKey(String incrementalKey) {
		this.incrementalKey = incrementalKey;
	}

	// TODO: Check sheet name
	@Override
	public ConfigurationStatus checkConfig(ConfigurationStatus status) {
		super.checkConfig(status);

		if (!checkInputPorts(status, 0, 1) || !checkOutputPorts(status, 1, Integer.MAX_VALUE)) {
			return status;
		}

		if (charset != null && !Charset.isSupported(charset)) {
			status.add(new ConfigurationProblem("Charset " + charset + " not supported!", ConfigurationStatus.Severity.ERROR, this, ConfigurationStatus.Priority.NORMAL));
		}

		try {
			XLSMapping mapping = prepareMapping();
			if (mapping != null) {
				if (mapping.getOrientation() == SpreadsheetOrientation.HORIZONTAL && parserAttitude == SpreadsheetAttitude.STREAM) {
					status.add(new ConfigurationProblem("Horizontal reading is not supported with streaming parser type!", ConfigurationStatus.Severity.ERROR, this, ConfigurationStatus.Priority.NORMAL));
				}
				mapping.checkConfig(status);
			}
			prepareParser(mapping);
			prepareReader();
			// reader.checkConfig(getOutMetadata().get(OUTPUT_PORT)); TODO do the check, but do not open input file 
		} catch (ComponentNotReadyException e) {
			ConfigurationProblem problem = new ConfigurationProblem(e.getMessage(), ConfigurationStatus.Severity.WARNING, this, ConfigurationStatus.Priority.NORMAL);
			if (!StringUtils.isEmpty(e.getAttributeName())) {
				problem.setAttributeName(e.getAttributeName());
			}
			status.add(problem);
		} finally {
			free();
		}
		
        return status;
    }
	
	@Override
	public void init() throws ComponentNotReadyException {
		if (isInitialized()) {
			return;
		}
		super.init();
		if (charset == null) {
			charset = Defaults.DataFormatter.DEFAULT_CHARSET_ENCODER;
		}
		
		if (getOutPorts().size() == 2) {
			if (checkErrorPortMetadata()) {
				logging = true;
			} else {
				StringBuilder sb = new StringBuilder();
				sb.append(this.getName())
				  .append("|")
				  .append(this.getId())
				  .append(": Error port metadata have invalid format (expected data fields - integer (record number), string (file name)")
				  .append(", string (sheet name), integer (field number), string (field name), string (cell coordinates), string (cell value)")
				  .append(", string (cell type), string (cell format), string (error message)");
//				throw new ComponentNotReadyException(this.getName() + "|" + this.getId() + ": Error port metadata have invalid format " +
//            			"(expected data fields - integer (record number), string (cell coordinates), string (offending value), string (field name), string (error message)");
				throw new ComponentNotReadyException(sb.toString());
			}
		}
		
		if (policyType == PolicyType.STRICT) {
			maxErrorCount = 1;
		}
		
		prepareParser(prepareMapping());
		prepareReader();
	}
	
	private boolean checkErrorPortMetadata() {
        DataRecordMetadata errorMetadata = getOutputPort(ERROR_PORT).getMetadata();

        int errorNumFields = errorMetadata.getNumFields();
        boolean ret = errorNumFields >= 10
        		&& errorMetadata.getFieldType(0) == DataFieldMetadata.INTEGER_FIELD		// record number
        		&& isStringOrByte(errorMetadata.getField(1))							// file name
        		&& isStringOrByte(errorMetadata.getField(2))							// sheet name
        		&& errorMetadata.getFieldType(3) == DataFieldMetadata.INTEGER_FIELD		// field number
        		&& isStringOrByte(errorMetadata.getField(4))							// field name
        		&& isStringOrByte(errorMetadata.getField(5))							// cell coordinates
        		&& isStringOrByte(errorMetadata.getField(6))							// cell value
        		&& isStringOrByte(errorMetadata.getField(7))							// cell type
        		&& isStringOrByte(errorMetadata.getField(8))							// cell format (Excel)
        		&& isStringOrByte(errorMetadata.getField(9));							// error message
                
        return ret;
    }
	
	private boolean isStringOrByte(DataFieldMetadata field) {
		return field.getType() == DataFieldMetadata.STRING_FIELD || field.getType() == DataFieldMetadata.BYTE_FIELD || field.getType() == DataFieldMetadata.BYTE_FIELD_COMPRESSED;
	}
	
	private void setCharSequenceToField(CharSequence charSeq, DataField field) {
		if (charSeq == null) {
			field.setNull(true);
		} else {
			field.setNull(false);
			
			if (field.getType() == DataFieldMetadata.STRING_FIELD) {
				field.setValue(charSeq);
			} else if (field.getType() == DataFieldMetadata.BYTE_FIELD || field.getType() == DataFieldMetadata.BYTE_FIELD_COMPRESSED) {
				String cs;
				if (charset != null) {
					cs = charset;
				} else {
					cs = Defaults.DataParser.DEFAULT_CHARSET_DECODER;
				}
				try {
					field.setValue(charSeq.toString().getBytes(cs));
				} catch (UnsupportedEncodingException e) {
					// if parameter charset set, encoding support was checked in checkConfig()
					LOGGER.error(getId() + ": failed to write log record", e);
				}
			} else {
				throw new IllegalArgumentException("DataField type has to be string, byte or cbyte");
			}
		}
	}
	
	private XLSMapping prepareMapping() throws ComponentNotReadyException {
		DataRecordMetadata metadata = getOutputPort(OUTPUT_PORT).getMetadata();

		XLSMapping parsedMapping = null;
		if (mappingURL != null) {
			TransformationGraph graph = getGraph();
			try {
				InputStream stream = FileUtils.getInputStream(graph.getRuntimeContext().getContextURL(), mappingURL);
				parsedMapping = XLSMapping.parse(stream, metadata, true, getId());
			} catch (IOException e) {
				LOGGER.error("cannot instantiate node from XML", e);
				throw new ComponentNotReadyException(e.getMessage(), e);
			}
		} else if (mapping != null) {
			parsedMapping = XLSMapping.parse(mapping, metadata, true, getId());
		}
		
		return parsedMapping;
	}

	private void prepareParser(XLSMapping parsedMapping) throws ComponentNotReadyException {
		DataRecordMetadata metadata = getOutputPort(OUTPUT_PORT).getMetadata();
        if (parserAttitude == SpreadsheetAttitude.IN_MEMORY) {
            parser = new SpreadsheetDOMParser(metadata, parsedMapping, password);
        } else {
        	parser = new SpreadsheetStreamParser(metadata, parsedMapping, password);
        }
        
        if (sheet != null) {
        	parser.setSheet(sheet);
        } else {
        	parser.setSheet(DEFAULT_SHEET_VALUE);
        }
        parser.setExceptionHandler(new SpreadsheetParserExceptionHandler(policyType));
        parser.useIncrementalReading(incrementalFile != null && incrementalKey != null);
    }
	
	private void prepareReader() {
		TransformationGraph graph = getGraph();
		reader = new MultiFileReader(parser, graph.getRuntimeContext().getContextURL(), fileURL);
        reader.setLogger(LOGGER);
        reader.setIncrementalFile(incrementalFile);
        reader.setIncrementalKey(incrementalKey);
        reader.setInputPort(getInputPort(INPUT_PORT)); //for port protocol: ReadableChannelIterator reads data
        reader.setCharset(charset);
        reader.setPropertyRefResolver(new PropertyRefResolver(graph.getGraphProperties()));
        reader.setDictionary(graph.getDictionary());
        reader.setNumRecords(numRecords);
        reader.setNumSourceRecords(numSourceRecords);
        reader.setL3NumRecords(numSheetRecords);
        reader.setSkip(skipRecords);
        reader.setL3Skip(skipSheetRecords);
        
        // skip source rows
        if (skipSourceRecords == -1) {
        	OutputPort outputPort = getOutputPort(OUTPUT_PORT);
        	DataRecordMetadata metadata;
        	if (outputPort != null && (metadata = outputPort.getMetadata()) != null) {
            	int ssr = metadata.getSkipSourceRows();
            	if (ssr > 0) {
            		skipSourceRecords = ssr;
            	}
        	}
        }
        reader.setSkipSourceRows(skipSourceRecords > 0 ? skipSourceRecords : 0);
	}
	
	@Override
	public void preExecute() throws ComponentNotReadyException {
		super.preExecute();
    	if (firstRun()) {//a phase-dependent part of initialization
            reader.init(getOutputPort(OUTPUT_PORT).getMetadata());
    	}
    	reader.preExecute();
	}
	
	private SpreadsheetException writeErrorRecord(SpreadsheetException se, int recordMetadataFields, DataRecord record, DataRecord errorRecord) throws IOException, InterruptedException {
		// set mandatory fields
		((IntegerDataField) errorRecord.getField(0)).setValue(se.getRecordNumber());
		setCharSequenceToField(se.getFileName(), errorRecord.getField(1));
		setCharSequenceToField(se.getSheetName(), errorRecord.getField(2));
		((IntegerDataField) errorRecord.getField(3)).setValue(se.getFieldNumber() + 1);
		setCharSequenceToField(se.getFieldName(), errorRecord.getField(4));
		setCharSequenceToField(se.getCellCoordinates(), errorRecord.getField(5));
		setCharSequenceToField(se.getOffendingValue(), errorRecord.getField(6));
		setCharSequenceToField(se.getCellType(), errorRecord.getField(7));
		setCharSequenceToField(se.getCellFormat(), errorRecord.getField(8));
		setCharSequenceToField(se.getMessage(), errorRecord.getField(9));
		
		// fill the optional fields with data from output record
		DataField recordField;
		DataField errorField;
		for (int i = 0; i < recordMetadataFields; i++) {
			recordField = record.getField(i);
			for (int j = firstOptional; j < lastOptional; j++) {
				errorField = errorRecord.getField(j);
				if (errorField.getType() == recordField.getType() && 
						errorField.getMetadata().getName().equals(recordField.getMetadata().getName())) {
					errorField.setValue(recordField);
				}
			}
		}
		
		errorPort.writeRecord(errorRecord);
		return (SpreadsheetException) se.next();
	}

	@Override
	public Result execute() throws Exception {
		
		OutputPort outPort = getOutputPort(OUTPUT_PORT);
		DataRecordMetadata recordMetadata = outPort.getMetadata();
		DataRecord record = new DataRecord(recordMetadata);
		record.init();
		int recordMetadataFields = record.getNumFields();

		errorPort = getOutputPort(ERROR_PORT);
		DataRecord errorRecord = null;
		DataRecordMetadata errorMetadata = null;
		int errorMetadataFields = 0;
		if (logging) {
			errorMetadata = errorPort.getMetadata();
			errorMetadataFields = errorMetadata.getNumFields();
			if (errorMetadataFields > 10) {
				firstOptional = 10;
				lastOptional = errorMetadataFields;
			}
			errorRecord = new DataRecord(errorMetadata);
			errorRecord.init();
		}

		int errorCount = 0;

		try {
			while (runIt) {
				try {
					if ((reader.getNext(record)) == null) {
						break;
					}
					outPort.writeRecord(record);
				} catch (JetelException e) {
					// This may be general exception -> rethrow
					// or it is BadDataFormatException wrapped by AbstractSpreadsheetParser.getNext(record) which we want to handle.
					// Reason for wrapper exception: MultiFileReader increases record counters only on JetelExcepiton.
					
					if (e.getCause() == null || !(e.getCause() instanceof SpreadsheetException)) {
						throw e;
					}
					
					SpreadsheetException se = (SpreadsheetException) e.getCause();
					
					if (policyType == PolicyType.STRICT) {
						throw se;
					} else if (logging) {
						while (se != null && errorCount++ < maxErrorCount) {
							se = writeErrorRecord(se, recordMetadataFields, record, errorRecord);
						}
					} else {
						while (se != null && errorCount++ < maxErrorCount) {
							LOGGER.warn(se.getMessage());
							se = (SpreadsheetException) se.next();
						}
					}
					if (policyType == PolicyType.CONTROLLED && errorCount > maxErrorCount) {
							LOGGER.error("DataParser (" + getName() + "): Max error count exceeded.");
							LOGGER.error("ERROR: " + se.getMessage());
							return Result.ERROR;
					}
				}
				
				SynchronizeUtils.cloverYield();				
			}
		} catch (Exception e) {
			throw e;
		} finally {
			broadcastEOF();
		}
		return runIt ? Result.FINISHED_OK : Result.ABORTED;
	}

	@Override
	public void postExecute() throws ComponentNotReadyException {
		super.postExecute();
		parser.postExecute();
	}

    @Override
	public void commit() {
		super.commit();
		storeValues();
	}
    
	/**
     * Stores all values as incremental reading.
     */
    private void storeValues() {
		try {
			Object dictValue = getGraph().getDictionary().getValue(Defaults.INCREMENTAL_STORE_KEY);
			if (dictValue != null && dictValue == Boolean.FALSE) {
				return;
			}
			reader.storeIncrementalReading();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    }
}