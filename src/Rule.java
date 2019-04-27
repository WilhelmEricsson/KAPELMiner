public class Rule {
    private final Itemset x, y;
    private final double support, supportRatio, confidence, lift;

    public Rule(RuleWithTransactions rule) {
        this.x = rule.getX();
        this.y = rule.getY();
        this.support = rule.getSupport();
        this.supportRatio = rule.getSupportRatio();
        this.confidence = rule.getORconf();
        this.lift = rule.getLift();
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

    public double getConfidence() {
        return confidence;
    }

    public double getLift() {
        return lift;
    }
}
