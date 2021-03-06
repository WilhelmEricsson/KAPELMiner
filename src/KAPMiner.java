import com.carrotsearch.hppc.IntObjectMap;
import org.apache.commons.math3.util.Precision;


import java.util.*;


/**
 * Created by isak on 2017-04-21.
 */

public class KAPMiner{
    private TransactionInput input;
    private double minSup, minSupRatio, minConf;
    private int orderConstraint, level;
    private Map<Itemset, Double> supports;
    private Map<Itemset, List<RuleWithTransactions>> currentLevelMap, prevLevelMap;
    private List<ItemsetWithTransactions> currentLevel, nextLevel;
    private List<Rule> outputRules;

    public KAPMiner(TransactionInput input, double minSup, double minSupRatio, double minConf, int orderConstraint) {
        this.input = input;
        this.minSup = minSup;
        this.minSupRatio = minSupRatio;
        this.minConf = minConf;
        this.orderConstraint = orderConstraint;
    }


    public BitSet intersection(BitSet a, BitSet b) {
        BitSet clone;
        BitSet and;
        if (a.size() < b.size()) {
            clone = a;
            and = b;
        } else {
            clone = b;
            and = a;
        }

        BitSet intersection = (BitSet) clone.clone();
        intersection.and(and);
        return intersection;
    }

    public List<Rule> findFrequent(){
        return findFrequent(input,minSup,minSupRatio,orderConstraint,minConf);
    }

    // minSup, etc används inte pga att dessa redan är instansvariabler
    public List<Rule> findFrequent(TransactionInput transactionInput, double minSup, double minSupRatio, int orderConstraint, double minConf) {

        double noTransactions = transactionInput.getTransactions();
        IntObjectMap<ItemPosition> itemPositionMap = transactionInput.getItemPositions();
        supports = new HashMap<>();
        currentLevelMap = new HashMap<>();
        prevLevelMap = new HashMap<>();
        nextLevel = new ArrayList<>();
        currentLevel = new ArrayList<>();
        outputRules = new ArrayList<>();
        initialSupportCalculation(transactionInput,noTransactions);

        level = 1;

        while (true) {
            for (int i = 0; i < currentLevel.size(); i++) {
                extractRules(level, noTransactions, i, currentLevel, nextLevel, outputRules, itemPositionMap);
            }
            if (!nextLevel.isEmpty()) {
                prepareNextLevel();
            } else {
                break;
            }
        }
        return outputRules;
    }
    protected void prepareNextLevel(){
        currentLevel.clear();
        List<ItemsetWithTransactions> tmp = currentLevel;
        currentLevel = nextLevel;
        nextLevel = tmp;

        prevLevelMap.clear();
        Map<Itemset, List<RuleWithTransactions>> tmpMap = prevLevelMap;
        prevLevelMap = currentLevelMap;
        currentLevelMap = tmpMap;
        level += 1;
    }

    protected void initialSupportCalculation(TransactionInput transactionInput, double noTransactions){

        for (Map.Entry<Integer, BitSet> kv : transactionInput.getItemSets().entrySet()) {
            if (kv.getValue().cardinality() / noTransactions >= minSup) {
                Itemset item = new Itemset(new int[] {kv.getKey()});
                currentLevel.add(new ItemsetWithTransactions(item, kv.getValue()));
                supports.put(item, kv.getValue().cardinality() / noTransactions);
            }
        }
    }

