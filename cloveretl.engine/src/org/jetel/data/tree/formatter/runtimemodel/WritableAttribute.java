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
package org.jetel.data.tree.formatter.runtimemodel;

import org.jetel.data.DataRecord;
import org.jetel.data.tree.formatter.TreeFormatter;
import org.jetel.exception.JetelException;

/**
 * Class representing attribute
 * 
 * @author lkrejci (info@cloveretl.com) (c) Javlin, a.s. (www.cloveretl.com)
 * 
 * @created 20 Dec 2010
 */
public class WritableAttribute implements Writable {
	private final char[] name;
	private final WritableValue value;

	private final boolean writeNull;

	public WritableAttribute(String name, String prefix, WritableValue value, boolean writeNull) {
		if (prefix != null && !prefix.isEmpty()) {
			this.name = (prefix + ":" + name).toCharArray();
		} else {
			this.name = name.toCharArray();
		}
		this.value = value;
		this.writeNull = writeNull;
	}

	@Override
	public void write(TreeFormatter formatter, DataRecord[] availableData) throws JetelException {
		if (writeNull || !isEmpty(availableData)) {
			formatter.getAttributeWriter().writeAttribute(name, value.getContent(availableData));
		}
	}

	@Override
	public boolean isEmpty(DataRecord[] availableData) {
		return !writeNull && value.isEmpty(availableData);
	}

}
