package jokrey.utilities.animation.util;

import java.util.Arrays;

//TODO WORK IN PROGESS - NOT YET USED IN A PRACTICAL APPLICATION
public class AEImage {
    private final int[][] data;
    public AEImage(int[][] data) {
        this.data=data;
        if(data.length==0) throw new IllegalArgumentException("2d has to have same number of rows as columns");
        int orig = data[0].length;
        for(int x=0;x<data.length;x++)
            if(orig!=data[x].length)
                throw new IllegalArgumentException("each column has to be of equals size. Arrays has to be rectangular");
    }

    public int getWidth() {
        return data.length;
    }
    public int getHeight() {
        return data[0].length;
    }
    public int[][] getData() {
        return data;
    }

    public AEColor getColorAt(int x, int y) {
        return new AEColor(data[x][y]);
    }

    @Override public boolean equals(Object obj) {
        if(obj instanceof AEImage) {
            AEImage o = (AEImage)obj;
            return Arrays.deepEquals(data, o.data);
//            if(data.length==o.data.length)
//                for(int x=0;x<data.length;x++)
//                    if(data[x].length==o.data[x].length)
//                        for (int y = 0; y < data[x].length; y++)
//                            if (data[x][y] != o.data[x][y])
//                                return false;
        }
        return false;
    }
    @Override public int hashCode() {
        return Arrays.deepHashCode(data);
    }
}
