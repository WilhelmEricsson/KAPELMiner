import java.util.BitSet;

public class RuleWithTransactions {
    private final BitSet transactions;
    private final Itemset x, y;

    private final double support, supportRatio, ORconf;
    private final double lift;

    public RuleWithTransactions(Itemset x, Itemset y, BitSet transactions, double ruleSup, double supportRatio, double confidence, double lift) {
        this.x = x;
        this.y = y;
        this.transactions = transactions;
        this.ORconf = confidence;
        this.support = ruleSup;
        this.supportRatio = supportRatio;
        this.lift = lift;
    }

    public BitSet getTransactions() {
        return transactions;
    }

    public Itemset getX() {
        return x;
    }

    public Itemset getY() {
        return y;
    }

    public double getSupport() {
        return support;
    }

    public double getSupportRatio() {
        return supportRatio;
    }

    public double getORconf() {
        return ORconf;
    }

    public double getLift() {
        return lift;
    }

    public double frequency() {
        return transactions.cardinality();
    }

    @Override
    public String toString() {
        return "{<" + x + " => " + y + "> freq: " + transactions + "}";
    }
}
