import org.apache.commons.cli.*;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExtKAPELMiner {
    private static ConcurrentHashMap<Integer, List<Rule>> rulesFromPartitions;
    private static double minSup, minSupRatio, minConf;
    private static int orderConstraint ,numOfPartitions;
    //------------------------------------------CONSTRUCTORS------------------------------------------
    public ExtKAPELMiner(){}


    //------------------------------------------MAIN--------------------------------------------------

    public static void main(String[] args){
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
            numOfPartitions = Runtime.getRuntime().availableProcessors();
            HashMap<Integer, List<String>> partitions = partitionData(file,numOfPartitions);
            List<TransactionInput> partitionsTransactionInput = new ArrayList<>();
            for(int i = 0; i < partitions.size(); i++){
                partitionsTransactionInput.add(TransactionInput.readTransactions(partitions.get(i), !cmd.hasOption("time")));
            }

            minSup = Double.parseDouble(cmd.getOptionValue("minSup", "0.1"));
            minSupRatio = Double.parseDouble(cmd.getOptionValue("minSupRatio", "0.0"));
            minConf = Double.parseDouble(cmd.getOptionValue("minConf", "0.4"));
            orderConstraint = Integer.parseInt(cmd.getOptionValue("delta", "1"));

            long start = System.currentTimeMillis();
            rulesFromPartitions = new ConcurrentHashMap<>();
            ExecutorService es =  Executors.newCachedThreadPool();
            System.out.println("Active: "  + Thread.activeCount() + " initial");
            for(int i = 0; i < partitions.size(); i++){

                //Här ska trådarna startas

                es.execute(new Partition(i, partitionsTransactionInput.get(i)));
                System.out.println("Active: "  + Thread.activeCount() + " Part: " + i);

            }
            boolean finished = false;
            es.shutdown();

            try{
                finished = es.awaitTermination(100000, TimeUnit.MILLISECONDS);
            }catch(InterruptedException ie){
                System.err.println(ie.getMessage());
            }



                //Här ska mergen ske

            System.out.println("time: " + (System.currentTimeMillis() - start) +" ms");
            List<Rule> rules = merge(numOfPartitions, minSup, minSupRatio,minConf);

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
            formatter.printHelp("ExtKAPELMiner", options);
            System.exit(0);
        }

    }

    //------------------------------------------METHODS-----------------------------------------------

    private static HashMap<Integer, List<String>> partitionData(String file , int numOfPartitions){
        //TEMP hårdkodat hur många partitions
        return TransactionInput.partitionTransactions(file,numOfPartitions);
    }


    // DENNA MÅSTE FIXAS,
    private static List<Rule> merge(int numOfPartitions, double minSup, double minSupRatio, double minConf){

        HashMap<Rule, Rule> mergedRules = new HashMap<>();
        List<Rule> listRule = new ArrayList<>();
        for(List<Rule> rules : rulesFromPartitions.values()){
            for(Rule r: rules){
                //System.out.println(r.getX() +" " + r.getY() + " Hash: "  + r.hashCode());
                if(mergedRules.containsKey(r)){
                    r.correctValuesToNumberOfPartitions(numOfPartitions);
                    mergedRules.get(r).mergeWithEqual(r);
                }else{
                    r.correctValuesToNumberOfPartitions(numOfPartitions);
                    mergedRules.put(r, r);
                }
            }
        }

       for(Rule rule: mergedRules.values()){
            Rule temp = prune(rule, minSup, minSupRatio,minConf);
            if(temp != null){
                listRule.add(temp);
            }
       }
        return listRule;
    }
    private static Rule prune(Rule rule, double minSup, double minSupRatio, double minConf){
        if(rule.getSupport() >= minSup && rule.getSupportRatio() >= minSupRatio && rule.getConfidence() >= minConf){
            return rule;
        }
        return null;
    }



    //------------------------------------------INNER-CLASSES------------------------------------------
    private static class Partition implements Runnable{
        private int partition;
        private TransactionInput partitionsTransactionInput;

        public Partition(int partition, TransactionInput transactionInput) {
            this.partition = partition;
            this.partitionsTransactionInput = transactionInput;
        }
        @Override
        public void run() {
            try{
                long start = System.currentTimeMillis();


                // om inte minSup mm delas på antalet partitions så blir resultatet missvisande, dock blir antalet kandidatregler för många vilket slöar ner allt.
                List<Rule> rules = (new KAPMiner(partitionsTransactionInput, minSup, minSupRatio, minConf,orderConstraint)).findFrequent();
                long end = System.currentTimeMillis() - start;
                System.out.println("Part: " +  partition + " time: " + end +" ms NUMofRUlES: " + rules.size());

                synchronized (rulesFromPartitions){
                    rulesFromPartitions.put(partition,rules);
                }

            }catch(Exception e){
                System.err.println( e.getMessage() + " partition: " + partition + " " + partitionsTransactionInput.getTransactions() + " " );
                e.printStackTrace();
            }

        }
    }

}
