import { spawn } from "node:child_process";
import { setTimeout as sleep } from "node:timers/promises";

const port = 18080;
const base = `http://localhost:${port}/api/v1`;
const server = spawn(process.execPath, ["tools/mock-api-server.mjs"], {
  env: { ...process.env, PORT: String(port) },
  stdio: ["ignore", "pipe", "pipe"]
});

async function get(path) {
  const response = await fetch(`${base}${path}`);
  if (!response.ok) throw new Error(`${path} -> HTTP ${response.status}`);
  return response.json();
}

async function post(path, body) {
  const response = await fetch(`${base}${path}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!response.ok) throw new Error(`${path} -> HTTP ${response.status}`);
  return response.json();
}

try {
  await sleep(500);
  const checks = [
    ["/health", data => data.status === "UP"],
    ["/stocks/US/AAPL/summary", data => data.data.symbol === "AAPL"],
    ["/stocks/TW/2330/summary", data => data.data.symbol === "2330.TW"],
    ["/stocks/US/NVDA/prices?range=1M", data => Array.isArray(data.data.prices)],
    ["/stocks/TW/2454.TW/prediction?horizonDays=5", data => data.data.riskLevel]
  ];

  for (const [path, ok] of checks) {
    const data = await get(path);
    if (!ok(data)) throw new Error(`${path} returned unexpected shape`);
    console.log(`ok ${path}`);
  }

  const analysis = await post("/ai/analysis", { market: "TW", symbol: "2330.TW", provider: "OPENAI", horizonDays: 5 });
  if (analysis.data.provider !== "OPENAI" || !analysis.data.conclusion) throw new Error("/ai/analysis returned unexpected shape");
  console.log("ok /ai/analysis");

  const comparison = await post("/ai/model-comparison", { market: "US", symbol: "NVDA", providers: ["OPENAI", "CLAUDE", "GEMINI", "DEEPSEEK"], horizonDays: 5 });
  if (!comparison.data.consensusScore || comparison.data.results.length !== 4) throw new Error("/ai/model-comparison returned unexpected shape");
  console.log("ok /ai/model-comparison");

  const chat = await post("/ai/chat", { market: "US", symbol: "AAPL", provider: "CLAUDE", message: "未來一週主要風險是什麼？" });
  if (!chat.data.message || chat.data.provider !== "CLAUDE") throw new Error("/ai/chat returned unexpected shape");
  console.log("ok /ai/chat");

  const backtest = await post("/backtests", { market: "TW", symbol: "2330.TW", strategy: { minAiScore: 70, minUpProbability: 0.6 } });
  if (!Number.isFinite(backtest.data.totalReturn) || !backtest.data.tradeCount) throw new Error("/backtests returned unexpected shape");
  console.log("ok /backtests");

  const added = await post("/watchlist", { market: "US", symbol: "TSLA" });
  if (added.data.symbol !== "TSLA") throw new Error("POST /watchlist returned unexpected shape");
  console.log("ok POST /watchlist");

  const watchlist = await get("/watchlist");
  if (!watchlist.data.some(item => item.symbol === "TSLA")) throw new Error("GET /watchlist missing TSLA");
  console.log("ok GET /watchlist");

  const deleted = await fetch(`${base}/watchlist/US/TSLA`, { method: "DELETE" }).then(response => response.json());
  if (!deleted.data.deleted) throw new Error("DELETE /watchlist returned unexpected shape");
  console.log("ok DELETE /watchlist");
} finally {
  server.kill();
}
