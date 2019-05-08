import com.carrotsearch.hppc.IntObjectMap;

import java.util.*;
import java.util.concurrent.*;

public class IntKAPELMiner extends KAPMiner {

    private int numOfPartitions;
    private IntObjectMap<ItemPosition> itemPositionMap;
    //---------------------------------------....MAIN....------------------------------------------------------------------


    //---------------------------------------....CONSTRUCTORS....----------------------------------------------------------

    public IntKAPELMiner(TransactionInput input, double minSup, double minSupRatio, double minConf, int orderConstraint, int numOfPartitions) {
        super(input, minSup, minSupRatio, minConf, orderConstraint);
        this.numOfPartitions = numOfPartitions;
    }


    //---------------------------------------....METHODS....---------------------------------------------------------------
    @Override
    public List<Rule> findFrequent(TransactionInput transactionInput, double minSup, double minSupRatio, int orderConstraint, double minConf) {

        double noTransactions = transactionInput.getTransactions();
        itemPositionMap = transactionInput.getItemPositions();
        initializeDataStructs();

        //initialSupportCalculation(transactionInput, noTransactions);
        parallelExtraction(transactionInput,noTransactions, true);
        prepareNextLevel();
        setLevel(1);

        while (true) {
            for (int i = 0; i < getCurrentLevel().size(); i++) {
                //System.out.println("LEVEL: " + getLevel());
                if(getCurrentLevel().size() - i < 10 || true ){

                    //System.out.println("EJ PARALLEL, SIZE: " + getCurrentLevel().size() + " " + Thread.activeCount());
                    long start = System.currentTimeMillis();
                    extractRules(getLevel(), noTransactions, i, getCurrentLevel(), getNextLevel(), getOutputRules(), itemPositionMap);
                    //System.out.println("TIME ONE ITEM: " + (System.currentTimeMillis() - start));
                }else {
                    //System.out.println("PARALLEL, SIZE: " + getCurrentLevel().size() + " " + Thread.activeCount());
                    //long start = System.currentTimeMillis();
                    parallelExtraction(transactionInput, noTransactions, false, i);
                    //System.out.println("TIME ONE ITEM: " + (System.currentTimeMillis() - start));
                }

            }
            if (!getNextLevel().isEmpty()) {
                prepareNextLevel();
            } else {
                break;
            }
        }
        return getOutputRules();
    }




    private void initializeDataStructs() {
        setSupports(new ConcurrentHashMap<>());
        setCurrentLevelMap(new ConcurrentHashMap<>());
        setPrevLevelMap(new ConcurrentHashMap<>());
        setNextLevel(new ArrayList<>());
        setCurrentLevel(new ArrayList<>());
        setOutputRules(new ArrayList<>());
    }

    @Override
    protected void addToOutputRules(Rule rule) {
        synchronized (getOutputRules()) {
            getOutputRules().add(rule);
        }
    }
    //Skall parallelliseras

    private void parallelExtraction(TransactionInput transactionInput, double noTransactions, boolean initialExtraction){
        parallelExtraction(transactionInput,noTransactions,initialExtraction, -1);
    }
    private void parallelExtraction(TransactionInput transactionInput, double noTransactions, boolean initialExtraction, int compIndex) {
        boolean fin = false;
        ExecutorService es = Executors.newCachedThreadPool();
        es.execute(new RuleExtractor(compIndex, initialExtraction, transactionInput, noTransactions));

        es.shutdown();

        try{
            fin = es.awaitTermination(10000, TimeUnit.MILLISECONDS);
        }catch(InterruptedException ie){
            System.err.println(ie.getMessage());
        }
        while(!fin);


    }

    /**
     *
     * @param numOfPartitions
     * @param inputSize
     * @return array with the partitions' starting index
     */
    private int[] partitionTransactionInput(int numOfPartitions, int inputSize){
        int[] temp = new int[numOfPartitions];
        int partition = inputSize/numOfPartitions;
        for(int i = 0; i < numOfPartitions; i++){
            temp[i] = partition*i;
        }

        return temp;
    }

