package jokrey.utilities.animation.util;

public class AEVector {
    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;

    private final double[] values;
    public AEVector(int n) {
        values = new double[n];
    }
    public AEVector(double... vs) {
        values = new double[vs.length];
        set(vs);
    }

    public double get(int ind){
        return values[ind];
    }
    public void set(int ind,double value) {
        values[ind] = value;
    }
    public void set(double... values_to_set) {
        for (int i=0;i!=getSize();i++)
        	values[i] = values_to_set[i];
    }

    public int getSize() {
        return values.length;
    }

    public double getLength() {
    	double addVal = 0;
        for (int i=0;i!=getSize();i++)
        	addVal+=values[i]*values[i];
    	return Math.sqrt(addVal);
    }

    public static AEVector timesScalar_getVector(AEVector v, double scalar) {
        AEVector resultV = new AEVector(v.getSize());
        for (int i=0;i!=resultV.getSize();i++)
            resultV.set(i,v.get(i)*scalar);
        return resultV;
    }
    public static AEVector plusScalar_getVector(AEVector v, double scalar) {
        AEVector resultV = new AEVector(v.getSize());
        for (int i=0;i!=resultV.getSize();i++)
            resultV.set(i,v.get(i) + scalar);
        return resultV;
    }
    public static double timesVector_getScalar(AEVector v, AEVector v_o) {
        double scalar = 0;
        for (int i=0;i!=v.getSize();i++)
            scalar += v.get(i) * v_o.get(i);
        return scalar;
    }
    public static AEVector divideScalar_getVector(AEVector v, double scalar) {
        AEVector resultV = new AEVector(v.getSize());
        for (int i=0;i!=resultV.getSize();i++)
            resultV.set(i,v.get(i) / scalar);
        return resultV;
    }
    public static double dotProduct(AEVector v, AEVector v_o) {
        return timesVector_getScalar(v, v_o);
    }
    public static AEVector timesVector_getVector(AEVector v, AEVector v_o) {
        AEVector resultV = new AEVector(v.getSize());
        for (int i=0;i!=resultV.getSize();i++)
            resultV.set(i,v.get(i) * v_o.get(i));
        return resultV;
    }
    public static AEVector plusVector_getVector(AEVector v, AEVector v_o) {
        AEVector resultV = new AEVector(v.getSize());
        for (int i=0;i!=resultV.getSize();i++)
            resultV.set(i,v.get(i) + v_o.get(i));
        return resultV;
    }
	public static AEVector addAllVectors(AEVector resultVec, AEVector[] vectors) {
		for(int i=0;i<vectors.length;i++) {
			if(vectors[i]!=null)resultVec=plusVector_getVector(resultVec, vectors[i]);
		}
		return resultVec;
	}

    @Override public boolean equals(Object o) {
    	if (o instanceof AEVector) {
    		AEVector v_o = (AEVector) o;
    		if (getSize() == v_o.getSize()) {
		        for (int i=0;i!=getSize();i++)
		            if (get(i) != v_o.get(i)) return false;
		        return true;
    		}
    	}
    	return false;
    }
}