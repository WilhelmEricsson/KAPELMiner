import java.util.BitSet;

public class ItemsetWithTransactions {
    private Itemset itemSet;
    private final BitSet transactions;

    public ItemsetWithTransactions(Itemset item, BitSet transactions) {
        this.itemSet = item;
        this.transactions = transactions;
    }

    public double frequency() {
        return transactions.cardinality();
    }

    public BitSet getTransactions() {
        return transactions;
    }

    public Itemset getItemSet() {
        return itemSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ItemsetWithTransactions itemSet = (ItemsetWithTransactions) o;
        return this.itemSet.equals(itemSet.itemSet);
    }

    @Override
    public int hashCode() {
        return itemSet.hashCode();
    }

    @Override
    public String toString() {
        return "#{" + itemSet + " freq:" + frequency() + "}";
    }
}
