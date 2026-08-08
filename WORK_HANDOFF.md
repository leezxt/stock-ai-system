# Stock AI Work Handoff

Last updated: 2026-08-09

## 本地 ML、Google 登入與 live 驗收聚合 2026-08-08

- 預測不再只使用固定動能公式：`LocalLogisticPredictionModel` 以目前標的的歷史 bar 建立 lagged return／波動／RSI／趨勢特徵，使用只看當時以前資料的 next-bar label 訓練 `local-logistic-v1`；歷史不足時才退回 `heuristic-momentum-v1`，並保留資料品質限制。
- Google OAuth 現在有前端 Google Identity Services 可選登入、`GET /api/v1/auth/google/config` 公開 client 設定，以及既有後端 credential 驗證；需部署者設定 `STOCKAI_GOOGLE_CLIENT_ID`，不把 client ID 當秘密。
- 新增 `scripts\smoke-all-live.cmd`，會尋找健康 backend、執行帳號／OpenAI／語意 RAG／來源 smoke，缺少 key 或加密設定回報 `BLOCKED`，失敗回報非零，不把未驗收狀態算通過。

## AI 工具型問答與來源引用 2026-08-08

- `/api/v1/ai/chat` 現在先由 `ChatToolRouter` 依問題意圖查詢 `market_quote`、`technical_indicators`、`prediction_model`、新聞／財報 RAG 文件與 `backtest_guidance`。
- 回應新增 `intent`、`answerStatus`、`tools[]`；工具結果包含實際來源、觀測時間、資料狀態與文件 `citationIds`，前端 AI 問答頁會分開顯示工具結果與文件證據。
- 天氣、食譜、程式碼、翻譯等明顯非股票問題回傳 `OUT_OF_SCOPE`，不呼叫外部 AI provider；資料不完整時回傳 `PARTIAL` 或 `INSUFFICIENT_DATA`，不把缺值轉成預測結論。
- `scripts\smoke-chat-tools.cmd` 會建立暫時帳號，驗證必要工具結果與 out-of-scope fail-closed 行為。

## 資料品質／血緣與公司行動 2026-08-08

- 新增 `GET /api/v1/stocks/{market}/{symbol}/data-lineage`，回傳價格點數、日期區間、逐點來源、adjustment status、完整性 findings、技術／預測資料品質與公司行動。
- Yahoo chart 改使用 `events=div,splits` 取得股利／分割事件，歷史價格若有 `adjclose` 會標示 `ADJUSTED_CLOSE`；不再把未調整的 `regularMarketPrice` 硬接到調整後歷史序列，避免圖表製造假跳空。
- TWSE realtime 現在只更新摘要的最新報價；若已有 FinMind 歷史日線，不再替換或追加最後一根 bar，`/prices` 保持單一歷史來源，避免台股圖表混入快照價格。
- 前端價格圖只在 provider 回傳事件且事件日期落在圖表序列時畫公司行動標記；沒有事件時明確顯示「不從價格缺口自行推測」。
- `scripts\smoke-data-lineage.cmd` 會驗證健康狀態、來源、調整狀態、完整性與 `corporateActions` 陣列，方便 Compose 或本機 backend 重新驗收。

## 自選股警示中心 2026-08-08

- 新增 `GET/POST /api/v1/watchlist/alerts` 與 `PUT/DELETE /api/v1/watchlist/alerts/{id}`；規則以目前登入帳號隔離，且只能綁定該帳號已存在的自選股。
- 支援價格高於／低於、漲跌幅百分點、風險等級門檻，以及 `DATA_QUALITY_NOT_OK`；評估結果會附 `TRIGGERED`、`NORMAL`、`DISABLED` 或 `DATA_UNAVAILABLE`、來源與資料狀態。
- PostgreSQL 使用 `V3__watchlist_alerts.sql`（V2 已由文件 ownership migration 使用）；未設定資料庫時使用 SHA-256 使用者檔案與 atomic write，保留本機 fallback。
- 前端自選股頁新增規則表單、觸發狀態、停用／啟用／刪除操作；明確標示提醒不是交易指令，mock 模式不假造觸發結果。
- V4 `stockai_watchlist_alert_events`／`watchlist-alert-events-<sha256>.txt` 只記錄狀態轉換；`newlyTriggered` 與 `newTriggerCount` 對同一觸發狀態去重，回傳最近 20 筆本機歷史，刪除規則時同步刪除其歷史。
- `scripts\smoke-watchlist-alerts.cmd` 會驗證登入、建立、首次觸發、重複刷新去重、停用與刪除流程；目前未接 Email／推播。

## 警示排程與通知準備 2026-08-08

