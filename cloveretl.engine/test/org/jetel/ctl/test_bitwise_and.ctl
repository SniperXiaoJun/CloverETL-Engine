integer resultInt1;
integer resultInt2;
integer resultInt3;
integer resultInt4;
long resultLong1;
long resultLong2;
long resultLong3;
long resultLong4;

function integer transform() {

	resultInt1 = bitAnd(0,1);
	resultInt2 = bitAnd(1,1);
	resultInt3 = bitAnd(2,1);
	resultInt4 = bitAnd(3,1);
	resultLong1 = bitAnd(0l,1l);
	resultLong2 = bitAnd(1l,1l);
	resultLong3 = bitAnd(2l,1l);
	resultLong4 = bitAnd(3l,1l);
	
	return 0;
}