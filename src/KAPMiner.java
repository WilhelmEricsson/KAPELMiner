import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;



import org.apache.commons.cli.*;
import org.apache.commons.math3.util.Precision;

import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntObjectOpenHashMap;

/**
 * Created by isak on 2017-04-21.
 */

public class KAPMiner {


    private static class ItemsetWithTransactions {
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

    public static BitSet intersection(BitSet a, BitSet b) {
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

    public static void main(String[] args) throws IOException {
        Options options = new Options();
        options.addOption("minSup", true, "minimum support");
        options.addOption("minSupRatio", true, "minimum support ratio");
        options.addOption("minConf", true, "minimum confidence");
        options.addOption("delta", true, "min temporal distance");
        options.addOption("input", true, "input file (use -time if time information is given)");
        options.addOption("time", false, "true if file contains no temporal information (event label, time)");
        options.addOption("output", true, "file for writing the results (default: write to standard out)");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);


            String file = cmd.getOptionValue("input");
            if (file == null) {
                throw new RuntimeException("no input");
            }
            TransactionInput transactionInput = readTransactions(file, !cmd.hasOption("time"));
            System.out.println(transactionInput.transactions);
            double minSup = Double.parseDouble(cmd.getOptionValue("minSup", "0.1"));
            double minSupRatio = Double.parseDouble(cmd.getOptionValue("minSupRatio", "0.0"));
            double minConf = Double.parseDouble(cmd.getOptionValue("minConf", "0.4"));;
            int orderConstraint = Integer.parseInt(cmd.getOptionValue("delta", "1"));

            long start = System.currentTimeMillis();
            List<Rule> rules =
                    findFrequent(transactionInput, minSup, minSupRatio, orderConstraint, minConf);

            String output = cmd.getOptionValue("output", "<std>");
            PrintStream out;
            if ("<std>".equalsIgnoreCase(output)) {
                out = System.out;
            } else {
                out = new PrintStream(new FileOutputStream(output));
            }
            System.err.printf("Found %d rules in %d ms with %.4f MB memory %n", rules.size(),
                    System.currentTimeMillis() - start,
                    (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024.0
                            / 1024.0);
            out.printf("Rule, support, supportRatio, conf, lift%n");
            rules.stream().sorted(Comparator.comparing(Rule::getSupport).reversed()).forEach(rule -> {
                out.printf("\"%s => %s\",%f,%f,%f,%f%n", rule.getX(), rule.getY(), rule.getSupport(),
                        rule.getSupportRatio(), rule.getConfidence(), rule.getLift());
            });

            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("KAPMiner", options);
            System.exit(0);
        }
    }

