# CyberShield Nexus — Enhancements Added

This document covers the 3 enhancements implemented in this pass, plus a
list of remaining ideas from the original menu that are not yet built.

## 1. Docker Support (one-command startup)

**Files added:** `Dockerfile`, `docker-compose.yml`, `.dockerignore`

```bash
docker compose up --build
# then open http://localhost:8081/index.html
```

- Multi-stage build (Maven build stage → slim JRE runtime stage), runs as
  a non-root user.
- Uses the existing embedded H2 file DB by default — zero external setup.
- Data persists across container restarts via the `cybershield_data`
  named volume.
- A commented-out `postgres` service + datasource env vars are included
  in `docker-compose.yml` if you want to switch to real PostgreSQL later.

## 2. Email Alerting on HIGH / CRITICAL Incidents

**Files added:** `AlertService.java`
**Files changed:** `IncidentService.java` (calls the alert after every
incident is saved), `application.properties` (new `alert.email.*` and
`spring.mail.*` keys)

- Off by default (`alert.email.enabled=false`) — the app still runs with
  zero setup; disabled alerts are simply logged instead of sent, so you
  can see the message it *would* have sent in the console.
- To activate: set `alert.email.enabled=true` in
  `application.properties` (or via env vars in Docker), and fill in
  `spring.mail.username` / `spring.mail.password` with real SMTP creds
  (Gmail example is pre-filled — use an **App Password**, not your real
  Gmail password).
- Only fires for `HIGH` and `CRITICAL` severity, to avoid alert fatigue
  from routine `LOW`/`MEDIUM` events.
- **To test:** run `brute_force.py` from `attack-scripts/` until an
  incident reaches HIGH/CRITICAL, then check the console log for the
  `[ALERT-SIMULATED]` or `Email alert sent` line.

## 3. PDF Incident Report Export

**Files changed:** `pom.xml` (added `openhtmltopdf-core` +
`openhtmltopdf-pdfbox`), `ReportService.java` (new
`generateIncidentReportPdf()` + a print-safe HTML builder),
`ReportController.java` (new `GET /api/reports/incident/{id}/pdf`),
`incidents.html` (new "⬇️ Download PDF" buttons)

- The existing `/html` report uses modern CSS (flexbox/grid/CSS
  variables) meant for the browser — the pure-Java PDF renderer only
  supports a CSS 2.1-level subset, so the PDF uses a separate,
  simpler table-based layout built from the same underlying report
  data (same numbers, same breakdown, same compliance attestations).
- Click "⬇️ Download PDF" on any incident (list view or detail modal)
  to get `incident-report-{id}.pdf`.

## 4. Phishing & Scam Link Detector (new module, built INTO this project)

**Files added:** `service/PhishingDetectionService.java`,
`controller/PhishingController.java`, `static/phishing-detector.html`
**Files changed:** `SecurityConfig.java` (permit the new static page),
`style.css` (added `.info-box`), nav sidebar added to every existing
page

- Same offline, heuristic scoring engine as the standalone version
  (12 checks: IP hostname, `@` symbol, long URLs, excessive subdomains,
  shorteners, suspicious TLDs, punycode, hyphens, no-HTTPS, lure
  keywords, non-standard ports, typosquatting) — ported from Python to
  Java so it runs as part of the same Spring Boot app, no separate
  server or port.
- Uses the exact same login/JWT session as the rest of CyberShield
  Nexus — click **Phishing Detector** in the sidebar after logging in.
- Endpoints: `POST /api/phishing/analyze` and
  `POST /api/phishing/analyze-batch`, open to any authenticated role
  (ADMIN/SERVER_ADMIN/VIEWER) since it's a personal-safety tool, not an
  asset-management action.

## Not yet built (from the original enhancement menu)

These remain open for a future pass — happy to build any of them next:

| # | Idea |
|---|---|
| 1 | Real AI/ML risk scoring (replace rule-based engine) |
| 3 | Real-time dashboard via WebSocket |
| 5 | More OWASP coverage (A03, A05, A08) |
| 8 | 2FA/MFA login |
| 9 | Live threat-intel feed integration |
| 10 | React frontend rebuild |

---

## Round 2: PS-Compliance Modules (matching PYHack02 problem statement)

The project's actual PS (AICTE Data Center Management Portal) lists 6
required features (points 5–10). Cross-checking against what existed
found 3 real gaps — these close them:

### 5. Firewall Rule Management (PS point 6)
**Added:** `model/FirewallRule.java`, `repository/FirewallRuleRepository.java`,
`service/FirewallRuleService.java`, `controller/FirewallRuleController.java`
**Changed:** `firewalls.html` (new "🛡 Rules" button + modal per firewall)

- Previously `Firewall.activeRulesCount` was just a manually-typed
  number — now each firewall has real, individually manageable rules
  (source/dest IP, port, protocol, ALLOW/DENY, priority, enabled toggle).
- `activeRulesCount` is now auto-synced from the real rule count
  whenever a rule is added/removed, so old dashboard/risk logic that
  reads that field keeps working unchanged.
- Endpoints: `GET/POST /api/firewalls/{firewallId}/rules`,
  `PUT/DELETE /api/firewall-rules/{id}`.

### 6. Load Balancer Management (PS point 7)
**Added:** `model/LoadBalancer.java`, `model/LoadBalancerBackend.java`,
repositories, `service/LoadBalancerService.java`,
`controller/LoadBalancerController.java`, `loadbalancers.html`

- Previously load balancers only existed as one tag inside generic
  Hardware inventory. Now they're a first-class module: distribution
  algorithm (Round Robin / Least Connections / IP Hash / Weighted),
  health-check config, status, and a manageable backend server pool
  with per-backend health toggling (Healthy/Unhealthy).
- New sidebar page under **Assets → Load Balancers**.
- Endpoints: `GET/POST/PUT/DELETE /api/loadbalancers`,
  `GET/POST /api/loadbalancers/{id}/backends`,
  `PUT /api/loadbalancer-backends/{id}/health`,
  `DELETE /api/loadbalancer-backends/{id}`.

### 7. User & Role Management (PS point 9)
**Added:** `dto/UserRequest.java`, `service/UserService.java`,
`controller/UserController.java`, `users.html`
**Changed:** `User.java` (`@JsonIgnore` on `passwordHash` — defense in
depth now that user objects are returned over the API)

- Previously user accounts only existed via the DB seed script — no
  admin UI to create accounts, change roles, or deactivate access.
  Now ADMIN accounts get a full **Users & Roles** page: create users,
  assign ADMIN/SERVER_ADMIN/VIEWER, edit department, activate/
  deactivate, optional password reset. Self-deletion is blocked.
- Every action logged to the audit trail like all other modules.
- ADMIN-only at both the API (`@PreAuthorize("hasRole('ADMIN')")`)
  and page level (non-admins are redirected off `users.html`).
- Endpoints: `GET/POST/PUT/DELETE /api/users`.

**Build note (same caveat as Round 1):** written and reviewed by hand
against the existing code's exact patterns (same Lombok annotations,
same `@PreAuthorize` style, same audit-log calls, same frontend
apiGet/apiPost helpers) but not compiled in this sandbox — run
`mvn clean package` locally first.

## Build note

This code was written and reviewed carefully by hand but **could not be
compiled in this sandbox** (Maven Central is not reachable from here —
only a small allowlist of package registries is). Run `mvn clean package`
or `mvn spring-boot:run` locally to build/verify — that's also how
you'd normally run this project.