    protected void extractRules(int level, double noTransactions, int compIndex, List<ItemsetWithTransactions> currentLevel, List<ItemsetWithTransactions> nextLevel,
                                List<Rule> outputRules, IntObjectMap<ItemPosition> itemPositionMap){

        List<RuleWithTransactions> matches = new ArrayList<>();
        for (int j = compIndex + 1; j < currentLevel.size(); j++) {
            ItemsetWithTransactions iItem = currentLevel.get(compIndex);
            ItemsetWithTransactions jItem = currentLevel.get(j);

            if (iItem.getItemSet().prefixMatch(jItem.getItemSet(), level - 1)) {
                BitSet intersectingTransactions =
                        intersection(iItem.getTransactions(), jItem.getTransactions());
                if (minSup >= intersectingTransactions.cardinality() / noTransactions)
                    continue;

                double itemsetSup = intersectingTransactions.cardinality() / noTransactions;
                Itemset newItemset = iItem.getItemSet().merge(jItem.getItemSet(), level - 1);
                supports.put(newItemset, itemsetSup);

                matches.clear();
                List<RuleWithTransactions> iItemRules = prevLevelMap.get(iItem.getItemSet());
                if (iItemRules != null) {
                    matches.addAll(iItemRules);
                }
                List<RuleWithTransactions> jItemRules = prevLevelMap.get(jItem.getItemSet());
                if (jItemRules != null) {
                    matches.addAll(jItemRules);
                }
                List<RuleWithTransactions> rules = new ArrayList<>();

                if (level > 1) {
                    extractHigherLevelRules(level, noTransactions,supports,intersectingTransactions,rules,outputRules,prevLevelMap,matches,newItemset);
                } else {
                    extractFirstLevelRules(iItem, jItem, itemPositionMap, intersectingTransactions, noTransactions, supports, rules, outputRules);
                }

                nextLevel.add(new ItemsetWithTransactions(newItemset, intersectingTransactions));
                currentLevelMap.put(newItemset, rules);
            } else {
                break;
            }
        }
    }

    protected void extractFirstLevelRules(ItemsetWithTransactions iItem, ItemsetWithTransactions jItem, IntObjectMap<ItemPosition> itemPositionMap, BitSet intersectingTransactions,
                                        double noTransactions, Map<Itemset, Double> supports, List<RuleWithTransactions> rules, List<Rule> outputRules){
        List<ItemsetWithTransactions> matches2 = new ArrayList<>();

        matches2.add(iItem);
        matches2.add(jItem);
        for (int k = 0; k < matches2.size(); k++) {
            ItemsetWithTransactions kOrder = matches2.get(k);
            ItemsetWithTransactions mOrder = null;
            for (int m = 0; m < matches2.size(); m++) {
                if (m == k) {
                    continue;
                }
                mOrder = matches2.get(m);
            }
            int itemA = kOrder.getItemSet().get(0);
            int itemB = mOrder.getItemSet().get(0);


            BitSet beforeIntersection = itemIntersect(orderConstraint, itemPositionMap,
                    intersectingTransactions, itemA, itemB);


            double ruleSup = beforeIntersection.cardinality() / noTransactions;
            if (ruleSup >= minSup) {
                double supportRatio = beforeIntersection.cardinality()
                        / (double) intersectingTransactions.cardinality();

                double ORconf = ruleSup / supports.get(kOrder.getItemSet());
                double lift = ruleSup
                        / (supports.get(kOrder.getItemSet()) * supports.get(mOrder.getItemSet()));
                RuleWithTransactions rule = new RuleWithTransactions(kOrder.getItemSet(),
                        mOrder.getItemSet(), beforeIntersection, ruleSup, supportRatio, ORconf, lift);
                rules.add(rule);
                if (Precision.compareTo(rule.getORconf(), minConf, 0.0001) >= 0
                        && supportRatio >= minSupRatio) {
                    addToOutputRules(new Rule(rule));
                }
            }
        }
    }