    private static List<Rule> findFrequent(TransactionInput transactionInput, double minSup,
                                           double minSupRatio, int orderConstraint, double minConf) {
        Map<Itemset, Double> supports = new HashMap<>();
        IntObjectMap<ItemPosition> itemPositionMap = transactionInput.itemPositions;
        double noTransactions = transactionInput.getTransactions();


        List<ItemsetWithTransactions> currentLevel = new ArrayList<>();
        for (Map.Entry<Integer, BitSet> kv : transactionInput.getItemSets().entrySet()) {
            if (kv.getValue().cardinality() / noTransactions >= minSup) {
                Itemset item = new Itemset(new int[] {kv.getKey()});
                currentLevel.add(new ItemsetWithTransactions(item, kv.getValue()));
                supports.put(item, kv.getValue().cardinality() / noTransactions);
            }
        }

        List<Rule> outputRules = new ArrayList<>();
        List<ItemsetWithTransactions> nextLevel = new ArrayList<>();

        Map<Itemset, List<RuleWithTransactions>> currentLevelMap = new HashMap<>();
        Map<Itemset, List<RuleWithTransactions>> prevLevelMap = new HashMap<>();

        int level = 1;
        while (true) {
            for (int i = 0; i < currentLevel.size(); i++) {
                List<RuleWithTransactions> matches = new ArrayList<>();
                for (int j = i + 1; j < currentLevel.size(); j++) {
                    ItemsetWithTransactions iItem = currentLevel.get(i);
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
                            int[] tmpItemSet = new int[newItemset.size() - 1];
                            for (int k = newItemset.size() - 3; k >= 0; k--) {
                                int tmpCnt = 0;
                                for (int l = 0; l < newItemset.size(); l++) {
                                    if (k != l) {
                                        tmpItemSet[tmpCnt++] = newItemset.get(l);
                                    }
                                }

                                List<RuleWithTransactions> ruleWithTransactions =
                                        prevLevelMap.get(new Itemset(tmpItemSet));
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
                                    inter.or(xs.get(0).getTransactions());
                                    for (int i1 = 1; i1 < xs.size(); i1++) {
                                        RuleWithTransactions rule = xs.get(i1);
                                        inter.and(rule.getTransactions());
                                    }

                                    double ruleSup = inter.cardinality() / noTransactions;
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
                                            outputRules.add(new Rule(rule));
                                        }
                                    }
                                }
                                if (ys.size() == level + 1 - r.getY().size()) {
                                    BitSet inter = new BitSet();
                                    inter.or(ys.get(0).getTransactions());
                                    for (int i1 = 1; i1 < ys.size(); i1++) {
                                        RuleWithTransactions rule = ys.get(i1);
                                        inter.and(rule.getTransactions());
                                    }

                                    double ruleSup = inter.cardinality() / noTransactions;
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
                                            outputRules.add(new Rule(rule));
                                        }

                                    }
                                }
                            }
                        } else {
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
                                        outputRules.add(new Rule(rule));
                                    }
                                }
                            }
                        }

                        nextLevel.add(new ItemsetWithTransactions(newItemset, intersectingTransactions));
                        currentLevelMap.put(newItemset, rules);
                    } else {
                        break;
                    }
                }
            }
            if (!nextLevel.isEmpty()) {
                // levels.add(nextLevel);
                currentLevel.clear();
                List<ItemsetWithTransactions> tmp = currentLevel;
                currentLevel = nextLevel;
                nextLevel = tmp;

                prevLevelMap.clear();
                Map<Itemset, List<RuleWithTransactions>> tmpMap = prevLevelMap;
                prevLevelMap = currentLevelMap;
                currentLevelMap = tmpMap;

                level += 1;
            } else {
                break;
            }
        }
        return outputRules;
    }

    private static BitSet itemIntersect(int orderConstraint,
                                        IntObjectMap<ItemPosition> itemPositionMap, BitSet intersectingTransactions, int itemA,
                                        int itemB) {
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

    private static Itemset mergeAntecedents(List<RuleWithTransactions> consequents, int size) {
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

    private static Itemset mergeConsequents(List<RuleWithTransactions> antecedents, int size) {
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


    private static class TransactionInput {
        private final Map<Integer, BitSet> itemSets;
        private final double transactions;
        private final IntObjectMap<ItemPosition> itemPositions;


        private TransactionInput(Map<Integer, BitSet> itemSets, double transactions,
                                 IntObjectMap<ItemPosition> itemPositions) {
            this.itemSets = itemSets;
            this.transactions = transactions;
            this.itemPositions = itemPositions;
        }

        public double getTransactions() {
            return transactions;
        }

        public Map<Integer, BitSet> getItemSets() {
            return itemSets;
        }

        public ItemPosition getPosition(int transaction) {
            return itemPositions.get(transaction);
        }

    }

    private static List<List<Event>> readEventTransactions(String file) {
        Path path = Paths.get(file);
        try {
            List<List<Event>> transactions = new ArrayList<>();
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                List<Event> transaction = new ArrayList<>();
                String[] events = line.trim().split("\\s+");
                for (String event : events) {
                    String[] split = event.split(",");
                    transaction.add(new Event(Integer.parseInt(split[0]), Integer.parseInt(split[1])));
                }
                transactions.add(transaction);
            }
            return transactions;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static class Event {
        int value;
        int time;

        public Event(int value, int time) {
            this.value = value;
            this.time = time;
        }

        public int getValue() {
            return value;
        }

        public int getTime() {
            return time;
        }
    }

    private static TransactionInput readTransactions(String file, boolean b) {
        Map<Integer, BitSet> items = new HashMap<>();

        IntObjectMap<ItemPosition> itemPosition = new IntObjectOpenHashMap<>();
        List<List<Event>> transactions;
        if (b) {
            transactions = readSimpleEventTransactions(file);
        } else {
            transactions = readEventTransactions(file);
        }

        int transactionId = 0;
        for (List<Event> transaction : transactions) {
            ItemPosition pos = new ItemPosition();
            for (Event item : transaction) {
                if (items.containsKey(item.getValue())) {
                    items.get(item.getValue()).set(transactionId);
                } else {
                    BitSet trans = new BitSet();
                    trans.set(transactionId);
                    items.put(item.getValue(), trans);
                }
                pos.putAndExpand(item.getValue(), new Position(item.getTime(), item.getTime()));
            }
            itemPosition.put(transactionId, pos);
            transactionId++;
        }
        return new TransactionInput(items, transactions.size(), itemPosition);
    }

    private static List<List<Event>> readSimpleEventTransactions(String file) {

        Path path = Paths.get(file);
        try {
            List<List<Event>> transactions = new ArrayList<>();
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                List<Event> transaction = new ArrayList<>();
                String[] events = line.trim().split("\\s+");
                for (int i = 0; i < events.length; i++) {
                    String event = events[i];
                    transaction.add(new Event(Integer.parseInt(event), i));
                }
                transactions.add(transaction);
            }
            return transactions;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
