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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.parsers.SAXParser;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author dpavlis (info@cloveretl.com)
 *         (c) Javlin, a.s. (www.cloveretl.com)
 *
 * @created Aug 8, 2013
 */
public class JsonSaxParser extends SAXParser {
	
	
	private static final String NAMESPACE_URI = "";
	private static final String XML_NAME_OBJECT = "json_object";
	private static final String XML_NAME_ARRAY = "json_array";
	private static final String XML_ARRAY_DEPTH = "arrayDepth";
	
	private static final JsonFactory JSON_FACTORY = new JsonFactory();
	private static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();
	
	private DefaultHandler handler;
	
	private boolean xmlEscapeChars=false;
	
	@Override
	public org.xml.sax.Parser getParser() throws SAXException {
		return null;
	}

	@Override
	public XMLReader getXMLReader() throws SAXException {
		return null;
	}

	@Override
	public boolean isNamespaceAware() {
		return false;
	}

	@Override
	public boolean isValidating() {
		return false;
	}

	@Override
	public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
		//do nothing - no properties supported
	}

	@Override
	public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
		return null;
	}

	@Override
	public void parse(InputSource is,DefaultHandler handler) throws IOException, SAXException{
		this.handler=handler;
		xmlEscapeChars=false;
		doParse(is.getCharacterStream());
	}

	
	public void convertJSON2XML(Reader in,Writer out,boolean firstObjectOnly,boolean suppresNodeValues)throws IOException, SAXException{
		this.handler=new JSON2XMLHandler(out,suppresNodeValues);
		JsonParser parser;
		try {
			parser = JSON_FACTORY.createJsonParser(in);
		} catch (JsonParseException e) {
			throw new IOException(e);
		}
		Deque<JsonToken> tokens = new ArrayDeque<JsonToken>();
		Deque<String> names = new ArrayDeque<String>();
		Deque<Integer> depthCounter = new ArrayDeque<Integer>();
		depthCounter.add(0);
		JsonToken currentToken = null;
		
		xmlEscapeChars=true;
		
		handler.startDocument();
		int startEndCounter = 0;
		while ((currentToken = parser.nextToken()) != null) {
			processToken(currentToken, parser, tokens, names, depthCounter);
			if (currentToken == JsonToken.START_ARRAY || currentToken == JsonToken.START_OBJECT) {
				startEndCounter++;
			} else if (currentToken == JsonToken.END_ARRAY || currentToken == JsonToken.END_OBJECT) {
				startEndCounter--;
			}
			if (startEndCounter == 0) {
				break;
			}
		}
		
		handler.endDocument();
	}
	
	
	protected void doParse(Reader reader)throws IOException, SAXException{
		JsonParser parser;
		try {
			parser = JSON_FACTORY.createJsonParser(reader);
		} catch (JsonParseException e) {
			throw new IOException(e);
		}
		Deque<JsonToken> tokens = new ArrayDeque<JsonToken>();
		Deque<String> names = new ArrayDeque<String>();
		Deque<Integer> depthCounter = new ArrayDeque<Integer>();
		depthCounter.add(0);
		JsonToken currentToken = null;
		
		handler.startDocument();
		
		while ((currentToken = parser.nextToken()) != null) {
			processToken(currentToken, parser, tokens, names, depthCounter);
		}
		
		handler.endDocument();
		
		
	}
	
	protected void processToken(final JsonToken token, JsonParser parser, Deque<JsonToken> tokens, Deque<String> names, Deque<Integer> depthCounter) 
		throws JsonParseException, IOException, SAXException {
		
		if (token == null) {
			return;
		}
		switch (token) {
		case FIELD_NAME: {
			names.add(parser.getText());
			tokens.add(token);
			break;
		}
		case START_ARRAY: {
			if (names.isEmpty()) {
				// top level array
				names.add(XML_NAME_ARRAY);
				if (depthCounter.size() == 1 && depthCounter.peek() == 0) {
					tokens.addFirst(JsonToken.START_ARRAY);
					depthCounter.add(1);
					handler.startElement(NAMESPACE_URI, normalizeElementName(names.getFirst()), normalizeElementName(names.getFirst()), EMPTY_ATTRIBUTES);
				}
			} else if (tokens.peekLast() == JsonToken.FIELD_NAME) {
				// named array - remove field token
				tokens.removeLast();
			} else if (tokens.peekLast() == JsonToken.START_ARRAY) {
				// add nested element
				
				AttributesImpl attributesImpl = new AttributesImpl();
				String name = names.getLast();
				int top = depthCounter.pollLast();
				attributesImpl.addAttribute("", XML_ARRAY_DEPTH, XML_ARRAY_DEPTH, "CDATA", String.valueOf(top));
				top++;
				depthCounter.add(top);
				handler.startElement(NAMESPACE_URI, normalizeElementName(name), normalizeElementName(name), attributesImpl);
			}
			tokens.add(token);
			break;
		}
		case START_OBJECT: {
			if (names.isEmpty()) {
				names.add(XML_NAME_OBJECT);
			} else if (tokens.peekLast() == JsonToken.FIELD_NAME) {
				// named object - remove field token
				tokens.removeLast();
			}
			tokens.add(token);
			AttributesImpl attributesImpl = new AttributesImpl();
			String name = names.getLast();
			if (!depthCounter.isEmpty()) {
				int top = depthCounter.peekLast();
				if (top > 0) {
					attributesImpl.addAttribute("", XML_ARRAY_DEPTH, XML_ARRAY_DEPTH, "CDATA", String.valueOf(top));
				}
			}
			depthCounter.add(Integer.valueOf(0));
			handler.startElement(NAMESPACE_URI, normalizeElementName(name),normalizeElementName(name), attributesImpl);
			break;
		}
		case END_ARRAY: {
			// remove corresponding start
			tokens.removeLast();
			
			String name = names.getLast();
			int top = depthCounter.pollLast();
			if (top > 0) {
				top--;
			}
			depthCounter.add(top);
			
			if (names.size() == 1) {
				handler.endElement(NAMESPACE_URI, normalizeElementName(names.getFirst()), normalizeElementName(names.getFirst()));
				names.removeLast();
			} else if (!tokens.isEmpty() && tokens.peekLast() == JsonToken.START_ARRAY) {
				// end nested array
				handler.endElement(NAMESPACE_URI,normalizeElementName(name), normalizeElementName(name));
			} else {
				// remove name if not inside array
				names.removeLast();
			}
			break;
		}
		case END_OBJECT: {
			// remove corresponding start
			tokens.removeLast();
			// end current object
			String name = names.getLast();
			depthCounter.pollLast();
			handler.endElement(NAMESPACE_URI, normalizeElementName(name), normalizeElementName(name));
			if (tokens.isEmpty() || tokens.peekLast() != JsonToken.START_ARRAY) {
				// remove name if not inside array
				names.removeLast();
			}
			break;
		}
		}
		if (token.isScalarValue()) {
			String valueName = names.getLast();
			switch (tokens.getLast()) {
			case FIELD_NAME: {
				// simple property
				handler.startElement(NAMESPACE_URI, normalizeElementName(valueName), normalizeElementName(valueName), EMPTY_ATTRIBUTES);
				processScalarValue(parser);
				handler.endElement(NAMESPACE_URI, normalizeElementName(valueName), normalizeElementName(valueName));
				tokens.removeLast();
				names.removeLast();
				break;
			}
			case START_ARRAY: {
				// array item
				
				AttributesImpl attributesImpl = new AttributesImpl();
				String name = names.getLast();
				attributesImpl.addAttribute("", XML_ARRAY_DEPTH, XML_ARRAY_DEPTH, "CDATA", String.valueOf(depthCounter.peekLast()));
				
				handler.startElement(NAMESPACE_URI, normalizeElementName(name), normalizeElementName(name), attributesImpl);
				processScalarValue(parser);
				handler.endElement(NAMESPACE_URI, normalizeElementName(name), normalizeElementName(name));
			}
			}
		}
	}
	
	protected void processScalarValue(JsonParser parser) throws JsonParseException, IOException, SAXException {
		
		if (parser.getCurrentToken() != JsonToken.VALUE_NULL) {
			char[] chars;
			if (xmlEscapeChars){
				chars=URLEncoder.encode(parser.getText()).toCharArray();
			}else{
				chars= parser.getText().toCharArray();
			}
			handler.characters(chars, 0, chars.length);
		}
	}

	private class JSON2XMLHandler extends DefaultHandler {
		
		private final Writer out;
		private final boolean suppressNodeValues; 
		
		private JSON2XMLHandler(Writer out, boolean suppresNodeValues){
			this.out=out;
			this.suppressNodeValues=suppresNodeValues;
		}
		
		@Override
		public void startElement (String uri, String localName, String qName, Attributes attributes) throws SAXException {
			try {
				out.append("<").append(localName);
				//check if there is array depth attribute
				if (attributes.getLength() == 1) {
					//write attribute value
					out.append(" ");
					out.append(attributes.getLocalName(0));
					out.append("=\"");
					out.append(attributes.getValue(0));
					out.append("\"");
				}
				out.append(">");
			} catch (IOException e) {
				new SAXException(e);
			}
			
		}
		
		@Override
		 public void endElement (String uri, String localName, String qName) throws SAXException{
			try {
				out.append("</").append(localName).append(">");
			} catch (IOException e) {
				new SAXException(e);
			}
			
		}
		
		@Override
		public void characters (char ch[], int start, int length) throws SAXException{
			if (suppressNodeValues) return;
			try {
				out.write(ch,start,length);
			} catch (IOException e) {
				new SAXException(e);
			}
		}
	}
	
	private static String normalizeElementName(String name) {
		if(name==null || name.trim().length()==0) {
			return "UNNAMED";
		}
		if(!Character.isLetter(name.trim().charAt(0))) {
			return "_"+name.trim();
		}
		return name;
	}
	
}