    protected void extractHigherLevelRules(int level, double noTransactions,  Map<Itemset, Double> supports, BitSet intersectingTransactions, List<RuleWithTransactions> rules,
                              List<Rule> outputRules, Map<Itemset, List<RuleWithTransactions>> prevLevelMap, List<RuleWithTransactions> matches, Itemset newItemset ){
        int[] tmpItemSet = new int[newItemset.size() - 1];
        for (int k = newItemset.size() - 3; k >= 0; k--) {
            int tmpCnt = 0;
            for (int l = 0; l < newItemset.size(); l++) {
                if (k != l) {
                    tmpItemSet[tmpCnt++] = newItemset.get(l);
                }
            }

            List<RuleWithTransactions> ruleWithTransactions = prevLevelMap.get(new Itemset(tmpItemSet));

            if (ruleWithTransactions != null) {
                matches.addAll(ruleWithTransactions);
            }
        }

        Set<Itemset> usedX = new HashSet<>();
        Set<Itemset> usedY = new HashSet<>();
        for (int k = 0; k < matches.size(); k++) {
            List<RuleWithTransactions> xs = new ArrayList<>();
            List<RuleWithTransactions> ys = new ArrayList<>();

            RuleWithTransactions r = matches.get(k);
            boolean checkedX = usedX.contains(r.getX());
            boolean checkedY = usedY.contains(r.getY());


            if (!checkedX) {
                xs.add(r);
                usedX.add(r.getX());
            }

            if (!checkedY) {
                ys.add(r);
                usedY.add(r.getY());
            }

            for (int l = k + 1; l < matches.size(); l++) {
                RuleWithTransactions o = matches.get(l);
                if (!checkedX && o.getX().equals(r.getX())) {
                    xs.add(o);
                }
                if (!checkedY && o.getY().equals(r.getY())) {
                    ys.add(o);
                }
            }

            // If the number of itemsets to merge for the y
            if (xs.size() == level + 1 - r.getX().size()) {
                BitSet inter = new BitSet();
                double ruleSup = ruleInterSupport(inter, xs, noTransactions);

                if (ruleSup >= minSup) {
                    double supportRatio =
                            inter.cardinality() / (double) intersectingTransactions.cardinality();
                    Itemset mergedAntecedent = mergeAntecedents(xs, xs.size() - 1);
                    if (usedY.contains(mergedAntecedent)) {
                        continue;
                    }
                    double confidence = ruleSup / supports.get(r.getX());
                    double lift = ruleSup / (supports.get(mergedAntecedent) * supports.get(r.getX()));
                    RuleWithTransactions rule = new RuleWithTransactions(r.getX(), mergedAntecedent,
                            inter, ruleSup, supportRatio, confidence, lift);
                    rules.add(rule);
                    if (Precision.compareTo(confidence, minConf, 0.0001) >= 0
                            && supportRatio >= minSupRatio) {
                        Rule temp = new Rule(rule);
                        addToOutputRules(temp);
                    }
                }
            }
            if (ys.size() == level + 1 - r.getY().size()) {
                BitSet inter = new BitSet();
                double ruleSup = ruleInterSupport(inter,ys,noTransactions);

                if (ruleSup >= minSup) {
                    double supportRatio =
                            inter.cardinality() / (double) intersectingTransactions.cardinality();
                    Itemset mergeConsequents = mergeConsequents(ys, ys.size() - 1);
                    if (usedX.contains(mergeConsequents)) {
                        continue;
                    }

                    double ORconf = ruleSup / supports.get(mergeConsequents);
                    double lift = ruleSup / (supports.get(mergeConsequents) * supports.get(r.getY()));

                    RuleWithTransactions rule = new RuleWithTransactions(mergeConsequents, r.getY(),
                            inter, ruleSup, supportRatio, ORconf, lift);
                    rules.add(rule);
                    if (Precision.compareTo(rule.getORconf(), minConf, 0.0001) >= 0
                            && supportRatio >= minSupRatio) {
                        addToOutputRules(new Rule(rule));
                    }

                }
            }
        }
    }
    protected double ruleInterSupport(BitSet inter, List<RuleWithTransactions> xsys, double noTransactions){
        inter.or(xsys.get(0).getTransactions());
        for (int i1 = 1; i1 < xsys.size(); i1++) {
            RuleWithTransactions rule = xsys.get(i1);
            inter.and(rule.getTransactions());
        }
        return inter.cardinality() / noTransactions;
    }

