/**
 * auth.js — JWT management and API helper
 * All pages include this file first.
 */

const API = 'http://localhost:8081/api';

// ── Token management ───────────────────────────────────────────────
function getToken()           { return localStorage.getItem('cs_token'); }
function getUser()            { return JSON.parse(localStorage.getItem('cs_user') || '{}'); }
function setSession(token, user) {
  localStorage.setItem('cs_token', token);
  localStorage.setItem('cs_user', JSON.stringify(user));
}
function clearSession() {
  localStorage.removeItem('cs_token');
  localStorage.removeItem('cs_user');
}
function isLoggedIn()         { return !!getToken(); }

// ── Redirect guard ─────────────────────────────────────────────────
function requireAuth() {
  if (!isLoggedIn()) {
    window.location.href = '/login.html';
    return false;
  }
  return true;
}

function redirectIfLoggedIn() {
  if (isLoggedIn()) window.location.href = '/dashboard.html';
}

// ── HTTP helpers ───────────────────────────────────────────────────
async function apiGet(path) {
  const res = await fetch(API + path, {
    headers: { 'Authorization': 'Bearer ' + getToken() }
  });
  if (res.status === 401) { clearSession(); window.location.href = '/login.html'; return; }
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

async function apiPost(path, body) {
  const res = await fetch(API + path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + getToken()
    },
    body: JSON.stringify(body)
  });
  if (res.status === 401) { clearSession(); window.location.href = '/login.html'; return; }
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

async function apiPut(path, body) {
  const res = await fetch(API + path, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + getToken()
    },
    body: body ? JSON.stringify(body) : undefined
  });
  if (res.status === 401) { clearSession(); window.location.href = '/login.html'; return; }
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

async function apiDelete(path) {
  const res = await fetch(API + path, {
    method: 'DELETE',
    headers: { 'Authorization': 'Bearer ' + getToken() }
  });
  if (res.status === 401) { clearSession(); window.location.href = '/login.html'; return; }
  if (!res.ok) throw new Error(await res.text());
  return res.status === 204 ? null : res.json();
}

// ── Toast notifications ────────────────────────────────────────────
function toast(msg, type = 'info') {
  const icons = { success: '✅', error: '❌', info: 'ℹ️' };
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = `<span>${icons[type]}</span><span>${msg}</span>`;
  document.getElementById('toast-container').appendChild(el);
  setTimeout(() => el.remove(), 3500);
}

// ── Logout ─────────────────────────────────────────────────────────
function logout() {
  clearSession();
  window.location.href = '/login.html';
}

// ── User info in topbar ────────────────────────────────────────────
function renderTopbarUser() {
  const user = getUser();
  const nameEl = document.getElementById('topbar-username');
  const roleEl = document.getElementById('topbar-role');
  const avatarEl = document.getElementById('topbar-avatar');
  if (nameEl) nameEl.textContent = user.username || 'Unknown';
  if (roleEl) roleEl.textContent = user.role || '';
  if (avatarEl) avatarEl.textContent = (user.username || 'U')[0].toUpperCase();
}

// ── Badge helpers ──────────────────────────────────────────────────
function severityBadge(sev) {
  const map = {
    CRITICAL: 'badge-red',
    HIGH:     'badge-red',
    MEDIUM:   'badge-orange',
    LOW:      'badge-yellow',
    SAFE:     'badge-green'
  };
  return `<span class="badge ${map[sev] || 'badge-gray'}">${sev}</span>`;
}

function statusBadge(status) {
  const map = {
    RUNNING:       'badge-green',
    ACTIVE:        'badge-green',
    STOPPED:       'badge-gray',
    MAINTENANCE:   'badge-yellow',
    DECOMMISSIONED:'badge-gray',
    EXPIRED:       'badge-red',
    PENDING_RENEWAL:'badge-yellow',
    IN_USE:        'badge-blue',
    SPARE:         'badge-gray',
    OPEN:          'badge-red',
    INVESTIGATING: 'badge-orange',
    RESOLVED:      'badge-green',
    FALSE_POSITIVE:'badge-gray'
  };
  return `<span class="badge ${map[status] || 'badge-gray'}">${status.replace(/_/g,' ')}</span>`;
}

function riskBar(score) {
  let cls = 'safe';
  if (score >= 95) cls = 'critical';
  else if (score >= 85) cls = 'high';
  else if (score >= 70) cls = 'medium';
  else if (score >= 50) cls = 'low';
  return `
    <div style="min-width:100px">
      <div class="flex items-center justify-between" style="margin-bottom:4px">
        <span style="font-size:12px;font-weight:600">${score}/100</span>
        <span style="font-size:11px" class="text-${cls === 'safe' ? 'green' : cls === 'low' ? 'yellow' : 'red'}">${cls.toUpperCase()}</span>
      </div>
      <div class="risk-bar-wrap"><div class="risk-bar ${cls}" style="width:${score}%"></div></div>
    </div>`;
}

function formatDate(str) {
  if (!str) return '<span class="text-muted">—</span>';
  return new Date(str).toLocaleDateString('en-IN', { day:'2-digit', month:'short', year:'numeric' });
}

function formatDateTime(str) {
  if (!str) return '—';
  return new Date(str).toLocaleString('en-IN', { day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit' });
}
