# Personal Expense Tracking and Budget Management System

Mini project for Industrial Practice (Full Stack Development with Java).

## Features
- Add income and expense transactions with category, amount, date and note
- Delete transactions
- Monthly dashboard: total income, total expenses, balance
- Set monthly budget limits per category, with progress bars
- Alerts when you reach 80% or exceed a category budget
- Spending-by-category doughnut chart (HTML5 Canvas)
- Month selector to review any month
- Data saved automatically in text files (`data/` folder), no database setup

## Tech Stack
| Layer     | Technology |
|-----------|------------|
| Frontend  | HTML, CSS, JavaScript (fetch API, Canvas) |
| Backend   | Java (JDK 11+), `com.sun.net.httpserver` built-in server |
| Storage   | Plain text files (`transactions.txt`, `budgets.txt`) |

No external libraries, Maven or Gradle required.

## Project Structure
```
ExpenseTracker/
├── src/
│   ├── Main.java          # HTTP server + REST API handlers
│   ├── Transaction.java   # Model class
│   ├── DataStore.java     # Storage layer (in-memory + file persistence)
│   └── Json.java          # JSON helper
├── public/
│   ├── index.html         # UI
│   ├── style.css          # Styling
│   └── app.js             # Frontend logic
└── data/                  # Created automatically on first run
```

## How to Run
Open a terminal inside the `ExpenseTracker` folder:

```
javac -d out src/*.java
java -cp out Main
```

Then open **http://localhost:8080** in your browser.
(Use `java -cp out Main 9090` to run on a different port.)

## REST API
| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET    | `/api/transactions?month=2026-10` | List transactions (month optional) |
| POST   | `/api/transactions` | Add a transaction (type, category, amount, date, description) |
| DELETE | `/api/transactions?id=3` | Delete a transaction |
| GET    | `/api/budgets` | List budgets |
| POST   | `/api/budgets` | Set a budget (category, limit) |
| DELETE | `/api/budgets?category=Food` | Remove a budget |
| GET    | `/api/summary?month=2026-10` | Totals + per-category spent vs budget |

## Possible Future Enhancements
- User login and multiple accounts
- Replace text files with MySQL using JDBC
- Export reports to CSV/PDF
- Recurring transactions