- `WatchlistAlertScheduler` 已加入 `@Scheduled` 全域評估；`STOCKAI_WATCHLIST_ALERTS_SCHEDULER_ENABLED` 預設 `false`，避免本機未授權時背景呼叫行情／預測 provider。
- 啟用後會讀取 `UserStore.listEmails()`，逐帳號呼叫既有 `WatchlistAlertService.evaluate`，沿用事件表／檔案 fallback 的狀態去重；單一帳號失敗只增加錯誤計數，不中斷其他帳號。
- `GET /api/v1/watchlist/alerts/scheduler` 需要登入，只回傳間隔、最近執行時間、評估數量與錯誤計數；通知模式固定 `LOCAL_ONLY`，沒有 Email、推播或交易副作用。
- Compose 與 `local-env.example.cmd` 已提供排程開關、間隔、初始延遲與通知模式設定；目前 mock 只回傳可驗證的排程狀態，不假造背景執行。

## 本機通知佇列 2026-08-09

- V5 `stockai_watchlist_alert_notifications`／`watchlist-alert-notifications-<sha256>.txt` 只在 `TRIGGERED` 狀態轉換時建立 `LOCAL_ONLY` 通知，透過 `(email,event_id,channel)` 去重。
- 新增 `GET /api/v1/watchlist/alerts/notifications?limit=20` 與 `PUT /api/v1/watchlist/alerts/notifications/{id}`；通知只回傳目前登入帳號，可標記 `READ`／`UNREAD`。
- 刪除警示規則時同步刪除事件與通知；前端警示中心顯示未讀數與本機通知佇列，沒有外部傳送副作用。
- `scripts\smoke-watchlist-alerts.cmd` 已涵蓋通知建立、已讀、重複評估去重與刪除清理。

## 通知偏好與 fail-closed 2026-08-09

- V6 `stockai_watchlist_alert_notification_preferences`／`watchlist-alert-notification-preferences-<sha256>.properties` 保存帳號的 `localEnabled`、`emailEnabled`、`pushEnabled` 意圖。
- 新增 `GET/PUT /api/v1/watchlist/alerts/notification-preferences`；回應會明確回傳 `externalChannelsAvailable=false` 與阻擋原因，避免 UI 把偏好勾選誤認為外部傳送已生效。
- `localEnabled=false` 時仍保留警示事件，但不建立本機通知；Email／推播偏好目前不會觸發任何外部呼叫。
- 前端新增本機通知開關與外部通道阻擋提示；mock／real smoke 均驗證偏好更新後仍維持 fail-closed。

## 通知清理 2026-08-09

- 新增 `DELETE /api/v1/watchlist/alerts/notifications/read`，只清除目前帳號的 `READ` 通知，回傳 `deleted` 與 `localOnly=true`。
- 前端通知佇列提供「清除已讀」按鈕；V5 警示事件與 `recentEvents` 不會被清除，保留稽核能力。
- mock／real smoke 均驗證標記已讀後清理一筆通知，且未觸碰警示規則。

## Live smoke backend 版本探測 2026-08-09

- `scripts\smoke-all-live.ps1` 不再只依賴 `/health` 選擇 backend；現在會再檢查未授權的 API index 與目前來源抓取端點，避免同一台電腦上的舊程序回報健康卻在 live smoke 時產生 404。
- 若同時存在多個埠，腳本會略過不符合目前 API contract 的舊服務，並將實際驗收 URL 印出；金鑰與加密設定仍只在本機環境讀取，不會輸出值。
- 本機驗證曾同時發現 8080 舊服務與 Compose 18081；修正後會選擇 18081。live gate 仍因 `local-env.cmd` 沒有啟用 API key／加密 key 而明確回報 `BLOCKED`，不把來源 warning 或 mock 結果算成 live 通過。

## TWSE live 來源驗證 2026-08-09

- 新增 `scripts\smoke-twse-live.cmd`／`.ps1`，直接讀取官方 `STOCK_DAY_ALL`，再比對 backend 的台股摘要與有日期歷史序列。
- 本機驗證官方 2330 收盤 `2370.00`，backend 摘要 `lastPrice=2370`、`summarySource=finmind+twse-realtime`；歷史 21 筆為 `2026-07-09`～`2026-08-07`，逐點來源為 `finmind`，未混入 mock。
- TLS／官方 endpoint 本次可正常回應；若日後連線失敗或最新價與官方值不一致，smoke 會明確 `BLOCKED`／失敗，不把 fallback 圖表當成 TWSE live 通過。

## Live 設定前置檢查 2026-08-09

- 新增 `scripts\check-live-config.cmd`／`.ps1`，只檢查 `OPENAI_API_KEY`、RAG provider／embedding key、帳號加密 secret 長度與目前 backend API contract，不會發出外部 AI 請求，也不輸出 key 值。
- 若 `local-env.cmd` 仍保留被註解的模板，preflight 會明確指出「未啟用」；`smoke-all-live` 再負責真正的端到端驗收。
- `smoke-all-live.cmd` 現在會自動先執行 preflight；若外部 smoke 失敗優先回傳失敗碼，否則保留設定阻擋碼，避免呼叫端把未就緒環境當成成功。

## Compose 語意 RAG 啟動入口 2026-08-08

