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

async function put(path, body) {
  const response = await fetch(`${base}${path}`, {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!response.ok) throw new Error(`${path} -> HTTP ${response.status}`);
  return response.json();
}

async function del(path) {
  const response = await fetch(`${base}${path}`, { method: "DELETE" });
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
    ["/stocks/US/AAPL/data-lineage", data => data.data.integrityStatus === "MOCK" && data.data.adjustmentStatus === "MOCK" && Array.isArray(data.data.corporateActions)],
    ["/stocks/TW/2330/technical-summary", data => data.data.dataQuality?.sampleCount === 9 && data.data.dataQuality?.status === "MOCK" && data.data.dataQuality?.unavailableIndicators.includes("MA60")],
    ["/stocks/TW/2454.TW/prediction?horizonDays=5", data => data.data.riskLevel && data.data.dataQuality?.requiredSampleCount === 2]
  ];

  for (const [path, ok] of checks) {
    const data = await get(path);
    if (!ok(data)) throw new Error(`${path} returned unexpected shape`);
    console.log(`ok ${path}`);
  }

  const analysis = await post("/ai/analysis", { market: "TW", symbol: "2330.TW", provider: "OPENAI", horizonDays: 5 });
  if (analysis.data.provider !== "OPENAI" || !analysis.data.conclusion) throw new Error("/ai/analysis returned unexpected shape");
  console.log("ok /ai/analysis");

  const comparison = await post("/ai/model-comparison", { market: "US", symbol: "NVDA", providers: ["OPENAI", "GEMINI", "DEEPSEEK", "MIMO"], horizonDays: 5 });
  if (!comparison.data.consensusScore || comparison.data.results.length !== 4) throw new Error("/ai/model-comparison returned unexpected shape");
  console.log("ok /ai/model-comparison");

  const chat = await post("/ai/chat", {
    market: "US",
    symbol: "AAPL",
    provider: "OPENAI",
    message: "未來一週主要風險是什麼？",
    history: [{ role: "user", content: "先看一下目前趨勢" }, { role: "assistant", content: "目前偏中性偏多。" }]
  });
  if (!chat.data.message || chat.data.provider !== "OPENAI" || chat.data.answerStatus !== "MOCK" || !Array.isArray(chat.data.tools)) throw new Error("/ai/chat returned unexpected shape");
  console.log("ok /ai/chat");

  const backtest = await post("/backtests", { market: "TW", symbol: "2330.TW", strategy: { minAiScore: 70, minUpProbability: 0.6 } });
  if (!Number.isFinite(backtest.data.totalReturn) || !Number.isInteger(backtest.data.tradeCount) || backtest.data.status !== "MOCK_FALLBACK" || backtest.data.conditions?.maxRiskLevel !== "MEDIUM" || !Array.isArray(backtest.data.trades)) throw new Error("/backtests returned unexpected shape");
  console.log("ok /backtests");

  const added = await post("/watchlist", { market: "US", symbol: "TSLA" });
  if (added.data.symbol !== "TSLA") throw new Error("POST /watchlist returned unexpected shape");
  console.log("ok POST /watchlist");

  const watchlist = await get("/watchlist");
  if (!watchlist.data.some(item => item.symbol === "TSLA")) throw new Error("GET /watchlist missing TSLA");
  console.log("ok GET /watchlist");

  const scheduler = await get("/watchlist/alerts/scheduler");
  if (scheduler.data.notificationMode !== "LOCAL_ONLY" || !Number.isFinite(scheduler.data.fixedDelayMs)) throw new Error("GET /watchlist/alerts/scheduler returned unexpected shape");
  console.log("ok GET /watchlist/alerts/scheduler");
  const preferences = await get("/watchlist/alerts/notification-preferences");
  if (preferences.data.preferences?.localEnabled !== true || preferences.data.externalChannelsAvailable !== false) throw new Error("GET /watchlist/alerts/notification-preferences returned unexpected shape");
  const updatedPreferences = await put("/watchlist/alerts/notification-preferences", { localEnabled: false, emailEnabled: true });
  if (updatedPreferences.data.preferences?.localEnabled !== false || updatedPreferences.data.externalChannelsAvailable !== false) throw new Error("PUT /watchlist/alerts/notification-preferences did not stay fail-closed");
  await put("/watchlist/alerts/notification-preferences", { localEnabled: true });
  console.log("ok watchlist alert notification preferences");

  const alert = await post("/watchlist/alerts", { market: "US", symbol: "AAPL", condition: "PRICE_ABOVE", threshold: 0, enabled: true });
  if (!alert.data.id || alert.data.condition !== "PRICE_ABOVE") throw new Error("POST /watchlist/alerts returned unexpected shape");
  console.log("ok POST /watchlist/alerts");

  const alertCenter = await get("/watchlist/alerts");
  if (alertCenter.data.evaluations?.[0]?.status !== "TRIGGERED"
      || alertCenter.data.evaluations?.[0]?.newlyTriggered !== true
      || alertCenter.data.newTriggerCount !== 1
      || alertCenter.data.recentEvents?.length !== 1) throw new Error("GET /watchlist/alerts did not trigger or record history");
  console.log("ok GET /watchlist/alerts");
  const notifications = await get("/watchlist/alerts/notifications?limit=10");
  if (notifications.data.notifications?.length !== 1 || notifications.data.unreadCount !== 1 || notifications.data.notifications[0].channel !== "LOCAL_ONLY") throw new Error("GET /watchlist/alerts/notifications returned unexpected shape");
  console.log("ok GET /watchlist/alerts/notifications");
  const readNotification = await put(`/watchlist/alerts/notifications/${notifications.data.notifications[0].id}`, { read: true });
  if (readNotification.data.state !== "READ") throw new Error("PUT /watchlist/alerts/notifications did not mark read");
  console.log("ok PUT /watchlist/alerts/notifications");
  const cleanupNotifications = await del("/watchlist/alerts/notifications/read");
  if (cleanupNotifications.data.deleted !== 1 || cleanupNotifications.data.localOnly !== true) throw new Error("DELETE /watchlist/alerts/notifications/read returned unexpected shape");
  console.log("ok DELETE /watchlist/alerts/notifications/read");
  const refreshedAlerts = await get("/watchlist/alerts");
  if (refreshedAlerts.data.evaluations?.[0]?.newlyTriggered !== false
      || refreshedAlerts.data.newTriggerCount !== 0
      || refreshedAlerts.data.recentEvents?.length !== 1) throw new Error("GET /watchlist/alerts did not deduplicate repeated trigger");
  console.log("ok GET /watchlist/alerts deduplicates repeated trigger");

  const disabledAlert = await put(`/watchlist/alerts/${alert.data.id}`, { enabled: false });
  if (disabledAlert.data.enabled !== false) throw new Error("PUT /watchlist/alerts did not disable");
  console.log("ok PUT /watchlist/alerts");

  const deletedAlert = await del(`/watchlist/alerts/${alert.data.id}`);
  if (!deletedAlert.data.deleted) throw new Error("DELETE /watchlist/alerts returned unexpected shape");
  console.log("ok DELETE /watchlist/alerts");

  const deleted = await fetch(`${base}/watchlist/US/TSLA`, { method: "DELETE" }).then(response => response.json());
  if (!deleted.data.deleted) throw new Error("DELETE /watchlist returned unexpected shape");
  console.log("ok DELETE /watchlist");
} finally {
  server.kill();
}
