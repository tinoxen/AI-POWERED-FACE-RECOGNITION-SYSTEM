// Shared API helper for all pages.
// Prefer an explicit override when the app is hosted somewhere else.
function resolveApiBase() {
  const override = window.__API_BASE__ || window.API_BASE_URL || "";
  if (override) return override.replace(/\/$/, "");

  // During local development the static frontend is served on port 5500 while
  // Spring Boot listens on port 10000. Use the same hostname so LAN devices
  // can authenticate and load protected photos from the development machine.
  const isLocalStaticServer = window.location.port === "5500";
  if (isLocalStaticServer) {
    return `${window.location.protocol}//${window.location.hostname}:10000/api`;
  }

  return "/api";
}

const API_BASE = resolveApiBase();

function apiUrl(path) {
  return API_BASE + path.replace(/^\/api/, "");
}

async function loadProtectedImage(imageElement, photoUrl) {
  if (!imageElement || !photoUrl) return;
  try {
    const response = await apiFetch(photoUrl);
    if (!response.ok) return;
    const blob = await response.blob();
    const previousUrl = imageElement.dataset.objectUrl;
    if (previousUrl) URL.revokeObjectURL(previousUrl);
    const objectUrl = URL.createObjectURL(blob);
    imageElement.dataset.objectUrl = objectUrl;
    imageElement.src = objectUrl;
  } catch (_) {
    imageElement.removeAttribute("src");
  }
}

function initTheme() {
  const savedTheme = localStorage.getItem("CriminalDB_theme");
  const systemTheme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  document.documentElement.dataset.theme = savedTheme || systemTheme;
}

function toggleTheme() {
  const nextTheme = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = nextTheme;
  localStorage.setItem("CriminalDB_theme", nextTheme);
  updateThemeToggle();
}

function updateThemeToggle() {
  const toggle = document.getElementById("theme-toggle");
  if (!toggle) return;
  const isDark = document.documentElement.dataset.theme === "dark";
  toggle.innerHTML = isDark ? "&#9788;<span>Light mode</span>" : "&#9681;<span>Dark mode</span>";
  toggle.setAttribute("aria-label", isDark ? "Switch to light mode" : "Switch to dark mode");
  toggle.title = isDark ? "Switch to light mode" : "Switch to dark mode";
}

initTheme();

const Auth = {
  getToken() { return localStorage.getItem("CriminalDB_token"); },
  getUsername() { return localStorage.getItem("CriminalDB_username"); },
  getRole() { return localStorage.getItem("CriminalDB_role"); },

  setSession(token, username, role) {
    localStorage.setItem("CriminalDB_token", token);
    localStorage.setItem("CriminalDB_username", username);
    localStorage.setItem("CriminalDB_role", role);
  },

  clearSession() {
    localStorage.removeItem("CriminalDB_token");
    localStorage.removeItem("CriminalDB_username");
    localStorage.removeItem("CriminalDB_role");
  },

  isLoggedIn() { return !!this.getToken(); },

  isAdmin() { return this.getRole() === "ADMIN"; },
  canManageRecords() { return ["ADMIN", "OFFICER"].includes(this.getRole()); },

  requireLogin() {
    if (!this.isLoggedIn()) {
      window.location.href = "login.html";
    }
  },

  logout() {
    this.clearSession();
    window.location.href = "login.html";
  }
};

// Wraps fetch() to attach the bearer token and handle 401s uniformly.
async function apiFetch(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  const token = Auth.getToken();
  if (token) headers["Authorization"] = "Bearer " + token;

  const response = await fetch(apiUrl(path), { ...options, headers });

  if (response.status === 401) {
    Auth.logout();
    throw new Error("Session expired. Please log in again.");
  }
  return response;
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value == null ? "" : String(value);
  return div.innerHTML;
}

async function getApiError(response, fallback) {
  const body = await response.text();
  if (!body) return fallback;
  try {
    const parsed = JSON.parse(body);
    return parsed.message || parsed.error || fallback;
  } catch (_) {
    return body;
  }
}

function formatDate(isoString) {
  if (!isoString) return "-";
  const d = new Date(isoString);
  return d.toLocaleString();
}