- 新增 `scripts\start-compose.cmd`，Windows 啟動 Compose 時會先載入被 `.gitignore` 排除的 `local-env.cmd`，避免本機已選擇 OpenAI 語意 RAG 卻因直接執行 `docker compose` 而退回 `hash`。
- 啟動入口只輸出 provider 與連線埠，不輸出任何 API key；`openai` provider 若沒有 `STOCKAI_RAG_EMBEDDING_API_KEY` 或 `OPENAI_API_KEY` 會在 Docker 啟動前 fail-fast。
- `hash` 仍是安全離線預設；切換 embedding provider 後必須重新匯入文件，避免混用不同向量模型。

## 真實模式 AI 失敗時禁止前端模擬分數 2026-08-08

- 真實模式若尚未取得 `/ai/analysis` 或 `/ai/model-comparison` 結果，模型比較改顯示失敗與 `--`，不再套用前端 fixture 分數。
- 個股分析在真實 AI 不可用時不再顯示「模型預測仍偏正向」、模擬偏多理由或把技術模型風險誤標成 AI 風險；行情與技術模型仍可獨立顯示。
- `unavailable-ai` 會被來源提示視為警告，明確要求完成登入、API key 與 provider 驗證。

## AI 缺值與單點預測限制 2026-08-08

- 前端模型比較不再把缺少或超出範圍的 `aiScore`／`bullishProbability` 轉成 0；無效模型會排除於共識，模型結果不足時顯示未計算。
- RAG context 在 `SNAPSHOT_ONLY`／`INSUFFICIENT_DATA` 或指標列入 `unavailableIndicators` 時，不再把計算欄位當成可用偏多理由、風險等級或 prompt 事實；改用明確的資料不足限制。
- AI prompt 會將不可用技術／預測欄位標示為 `UNAVAILABLE`，避免模型誤用單點快照產生技術或預測結論。

## 真實行情序列缺值防混用 2026-08-08

- 前端真實行情載入現在只接受 API 回傳的有效正價格點；價格清單缺失或格式錯誤時最多保留有來源的單點快照，不再以本地 mock 歷史序列補圖。
- 價格與日期會以同一筆資料同步解析，缺日期只留下該點的空日期標籤，不會因分開過濾造成日期與收盤價錯位。
- 真實模式若 API 沒有回傳 `dataQuality`，前端依實際點數產生 `SNAPSHOT_ONLY`／`PARTIAL`／`OK` 或 `INSUFFICIENT_DATA`，不沿用 mock 股票的資料品質狀態。

## 全模型失敗時的共識限制 2026-08-08

- 首頁 AI 共識現在只計算有有效分數與偏多機率的模型；全部模型失敗時顯示 `--`，並明確標示未計算共識，不再出現 `NaN`。
- 模型比較分歧提示區分「部分模型失敗」與「所有模型失敗」，全失敗時不會把空結果誤稱為低分歧。

## 回測 fallback 績效限制 2026-08-08

- 前端 mock／真實回測失敗回落不再用 AI 分數與預測機率推算總報酬、勝率、最大回撤或夏普比率；這些欄位改顯示未執行歷史回放。
- fallback 條件明確標示 `frontend-mock`、沒有可驗證的有日期收盤價、不建立部位且不計算績效；只有 backend `historical-price-replay-v1` 回傳的結果才會呈現績效與交易明細。

## 自選股來源與模型分數一致性 2026-08-08

- 自選股清單不再固定把模型總分除以 4；失敗模型會排除，全部失敗時顯示 `--`，避免出現 `NaN` 或虛假的平均分數。
- 清單新增 AI 來源、行情來源與資料品質欄位；非目前選取的真實標的若仍使用本地 fixture，會明確標示 `mock` 與樣本狀態。
- 風險欄位沿用預測 `dataQuality`，單點或樣本不足時顯示「資料不足」，不把 fixture 風險當作可用預測。

## 多模型比較資料品質提示 2026-08-08

- 多模型比較頁現在顯示共用股票資料的技術／預測 `dataQuality`，包含樣本數、來源、狀態與未可用指標，避免只看模型分數而忽略歷史資料不足。
- 真實模式會明確說明各模型共用同一檔股票資料與檢索上下文；mock 模式則標示未呼叫即時 provider，與模型分數來源分開呈現。

## AI 問答資料品質同步 2026-08-08

- 前端 mock／fallback AI 問答現在沿用個股行情的 `dataQuality`，MA20、RSI14、上漲機率與風險在樣本不足時會顯示「資料不足」，不再把 fallback fixture 的數值當成可用指標。
- 問答回覆同時列出技術資料與預測資料的樣本數、來源與狀態；真實 API 失敗回落時仍保留這些限制，與後端 `MockAiProviderAdapter` 的行為一致。

## 個股分析觀察重點 2026-08-08

- 個股分析卡片新增「觀察重點」，前端會呈現 backend AI analysis 回傳的 `watchPoints`，不再遺漏 RAG context 產生的後續追蹤建議。
- 真實分析會優先顯示依資料品質產生的限制（例如 MACD／MA20 樣本不足、行情來源與證據筆數）；mock 或未取得分析時則使用保守的前端 fallback，不把缺失指標當成可用訊號。
- 若沒有新聞評分，觀察重點會明確要求先確認消息面資料日期；新聞訊號仍與 AI 分數、技術模型風險分開呈現。

