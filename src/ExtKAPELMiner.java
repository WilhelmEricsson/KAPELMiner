import org.apache.commons.cli.*;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class ExtKAPELMiner {



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

            HashMap<Integer, List<String>> partitions = partitionData(file);
            List<TransactionInput> partitionsTransactionInput = new ArrayList<>();
            for(int i = 0; i < partitions.size(); i++){
                partitionsTransactionInput.add(TransactionInput.readTransactions(partitions.get(i), !cmd.hasOption("time")));
            }

            double minSup = Double.parseDouble(cmd.getOptionValue("minSup", "0.1"));
            double minSupRatio = Double.parseDouble(cmd.getOptionValue("minSupRatio", "0.0"));
            double minConf = Double.parseDouble(cmd.getOptionValue("minConf", "0.4"));;
            int orderConstraint = Integer.parseInt(cmd.getOptionValue("delta", "1"));

            long start = System.currentTimeMillis();
            List<List<Rule>> partitionRules = new ArrayList<>();
            for(int i = 0; i < partitions.size(); i++){

                //Här ska trådarna startas
                partitionRules.add(KAPMiner.findFrequent(partitionsTransactionInput.get(i), minSup, minSupRatio, orderConstraint, minConf));


            }
                //Här ska mergen ske


            String output = cmd.getOptionValue("output", "<std>");
            PrintStream out;
            if ("<std>".equalsIgnoreCase(output)) {
                out = System.out;
            } else {
                out = new PrintStream(new FileOutputStream(output));
            }
            for(List<Rule>rules: partitionRules) {
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
            }
        } catch (Exception e) {
            e.printStackTrace();
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("ExtKAPELMiner", options);
            System.exit(0);
        }

    }

    //------------------------------------------METHODS-----------------------------------------------

    private static HashMap<Integer, List<String>> partitionData(String file){
        //TEMP hårdkodat hur många partitions
        return TransactionInput.partitionTransactions(file, Runtime.getRuntime().availableProcessors()/8);
    }



    //------------------------------------------INNER-CLASSES------------------------------------------

}
