public class Position {
    private int first, last;

    public Position(int first, int last) {
        this.first = first;
        this.last = last;
    }

    public int getFirst() {
        return first;
    }

    public int getLast() {
        return last;
    }

    public void expand(Position other) {
        this.first = Math.min(this.first, other.first);
        this.last = Math.max(this.last, other.last);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Position that = (Position) o;
        if (first != that.first)
            return false;
        return last == that.last;
    }

    @Override
    public int hashCode() {
        int result = first;
        result = 31 * result + last;
        return result;
    }

    @Override
    public String toString() {
        return "{" + "first=" + first + ", last=" + last + '}';
    }
}