## 個股分析消息面整合 2026-08-08

- 個股分析卡片新增「消息面訊號」欄位，沿用總覽的可追溯新聞評分，但不把新聞分數混入 AI 分數或技術模型風險。
- 已評估時顯示整體新聞分數、偏多／偏空標籤、文章數與最新發布時間；未評估或 `INSUFFICIENT_DATA` 時會明確顯示限制與不納入結論。
- 切換市場、搜尋載入或點選自選股時會清除上一檔股票的新聞評分，避免個股切換後沿用錯誤消息面資料。

## 價格圖表資料一致性 2026-08-08

- 圖表均線圖例現在只列出依 `dataQuality.unavailableIndicators` 實際可繪製的 MA5／MA20／MA60；資料不足的線不會只留在圖例中造成誤判。
- 圖表下方新增實際價格點數、資料來源與日期區間；真實 AAPL 驗證顯示 24 個 `yahoo-finance` 價格點、2026-07-08～2026-08-08，且只繪製 MA5／MA20。
- mock 或快照序列會明確顯示沒有完整日期區間，避免把展示用序列誤認為交易日歷史。

## 個股分析資料品質 2026-08-08

- `TechnicalSummaryResponse` 與 `PredictionResponse` 現在都回傳 `dataQuality`：實際樣本數、所需樣本數、日期起訖、來源、`OK`／`PARTIAL`／`SNAPSHOT_ONLY`／`INSUFFICIENT_DATA`／`MOCK` 狀態與未可用指標清單。
- 技術指標依實際樣本門檻標示可用性（MA5/20/60、RSI14、MACD、ATR14）；即使為保持 API 相容仍回傳計算欄位，前端會依 `unavailableIndicators` 顯示「資料不足」，不把少量樣本冒充完整指標。
- realtime-only 行情只會形成單點快照；預測的上漲機率、預期報酬、波動率與風險在單點資料時顯示不可用。RAG prompt 同步帶入資料品質與日期區間，要求 AI 明確揭露限制。
- 新增 `StockServiceTest` 覆蓋 3 筆歷史資料的部分可用狀態與單點 realtime 快照狀態；API contract/OpenAPI 已同步 `MarketDataQuality` schema。
- 本次驗證：`mvn -q test` 119 tests passed、`scripts\test-all.cmd` passed、前端／OpenAPI 解析 passed；Compose `stock-ai-fresh` host `18081`／PostgreSQL `15433` healthy，真實 AAPL 頁面顯示 24 筆 `yahoo-finance`、`PARTIAL`、MA60／MACD 資料不足。

## 個股分析 AI 指標一致性 2026-08-08

- 個股分析頁現在以同一份 `analysisViewValues` 決定趨勢、AI 分數、上漲機率與 AI 風險；不再把 AI 卡片的機率／風險誤讀成技術模型 `prediction` 的值。
- 結論同時標示 `AI 風險等級` 與 `技術模型風險等級`，總覽的技術模型預測仍保留，兩者不混稱為同一個結果。
- 前端語法檢查與 Docker Compose 本機頁面驗證已通過；mock 個股分析畫面可看到一致的 61% 模型機率與中風險，結論明確列出兩種風險來源。

## 歷史價格回測 2026-08-08

- `POST /api/v1/backtests` 驗證 `minAiScore`（0～100）、`minUpProbability`（0～1）、`maxRiskLevel`（LOW/MEDIUM/HIGH）、`holdingDays`（1～60）、成本率（0～0.05）與停利／停損率（0～2）。未指定時使用 70、0.60、MEDIUM、5 日與零成本／停利停損。
- `HistoricalBacktestEngine` 使用 `StockRecord.priceHistory` 的有日期收盤價逐根回放，訊號模型為 `technical-momentum-replay-v1`；只使用目前 bar 以前的五根動能／均線／波動資料，不把現在日期的 AI 輸出回填到歷史，也不補 synthetic 日期。
- API 與前端會顯示 AND 進場條件、持有／停利／停損、手續費／證交稅／滑價、資料點數、`COMPLETED`／`NO_SIGNALS`／`INSUFFICIENT_DATA` 狀態、逐筆交易明細與實際 `priceSources`。資料不足時回傳零交易與原因，不製造固定交易次數或績效。
- 回測回傳來源為 `historical-price-replay-v1`，`conditions` 會回傳 `signalModel` 與完整限制；mock API 另標示 `mock-backtest-v1`，避免與真實歷史回放混淆。
- 新增歷史引擎、策略成本與交易明細測試，以及 `scripts\smoke-backtest-conditions.cmd` 的條件／來源／狀態／非法門檻驗證。

## 新聞評分評估完成 2026-08-08

