import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.IntObjectOpenHashMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TransactionInput {
    private final Map<Integer, BitSet> itemSets;
    private final double transactions;
    private final IntObjectMap<ItemPosition> itemPositions;


    public TransactionInput(Map<Integer, BitSet> itemSets, double transactions,IntObjectMap<ItemPosition> itemPositions) {
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
    public IntObjectMap<ItemPosition> getItemPositions() {
        return itemPositions;
    }



    public static TransactionInput readTransactions(String file, boolean hasNoTimestamp) {
        Map<Integer, BitSet> items = new HashMap<>();

        IntObjectMap<ItemPosition> itemPosition = new IntObjectOpenHashMap<>();
        List<List<Event>> transactions;
        if (hasNoTimestamp) {
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
    private static List<List<Event>> readEventTransactions(String file) {
        Path path = Paths.get(file);
        try {
            List<String> lines = Files.readAllLines(path);
            return readEventTransactions(lines);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
    private static List<List<Event>> readEventTransactions(List<String> lines) {
        List<List<Event>> transactions = new ArrayList<>();
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
    }

    private static List<List<Event>> readSimpleEventTransactions(String file) {
        Path path = Paths.get(file);
        try {
            List<String> lines = Files.readAllLines(path);
            return readSimpleEventTransactions(lines);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
    private static List<List<Event>> readSimpleEventTransactions(List<String> lines) {
        List<List<Event>> transactions = new ArrayList<>();
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
    }

    /**
     *
     * @param filePath
     * @param numOfPartitions
     * @return HashMap of partitions or null if filePath is an invalid filepath
     */
    public static HashMap<Integer,List<String>> partitionTransactions(String filePath, int numOfPartitions){
        Path path = Paths.get(filePath);
        HashMap<Integer, List<String>> partitions = null;
        try {
            List<String> lines = Files.readAllLines(path);

            partitions = new HashMap<>();
            int numOfTransactions = lines.size();
            int partSize = numOfTransactions/numOfPartitions;
            int count = 0;
            int partition = 0;
            partitions.put(partition,new ArrayList<>());

            /*Adds a transaction to a partition until count is equal to partition size, then resets count goes on to the next partition, this is done as long as the partition
              is not the last partition which gets the remainder of the transactions.
             */
            for(String line: lines){
                partitions.get(partition).add(line);
                count++;
                if(partition < numOfPartitions-1 && count == partSize){
                    count = 0;
                    partition++;
                    partitions.put(partition,new ArrayList<>());
                }
            }

            //Temp, vill bara se hur det förhåller sig
            printPartitions(partitions, numOfPartitions, numOfTransactions, partSize);

        }catch(IOException ioe){
            ioe.printStackTrace();
        }
        return  partitions;
     }
     private static void printPartitions(HashMap<Integer, List<String>> partitions, int numOfPartitions,int numOfTransactions, int partSize){
         System.out.println("mod: " + numOfTransactions%numOfPartitions + " div: " + partSize);
         int TEMP_COUNTER = 0;
         System.out.println("Partitions\tSize");
         for(int i = 0 ; i < numOfPartitions; i++){
             System.out.printf("%d\t\t%d%n", i, partitions.get(i).size());
             TEMP_COUNTER += partitions.get(i).size();
         }
         System.out.println("Expected size: " + numOfTransactions + " Size: " + TEMP_COUNTER);
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
}
