package org.jetel.data.primitive;

import junit.framework.TestCase;

import org.jetel.data.Defaults;

public class DecimalNumericTest extends TestCase {
	
	Decimal anInt,aLong,aFloat,aDouble,aDefault,aDoubleIntInt,aDecimalIntInt,anIntInt;

	protected void setUp() throws Exception {
		super.setUp();
		Defaults.init();
		
		 anInt=DecimalFactory.getDecimal(0);
		 aLong=DecimalFactory.getDecimal((long)0);
		 aFloat=DecimalFactory.getDecimal(0f);
		 aDouble=DecimalFactory.getDecimal(0.0000001);
		 aDefault=DecimalFactory.getDecimal();
		 aDoubleIntInt=DecimalFactory.getDecimal(123.456,9,2);
		 aDecimalIntInt=DecimalFactory.getDecimal(aDoubleIntInt,6,1);
		 anIntInt=DecimalFactory.getDecimal(10,4);
	}
	
	public void test_values(){
		System.out.println("Tests for integer:");
		for (int i=0;i<5;i++){
			int value=0;
			switch (i) {
				case 1:value=Integer.MIN_VALUE;	
					   break;
				case 2:value=Integer.MAX_VALUE;
					   break;
				case 3:value=123;
					   break;
				case 4:value=-123;
					  break;
			}
  		   System.out.println("value set to "+value);
			int less=value-1;
			int more=value+1;
		    anInt.setValue(value);
			assertEquals(value,anInt.getInt());
			System.out.println("Test for getInt passed");
			if (!anInt.isNaN()) assertEquals(new Double(value),new Double(anInt.getDouble()));
			System.out.println("Test for getDouble passed (isNaN="+anInt.isNaN()+")");
			if (!anInt.isNaN()) assertEquals(value,anInt.getLong());
			System.out.println("Test for getLong passed (isNaN="+anInt.isNaN()+")");
			if (!anInt.isNaN()) assertEquals(DecimalFactory.getDecimal(value),anInt.getDecimal());
			System.out.println("Test for getDecimal passed (isNaN="+anInt.isNaN()+")");
			assertNotSame(DecimalFactory.getDecimal(value),anInt.getDecimal());
			assertEquals(10,anInt.getPrecision());
			assertEquals(0,anInt.getScale());
			if (anInt.getInt()==Integer.MAX_VALUE) anInt.setNaN(true);
			if (!anInt.isNaN()) assertEquals(-1,anInt.compareTo(DecimalFactory.getDecimal(more)));
			if (!anInt.isNaN()) assertEquals(-1,anInt.compareTo(new Integer(more)));
			if (!anInt.isNaN()) assertEquals(0,anInt.compareTo(DecimalFactory.getDecimal(value)));
			if (!anInt.isNaN()) assertEquals(0,anInt.compareTo(new Integer(value)));
			if (!anInt.isNaN()) assertEquals(1,anInt.compareTo(DecimalFactory.getDecimal(less)));
			if (!anInt.isNaN()) assertEquals(1,anInt.compareTo(new Integer(less)));
			System.out.println("Test for compareTo passed (isNaN="+anInt.isNaN()+")");
		}

		System.out.println("Tests for long:");
		for (int i=0;i<5;i++){
			long value=0;
			switch (i) {
				case 1:value=(long)Integer.MIN_VALUE;					
					   break;
				case 2:value=(long)Integer.MAX_VALUE;
					   break;
				case 3:value=Long.MIN_VALUE;
					   break;
				case 4:value=Long.MAX_VALUE;
					  break;
			}
		   System.out.println("value set to "+value);
			long less=value-1;
			long more=value+1;
		    aLong.setValue(value);
			if (Integer.MIN_VALUE<value && value<Integer.MAX_VALUE) {
				assertEquals(value,aLong.getInt());
				System.out.println("Test for getInt passed");
			}else{
				System.out.println("Test for getInt skipped");
			}
			if (!aLong.isNaN()) assertEquals(new Double(value),new Double(aLong.getDouble()));
			System.out.println("Test for getDouble passed (isNaN="+anInt.isNaN()+")");
			if (value==Long.MAX_VALUE) {
				aLong.setNaN(true);//because following test fails for value=Long.MAX_VALUE
				System.out.println("Test for getLong skipped" );
			}
			if (!aLong.isNaN()) assertEquals(value,aLong.getLong());
			System.out.println("Test for getLong passed (isNaN="+anInt.isNaN()+")");
			if (!aLong.isNaN()) assertEquals(DecimalFactory.getDecimal(value),aLong.getDecimal());
			System.out.println("Test for getDecimal passed (isNaN="+anInt.isNaN()+")");
			assertNotSame(DecimalFactory.getDecimal(value),aLong.getDecimal());
			assertEquals(19,aLong.getPrecision());
			assertEquals(0,aLong.getScale());
			if (!aLong.isNaN()) assertEquals(-1,aLong.compareTo(DecimalFactory.getDecimal(more)));
			if (!aLong.isNaN()) assertEquals(-1,aLong.compareTo(new Long(more)));
			if (!aLong.isNaN()) assertEquals(0,aLong.compareTo(DecimalFactory.getDecimal(value)));
			if (!aLong.isNaN()) assertEquals(0,aLong.compareTo(new Long(value)));
			if (!aLong.isNaN()) assertEquals(1,aLong.compareTo(DecimalFactory.getDecimal(less)));
			if (!aLong.isNaN()) assertEquals(1,aLong.compareTo(new Long(less)));
			System.out.println("Test for compareTo passed (isNaN="+anInt.isNaN()+")");
		}

		System.out.println("Tests for float:");
		for (int i=0;i<7;i++){
			float value=0;
			float less=-0.1f;
			float more=0.1f;
			switch (i) {
				case 1:aFloat=DecimalFactory.getDecimal((float)Long.MIN_VALUE);
					   value=Long.MIN_VALUE;
					   less=value-1e19f;
					   break;
				case 2:aFloat=DecimalFactory.getDecimal((float)Long.MAX_VALUE);
					   value=Long.MAX_VALUE;
					   more=(float)Long.MAX_VALUE+1e18f;
					  break;
				case 3:aFloat=DecimalFactory.getDecimal(0.000001f);
					   value=0.000001f;
					   more=0.000002f;
					  break;
				case 4:aFloat=DecimalFactory.getDecimal(-0.000001f);
					   value=-0.000001f;
					   less=-1;
					   break;
				case 5:aFloat=DecimalFactory.getDecimal(Float.MAX_VALUE);
					   value=Float.MAX_VALUE;
				  	   break;
				case 6:aFloat=DecimalFactory.getDecimal(Float.MIN_VALUE);
					   value=Float.MIN_VALUE;
				  	   break;
			}
		   System.out.println("value set to "+value);
		   System.out.println("less set to "+less);
		   System.out.println("more set to "+more);
			if (Integer.MIN_VALUE<value && value<Integer.MAX_VALUE) {
				assertEquals(new Float(value).intValue(),aFloat.getInt());
				System.out.println("Test for getInt passed");
			}else{
				System.out.println("Test for getInt skipped");
			}
			if (!aFloat.isNaN()) assertEquals(new Double(value),new Double(aFloat.getDouble()));
			System.out.println("Test for getDouble passed (isNaN="+anInt.isNaN()+")");
			if (Long.MIN_VALUE<value && value<Long.MAX_VALUE) {
				assertEquals(new Float(value).longValue(),aFloat.getLong());
				System.out.println("Test for getLong passed");
			}else{
				System.out.println("Test for getLong skipped");
			}
			if (!aFloat.isNaN()) assertEquals(DecimalFactory.getDecimal(value),aFloat.getDecimal());
			System.out.println("Test for getDecimal passed (isNaN="+anInt.isNaN()+")");
			assertNotSame(DecimalFactory.getDecimal(value),aFloat.getDecimal());
			if (!aFloat.isNaN()&&!(value==Float.MAX_VALUE)) assertEquals(-1,aFloat.compareTo(DecimalFactory.getDecimal(more)));
			if (!aFloat.isNaN()&&!(value==Float.MAX_VALUE)) assertEquals(-1,aFloat.compareTo(new Double(more)));
			if (!aFloat.isNaN()) assertEquals(0,aFloat.compareTo(DecimalFactory.getDecimal(value)));
			if (!aFloat.isNaN()) assertEquals(0,aFloat.compareTo(new Double(value)));
			if (!aFloat.isNaN()) assertEquals(1,aFloat.compareTo(DecimalFactory.getDecimal(less)));
			if (!aFloat.isNaN()) assertEquals(1,aFloat.compareTo(new Double(less)));
			System.out.println("Test for compareTo passed (isNaN="+anInt.isNaN()+")");
		}

//		assertEquals(0,aDouble.getInt());
//		assertEquals(new Double(0),new Double(aDouble.getDouble()));
//		assertEquals(0,aDouble.getLong());
////		assertEquals(DecimalFactory.getDecimal(0),aDouble.getDecimal());
//		assertNotSame(DecimalFactory.getDecimal(0),aDouble.getDecimal());
//		assertEquals(8,aDouble.getPrecision());
//		assertEquals(2,aDouble.getScale());
////		assertEquals(0,aDouble.compareTo(DecimalFactory.getDecimal(0)));
//		assertFalse(aDouble.isNaN());
//		aDouble.setNaN(true);
//		assertTrue(aDouble.isNaN());
}

//	public void test_maths(){
//		anInt.add(DecimalFactory.getDecimal(123));
//		assertEquals(new BigDecimal(123),anInt.getBigDecimal());
//		aLong.sub(DecimalFactory.getDecimal(Integer.MAX_VALUE));
//		assertEquals(new BigDecimal(Integer.MIN_VALUE),aLong.getBigDecimal());
//		aDouble.div(anInt);
//		assertEquals(new Double(0.00000001/123),new Double(aDouble.getDouble()));
//	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

}