- 新增需登入的 `GET /api/v1/stocks/{market}/{symbol}/news-score?limit=5`，只讀取目前帳號可見、已匯入且類型為 `NEWS` 的 RAG 文件。
- `NewsScoreService` 以可追溯的規則式中英文字詞訊號計算每篇新聞的多空分數（-100～100）、影響度、相關度、信心度與命中詞理由，再產生加權整體分數；這是新聞篩選訊號，不是價格預測。
- 沒有可用新聞時回傳 `INSUFFICIENT_DATA`、`no-indexed-news` 與 0 信心度，不產生假分數；前端總覽新增「新聞評分評估」卡片並顯示來源、發布時間、RAG 相關度與逐篇理由。
- 新增 `NewsScoreServiceTest` 與 `NewsEvaluationControllerTest`，涵蓋台股代碼正規化、正負面分布、資料不足、限制參數與登入授權。
- 新增 `scripts\\smoke-news-score.cmd`：建立暫時帳號、匯入三篇測試新聞、驗證逐篇來源與多空分布，並確認第二個帳號回傳 `INSUFFICIENT_DATA`，不會看到別人的文件。
- 本次驗證：`mvn -q test` 113 tests passed、`scripts\\test-all.cmd` passed、OpenAPI/前端語法解析 passed；Compose `stock-ai-fresh` 重建後 PostgreSQL healthy，未授權呼叫新聞評分回傳 401。

## AI Q&A hardening completed 2026-08-07

- Evidence rendering now escapes external document fields before inserting HTML, preventing document-driven XSS in the frontend evidence cards.
- RAG retrieval now applies a configurable relevance threshold and hybrid vector/lexical reranking. Low-signal vector-only hits are rejected unless they clear a high vector-only gate.
- AI prompts explicitly treat user text and retrieved documents as untrusted input, require uncertainty disclosure, and request citations using retrieved chunk IDs.
- `/api/v1/ai/chat` now returns `citationIds` validated against the retrieved evidence list.
- Retrieval evaluation tests cover services margin, buyback, TW advanced-node capex, and low-signal rejection.
- AI provider telemetry records process-local request counts, live successes, fallbacks, exceptions, and latency by provider/operation; `/api/v1/health` exposes aggregate counters only.
- `X-Request-Id` is generated or preserved by the backend and included in fallback logs for request correlation.
- AI Chat now accepts bounded multi-turn context (`history`, max 8 turns / 8,000 characters); history is escaped in the prompt and explicitly treated as untrusted input. The frontend keeps only the current stock's recent turns in memory and resets them when the stock changes.
- AI Chat is now open-ended instead of being limited to the three shortcut questions: the frontend has free-text + Enter/送出 input, real mode calls `/api/v1/ai/chat` even before a stock summary is loaded, and the last answer is preserved across rerenders. Provider prompts explicitly require intent-first answers rather than a fixed MA20 template; mock/fallback replies expose their degraded status.
- Taiwan price history now prefers FinMind before realtime-only sources. Realtime/OpenAPI quotes are merged only onto a real historical series; when no historical source exists, the response contains a single quote instead of mixing a live price with mock history, which fixes the Taiwan chart's false jump.
- Frontend stock loading now keeps the successfully loaded market history when authenticated AI analysis/model-comparison calls return 401; AI degradation is shown as a notice instead of replacing the chart with the search endpoint's single-point quote.
- Price bars now retain provider trading dates and per-point sources. FinMind/TWSE/Yahoo/Alpha Vantage histories no longer get re-indexed against synthetic weekdays; real realtime-only quotes remain one point, while mock data is the only path that generates illustrative dates. Backend test result after this fix: 108 tests passed.
- `priceSnapshot.updatedAt` now carries provider observation time instead of backend fetch time: TWSE intraday `d/t` is converted to Asia/Taipei, daily providers use the latest trading date in the market timezone, and mock data is the only path that falls back to request time. Tests cover FinMind, TWSE realtime/OpenAPI, Alpha Vantage, Yahoo, and summary propagation.
- Frontend/provider smoke choices now match the backend's supported providers (`OPENAI`, `GEMINI`, `DEEPSEEK`, `MIMO`); stale `CLAUDE` selections fall back to `OPENAI` instead of producing an avoidable 400.
- RAG embedding provider is now explicit: `hash` remains the offline default, while `openai` calls `text-embedding-3-small` with a 16-dimensional projection. Vector queries filter by `embeddingModel`; switching providers requires re-importing documents and the current pgvector schema remains `vector(16)`.
- `/api/v1/health.providers` reports `ragEmbeddingProvider`, model, dimension, and readiness without exposing the API key.
- Local OpenAI/RAG keys belong only in ignored `local-env.cmd`; run `scripts\check-no-secrets.cmd -Staged` before syncing to GitHub so staged API keys or secret-bearing files stop the sync.
- `scripts\smoke-rag-openai-live.cmd` now provides the real-key gate: it safely skips for missing/example keys, otherwise checks OpenAI RAG health, imports one smoke document, and verifies semantic retrieval.
- Account settings runtime smoke passed with an ephemeral process-only encryption key; `scripts\smoke-account-settings.ps1` now fails before HTTP calls when `STOCKAI_SECRETS_ENCRYPTION_KEY`/`STOCKAI_AUTH_SECRET` is missing.
- `/api/v1/health.providers.secretsEncryptionConfigured` and the Settings row now expose account-key encryption readiness before account settings writes are attempted.
- `scripts\smoke-source-fetch-live.ps1` now blocks before external source calls when the selected OpenAI RAG provider has no embedding key, instead of failing during document indexing.
- Source-fetch runtime smoke passed on temporary port 8081 with a process-only `hash` embedding override: FinMind TW financials imported 8 documents/22 chunks and TW announcements imported 2/2; Yahoo news/transcript paths returned 0 documents with warnings.
- Docker Compose build/runtime passed with a fresh project and host mappings 18081/15433: PostgreSQL + pgvector healthy, `/api/v1/health` and `/app` returned 200, and Compose RAG import/retrieve returned 1 chunk/1 hit using the safe default `hash` embedding. Existing volumes are preserved; host 5432 was unavailable.
- Backend test result after this hardening: 105 tests passed; frontend and OpenAPI parsing passed; mock smoke checks passed.

