import java.util.Arrays;
import java.util.stream.Collectors;

public class Itemset {
    private final int[] items;
    private double support;

    public Itemset(int[] items) {
        this.items = items;
    }

    public int get(int i) {
        return items[i];
    }

    public int size() {
        return items.length;
    }

    public boolean prefixMatch(Itemset other, int prefixSize) {
        // assume sorted and both have size < prefixSize
        for (int i = 0; i < prefixSize; i++) {
            if (items[i] != other.items[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean contains(Itemset other) {
        int i = 0, j = 0;
        while (i < other.items.length && j < this.items.length) {
            if (this.items[j] < other.items[i]) {
                j++;
            } else if (this.items[j] == other.items[i]) {
                j++;
                i++;
            } else if (this.items[j] > other.items[i]) {
                return false;
            }
        }
        return i >= other.items.length;
    }

    public Itemset merge(Itemset b, int prefixSize) {
        int[] union = new int[this.items.length + 1];
        System.arraycopy(this.items, 0, union, 0, prefixSize);
        if (this.items[prefixSize] < b.items[prefixSize]) {
            union[prefixSize] = this.items[prefixSize];
            union[prefixSize + 1] = b.items[prefixSize];
        } else {
            union[prefixSize] = b.items[prefixSize];
            union[prefixSize + 1] = this.items[prefixSize];
        }
        return new Itemset(union);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Itemset other = (Itemset) o;
        return Arrays.equals(items, other.items);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(items);
    }

    @Override
    public String toString() {
        return "{" + Arrays.stream(items).mapToObj(Integer::toString).collect(Collectors.joining(","))
                + "}";
    }
}
