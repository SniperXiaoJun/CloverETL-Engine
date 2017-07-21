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
package org.jetel.data.formatter.provider;

import org.jetel.data.formatter.DataFormatter;
import org.jetel.data.formatter.Formatter;

/**
 * Provides support for getting the universal data formatter.
 * 
 * @author Jan Ausperger (jan.ausperger@javlinconsulting.cz)
 *         (c) Javlin Consulting (www.javlinconsulting.cz)
 */
public class DataFormatterProvider implements FormatterProvider {

	private String charEncoder;
	private String header;
	private String charSet;
	private boolean quotedStrings;
	private Character quoteChar;
	
	private String[] excludedFieldNames;

	/**
	 * Contructors.
	 */
	public DataFormatterProvider() {
	}
	public DataFormatterProvider(String charEncoder) {
		this.charEncoder = charEncoder;
	}

	/**
	 * Creates new data formatter.
	 * 
	 * @return data formatter
	 */
	@Override
	public Formatter getNewFormatter() {
		DataFormatter formatter;
		if (charEncoder == null) {
			formatter =	new DataFormatter();
		} else {
			formatter =	new DataFormatter(charEncoder);
		}
		formatter.setHeader(header);
		charSet = formatter.getCharsetName();
		formatter.setExcludedFieldNames(excludedFieldNames);
		formatter.setQuotedStrings(quotedStrings);
		formatter.setQuoteChar(quoteChar);
		return formatter;
	}

	/**
	 * Sets header.
	 * 
	 * @param header
	 */
	public void setHeader(String header) {
    	this.header = header;
    }
	
	public void setQuotedStrings(boolean quotedStrings) {
		this.quotedStrings = quotedStrings;
	}
	
	public boolean getQuotedStrings() {
		return quotedStrings;
	}
	
	public void setQuoteChar(Character quoteChar) {
		this.quoteChar = quoteChar;
	}
	
	public Character getQuoteChar() {
		return quoteChar;
	}
	
	public String getCharsetName() {
		return charSet ;
	}

	public void setExcludedFieldNames(String[] excludedFieldNames) {
		this.excludedFieldNames = excludedFieldNames;
	}

}