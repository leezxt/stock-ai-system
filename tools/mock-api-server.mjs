import http from "node:http";

const port = Number(process.env.PORT || 8080);
const now = () => new Date().toISOString();

const stocks = {
  US: {
    AAPL: ["Apple Inc.", "USD", 217.63, 2.14, [204, 207, 206, 211, 210, 214, 216, 215, 218]],
    NVDA: ["NVIDIA Corp.", "USD", 164.29, 3.72, [144, 148, 151, 150, 156, 158, 162, 160, 164]],
    TSLA: ["Tesla Inc.", "USD", 318.76, -1.86, [334, 329, 326, 322, 319, 321, 317, 320, 319]]
  },
  TW: {
    "2330.TW": ["台積電", "TWD", 1035, 1.47, [965, 972, 988, 996, 1008, 1015, 1022, 1018, 1035]],
    "2454.TW": ["聯發科", "TWD", 1320, -0.75, [1365, 1350, 1342, 1330, 1328, 1316, 1322, 1310, 1320]],
    "2317.TW": ["鴻海", "TWD", 184.5, 0.82, [176, 178, 181, 180, 182, 183, 185, 184, 184.5]]
  }
};
const watchlist = new Map([
  ["US:AAPL", { market: "US", symbol: "AAPL" }],
  ["TW:2330.TW", { market: "TW", symbol: "2330.TW" }]
]);
const alertRules = new Map();
const alertEvents = new Map();
const alertNotifications = new Map();
let mockAlertSequence = 0;
const alertNotificationPreferences = {
  localEnabled: true,
  emailEnabled: false,
  pushEnabled: false,
  updatedAt: now()
};
const schedulerEnabled = String(process.env.STOCKAI_WATCHLIST_ALERTS_SCHEDULER_ENABLED || "false").toLowerCase() === "true";
const schedulerFixedDelayMs = Number(process.env.STOCKAI_WATCHLIST_ALERTS_SCHEDULER_FIXED_DELAY_MS || 900000);

function normalize(market, symbol) {
  const raw = decodeURIComponent(symbol || "").toUpperCase();
  return market === "TW" && /^[0-9]{4}(\.TW)?$/.test(raw) ? raw.replace(".TW", "") + ".TW" : raw;
}

function getStock(market, symbol) {
  const normalized = normalize(market, symbol);
  const data = stocks[market]?.[normalized];
  if (!data) return null;
  const [name, currency, lastPrice, changePercent, prices] = data;
  return { symbol: normalized, name, market, currency, lastPrice, changePercent, prices };
}

function send(res, status, body) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, POST, PUT, DELETE, OPTIONS",
    "access-control-allow-headers": "content-type, accept"
  });
  res.end(JSON.stringify(body));
}

function notFound(res) {
  send(res, 404, { error: "not_found" });
}

function readJson(req) {
  return new Promise(resolve => {
    let body = "";
    req.on("data", chunk => body += chunk);
    req.on("end", () => {
      try { resolve(body ? JSON.parse(body) : {}); } catch { resolve({}); }
    });
  });
}

function providerResult(stock, provider, i = 0) {
  const score = Math.max(35, Math.min(90, 66 + stock.symbol.length % 8 + i * 2 - (provider === "DEEPSEEK" ? 5 : 0)));
  return {
    provider,
    trend: score > 72 ? "偏多" : score > 62 ? "中性偏多" : "震盪",
    aiScore: score,
    bullishProbability: Number((score / 100 - 0.06 + i * 0.01).toFixed(2)),
    riskLevel: score < 62 || provider === "DEEPSEEK" && stock.changePercent < 0 ? "HIGH" : "MEDIUM",
    summary: "趨勢仍有支撐，需觀察量能延續。"
  };
}

