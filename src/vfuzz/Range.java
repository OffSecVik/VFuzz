package vfuzz;

public class Range {
    private final int start;
    private final int end;

    public Range(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public boolean contains(int value) {
        return value >= start && value <= end;
    }

    @Override
    public String toString() {
        if (start == end) {
            return ""+start;
        }
        return start + "-" + end;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public static Range parseToRange(String s) {
        if (s.contains("-")) {
            String[] parts = s.split("-");
            return new Range(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } else {
            int singleValue = Integer.parseInt(s.trim());
            return new Range(singleValue, singleValue);
        }
    }

}
