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
package org.jetel.data;

import java.text.RuleBasedCollator;
import java.util.Arrays;

import org.jetel.metadata.DataRecordMetadata;
import org.jetel.util.key.OrderType;

/**
 * @author avackova (info@cloveretl.com)
 *         (c) Javlin, a.s. (www.cloveretl.com)
 *
 * @created 26 Jan 2011
 */
public class RecordComapratorAnyOrderType extends RecordComparator {

	private OrderType[] sortOrderingsAnyType;

	/**
	 * @param keyFields
	 */
	public RecordComapratorAnyOrderType(int[] keyFields) {
		this(keyFields, null);
	}

	/**
	 * @param keyFields
	 * @param object
	 */
	public RecordComapratorAnyOrderType(int[] keyFields, RuleBasedCollator collator) {
		super(keyFields, collator);
        sortOrderingsAnyType = new OrderType[keyFields.length];
        Arrays.fill(sortOrderingsAnyType, OrderType.AUTO);
	}
	
	public static RecordComapratorAnyOrderType createRecordComparator(DataRecordMetadata metadata, int[] keyFields, 
			OrderType[] sortOrderings) {
		RecordComapratorAnyOrderType comparator = new RecordComapratorAnyOrderType(keyFields);
		if (sortOrderings != null) {
			comparator.setSortOrderingsAnyType(sortOrderings);
		}
		comparator.updateCollators(metadata);
		return comparator;
	}
	
	@Override
	protected int orderCorrection(int keyField, int compResult) {
		if (compResult == 0) return compResult;
		switch (sortOrderingsAnyType[keyField]) {
		case IGNORE:
			return -1;//always first is before the second
		case ASCENDING:
			return compResult;
		case DESCENDING:
			return -compResult;
		case AUTO:
			if (compResult != 0) {
				sortOrderingsAnyType[keyField] = compResult < 0 ? OrderType.ASCENDING : OrderType.DESCENDING; 
			} 
			return orderCorrection(keyField, compResult);
		}
		return compResult;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof RecordComapratorAnyOrderType && super.equals(obj)) {
			return Arrays.equals(this.sortOrderingsAnyType,
					((RecordComapratorAnyOrderType)obj).getSortOrderingsAnyType());
		}
		return false;
	}

	/**
	 * @return the sortOrderingsAnyType
	 */
	public OrderType[] getSortOrderingsAnyType() {
		return sortOrderingsAnyType;
	}

	/**
	 * @param sortOrderingsAnyType the sortOrderingsAnyType to set
	 */
	public void setSortOrderingsAnyType(OrderType[] sortOrderingsAnyType) {
		this.sortOrderingsAnyType = sortOrderingsAnyType;
	}
}