function mockTechnicalQuality(sampleCount) {
  const thresholds = { MA5: 5, MA20: 20, MA60: 60, RSI14: 15, MACD: 26, ATR14: 15 };
  return {
    sampleCount,
    requiredSampleCount: 60,
    dataFrom: null,
    dataTo: null,
    source: "mock-api",
    status: "MOCK",
    unavailableIndicators: Object.entries(thresholds)
      .filter(([, required]) => sampleCount < required)
      .map(([name]) => name)
  };
}

function mockPredictionQuality(sampleCount) {
  return {
    sampleCount,
    requiredSampleCount: 2,
    dataFrom: null,
    dataTo: null,
    source: "mock-api",
    status: "MOCK",
    unavailableIndicators: sampleCount < 2
      ? ["MOMENTUM", "VOLATILITY", "UP_PROBABILITY", "EXPECTED_RETURN", "RISK_LEVEL"]
      : []
  };
}

http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") return send(res, 204, {});
  const url = new URL(req.url, `http://localhost:${port}`);
  const parts = url.pathname.split("/").filter(Boolean);
  if (url.pathname === "/api/v1/health") return send(res, 200, { status: "UP", timestamp: now() });
  if (url.pathname === "/api/v1/markets") return send(res, 200, { data: ["US", "TW"] });
  if (url.pathname === "/api/v1/watchlist" && req.method === "GET") {
    return send(res, 200, { data: [...watchlist.values()].map(item => getStock(item.market, item.symbol)).filter(Boolean) });
  }
  if (url.pathname === "/api/v1/watchlist" && req.method === "POST") {
    const body = await readJson(req);
    const stock = getStock(body.market, body.symbol);
    if (!stock) return notFound(res);
    watchlist.set(`${stock.market}:${stock.symbol}`, { market: stock.market, symbol: stock.symbol });
    return send(res, 200, { data: stock });
  }
  if (url.pathname === "/api/v1/watchlist/alerts/scheduler" && req.method === "GET") {
    return send(res, 200, {
      data: {
        enabled: schedulerEnabled,
        fixedDelayMs: schedulerFixedDelayMs,
        notificationMode: "LOCAL_ONLY",
        lastStartedAt: null,
        lastCompletedAt: null,
        lastEvaluatedUsers: 0,
        lastEvaluatedRules: 0,
        lastNewTriggerCount: 0,
        lastErrorCount: 0,
        lastError: ""
      }
    });
  }
  if (url.pathname === "/api/v1/watchlist/alerts/notification-preferences" && req.method === "GET") {
    return send(res, 200, {
      data: {
        preferences: { ...alertNotificationPreferences },
        externalChannelsAvailable: false,
        externalBlockReason: "目前只支援 LOCAL_ONLY；Email／推播尚未設定且不會外送。",
        source: "watchlist-alert-notification-preferences-mock-v1"
      }
    });
  }
  if (url.pathname === "/api/v1/watchlist/alerts/notification-preferences" && req.method === "PUT") {
    const body = await readJson(req);
    if (body.localEnabled != null) alertNotificationPreferences.localEnabled = body.localEnabled === true;
    if (body.emailEnabled != null) alertNotificationPreferences.emailEnabled = body.emailEnabled === true;
    if (body.pushEnabled != null) alertNotificationPreferences.pushEnabled = body.pushEnabled === true;
    alertNotificationPreferences.updatedAt = now();
    return send(res, 200, {
      data: {
        preferences: { ...alertNotificationPreferences },
        externalChannelsAvailable: false,
        externalBlockReason: "目前只支援 LOCAL_ONLY；Email／推播尚未設定且不會外送。",
        source: "watchlist-alert-notification-preferences-mock-v1"
      }
    });
  }
  if (url.pathname === "/api/v1/watchlist/alerts/notifications" && req.method === "GET") {
    const limit = Math.max(1, Math.min(Number(url.searchParams.get("limit") || 20), 100));
    const notifications = [...alertNotifications.values()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, limit);
    return send(res, 200, {
      data: {
        notifications,
        unreadCount: [...alertNotifications.values()].filter(item => item.state === "UNREAD").length,
        source: "watchlist-alert-notifications-mock-v1"
      }
    });
  }
  if (parts[0] === "api" && parts[1] === "v1" && parts[2] === "watchlist" && parts[3] === "alerts" && parts[4] === "notifications" && req.method === "PUT") {
    const notification = alertNotifications.get(parts[5]);
    if (!notification) return notFound(res);
    const body = await readJson(req);
    notification.state = body.read === true ? "READ" : "UNREAD";
    notification.readAt = notification.state === "READ" ? now() : null;
    return send(res, 200, { data: notification });
  }
  if (url.pathname === "/api/v1/watchlist/alerts/notifications/read" && req.method === "DELETE") {
    let deleted = 0;
    for (const [notificationId, notification] of alertNotifications) {
      if (notification.state === "READ") {
        alertNotifications.delete(notificationId);
        deleted++;
      }
    }
    return send(res, 200, { data: { deleted, localOnly: true } });
  }
  if (url.pathname === "/api/v1/watchlist/alerts" && req.method === "GET") {
    let newTriggerCount = 0;
    const evaluations = [...alertRules.values()].filter(rule => watchlist.has(`${rule.market}:${rule.symbol}`)).map(rule => {
      const stock = getStock(rule.market, rule.symbol);
      const currentValue = rule.condition === "CHANGE_PCT_ABOVE" || rule.condition === "CHANGE_PCT_BELOW" ? stock.changePercent : stock.lastPrice;
      const triggered = rule.condition === "PRICE_ABOVE" ? currentValue >= rule.threshold
        : rule.condition === "PRICE_BELOW" ? currentValue <= rule.threshold
          : rule.condition === "CHANGE_PCT_ABOVE" ? currentValue >= rule.threshold
            : rule.condition === "CHANGE_PCT_BELOW" ? currentValue <= rule.threshold
            : rule.condition === "DATA_QUALITY_NOT_OK";
      const status = !rule.enabled ? "DISABLED" : triggered ? "TRIGGERED" : "NORMAL";
      const events = alertEvents.get(rule.id) || [];
      const previous = events.at(-1);
      const newlyTriggered = status === "TRIGGERED" && previous?.status !== "TRIGGERED";
      if (!previous || previous.status !== status) {
        const event = {
          id: `mock-alert-event-${Date.now()}-${mockAlertSequence++}`,
          ruleId: rule.id,
          market: rule.market,
          symbol: rule.symbol,
          condition: rule.condition,
          status,
          currentValue: rule.condition === "DATA_QUALITY_NOT_OK" ? null : currentValue,
          message: !rule.enabled ? "規則已停用" : triggered ? "Mock 資料已達警示條件" : "Mock 資料尚未達到門檻",
          source: "mock-api",
          dataStatus: "MOCK",
          observedAt: now(),
          createdAt: now()
        };
        events.push(event);
        alertEvents.set(rule.id, events);
        if (status === "TRIGGERED" && alertNotificationPreferences.localEnabled) {
          const notification = {
            id: `mock-alert-notification-${Date.now()}-${mockAlertSequence++}`,
            eventId: event.id,
            ruleId: rule.id,
            market: rule.market,
            symbol: rule.symbol,
            condition: rule.condition,
            title: `${rule.symbol} 警示觸發`,
            message: event.message,
            channel: "LOCAL_ONLY",
            state: "UNREAD",
            source: "mock-api",
            dataStatus: "MOCK",
            createdAt: event.createdAt,
            readAt: null
          };
          alertNotifications.set(notification.id, notification);
        }
      }
      if (newlyTriggered) newTriggerCount++;
      const lastTriggered = [...events].reverse().find(event => event.status === "TRIGGERED");
      return {
        ruleId: rule.id,
        market: rule.market,
        symbol: rule.symbol,
        name: stock.name,
        condition: rule.condition,
        threshold: rule.threshold,
        currentValue: rule.condition === "DATA_QUALITY_NOT_OK" ? null : currentValue,
        currentLabel: rule.condition === "DATA_QUALITY_NOT_OK" ? "MOCK / MOCK" : "",
        status,
        triggered: rule.enabled && triggered,
        newlyTriggered,
        lastTriggeredAt: lastTriggered?.createdAt || null,
        message: !rule.enabled ? "規則已停用" : triggered ? "Mock 資料已達警示條件" : "Mock 資料尚未達到門檻",
        source: "mock-api",
        dataStatus: "MOCK",
        observedAt: now()
      };
    });
    return send(res, 200, {
      data: {
        rules: [...alertRules.values()].filter(rule => watchlist.has(`${rule.market}:${rule.symbol}`)),
        evaluations,
        triggeredCount: evaluations.filter(item => item.triggered).length,
        newTriggerCount,
        recentEvents: [...alertEvents.values()].flat().sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 20),
        evaluatedAt: now(),
        source: "watchlist-alerts-mock-v2"
      }
    });
  }
  if (url.pathname === "/api/v1/watchlist/alerts" && req.method === "POST") {
    const body = await readJson(req);
    const symbol = normalize(body.market, body.symbol);
    if (!watchlist.has(`${body.market}:${symbol}`)) return send(res, 400, { error: "alert symbol must already be in the watchlist" });
    const id = `mock-alert-${Date.now()}-${mockAlertSequence++}`;
    const rule = {
      id,
      market: body.market,
      symbol,
      condition: body.condition,
      threshold: body.threshold == null ? null : Number(body.threshold),
      enabled: body.enabled !== false,
      createdAt: now(),
      updatedAt: now()
    };
    alertRules.set(id, rule);
    alertEvents.set(id, []);
    return send(res, 200, { data: rule });
  }
  if (parts[0] === "api" && parts[1] === "v1" && parts[2] === "watchlist" && parts[3] === "alerts" && req.method === "PUT") {
    const rule = alertRules.get(parts[4]);
    if (!rule) return notFound(res);
    const body = await readJson(req);
    rule.enabled = body.enabled !== false;
    rule.updatedAt = now();
    return send(res, 200, { data: rule });
  }
  if (parts[0] === "api" && parts[1] === "v1" && parts[2] === "watchlist" && parts[3] === "alerts" && req.method === "DELETE") {
    const id = parts[4];
    alertRules.delete(id);
    alertEvents.delete(id);
    for (const [notificationId, notification] of alertNotifications) {
      if (notification.ruleId === id) alertNotifications.delete(notificationId);
    }
    return send(res, 200, { data: { id, deleted: true } });
  }
  if (parts[0] === "api" && parts[1] === "v1" && parts[2] === "watchlist" && req.method === "DELETE") {
    const market = parts[3];
    const symbol = normalize(market, parts[4]);
    watchlist.delete(`${market}:${symbol}`);
    return send(res, 200, { data: { market, symbol, deleted: true } });
  }
  if (url.pathname === "/api/v1/ai/analysis" && req.method === "POST") {
    const body = await readJson(req);
    const stock = getStock(body.market, body.symbol);
    if (!stock) return notFound(res);
    const result = providerResult(stock, body.provider || "OPENAI");
    return send(res, 200, {
      data: {
        symbol: stock.symbol,
        market: stock.market,
        provider: result.provider,
        trend: result.trend,
        aiScore: result.aiScore,
        bullishProbability: result.bullishProbability,
        riskLevel: result.riskLevel,
        bullishReasons: ["短期均線維持支撐", "模型預測偏正向"],
        bearishRisks: ["追價風險上升", "大盤回檔會放大波動"],
        watchPoints: ["MA20 支撐", "成交量延續"],
        conclusion: `${result.summary} 模型輸出僅供研究，不代表投資建議。`,
        generatedAt: now()
      }
    });
  }
  if (url.pathname === "/api/v1/ai/model-comparison" && req.method === "POST") {
    const body = await readJson(req);
    const stock = getStock(body.market, body.symbol);
    if (!stock) return notFound(res);
    const providers = body.providers?.length ? body.providers : ["OPENAI", "GEMINI", "DEEPSEEK", "MIMO"];
    const results = providers.map((provider, i) => providerResult(stock, provider, i));
    const consensusScore = results.reduce((sum, item) => sum + item.aiScore, 0) / results.length;
    const averageBullishProbability = results.reduce((sum, item) => sum + item.bullishProbability, 0) / results.length;
    return send(res, 200, {
      data: {
        symbol: stock.symbol,
        market: stock.market,
        horizonDays: body.horizonDays || 5,
        consensusScore,
        consensusTrend: consensusScore > 72 ? "偏多" : "中性偏多",
        averageBullishProbability,
        divergenceLevel: "LOW",
        divergenceReason: "Mock providers 方向接近，風險評估略有差異。",
        results,
        generatedAt: now()
      }
    });
  }
  if (url.pathname === "/api/v1/ai/chat" && req.method === "POST") {
    const body = await readJson(req);
    const stock = getStock(body.market, body.symbol);
    if (!stock) return notFound(res);
    return send(res, 200, {
      data: {
        market: stock.market,
        symbol: stock.symbol,
        provider: body.provider || "OPENAI",
        source: "mock-ai",
        message: `根據 ${stock.symbol} 目前 mock data：${body.message || "請評估風險"} 重點是價格是否守住 MA20、波動是否擴大，以及模型分歧是否升高。此回覆僅供研究，不代表投資建議。`,
        evidence: [],
        citationIds: [],
        intent: "GENERAL_RESEARCH",
        answerStatus: "MOCK",
        tools: [{ name: "market_quote", status: "READY", summary: `最新價=${stock.price}，來源=mock`, source: "mock", observedAt: now(), citationIds: [] }],
        generatedAt: now()
      }
    });
  }
  if (url.pathname === "/api/v1/backtests" && req.method === "POST") {
    const body = await readJson(req);
    const stock = getStock(body.market, body.symbol);
    if (!stock) return notFound(res);
    const minAiScore = Number(body.strategy?.minAiScore ?? 70);
    const minUpProbability = Number(body.strategy?.minUpProbability ?? 0.6);
    const maxRiskLevel = String(body.strategy?.maxRiskLevel ?? "MEDIUM").toUpperCase();
    const horizonDays = Number(body.strategy?.holdingDays ?? 5);
    const commissionRate = Number(body.strategy?.commissionRate ?? 0);
    const taxRate = Number(body.strategy?.taxRate ?? 0);
    const slippageRate = Number(body.strategy?.slippageRate ?? 0);
    const takeProfitRate = Number(body.strategy?.takeProfitRate ?? 0);
    const stopLossRate = Number(body.strategy?.stopLossRate ?? 0);
    return send(res, 200, {
      data: {
        symbol: stock.symbol,
        market: stock.market,
        totalReturn: 0,
        winRate: 0,
        maxDrawdown: 0,
        sharpeRatio: 0,
        tradeCount: 0,
        conditions: {
          horizonDays,
          signalModel: "mock-signal-v1",
          minAiScore,
          minUpProbability,
          maxRiskLevel,
          entryRule: `mock signal score >= ${minAiScore} AND upProbability >= ${minUpProbability} AND risk <= ${maxRiskLevel}`,
          exitRule: `Mock mode; holding ${horizonDays} days, take-profit ${takeProfitRate || "disabled"}, stop-loss ${stopLossRate || "disabled"}.`,
          positionSizing: "Mock only; no historical position replay or portfolio rebalancing.",
          costModel: `Mock only; commission ${commissionRate}, tax ${taxRate}, slippage ${slippageRate}.`,
          dataScope: "Mock fixture; no historical price replay."
        },
        status: "MOCK_FALLBACK",
        signalModel: "mock-signal-v1",
        dataPointCount: Array.isArray(stock.prices) ? stock.prices.length : 0,
        note: "目前是 mock API fixture，沒有逐筆歷史交易；請切換真實 API 進行歷史價格回放。",
        trades: [],
        priceSources: ["mock"],
        source: "mock-backtest-v1",
        generatedAt: now()
      }
    });
  }
  if (parts[0] !== "api" || parts[1] !== "v1" || parts[2] !== "stocks") return notFound(res);

  const market = parts[3];
  if (!stocks[market]) return notFound(res);
  if (parts[4] === "search") {
    const q = (url.searchParams.get("query") || "").toUpperCase();
    const data = Object.keys(stocks[market]).filter(symbol => symbol.includes(q)).map(symbol => getStock(market, symbol));
    return send(res, 200, { data });
  }

  const stock = getStock(market, parts[4]);
  if (!stock) return notFound(res);
  const action = parts[5];
  if (action === "summary") {
    return send(res, 200, {
      data: {
        symbol: stock.symbol,
        name: stock.name,
        market,
        currency: stock.currency,
        timezone: market === "TW" ? "Asia/Taipei" : "America/New_York",
        priceSnapshot: {
          symbol: stock.symbol,
          market,
          lastPrice: stock.lastPrice,
          changePercent: stock.changePercent,
          currency: stock.currency,
          updatedAt: now()
        }
      }
    });
  }
  if (action === "prices") {
    return send(res, 200, { data: { prices: stock.prices.map((close, i) => ({ close, date: `D-${stock.prices.length - i - 1}` })) } });
  }
  if (action === "data-lineage") {
    return send(res, 200, {
      data: {
        symbol: stock.symbol,
        market,
        source: "mock-api",
        adjustmentStatus: "MOCK",
        observedAt: now(),
        pricePointCount: stock.prices.length,
        dataFrom: null,
        dataTo: null,
        priceSources: ["mock-api"],
        integrityStatus: "MOCK",
        findings: ["MOCK_SOURCE", "CORPORATE_ACTION_FEED_NOT_AVAILABLE"],
        corporateActions: [],
        technicalQuality: mockTechnicalQuality(stock.prices.length),
        predictionQuality: mockPredictionQuality(stock.prices.length)
      }
    });
  }
  if (action === "technical-summary") {
    return send(res, 200, {
      data: {
        symbol: stock.symbol,
        market,
        ma5: stock.lastPrice * 0.99,
        ma20: stock.lastPrice * 0.965,
        ma60: stock.lastPrice * 0.928,
        rsi14: 62.5,
        macdSignal: stock.changePercent >= 0 ? "BULLISH" : "NEUTRAL",
        atr14: stock.lastPrice * 0.026,
        updatedAt: now(),
        dataQuality: mockTechnicalQuality(stock.prices.length)
      }
    });
  }
  if (action === "prediction") {
    return send(res, 200, {
      data: {
        symbol: stock.symbol,
        market,
        horizonDays: Number(url.searchParams.get("horizonDays") || 5),
        upProbability: Math.max(0.42, Math.min(0.78, 0.58 + stock.changePercent / 40)),
        expectedReturn: stock.changePercent / 100 * 0.8,
        volatility: 0.032,
        riskLevel: Math.abs(stock.changePercent) > 2.5 ? "HIGH" : "MEDIUM",
        modelVersion: "mock-xgboost-v0.1",
        generatedAt: now(),
        dataQuality: mockPredictionQuality(stock.prices.length)
      }
    });
  }
  notFound(res);
}).listen(port, () => {
  console.log(`Mock API ready: http://localhost:${port}/api/v1/health`);
});