    protected void extractRules(int level, double noTransactions, ItemsetWithTransactions compItem, List<ItemsetWithTransactions> currentLevel, List<ItemsetWithTransactions> nextLevel,
                                List<Rule> outputRules, IntObjectMap<ItemPosition> itemPositionMap) {

        List<RuleWithTransactions> matches = new ArrayList<>();
        for (int j = 0; j < currentLevel.size(); j++) {
            ItemsetWithTransactions jItem = currentLevel.get(j);

            if (compItem.getItemSet().prefixMatch(jItem.getItemSet(), level - 1)) {
                BitSet intersectingTransactions =
                        intersection(compItem.getTransactions(), jItem.getTransactions());
                if (getMinSup() >= intersectingTransactions.cardinality() / noTransactions)
                    continue;

                double itemsetSup = intersectingTransactions.cardinality() / noTransactions;
                Itemset newItemset = compItem.getItemSet().merge(jItem.getItemSet(), level - 1);
                getSupports().put(newItemset, itemsetSup);

                matches.clear();
                List<RuleWithTransactions> iItemRules = getPrevLevelMap().get(compItem.getItemSet());
                if (iItemRules != null) {
                    matches.addAll(iItemRules);
                }
                List<RuleWithTransactions> jItemRules = getPrevLevelMap().get(jItem.getItemSet());
                if (jItemRules != null) {
                    matches.addAll(jItemRules);
                }
                List<RuleWithTransactions> rules = new ArrayList<>();

                if (level > 1) {
                    extractHigherLevelRules(level, noTransactions, getSupports(), intersectingTransactions, rules, outputRules, getPrevLevelMap(), matches, newItemset);
                } else {
                    extractFirstLevelRules(compItem, jItem, itemPositionMap, intersectingTransactions, noTransactions, getSupports(), rules, outputRules);
                }

                nextLevel.add(new ItemsetWithTransactions(newItemset, intersectingTransactions));
                getCurrentLevelMap().put(newItemset, rules);
            } else {
                break;
            }
        }
    }



        //---------------------------------------....INNER-CLASSES....---------------------------------------------------------


    private class RuleExtractor implements Runnable{
        private final int compIndex;
        private double noTransactions;
        private boolean isInitial;
        private TransactionInput transactionInput;
        private ConcurrentHashMap<Integer, List<ItemsetWithTransactions>> nextLevel;
        //---------------------------------------....CONSTRUCTORS....----------------------------------------------------------
        public RuleExtractor(int compIndex, boolean isInitial, TransactionInput transactionInput, double noTransactions){
            this.compIndex = compIndex;
            this.isInitial = isInitial;
            this.transactionInput = transactionInput;
            this.nextLevel = new ConcurrentHashMap();
            this.noTransactions = noTransactions;
        }

        //---------------------------------------....METHODS....---------------------------------------------------------------
        @Override
        public void run() {
            if(isInitial){
                initialExtraction();
            }else{
                extractRules();

            }
            merge();
        }
        private void extractRules(){
            List<List<ItemsetWithTransactions>> partitions = partition();
            ExecutorService es = Executors.newCachedThreadPool();
            boolean finished = false;
            int threadName = 0;
            for(List<ItemsetWithTransactions> part: partitions){
                es.execute(new Partition(threadName,compIndex, part, noTransactions, this.nextLevel));

                threadName++;
            }
            es.shutdown();
            try{
                finished = es.awaitTermination(1000,TimeUnit.MILLISECONDS);
            }catch (InterruptedException ie){
                ie.printStackTrace();
            }
            while (!finished);
        }
        private void initialExtraction(){
            boolean finished = false;
            ExecutorService es = Executors.newCachedThreadPool();
            int[] partitions = partitionTransactionInput(numOfPartitions,transactionInput.getItemSets().size());
            for(int i = 0; i < numOfPartitions-1; i++){
                es.execute(new InitialRuleSupportExtractor(i,partitions[i], partitions[i+1] , transactionInput, nextLevel));
            }
            es.execute(new InitialRuleSupportExtractor(numOfPartitions-1,partitions[numOfPartitions-1], transactionInput.getItemSets().size(), transactionInput, nextLevel));

            es.shutdown();
            do{
                try{
                    finished = es.awaitTermination(10000, TimeUnit.MILLISECONDS);
                }catch(InterruptedException ie){
                    System.err.println(ie.getMessage());
                }
            }while(!finished);
        }
        private void merge(){

            for(Integer i =0; i < nextLevel.size(); i++){
                getNextLevel().addAll(nextLevel.get(i));
            }


        }

