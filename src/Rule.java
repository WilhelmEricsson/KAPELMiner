import java.util.Arrays;

public class Rule{
    private final Itemset x, y;
    private double support, supportRatio, confidence, lift;

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
    @Override
    public boolean equals(Object o){
        if(o instanceof Rule){
            return (x.equals(((Rule) o).x) &&  y.equals(((Rule) o).y));
        }
        return false;

    }

    @Override
    public int hashCode() {
        return x.hashCode() + y.hashCode();
    }
    public void mergeWithEqual(Rule r){
        setSupport((support + r.getSupport()));
        setSupportRatio((supportRatio + r.supportRatio));
        setConfidence((confidence + r.confidence));
        setLift((lift + r.lift));
    }
    public void correctValuesToNumberOfPartitions(int numOfPartitions){
        support /= numOfPartitions;
        supportRatio /= numOfPartitions;
        confidence /= numOfPartitions;
        lift /= numOfPartitions;
    }

    private void setSupport(double support) {
        this.support = support;
    }

    private void setSupportRatio(double supportRatio) {
        this.supportRatio = supportRatio;
    }

    private void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    private void setLift(double lift) {
        this.lift = lift;
    }

}
