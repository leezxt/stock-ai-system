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
    "access-control-allow-methods": "GET, POST, OPTIONS",
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
    const providers = body.providers?.length ? body.providers : ["OPENAI", "CLAUDE", "GEMINI", "DEEPSEEK"];
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
        message: `根據 ${stock.symbol} 目前 mock data：${body.message || "請評估風險"} 重點是價格是否守住 MA20、波動是否擴大，以及模型分歧是否升高。此回覆僅供研究，不代表投資建議。`,
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
    const edge = (providerResult(stock, "OPENAI").aiScore - minAiScore) / 100 + (Math.max(0.42, Math.min(0.78, 0.58 + stock.changePercent / 40)) - minUpProbability);
    return send(res, 200, {
      data: {
        symbol: stock.symbol,
        market: stock.market,
        totalReturn: edge,
        winRate: Math.max(0.35, Math.min(0.72, 0.48 + edge)),
        maxDrawdown: -0.098,
        sharpeRatio: 1.05 + edge,
        tradeCount: 42,
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
        updatedAt: now()
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
        generatedAt: now()
      }
    });
  }
  notFound(res);
}).listen(port, () => {
  console.log(`Mock API ready: http://localhost:${port}/api/v1/health`);
});