## Security hardening completed 2026-07-11

- Cost-bearing AI, backtest, watchlist, RAG, and source-fetch endpoints now require authentication and use per-user/IP rate limits.
- Browser auth uses an HttpOnly SameSite session cookie; localStorage no longer contains session tokens or provider keys.
- Account provider keys are encrypted with AES-256-GCM before database/file persistence, with legacy plaintext read compatibility.
- RAG chunks are owner-scoped in memory and PostgreSQL; runtime two-user isolation smoke passed.
- PostgreSQL uses HikariCP and Flyway migrations. Existing schema was baselined at V1 and owner migration V2 was applied successfully.
- `/health` verifies PostgreSQL with `SELECT 1` and returns HTTP 503 when a configured database is unavailable.
- Frontend defaults to same-origin `/api/v1`, and CORS is restricted to configured origins.
- Technical indicators now derive from available price history; prediction uses `local-logistic-v1` when at least 14 dated bars can train the baseline and explicitly falls back to `heuristic-momentum-v1` when history is insufficient.
- Current automated result: 82 tests passed before final documentation-only validation.

## Run

```powershell
scripts\start-spring-dashboard.cmd
```

Docker Compose:

```powershell
docker compose up --build -d
```

Open:

```text
http://localhost:8080/app
```

Docker Compose default host URL:

```text
http://localhost:18080/app
```

Stop backend:

```powershell
scripts\stop-backend.cmd
```

## Current State

- Frontend is served by Spring Boot at `/app`.
- API base URL is `http://localhost:8080/api/v1`.
- Docker Compose now packages backend + frontend into one container and publishes by default on `http://localhost:18080`.
- Backend runs on Java 21 on this machine: `C:\Users\lee\.jdks\ms-21.0.10`.
- Stock data has mock fallback, optional Alpha Vantage US daily support, optional TWSE OpenAPI daily close support, and frontend fallback notices.
- AI analysis has mock fallback, optional OpenAI Responses API support, optional Gemini support, and frontend fallback notices.
- OpenAI model defaults to `gpt-5.5`; override with `OPENAI_MODEL` or `stockai.openai.model`. Gemini also supports request-header or account-stored key flow.
- `scripts\start-backend.cmd`, `scripts\smoke-openai-live.cmd`, and `scripts\smoke-account-settings.cmd` load ignored `local-env.cmd` when present; copy `local-env.example.cmd` to set local API keys without committing secrets.
- `scripts\smoke-rag-openai-live.cmd` loads the same ignored `local-env.cmd` and validates `text-embedding-3-small` with the fixed 16-dimensional vector contract.
- `local-env.example.cmd` also includes optional `STOCK_AI_BASE_URL` for smoke testing against a non-default backend URL.
- Auth MVP is available with `/api/v1/auth/register`, `/api/v1/auth/login`, and `/api/v1/auth/me`.
- Logged-in users can persist `preferredProvider`, `OpenAI API key`, and `Gemini API key` via `/api/v1/account/settings`.
- AI key resolution order is: request header -> logged-in account settings -> backend env/config fallback.
- Watchlist persists to `backend\data\watchlist.txt` for guest mode and `backend\data\watchlist-*.txt` per account.
- Account settings persist to `backend\data\account-settings\*.properties`.

## Verified

```powershell
cd backend
mvn test
```

Current result: 42 tests passed.

Browser verification:

