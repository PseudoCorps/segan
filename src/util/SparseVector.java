package util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

/**
 *
 * @author vietan
 */
public class SparseVector {

    private HashMap<Integer, Double> values;

    public SparseVector() {
        this.values = new HashMap<Integer, Double>();
    }

    public int size() {
        return this.values.size();
    }

    public double sum() {
        double sum = 0.0;
        for (double val : values.values()) {
            sum += val;
        }
        return sum;
    }

    public Double get(int index) {
        return this.values.get(index);
    }

    public void set(int index, Double value) {
        this.values.put(index, value);
    }

    public boolean containsIndex(int idx) {
        return this.values.containsKey(idx);
    }

    public Set<Integer> getIndices() {
        return this.values.keySet();
    }

    public ArrayList<Integer> getSortedIndices() {
        ArrayList<Integer> sortedIndices = new ArrayList<Integer>();
        for (int idx : getIndices()) {
            sortedIndices.add(idx);
        }
        Collections.sort(sortedIndices);
        return sortedIndices;
    }

    /**
     * Add another sparse vector to this vector
     *
     * @param other The other sparse vector
     */
    public void add(SparseVector other) {
        for (int idx : other.getIndices()) {
            double otherVal = other.get(idx);
            Double thisVal = this.get(idx);
            if (thisVal == null) {
                this.set(idx, otherVal);
            } else {
                this.set(idx, thisVal + otherVal);
            }
        }
    }

    /**
     * Divide each element by a constant
     *
     * @param c The constant
     */
    public void divide(double c) {
        if (c == 0) {
            throw new RuntimeException("Dividing 0");
        }
        for (int idx : this.getIndices()) {
            this.set(idx, this.get(idx) / c);
        }
    }

    public void multiply(double c) {
        for (int idx : this.getIndices()) {
            this.set(idx, this.get(idx) * c);
        }
    }

    public double getL2Norm() {
        double sumSquare = 0.0;
        for (double val : this.values.values()) {
            sumSquare += val * val;
        }
        return Math.sqrt(sumSquare);
    }

    public double dotProduct(SparseVector other) {
        double sum = 0.0;
        for (int idx : this.getIndices()) {
            Double otherVal = other.get(idx);
            if (otherVal == null) {
                continue;
            }
            sum += this.get(idx) * otherVal;
        }
        return sum;
    }

    public double cosineSimilarity(SparseVector other) {
        if (this.size() == 0 || other.size() == 0) {
            return 0.0;
        }
        double thisL2Norm = this.getL2Norm();
        double thatL2Norm = other.getL2Norm();
        double dotProd = this.dotProduct(other);
        double cosine = dotProd / (thisL2Norm * thatL2Norm);
        return cosine;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append(Integer.toString(this.values.size()));
        for (int idx : this.getIndices()) {
            str.append(" ").append(idx).append(":").append(this.get(idx));
        }
        return str.toString();
    }

    public static SparseVector parseString(String str) {
        SparseVector vector = new SparseVector();
        String[] sstr = str.split(" ");
        for (int ii = 1; ii < sstr.length; ii++) {
            String[] se = sstr[ii].split(":");
            vector.set(Integer.parseInt(se[0]), Double.parseDouble(se[1]));
        }
        return vector;
    }

    public ArrayList<RankingItem<Integer>> getSortedList() {
        ArrayList<RankingItem<Integer>> sortedList = new ArrayList<RankingItem<Integer>>();
        for (int key : this.getIndices()) {
            sortedList.add(new RankingItem<Integer>(key, this.values.get(key)));
        }
        Collections.sort(sortedList);
        return sortedList;
    }
}
