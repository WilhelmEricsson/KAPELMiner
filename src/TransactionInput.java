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