- `http://localhost:8080/app` loads.
- Real mode can load `AAPL`.
- Analysis label shows `OPENAI / mock-ai` when no OpenAI key is configured.
- Frontend shows a visible Real mode source panel with separate market and AI source status, plus the next live-fix hint.
- `GET /api/v1/health` returns provider readiness without exposing secrets.
- Settings API status table shows status pills for Market Source, AI Source, OpenAI Configured, Alpha Vantage Key, TWSE Endpoint, and the next live-readiness action.
- Settings footer and risk disclaimer no longer claim all data is mock-only; they now mention mock, live provider, and fallback sources.
- Real mode stock load and AI Chat now show compact loading states and prevent duplicate clicks while a request is running.
- Search and watchlist flow now supports Enter-to-search, clearer add/remove feedback, and showing the current real-loaded symbol inside watchlist rows.
- Logged-in settings now show whether OpenAI / Gemini keys are stored in backend account settings, and save account-scoped provider preference from the Settings tab.
- Settings `API 狀態` 現在可直接在頁面內刷新 live readiness，並執行 OpenAI / source-fetch live 驗收；source-fetch 驗收目前涵蓋 US news、TW news、TW financials、transcripts、announcements，缺 key / URL 時會顯示 skip 原因。
- AI analysis panel now groups trend, score, probability, reasons, risks, and conclusion into separate readable blocks.
- Backtest panel now shows rule summary, data source, result state, and real-to-mock fallback status.
- Settings panel now shows an OpenAI live smoke hint with the exact next command based on current readiness.
- `scripts\smoke-account-settings.cmd` now covers register, login, load empty settings, save provider + keys, partial update, clear-key behavior, and reload verification.
- user-scoped watchlist and account-settings files now use SHA-256 filenames for new writes while still reading legacy `hashCode()` filenames if they already exist.
- file-backed persistence now writes through atomic temp-file replacement for users, watchlists, and account settings.
- API contract docs now explicitly call out the `source` fields and `/health.providers` fields that the frontend depends on.
- Startup scripts now avoid double-starting a healthy backend and `stop-backend.cmd` reports clearly when 8080 is already free.
- AI Chat response includes `[mock-ai]`.
- `scripts\smoke-openai-live.cmd` safely skips when `OPENAI_API_KEY` is not set, and was verified to load ignored `local-env.cmd` with a temporary dummy key; live `openai-responses` still needs the real key and backend restart.
- TWSE parser test passes. Earlier local SSL/TLS fallback was rechecked on 2026-08-09; the official endpoint now responds and `scripts\smoke-twse-live.cmd` confirms the 2330 value against backend without mock history.
- Rechecked `GET /api/v1/stocks/TW/2330/summary`: backend returns `source: mock`, so TWSE fallback is working.
- Full smoke test rerun on 2026-07-06: `scripts\test-all.cmd` passed, frontend script parse passed, and `scripts\stop-backend.cmd` cleanly reported the no-listener path.
- MVP delivery check completed on 2026-07-06. Remaining work is live-provider completion and post-MVP RAG phase work.
- RAG step 56 completed: backend now has `rag.DocumentType` and `rag.DocumentChunk` with symbol normalization and required-field validation. `mvn test` passed with 17 tests.
- RAG step 57 completed: backend now has `rag.DocumentImportRequest` and `rag.DocumentIngestionService` for import, clean, and chunk. `mvn test` passed with 19 tests.
- RAG step 58 completed: backend now has `rag.VectorStore` plus `rag.InMemoryVectorStore`, `VectorDocument`, `VectorSearchQuery`, and `VectorSearchHit`. `mvn test` passed with 21 tests.
- RAG step 59 completed: backend now has `rag.EmbeddingModel`, `rag.HashEmbeddingModel`, and `rag.DocumentEmbeddingService`. `mvn test` passed with 23 tests.
- RAG step 60 completed: backend now has `rag.DocumentRetriever`, `DocumentRetrieveRequest`, and `RetrievedDocument` on top of the embedding + vector store path. `mvn test` passed with 24 tests.
- RAG step 61 completed: backend now has `stock.RagContextService` and `stock.RagContext`, which assemble technical summary, prediction output, and retrieved document evidence into one prompt context for AI analysis and chat. `/api/v1/ai/analysis`, `/ai/model-comparison`, `/ai/chat`, and `/backtest` now all read through the same RAG composition layer. `mvn test` passed with 26 tests.
- RAG step 62 completed: backend now exposes `POST /api/v1/documents/import` and `POST /api/v1/documents/retrieve` through `rag.RagDocumentController`. Import requests now run ingestion -> embedding -> vector upsert in one path, and retrieval can be checked directly through the API. `mvn test` passed with 27 tests.
- RAG step 63 completed: frontend `Settings` tab now includes a minimal document import panel for market, symbol, doc type, title, source, published time, and content. It calls `POST /api/v1/documents/import`, supports current-symbol autofill and sample seed content, and stores the last import result in local settings. Frontend script parse passed.
- RAG step 64 completed: frontend now calls `POST /api/v1/documents/retrieve` in Real mode and shows retrieved evidence blocks in both analysis and AI Chat panels. Stock load, provider refresh, and chat question all refresh evidence for the current symbol/query. Frontend script parse passed.
- RAG step 65 completed: `/api/v1/ai/analysis` and `/api/v1/ai/chat` now return `evidence` directly in the response body. Frontend now prefers response-embedded evidence and only falls back to `POST /api/v1/documents/retrieve` when evidence is absent. `mvn test` passed with 28 tests and frontend script parse passed.
- RAG step 66 completed: evidence UI is now clearer in analysis, chat, and model-comparison views. Empty states distinguish Mock mode vs missing imports, evidence cards show doc type/symbol/market tags, and Compare now explicitly shows the shared retrieved context used across models. Frontend script parse passed.
- RAG step 67 completed: backend now has source adapters and import routes for `POST /api/v1/documents/source/news/fetch`, `POST /api/v1/documents/source/announcements/fetch`, and `POST /api/v1/documents/source/transcripts/fetch`. US news uses Alpha Vantage `NEWS_SENTIMENT`, US earnings-call transcripts use Alpha Vantage `EARNINGS_CALL_TRANSCRIPT`, and TW company announcements now have a configurable JSON adapter via `stockai.twse.disclosure-url` with `{symbol}` / `{limit}` placeholders. `mvn test` passed with 32 tests.
- RAG step 68 completed: frontend `Settings` tab now exposes source-fetch controls for news, TW financials, company announcements, and earnings transcripts. Users can trigger source ingestion from the UI without manual API calls, and the panel tracks the latest source-import status. Frontend script parse passed.
- RAG step 69 completed: `/api/v1/health` now reports source-fetch readiness for Alpha Vantage news, FinMind TW news, FinMind TW financials, Alpha Vantage transcripts, and TW disclosure ingestion. Frontend `Settings` surfaces those readiness rows so the missing live prerequisites are visible before source import is attempted. `mvn test` passed with 32 tests and frontend script parse passed.
- RAG step 70 prep completed: added `scripts\smoke-source-fetch-live.cmd` and `scripts\smoke-source-fetch-live.ps1`, plus `STOCKAI_TWSE_DISCLOSURE_URL` to `local-env.example.cmd`. Source smoke now covers US news, TW news, TW financials, transcripts, and TW announcements. Current live verification is blocked because `local-env.cmd` does not exist and backend is not reachable at `http://localhost:8080/api/v1`.
- Account settings step completed on 2026-07-10: backend now has `AccountSettingsService` and `/api/v1/account/settings`, frontend now saves logged-in AI keys to backend instead of localStorage, and adapters resolve account-stored keys when request headers are absent. `mvn test` passed with 39 tests and frontend script parse passed.
- Persistence hardening step completed on 2026-07-10: `UserScopedFileLocator` now generates SHA-256 user-scoped filenames for watchlist and account settings, while load paths remain backward-compatible with legacy `hashCode()` filenames. `mvn test` passed with 41 tests and `scripts\\smoke-account-settings.ps1` passed.
- Atomic write step completed on 2026-07-10: `AtomicFileWriter` now handles temp-file + replace writes for `UserStore`, `WatchlistService`, and `AccountSettingsService`. Added `UserStoreTest`. `mvn test` passed with 42 tests and `scripts\\smoke-account-settings.ps1` passed.
- Docker Compose step completed on 2026-07-10: added root `Dockerfile`, `compose.yaml`, and `.dockerignore`. Verified `docker compose up --build -d`, `GET http://localhost:18080/api/v1/health`, and `GET http://localhost:18080/app`.

