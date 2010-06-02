/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002-04  David Pavlis <david_pavlis@hotmail.com>
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
package org.jetel.util.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * This class is intended to be implemented by third party applications. An implementation of this interface
 * can be passed to FileUtils class via addCustomPathResolver() method. Then all external resources are loaded through
 * this resolver: external metadata, external connections, input/output data files (fileURL attribute), 
 * imported CTL libraries, ... If null is returned the default clover implementation is used.
 *  
 * @author Martin Zatopek (martin.zatopek@javlinconsulting.cz)
 *         (c) Javlin Consulting (www.javlinconsulting.cz)
 *
 * @created 6.4.2010
 */
public interface CustomPathResolver {

	/**
	 * Method should return input stream corresponding 
	 * to given relative path and home directory specified in contextURL attribute.
	 * If null is returned the other CustomPathResolver implementation are used and finally if none of them has
	 * success the default clover implementation is used.
	 * @param contextURL
	 * @param input
	 * @return
	 */
	public InputStream getInputStream(URL contextURL, String input) throws IOException;

	/**
	 * Method should return output stream corresponding
	 * to given relative path and home directory specified in contextURL attribute.
	 * If null is returned the other CustomPathResolver implementation are used and finally if none of them has
	 * success the default clover implementation is used.
	 * @param contextURL
	 * @param input
	 * @return
	 */
	public OutputStream getOutputStream(URL contextURL, String input, boolean appendData, int compressLevel) throws IOException;

}
