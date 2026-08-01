// Shared API helper for all pages.
// Change this if your backend runs somewhere other than localhost:8080.
const API_BASE = "http://localhost:8080/api";

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
  const headers = options.headers || {};
  const token = Auth.getToken();
  if (token) headers["Authorization"] = "Bearer " + token;

  const response = await fetch(API_BASE + path, { ...options, headers });

  if (response.status === 401) {
    Auth.logout();
    throw new Error("Session expired. Please log in again.");
  }
  return response;
}

function formatDate(isoString) {
  if (!isoString) return "-";
  const d = new Date(isoString);
  return d.toLocaleString();
}

function initTopbar(activePage) {
  // 1. Inject animated background
  const bgContainer = document.createElement("div");
  bgContainer.innerHTML = `
    <div class="cyber-bg">
      <div class="cyber-grid"></div>
      <div class="hologram-glow"></div>
    </div>
    <div class="scanline-overlay"></div>
  `;
  document.body.appendChild(bgContainer);

  // 2. Build Sidebar Navigation Links
  const isAdmin = Auth.isAdmin();
  let links = [
    { href: "dashboard.html", label: "Dashboard", icon: "📊" },
    { href: "view-persons.html", label: "View Records", icon: "📁" },
    { href: "add-person.html", label: "Add Person", icon: "👤" },
    { href: "face-search.html", label: "Face Search", icon: "🔍" },
  ];
  if (isAdmin) {
    links.push({ href: "audit-logs.html", label: "Audit Logs", icon: "📜" });
  }

  const sidebarHtml = `
    <div class="sidebar">
      <div class="brand-title">
        <span>🛡️</span> CriminalDB
      </div>
      <div class="sidebar-nav">
        ${links.map(l => `
          <a href="${l.href}" class="${l.href === activePage ? 'active' : ''}">
            <span>${l.icon}</span> ${l.label}
          </a>
        `).join("")}
      </div>
      <div class="sidebar-footer">
        <a href="#" id="logout-link" style="display:flex; align-items:center; gap:14px; padding:12px 16px; border-radius:12px; color:var(--danger); font-size:14px; font-weight:600; text-decoration:none; border:1px solid transparent; transition:var(--transition);" onmouseover="this.style.background='rgba(255,77,109,0.08)'; this.style.borderColor='rgba(255,77,109,0.2)';" onmouseout="this.style.background='transparent'; this.style.borderColor='transparent';">
          <span>🚪</span> Logout
        </a>
      </div>
    </div>
  `;

  // 3. Build Top HUD Header
  const topHudHtml = `
    <div class="top-hud">
      <div class="hud-left">
        <div class="hud-status-dot"></div>
        <div class="hud-status-text">SECURE NODE // OPERATOR: <span>${Auth.getUsername() || "UNKNOWN"}</span></div>
      </div>
      <div class="hud-right">
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
