# Analytics System Improvements (NeoForge/Java/Gradle Modpack)

This plan focuses on turning the current relay analytics stream into player-facing, pack-level insights such as:

- **How many unique players are active** (DAU/WAU/MAU).
- **Peak concurrent players** by day/week.
- **Retention trends** (new vs returning players).
- **Server health correlations** (TPS/tick lag, memory, overload).

## Current state (what exists today)

The current system already sends analytics packets from game servers to the relay and persists data under `analytics/<serverId>/<playerUuid>.json`.

Strengths:

- Includes both **population** (`playerCount`, `maxPlayers`) and **health signals** (`worstTickMillis`, `cpuLoad`, memory, overloaded flag).
- Includes lightweight player identity (`uuid`, `name`) and sync deltas (joined/left IDs).

Gaps for pack-level reporting:

- Per-player file writes overwrite a single JSON file, which makes **historical trend analysis hard**.
- There is no built-in **rollup job** for DAU/WAU/MAU and retention.
- No explicit **consent/privacy policy boundaries** around analytics storage/retention.

## Improvement roadmap

## 1) Split analytics into event stream + aggregates

Keep the existing packet flow, but persist two data shapes:

1. **Raw time-series events** (append-only)
   - Example path: `analytics/events/YYYY-MM-DD/<serverId>.jsonl`
   - One line per snapshot packet.
2. **Materialized rollups** (query-friendly)
   - Example path: `analytics/rollups/daily/<date>.json`
   - Contains DAU, peak CCU, avg CCU, overload minutes, etc.

Why: append-only events preserve history; rollups make dashboards fast.

## 2) Define core KPIs for a modpack owner

Minimum KPI set:

- **DAU / WAU / MAU** based on unique `player.uuid`.
- **Peak CCU** (max `playerCount`) per day and per week.
- **New players per day** (first time UUID observed).
- **Returning players** (seen before, active again).
- **Session proxy** from join/left delta IDs (until full session tracking is added).
- **Performance SLOs**:
  - `% snapshots overloaded == true`
  - `p95 worstTickMillis`
  - `avg cpuLoad`

This gives both "how many play" and "how healthy is the experience".

## 3) Add durable storage suited for analytics

For low ops overhead, choose one of:

- **SQLite** (single relay host, simple backup).
- **PostgreSQL** (multi-host, future dashboard/API scaling).

Recommended schema (portable between SQLite/Postgres):

- `analytics_snapshots`
  - `server_id`, `timestamp`, `player_count`, `max_players`, `worst_tick_ms`, `cpu_load`, `used_memory_mb`, `overloaded`, `overloaded_reason`
- `analytics_player_presence`
  - `server_id`, `timestamp`, `player_uuid`, `player_name`
- `analytics_daily_rollup`
  - `date`, `dau`, `peak_ccu`, `avg_ccu`, `new_players`, `returning_players`, `p95_worst_tick_ms`, `overload_ratio`

## 4) Build a scheduled rollup task in Gradle runtime

Implement a relay-side scheduled job (every 5–10 minutes + daily closeout) that:

1. Reads new snapshots/events.
2. Updates per-day aggregates.
3. Emits JSON/API payload for dashboards.

Implementation options:

- Java scheduled executor inside relay process.
- Separate Java `main` task launched by systemd/cron using Gradle distribution.

## 5) Expose a tiny read API for dashboards/Discord bots

Add relay endpoints:

- `GET /analytics/summary?range=24h|7d|30d`
- `GET /analytics/players/active?range=7d`
- `GET /analytics/servers`

Use this to power:

- Admin web dashboard (Grafana/custom).
- Discord command like `/packstats` returning DAU + peak + overload alerts.

## 6) Improve session accuracy

Current joined/left ID lists are useful but not full sessions. Improve by tracking:

- `session_start` when UUID first appears after absence window.
- `session_end` on left/disconnect/timeout.
- Derived metrics: median session length, churn after first day, time-of-day heatmap.

## 7) Add privacy, compliance, and retention controls

For public modpacks/communities, add:

- Configurable retention (e.g., raw events 30–90 days, rollups 1 year).
- Optional UUID hashing/salting for long-term storage.
- Opt-out toggle in server config for analytics forwarding.
- Clear `README` section: what is collected, why, retention period.

## 8) Alerting for "help with other stuff"

Analytics can support operational automation:

- Alert when overload ratio exceeds threshold for 10+ minutes.
- Alert when DAU drops sharply week-over-week.
- Alert when crash/restart loops correlate with peak hours.
- Recommend memory/TPS tuning from observed p95 values.

## Suggested phased implementation

### Phase 1 (1–2 days)
- Keep packet format unchanged.
- Switch persistence from overwrite JSON to append-only JSONL events.
- Add daily rollup generator producing DAU + peak CCU.

### Phase 2 (2–4 days)
- Move events/rollups into SQLite/Postgres.
- Add `/analytics/summary` endpoint.
- Add Discord `/packstats` command integration.

### Phase 3 (ongoing)
- Sessionization + retention dashboards.
- Automated anomaly detection + alerting.
- Capacity forecasting (expected peak CCU next 7 days).

## Practical KPI definitions

- **DAU(date D)** = count of distinct `player_uuid` seen on date D.
- **WAU(date D)** = distinct `player_uuid` seen in `[D-6, D]`.
- **MAU(date D)** = distinct `player_uuid` seen in `[D-29, D]`.
- **New players(date D)** = UUIDs first observed on D.
- **Returning players(date D)** = DAU(D) - New players(D).
- **Peak CCU(date D)** = max(`player_count`) across snapshots on D.

## NeoForge/Gradle implementation notes

- Keep capture logic lightweight to avoid ticking impact; batching writes is preferred.
- Use async queues for IO and bounded backpressure.
- Add integration tests around rollup correctness with synthetic snapshots.
- Keep schema migrations versioned (`V1__init.sql`, etc.) if adopting SQL.

---

If you want, the next step can be a concrete implementation PR that adds:

1. JSONL event persistence in `GlobalChatRelayServer`,
2. a daily rollup job,
3. and a `/analytics/summary` endpoint returning DAU/peak/health metrics.
