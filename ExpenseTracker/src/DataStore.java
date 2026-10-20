import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Storage layer. Keeps everything in memory and persists to plain text files
 * (data/transactions.txt and data/budgets.txt) so no database setup is needed.
 */
public class DataStore {
    private final Path txFile;
    private final Path budgetFile;
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<String, Double> budgets = new TreeMap<>();
    private int nextId = 1;

    public DataStore(String dir) {
        try {
            Path d = Paths.get(dir);
            Files.createDirectories(d);
            txFile = d.resolve("transactions.txt");
            budgetFile = d.resolve("budgets.txt");
            load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void load() throws IOException {
        if (Files.exists(txFile)) {
            for (String line : Files.readAllLines(txFile, StandardCharsets.UTF_8)) {
                Transaction t = Transaction.fromLine(line);
                if (t != null) {
                    transactions.add(t);
                    nextId = Math.max(nextId, t.getId() + 1);
                }
            }
        }
        if (Files.exists(budgetFile)) {
            for (String line : Files.readAllLines(budgetFile, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length == 2) {
                    try { budgets.put(p[0], Double.parseDouble(p[1])); } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    private void save() {
        try {
            List<String> tx = transactions.stream().map(Transaction::toLine).collect(Collectors.toList());
            Files.write(txFile, tx, StandardCharsets.UTF_8);
            List<String> b = new ArrayList<>();
            budgets.forEach((k, v) -> b.add(k + "\t" + v));
            Files.write(budgetFile, b, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @param month "yyyy-MM" to filter, or null/empty for all */
    public synchronized List<Transaction> getTransactions(String month) {
        return transactions.stream()
                .filter(t -> month == null || month.isEmpty() || t.getDate().startsWith(month))
                .sorted(Comparator.comparing(Transaction::getDate).thenComparing(Transaction::getId).reversed())
                .collect(Collectors.toList());
    }

    public synchronized Transaction addTransaction(String type, String category, double amount, String description, String date) {
        Transaction t = new Transaction(nextId++, type, category, amount, description, date);
        transactions.add(t);
        save();
        return t;
    }

    public synchronized boolean deleteTransaction(int id) {
        boolean removed = transactions.removeIf(t -> t.getId() == id);
        if (removed) save();
        return removed;
    }

    public synchronized Map<String, Double> getBudgets() {
        return new TreeMap<>(budgets);
    }

    public synchronized void setBudget(String category, double limit) {
        budgets.put(category, limit);
        save();
    }

    public synchronized boolean deleteBudget(String category) {
        boolean removed = budgets.remove(category) != null;
        if (removed) save();
        return removed;
    }
}
