import java.util.HashMap;
import java.util.Map;

public class ItemPosition {
    private final Map<Integer, Position> itemPosition;

    public ItemPosition() {
        this.itemPosition = new HashMap<>();
    }

    public void putAndExpand(int item, Position position) {
        Position pos = itemPosition.get(item);
        if (pos == null) {
            itemPosition.put(item, position);
        } else {
            pos.expand(position);
        }
    }

    public Position get(int item) {
        return itemPosition.get(item);
    }

    @Override
    public String toString() {
        return "ItemPosition{" + "itemPosition=" + itemPosition + '}';
    }
}
