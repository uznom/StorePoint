/**
 * Authentic StorePoint Material 3 Expressive Stylesheet.
 * Preserves StorePoint's signature Refined Electric Indigo (#2D55D6) & Deep Midnight Dark (#020617)
 * color system, POS tender channel accents (Cash #16A34A, GCash #007DFE, Maya #00B4D8, Load #7C3AED, Credit #D97706),
 * pill-to-squircle morphing buttons, and responsive split-pane POS & ERP layouts.
 */
export const STOREPOINT_M3E_APP_CSS = `
:root {
  --sp-primary: #2D55D6;
  --sp-on-primary: #FFFFFF;
  --sp-primary-container: #DEE6FF;
  --sp-on-primary-container: #16233F;
  --sp-secondary: #4D6DE8;
  --sp-secondary-container: #EAF0FF;
  --sp-on-secondary-container: #2B2A6B;
  --sp-tertiary: #7C3AED;
  --sp-tertiary-container: #F1E8FF;
  --sp-on-tertiary-container: #4A148C;
  --sp-bg: #F6F8FC;
  --sp-surface: #FFFFFF;
  --sp-surface-low: #F6F8FC;
  --sp-surface-container: #EEF2F8;
  --sp-surface-high: #E6EBF4;
  --sp-surface-highest: #DCE2EE;
  --sp-text: #0F172A;
  --sp-text-muted: #475569;
  --sp-outline: #B9C2D4;
  --sp-outline-variant: #DEE3EE;
  --sp-cash: #16A34A;
  --sp-cash-bg: #DCFCE7;
  --sp-gcash: #007DFE;
  --sp-gcash-bg: #E0F2FE;
  --sp-maya: #00B4D8;
  --sp-maya-bg: #CFFAFE;
  --sp-load: #7C3AED;
  --sp-load-bg: #F3E8FF;
  --sp-credit: #D97706;
  --sp-credit-bg: #FEF3C7;
  --sp-danger: #DC2626;
  --sp-danger-bg: #FEE2E2;
  --sp-spring: cubic-bezier(0.22, 1.25, 0.36, 1);
}

[data-theme="dark"] {
  --sp-primary: #819EFE;
  --sp-on-primary: #08122E;
  --sp-primary-container: #1E293B;
  --sp-on-primary-container: #E0E7FF;
  --sp-secondary: #A5B9FF;
  --sp-secondary-container: #172033;
  --sp-on-secondary-container: #F8FAFC;
  --sp-tertiary: #C4B5FD;
  --sp-tertiary-container: #3B0764;
  --sp-on-tertiary-container: #F3E8FF;
  --sp-bg: #020617;
  --sp-surface: #0B0F19;
  --sp-surface-low: #0B0F19;
  --sp-surface-container: #0F172A;
  --sp-surface-high: #1E293B;
  --sp-surface-highest: #334155;
  --sp-text: #F8FAFC;
  --sp-text-muted: #94A3B8;
  --sp-outline: #334155;
  --sp-outline-variant: #1E293B;
  --sp-cash-bg: #14532D;
  --sp-gcash-bg: #0C4A6E;
  --sp-maya-bg: #164E63;
  --sp-load-bg: #3B0764;
  --sp-credit-bg: #78350F;
  --sp-danger-bg: #7F1D1D;
}

* { box-sizing: border-box; }
body {
  margin: 0;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: var(--sp-bg);
  color: var(--sp-text);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* Kiosk Top Status Bar */
.sp-kiosk-bar {
  background: var(--sp-surface);
  border-bottom: 1px solid var(--sp-outline-variant);
  padding: 10px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  position: sticky;
  top: 0;
  z-index: 40;
}
.sp-brand { display: flex; align-items: center; gap: 12px; }
.sp-brand-logo {
  width: 40px; height: 40px; border-radius: 14px;
  background: linear-gradient(135deg, var(--sp-primary), var(--sp-tertiary));
  color: #fff; font-weight: 800; font-size: 18px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 12px rgba(45, 85, 214, 0.28);
}
.sp-brand-title { font-weight: 800; font-size: 17px; letter-spacing: -0.02em; }
.sp-brand-sub { font-size: 12px; color: var(--sp-text-muted); }

/* Animated Pill Navigation Bar */
.sp-nav-pills {
  display: flex; gap: 6px; background: var(--sp-surface-container);
  padding: 5px; border-radius: 999px; flex-wrap: wrap;
}
.sp-nav-btn {
  border: none; background: transparent; color: var(--sp-text-muted);
  padding: 8px 16px; border-radius: 999px; font-weight: 700; font-size: 13px;
  cursor: pointer; transition: all 0.22s var(--sp-spring);
  display: inline-flex; align-items: center; gap: 6px;
}
.sp-nav-btn:hover { color: var(--sp-text); background: var(--sp-surface-high); }
.sp-nav-btn.active {
  background: var(--sp-primary); color: var(--sp-on-primary);
  transform: scale(1.03);
  box-shadow: 0 3px 10px rgba(45, 85, 214, 0.25);
}

/* Expressive Morphing Buttons (Pill <-> Squircle on press) */
.sp-btn {
  border: none; border-radius: 999px; padding: 10px 18px;
  font-weight: 700; font-size: 13px; cursor: pointer;
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  transition: transform 0.18s var(--sp-spring), border-radius 0.18s var(--sp-spring), background 0.15s;
  min-height: 42px;
}
.sp-btn:active { transform: scale(0.95); border-radius: 12px; }
.sp-btn-primary { background: var(--sp-primary); color: var(--sp-on-primary); }
.sp-btn-tonal { background: var(--sp-primary-container); color: var(--sp-on-primary-container); }
.sp-btn-outline { background: transparent; color: var(--sp-text); border: 1.5px solid var(--sp-outline); }
.sp-btn-cash { background: var(--sp-cash); color: #fff; }
.sp-btn-gcash { background: var(--sp-gcash); color: #fff; }
.sp-btn-maya { background: var(--sp-maya); color: #fff; }
.sp-btn-load { background: var(--sp-load); color: #fff; }
.sp-btn-credit { background: var(--sp-credit); color: #fff; }
.sp-btn-danger { background: var(--sp-danger); color: #fff; }
.sp-btn-sm { min-height: 34px; padding: 6px 12px; font-size: 12px; }

/* Expressive Cards & Badges */
.sp-card {
  background: var(--sp-surface);
  border: 1px solid var(--sp-outline-variant);
  border-radius: 24px;
  padding: 18px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
}
.sp-card-tonal {
  background: var(--sp-surface-container);
  border-radius: 22px;
  padding: 16px;
}
.sp-badge {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 10px; border-radius: 999px; font-size: 11px; font-weight: 700;
}
.sp-badge-success { background: var(--sp-cash-bg); color: var(--sp-cash); }
.sp-badge-warn { background: var(--sp-credit-bg); color: var(--sp-credit); }
.sp-badge-danger { background: var(--sp-danger-bg); color: var(--sp-danger); }
.sp-badge-info { background: var(--sp-gcash-bg); color: var(--sp-gcash); }
.sp-badge-purple { background: var(--sp-load-bg); color: var(--sp-load); }

/* POS Split Layout */
.sp-pos-layout {
  display: grid;
  grid-template-columns: 1fr 410px;
  gap: 18px;
  padding: 18px 20px;
  flex: 1;
  align-items: start;
}
@media (max-width: 1024px) {
  .sp-pos-layout { grid-template-columns: 1fr; }
}
.sp-product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(225px, 1fr));
  gap: 14px;
  margin-top: 14px;
}
.sp-prod-card {
  background: var(--sp-surface);
  border: 1.5px solid var(--sp-outline-variant);
  border-radius: 22px;
  padding: 14px;
  display: flex; flex-direction: column; justify-content: space-between;
  gap: 10px;
  transition: transform 0.18s var(--sp-spring), border-color 0.18s;
}
.sp-prod-card:hover {
  transform: translateY(-2px);
  border-color: var(--sp-primary);
}
.sp-uom-pills { display: flex; flex-wrap: wrap; gap: 6px; }
.sp-uom-btn {
  border: 1px solid var(--sp-outline);
  background: var(--sp-surface-low);
  color: var(--sp-text);
  border-radius: 12px;
  padding: 6px 10px;
  font-size: 11px; font-weight: 700;
  cursor: pointer;
  transition: all 0.15s var(--sp-spring);
}
.sp-uom-btn:hover, .sp-uom-btn.active {
  background: var(--sp-primary-container);
  color: var(--sp-on-primary-container);
  border-color: var(--sp-primary);
}

/* Inputs & Modals */
.sp-input, .sp-select {
  width: 100%; padding: 11px 14px; border-radius: 16px;
  border: 1.5px solid var(--sp-outline);
  background: var(--sp-surface); color: var(--sp-text);
  font-size: 14px; font-weight: 500;
}
.sp-input:focus, .sp-select:focus {
  outline: none; border-color: var(--sp-primary);
}
.sp-modal-backdrop {
  position: fixed; inset: 0; background: rgba(2, 6, 23, 0.62);
  backdrop-filter: blur(4px);
  display: none; align-items: center; justify-content: center;
  z-index: 100; padding: 16px;
}
.sp-modal-backdrop.open { display: flex; }
.sp-modal {
  background: var(--sp-surface);
  border-radius: 28px;
  max-width: 560px; width: 100%;
  max-height: 90vh; overflow-y: auto;
  padding: 24px;
  border: 1px solid var(--sp-outline-variant);
  box-shadow: 0 20px 50px rgba(2, 6, 23, 0.35);
}
.sp-receipt-paper {
  font-family: 'Courier New', Courier, monospace;
  background: #fffdf7; color: #111;
  padding: 18px; border-radius: 12px;
  border: 1px dashed #94a3b8;
  white-space: pre-wrap; font-size: 12.5px; line-height: 1.45;
}
.sp-kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 14px; margin-bottom: 18px;
}
.sp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.sp-table th, .sp-table td {
  padding: 10px 12px; text-align: left;
  border-bottom: 1px solid var(--sp-outline-variant);
}
.sp-table th { font-weight: 700; color: var(--sp-text-muted); font-size: 11px; text-transform: uppercase; }
`;