## Next Development Order

1. 設定有效 `OPENAI_API_KEY` 與 `STOCKAI_SECRETS_ENCRYPTION_KEY`，重啟 backend 後執行 `scripts\smoke-all-live.cmd`，完成 OpenAI Responses 與帳號加密的 live gate。
2. 將 `STOCKAI_RAG_EMBEDDING_PROVIDER=openai` 與 embedding key 注入 Compose，重新匯入文件，再執行 `scripts\smoke-rag-openai-live.cmd` 與 `RagRetrievalEvaluationTest`；不得混用既有 hash 向量。
3. 依部署環境補 Alpha Vantage／FMP／FinMind token／MOPS 公告 endpoint，重新執行 `scripts\smoke-source-fetch-live.cmd`；目前 FinMind 財報已可匯入，Yahoo 新聞／法說／公告仍可能 0 筆。
4. TWSE `STOCK_DAY_ALL`／realtime 已在本機完成一次官方值比對；後續部署仍應執行 `scripts\smoke-twse-live.cmd`，維持憑證驗證與 fallback 可追蹤性。
5. 若要啟用 Google 登入，設定 `STOCKAI_GOOGLE_CLIENT_ID`、Google Console 允許來源網域，並用前端 Google Identity Services 按鈕完成一次 credential 驗收。
6. 若進入多節點正式部署，再評估把目前已支援 JDBC 的 users／account settings／watchlist 由單機 fallback 強制切到 PostgreSQL，並補跨節點 session／限流與備份演練。

## Notes

- Do not add Docker, database, or React/Vite yet.
- Keep `/app` and `/api/v1` same-origin for local development.
- Keep mock fallback behavior for all external providers.
