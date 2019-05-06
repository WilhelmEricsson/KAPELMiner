import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class IntKAPELMiner extends KAPMiner {
    private ConcurrentHashMap<Integer, RuleWithTransactions> nextLevelMap;
    private ConcurrentHashMap<Itemset, Double> supports;


    //---------------------------------------....MAIN....------------------------------------------------------------------



    //---------------------------------------....CONSTRUCTORS....----------------------------------------------------------

    public IntKAPELMiner(TransactionInput input, double minSup, double minSupRatio, double minConf, int orderConstraint) {
        super(input, minSup, minSupRatio, minConf, orderConstraint);
    }


    //---------------------------------------....METHODS....---------------------------------------------------------------




    //---------------------------------------....INNER-CLASSES....---------------------------------------------------------


    private class RuleExtractor implements Runnable{

        //---------------------------------------....CONSTRUCTORS....----------------------------------------------------------



        //---------------------------------------....METHODS....---------------------------------------------------------------
        @Override
        public void run() {

        }
    }
}
