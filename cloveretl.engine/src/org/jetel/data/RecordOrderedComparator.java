package org.jetel.data;

import java.text.RuleBasedCollator;
import java.util.Arrays;
import java.util.Comparator;

import org.jetel.metadata.DataFieldMetadata;
import org.jetel.metadata.DataRecordMetadata;

public class RecordOrderedComparator extends RecordComparator implements
		Comparator {
	
	private boolean[] keyOrderings;

	/**
	 *  Constructor for the RecordOrderedComparator object
	 *
	 * @param  keyField  indexes of fields to be considered for sorting
	 * @since                 May 2, 2002
	 */
	private RecordOrderedComparator(int[] keyFields) {
		super(keyFields);
		keyOrderings = new boolean[keyFields.length];
		Arrays.fill(keyOrderings, true);
	}

	/**
	 *  Constructor for the RecordOrderedComparator object
 	 * @param keyOrderings ordering of columns for each key (true=ascending)
	 *
	 * @param  keyField  indexes of fields to be considered for sorting
	 */
	public RecordOrderedComparator(int[] keyFields, boolean[] keyOrderings) {
		super(keyFields);
		this.keyOrderings = keyOrderings;
	}

    /**
     * Constructor for the RecordOrderedComparator object
     * 
     * @param keyFields indexes of fields to be considered for sorting
     * @param collator  Collator which should be use for comparing String fields
     */
	private RecordOrderedComparator(int[] keyFields, RuleBasedCollator collator) {
	
		super(keyFields, collator);
		keyOrderings = new boolean[keyFields.length];
		Arrays.fill(keyOrderings, true);
		
	}
	
	/**
     * Constructor for the RecordOrderedComparator object
     * 
     * @param keyFields indexes of fields to be considered for sorting
     * @param collator  Collator which should be use for comparing String fields
     */
	public RecordOrderedComparator(int[] keyFields, boolean[] keyOrderings, RuleBasedCollator collator) {
	
		super(keyFields, collator);
		this.keyOrderings = keyOrderings;
				
	}
	
		/**
	 *  Compares two records (of the same layout) based on defined key-fields and returns (-1;0;1) if (< ; = ; >)
	 *
	 *@param  record1  Description of the Parameter
	 *@param  record2  Description of the Parameter
	 *@return          -1 ; 0 ; 1
	 */
	public int compare(Object o1, Object o2) {
        int compResult;
        final DataRecord record1 = (DataRecord) o1;
        final DataRecord record2 = (DataRecord) o2;
        /*
         * by D.Pavlis following check has been "relaxed" to speed up
         * processing. if (record1.getMetadata() != record2.getMetadata()) {
         * throw new RuntimeException("Can't compare - records have different
         * metadata associated." + " Possibly different structure"); }
         */
        if (collator != null) {
            for (int i = 0; i < keyFields.length; i++) {
                final DataField field1 = record1.getField(keyFields[i]);
                if (field1.getType() == DataFieldMetadata.STRING_FIELD) {
                    compResult = ((StringDataField) field1).compareTo(
                            record2.getField(keyFields[i]), collator);
                } else {
                    compResult = field1.compareTo(record2
                            .getField(keyFields[i]));
                }
                if (!keyOrderings[i]) {
                	compResult = -compResult;
                }
                if (compResult != 0) {
                    if (equalNULLs) {
                        if (!(record1.getField(keyFields[i]).isNull && record2
                                .getField(keyFields[i]).isNull)) {
                            return compResult;
                        }
                        continue;
                    }
                    return compResult;
                }
            }

        } else {

            for (int i = 0; i < keyFields.length; i++) {
                compResult = record1.getField(keyFields[i]).compareTo(
                        record2.getField(keyFields[i]));
				if (!keyOrderings[i]) {
                	compResult = -compResult;
                }
                if (compResult != 0) {
                    if (equalNULLs) {
                        if (!(record1.getField(keyFields[i]).isNull && record2
                                .getField(keyFields[i]).isNull)) {
                            return compResult;
                        }
                        continue;
                    }
                    return compResult;
                }
            }
        }
        return 0;
        // seem to be the same
    }
	
		/**
     * Compares two records (can have different layout) based on defined
     * key-fields and returns (-1;0;1) if (< ; = ; >).<br>
     * The particular fields to be compared have to be of the same type !
     * 
     * @param secondKey
     *            RecordKey defined for the second record
     * @param record1
     *            First record
     * @param record2
     *            Second record
     * @return -1 ; 0 ; 1
     */
	public int compare(RecordKey secondKey, DataRecord record1, DataRecord record2) {
	
		int compResult;
		int[] record2KeyFields = secondKey.getKeyFields();
		if (keyFields.length != record2KeyFields.length) {
			throw new RuntimeException("Can't compare. keys have different number of DataFields");
		}
        
         if (collator != null) {
             for (int i = 0; i < keyFields.length; i++) {
                 final DataField field1 = record1.getField(keyFields[i]);
                 if (field1.getType() == DataFieldMetadata.STRING_FIELD) {
                    compResult = ((StringDataField) field1).compareTo(
                             record2.getField(record2KeyFields[i]),collator);
                 }else{
                     compResult = field1.compareTo(
                             record2.getField(record2KeyFields[i]));
                 }
                 
                 if (!keyOrderings[i]) {
                 	compResult = -compResult;
                 }
                 
                 if (compResult != 0) {
                     if (equalNULLs) {
                         if (!(record1.getField(keyFields[i]).isNull && record2
                                 .getField(record2KeyFields[i]).isNull)) {
                             return compResult;
                         }
                         continue;
                     }
                     return compResult;
                 }
                 
            }             
             
         }else{
        
		for (int i = 0; i < keyFields.length; i++) {
		
                compResult = record1.getField(keyFields[i]).compareTo(
                        record2.getField(record2KeyFields[i]));
                        
				if (!keyOrderings[i]) {
                 	compResult = -compResult;
                 }
                
                if (compResult != 0) {
                    if (equalNULLs) {
                        if (!(record1.getField(keyFields[i]).isNull && record2
                                .getField(record2KeyFields[i]).isNull)) {
                            return compResult;
                        }
                        continue;
                    }
                    return compResult;
                }
            }
        }
		return 0;
		// seem to be the same
	}
	

}