        private List<List<ItemsetWithTransactions>> partition(){
            int sizeDif = getCurrentLevel().size()-(compIndex+1);
            int partitionSize = sizeDif/numOfPartitions;
            List<List<ItemsetWithTransactions>> partitions = new ArrayList<>();
            if(partitionSize <= 1 || numOfPartitions < 2){
                partitions.add(getCurrentLevel().subList(compIndex+1,getCurrentLevel().size()-1));
            }else{
                int first = compIndex +1;
                for(int i = 0; i < numOfPartitions-1; i++){
                    partitions.add(getCurrentLevel().subList(first, (first + partitionSize)));
                    first += partitionSize;
                }
                partitions.add(getCurrentLevel().subList(first, getCurrentLevel().size()));

            }

            return partitions;
        }
    }


    //------------_____________------------_____________------------_____________------------_____________------------_____________



    private class Partition implements Runnable{
        private int compIndex;
        private List<ItemsetWithTransactions> partitionData;
        private double noTransactions;
        private List<ItemsetWithTransactions> nextLevel;
        //---------------------------------------....CONSTRUCTORS....----------------------------------------------------------
        private Partition(int threadName, int compIndex, List<ItemsetWithTransactions> partitionData, double noTransactions, ConcurrentHashMap<Integer, List<ItemsetWithTransactions>> partitionNextLevel){
            this.compIndex = compIndex;
            this.partitionData = partitionData;
            this.noTransactions = noTransactions;
            this.nextLevel = new ArrayList<>();
            partitionNextLevel.put(threadName, this.nextLevel);
        }


        //---------------------------------------....METHODS....---------------------------------------------------------------
        @Override
        public void run() {
            extractRules(getLevel(), noTransactions, getCurrentLevel().get(compIndex), partitionData, nextLevel, getOutputRules(), itemPositionMap);
        }
    }


    private class InitialRuleSupportExtractor implements Runnable{
        private int firstElement, lastElement;
        private  TransactionInput transactionInput;
        private int threadName;
        private ConcurrentHashMap<Integer, List<ItemsetWithTransactions>> nextLevel;
        //----------------------------------------CONSTRUCTOR----------------------------------------------------
        public InitialRuleSupportExtractor(int threadName, int firstElement, int lastElement , TransactionInput transactionInput,ConcurrentHashMap<Integer, List<ItemsetWithTransactions> >nextLevel ){
            this.threadName = threadName;
            this.firstElement = firstElement;
            this.lastElement = lastElement;
            this.nextLevel = nextLevel;
            this.nextLevel.put(threadName, new ArrayList<>());
            this.transactionInput = transactionInput;

        }
        //----------------------------------------METHODS--------------------------------------------------------


        @Override
        public void run(){
            int currentKey = firstElement;
            double noTransactions = transactionInput.getTransactions();
            while(currentKey < lastElement){
                if(transactionInput.getItemSets().containsKey(currentKey)){
                    BitSet bitSet = transactionInput.getItemSets().get(currentKey);
                    if (bitSet.cardinality() / noTransactions >= getMinSup()) {
                        Itemset item = new Itemset(new int[]{currentKey});
                        nextLevel.get(threadName).add(new ItemsetWithTransactions(item, bitSet));
                        getSupports().put(item, bitSet.cardinality() / noTransactions);
                    }
                }
                currentKey++;
            }
        }
    }

}