function initTopbar(activePage) {
  // 1. Inject the shared page atmosphere
  const bgContainer = document.createElement("div");
  bgContainer.innerHTML = `
    <div class="cyber-bg">
      <div class="cyber-grid" aria-hidden="true"></div>
      <div class="hologram-glow" aria-hidden="true"></div>
    </div>
    <div class="scanline-overlay" aria-hidden="true"></div>
  `;
  document.body.appendChild(bgContainer);

  // 2. Build Sidebar Navigation Links
  const isAdmin = Auth.isAdmin();
  let links = [
    { href: "dashboard.html", label: "Dashboard", icon: "<svg viewBox='0 0 24 24' aria-hidden='true'><rect x='3' y='3' width='7' height='7'/><rect x='14' y='3' width='7' height='7'/><rect x='3' y='14' width='7' height='7'/><rect x='14' y='14' width='7' height='7'/></svg>" },
    { href: "view-persons.html", label: "View Records", icon: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M3 5h18M3 12h18M3 19h18'/></svg>" },
    { href: "face-search.html", label: "Face Search", icon: "<svg viewBox='0 0 24 24' aria-hidden='true'><circle cx='10.5' cy='10.5' r='6.5'/><path d='m16 16 5 5'/></svg>" },
  ];
  if (Auth.canManageRecords()) {
    links.splice(2, 0, { href: "add-person.html", label: "Add Person", icon: "<svg viewBox='0 0 24 24' aria-hidden='true'><path d='M12 5v14M5 12h14'/></svg>" });
  }
  if (isAdmin) {
    links.push({ href: "audit-logs.html", label: "Audit Logs", icon: "≡" });
  }

  const sidebarHtml = `
    <div class="sidebar">
      <div class="brand-title">
        <img class="brand-logo" src="assets/shield-logo.svg" alt="CriminalDB shield">
        <span class="brand-name">CriminalDB</span>
      </div>
      <div class="sidebar-nav">
        ${links.map(l => `
          <a href="${l.href}" class="${l.href === activePage ? 'active' : ''}">
            <span>${l.icon}</span> ${l.label}
          </a>
        `).join("")}
      </div>
      <div class="sidebar-footer">
        <a href="#" id="logout-link" class="logout-link">
          <span aria-hidden="true">↪</span> Sign out
        </a>
      </div>
    </div>
  `;

  // 3. Build Top HUD Header
  const topHudHtml = `
    <div class="top-hud">
      <div class="hud-left">
        <div class="hud-status-dot"></div>
        <div class="hud-status-text">SECURED CRIMINAL DATABASE // OPERATOR: <span>${Auth.getUsername() || "UNKNOWN"}</span></div>
      </div>
      <div class="hud-right">
        <button type="button" id="theme-toggle" class="theme-toggle"></button>
        <div class="hud-time" id="hud-clock">00:00:00</div>
        <div class="hud-user">
          <div class="pill ${isAdmin ? 'admin' : 'officer'}">${Auth.getRole() || ""}</div>
          <div class="hud-avatar">${(Auth.getUsername() || "U").substring(0, 1).toUpperCase()}</div>
        </div>
      </div>
    </div>
  `;

  // 4. Rearrange DOM into app-wrapper
  const oldTopbar = document.querySelector(".topbar");
  if (oldTopbar) oldTopbar.remove();

  // Create main container wrapper
  const appWrapper = document.createElement("div");
  appWrapper.className = "app-wrapper";

  // Create main content area
  const mainContent = document.createElement("div");
  mainContent.className = "main-content";
  mainContent.innerHTML = topHudHtml;

  // Move container elements to mainContent
  const pageContainers = document.querySelectorAll(".container, .center-screen");
  pageContainers.forEach(container => {
    mainContent.appendChild(container);
  });

  // Build app-wrapper structure
  appWrapper.innerHTML = sidebarHtml;
  appWrapper.appendChild(mainContent);

  // Clear body and append wrapper
  document.body.innerHTML = "";
  document.body.appendChild(appWrapper);
  document.body.appendChild(bgContainer);

  // Setup logout listener
  document.getElementById("logout-link").addEventListener("click", (e) => {
    e.preventDefault();
    Auth.logout();
  });
  document.getElementById("theme-toggle").addEventListener("click", toggleTheme);
  updateThemeToggle();

  // Live clock ticking
  const updateClock = () => {
    const clockEl = document.getElementById("hud-clock");
    if (clockEl) {
      const now = new Date();
      clockEl.textContent = now.toTimeString().split(" ")[0];
    }
  };
  updateClock();
  setInterval(updateClock, 1000);
}
