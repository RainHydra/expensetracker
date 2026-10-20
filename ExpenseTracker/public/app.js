// Frontend logic: talks to the Java REST API using fetch().

const CATEGORIES = {
  EXPENSE: ["Food", "Transport", "Rent", "Bills", "Shopping", "Entertainment", "Health", "Education", "Other"],
  INCOME: ["Salary", "Freelance", "Gift", "Investment", "Other"]
};
const COLORS = ["#4f46e5", "#16a34a", "#f59e0b", "#ef4444", "#06b6d4", "#a855f7", "#ec4899", "#84cc16", "#64748b", "#0ea5e9"];

const $ = (id) => document.getElementById(id);
const money = (n) => "₹" + Number(n).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

let month = new Date().toISOString().slice(0, 7);

// ---------- API helpers ----------
async function api(url, options) {
  const res = await fetch(url, options);
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || "Request failed");
  return data;
}
const post = (url, obj) =>
  api(url, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(obj).toString()
  });

function toast(msg, isError) {
  const t = $("toast");
  t.textContent = msg;
  t.className = "toast show" + (isError ? " error" : "");
  setTimeout(() => (t.className = "toast"), 2400);
}

// ---------- Rendering ----------
async function refresh() {
  try {
    const [summary, txs] = await Promise.all([
      api("/api/summary?month=" + month),
      api("/api/transactions?month=" + month)
    ]);
    renderSummary(summary);
    renderBudgets(summary.categories);
    renderChart(summary.categories);
    renderAlerts(summary.categories);
    renderTransactions(txs);
  } catch (e) {
    toast(e.message, true);
  }
}

function renderSummary(s) {
  $("sumIncome").textContent = money(s.income);
  $("sumExpense").textContent = money(s.expense);
  const b = $("sumBalance");
  b.textContent = money(s.balance);
  b.classList.toggle("negative", s.balance < 0);
}

function renderTransactions(list) {
  $("txBody").innerHTML = list.map((t) => `
    <tr>
      <td>${esc(t.date)}</td>
      <td>${esc(t.category)}</td>
      <td>${esc(t.description) || "—"}</td>
      <td class="num ${t.type === "INCOME" ? "amt-income" : "amt-expense"}">
        ${t.type === "INCOME" ? "+" : "−"}${money(t.amount)}
      </td>
      <td><button class="del" title="Delete" onclick="deleteTx(${t.id})">✕</button></td>
    </tr>`).join("");
  $("txEmpty").classList.toggle("hidden", list.length > 0);
}

function renderBudgets(cats) {
  const budgeted = cats.filter((c) => c.budget !== null);
  if (!budgeted.length) {
    $("budgetList").innerHTML = '<p class="empty">No budgets set. Choose a category above and set a limit.</p>';
    return;
  }
  $("budgetList").innerHTML = budgeted.map((c) => {
    const pct = (c.spent / c.budget) * 100;
    const cls = pct > 100 ? "over" : pct >= 80 ? "warn" : "";
    const left = c.budget - c.spent;
    return `
      <div class="budget-item">
        <div class="budget-head">
          <strong>${esc(c.category)}</strong>
          <span>
            <small>${money(c.spent)} of ${money(c.budget)}</small>
            <button class="link-btn" onclick="deleteBudget('${encodeURIComponent(c.category)}')">remove</button>
          </span>
        </div>
        <div class="bar"><span class="${cls}" style="width:${Math.min(pct, 100)}%"></span></div>
        <small style="color:var(--muted)">${left >= 0 ? money(left) + " left" : money(-left) + " over budget"} · ${pct.toFixed(0)}% used</small>
      </div>`;
  }).join("");
}

function renderAlerts(cats) {
  const html = [];
  cats.filter((c) => c.budget !== null).forEach((c) => {
    const pct = (c.spent / c.budget) * 100;
    if (pct > 100) html.push(`<div class="alert over">⚠ You have exceeded your <b>${esc(c.category)}</b> budget by ${money(c.spent - c.budget)}.</div>`);
    else if (pct >= 80) html.push(`<div class="alert warn">You have used ${pct.toFixed(0)}% of your <b>${esc(c.category)}</b> budget.</div>`);
  });
  $("alerts").innerHTML = html.join("");
}

function renderChart(cats) {
  const data = cats.filter((c) => c.spent > 0);
  const canvas = $("chart");
  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  $("chartEmpty").classList.toggle("hidden", data.length > 0);
  canvas.classList.toggle("hidden", data.length === 0);
  $("legend").innerHTML = "";
  if (!data.length) return;

  const total = data.reduce((s, c) => s + c.spent, 0);
  const cx = canvas.width / 2, cy = canvas.height / 2, r = 115, inner = 68;
  let start = -Math.PI / 2;
  data.forEach((c, i) => {
    const angle = (c.spent / total) * Math.PI * 2;
    ctx.beginPath();
    ctx.arc(cx, cy, r, start, start + angle);
    ctx.arc(cx, cy, inner, start + angle, start, true);
    ctx.closePath();
    ctx.fillStyle = COLORS[i % COLORS.length];
    ctx.fill();
    start += angle;
  });
  ctx.fillStyle = "#1c2333";
  ctx.font = "600 15px Segoe UI, sans-serif";
  ctx.textAlign = "center";
  ctx.fillText(money(total), cx, cy + 5);

  $("legend").innerHTML = data.map((c, i) =>
    `<li><i style="background:${COLORS[i % COLORS.length]}"></i>${esc(c.category)}<em>${((c.spent / total) * 100).toFixed(0)}%</em></li>`
  ).join("");
}

// ---------- Actions ----------
async function deleteTx(id) {
  if (!confirm("Delete this transaction?")) return;
  try {
    await api("/api/transactions?id=" + id, { method: "DELETE" });
    toast("Transaction deleted");
    refresh();
  } catch (e) { toast(e.message, true); }
}

async function deleteBudget(cat) {
  try {
    await api("/api/budgets?category=" + cat, { method: "DELETE" });
    toast("Budget removed");
    refresh();
  } catch (e) { toast(e.message, true); }
}

function fillCategories() {
  const type = document.querySelector('input[name="type"]:checked').value;
  $("txCategory").innerHTML = CATEGORIES[type].map((c) => `<option>${c}</option>`).join("");
}

// ---------- Init ----------
document.querySelectorAll('input[name="type"]').forEach((r) => r.addEventListener("change", fillCategories));

$("txForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const f = new FormData(e.target);
  try {
    const entryMonth = String(f.get("date")).slice(0, 7);
    await post("/api/transactions", Object.fromEntries(f));
    toast("Transaction added");
    if (entryMonth.length === 7) { month = entryMonth; $("monthPicker").value = month; }
    e.target.reset();
    $("txDate").value = new Date().toISOString().slice(0, 10);
    fillCategories();
    // jump to the month of the entry if different
    refresh();
  } catch (err) { toast(err.message, true); }
});

$("budgetForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    await post("/api/budgets", Object.fromEntries(new FormData(e.target)));
    toast("Budget saved");
    e.target.reset();
    refresh();
  } catch (err) { toast(err.message, true); }
});

$("monthPicker").addEventListener("change", (e) => {
  if (e.target.value) { month = e.target.value; refresh(); }
});

$("monthPicker").value = month;
$("txDate").value = new Date().toISOString().slice(0, 10);
$("budgetCategory").innerHTML = CATEGORIES.EXPENSE.map((c) => `<option>${c}</option>`).join("");
fillCategories();
refresh();
