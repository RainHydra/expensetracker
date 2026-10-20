/**
 * Model class: one income or expense entry.
 */
public class Transaction {
    private final int id;
    private final String type;        // "INCOME" or "EXPENSE"
    private final String category;
    private final double amount;
    private final String description;
    private final String date;        // yyyy-MM-dd

    public Transaction(int id, String type, String category, double amount, String description, String date) {
        this.id = id;
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.description = description;
        this.date = date;
    }

    public int getId() { return id; }
    public String getType() { return type; }
    public String getCategory() { return category; }
    public double getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getDate() { return date; }

    public boolean isExpense() { return "EXPENSE".equals(type); }

    /** Serialise to one tab-separated line for the data file. */
    public String toLine() {
        return id + "\t" + type + "\t" + category + "\t" + amount + "\t" + description + "\t" + date;
    }

    /** Rebuild a Transaction from a line written by toLine(). Returns null if malformed. */
    public static Transaction fromLine(String line) {
        String[] p = line.split("\t", -1);
        if (p.length != 6) return null;
        try {
            return new Transaction(Integer.parseInt(p[0]), p[1], p[2], Double.parseDouble(p[3]), p[4], p[5]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String toJson() {
        return "{\"id\":" + id
                + ",\"type\":\"" + Json.escape(type) + "\""
                + ",\"category\":\"" + Json.escape(category) + "\""
                + ",\"amount\":" + amount
                + ",\"description\":\"" + Json.escape(description) + "\""
                + ",\"date\":\"" + Json.escape(date) + "\"}";
    }
}