    protected void addToOutputRules(Rule rule){
        outputRules.add(rule);
    }

    private BitSet itemIntersect(int orderConstraint, IntObjectMap<ItemPosition> itemPositionMap, BitSet intersectingTransactions, int itemA, int itemB) {
        BitSet beforeIntersection = new BitSet();
        for (int transactionId =
             intersectingTransactions.nextSetBit(0); transactionId >= 0; transactionId =
                     intersectingTransactions.nextSetBit(transactionId + 1)) {
            ItemPosition pos = itemPositionMap.get(transactionId);
            if (pos.get(itemB).getLast() - pos.get(itemA).getFirst() >= orderConstraint) {
                beforeIntersection.set(transactionId);
            }
            if (transactionId == Integer.MAX_VALUE) {
                break; // or (i+1) would overflow
            }
        }
        return beforeIntersection;
    }

    private Itemset mergeAntecedents(List<RuleWithTransactions> consequents, int size) {
        int[] union = new int[size + 1];
        Arrays.fill(union, Integer.MAX_VALUE);
        for (int i = 0; i < size; i++) {
            for (RuleWithTransactions rule : consequents) {
                int cons = rule.getY().get(i);
                if (cons < union[i]) {
                    union[i] = cons;
                }
                union[i + 1] = cons;
            }

        }
        return new Itemset(union);
    }

    private Itemset mergeConsequents(List<RuleWithTransactions> antecedents, int size) {
        int[] union = new int[size + 1];
        // Arrays.fill(union, Integer.MAX_VALUE);
        for (int i = 0; i < size; i++) {
            for (RuleWithTransactions rule : antecedents) {
                int cons = rule.getX().get(i);
                if (cons < union[i] || union[i] == 0) {
                    union[i] = cons;
                }
                union[i + 1] = cons;
            }

        }
        return new Itemset(union);
    }

    /*
    SETTERS and GETTERS MAINLY USED BY SUB-CLASS
     */
    public void setSupports(Map<Itemset, Double> supports) {
        this.supports = supports;
    }
    public void setCurrentLevelMap(Map<Itemset, List<RuleWithTransactions>> currentLevelMap) {
        this.currentLevelMap = currentLevelMap;
    }
    public void setPrevLevelMap(Map<Itemset, List<RuleWithTransactions>> prevLevelMap) {
        this.prevLevelMap = prevLevelMap;
    }
    public void setCurrentLevel(List<ItemsetWithTransactions> currentLevel) {
        this.currentLevel = currentLevel;
    }
    public void setNextLevel(List<ItemsetWithTransactions> nextLevel) {
        this.nextLevel = nextLevel;
    }
    public void setOutputRules(List<Rule> outputRules){ this.outputRules = outputRules;}
    public void setLevel(int level){this.level = level;}

    public int getLevel() {
        return level;
    }
    public Map<Itemset, Double> getSupports() {
        return supports;
    }
    public Map<Itemset, List<RuleWithTransactions>> getCurrentLevelMap() {
        return currentLevelMap;
    }
    public Map<Itemset, List<RuleWithTransactions>> getPrevLevelMap() {
        return prevLevelMap;
    }
    public List<ItemsetWithTransactions> getCurrentLevel() {
        return currentLevel;
    }
    public List<ItemsetWithTransactions> getNextLevel() {
        return nextLevel;
    }
    public List<Rule> getOutputRules() {
        return outputRules;
    }
    public TransactionInput getInput() {
        return input;
    }
    public double getMinSup() {
        return minSup;
    }
    public double getMinSupRatio() {
        return minSupRatio;
    }
    public double getMinConf() {
        return minConf;
    }
    public int getOrderConstraint() {
        return orderConstraint;
    }

}
