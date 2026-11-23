/**
 * StorePoint v2 Material 3 Expressive Application Layout Builder.
 * Renders the 6 interactive StorePoint workspaces (Cashier POS, Digital & Float Hub,
 * Inventory & Bulk BOM Repackaging, Suppliers & POs / Overhead Calendar, Admin ERP & P&L,
 * and Customer Display & Barcode Labels) plus POS modals.
 */
export function buildStorePointAppBodyHtml(): string {
  return `
<!-- Top Kiosk & Navigation Bar -->
<header class="sp-kiosk-bar">
  <div class="sp-brand">
    <div class="sp-brand-logo">SP</div>
    <div>
      <div class="sp-brand-title" id="sp-store-title">StorePoint Sari-Sari & Mini-Mart</div>
      <div class="sp-brand-sub" id="sp-store-subtitle">Main Counter • Terminal 01 • 7/7 Microservices Online</div>
    </div>
  </div>

  <nav class="sp-nav-pills" role="tablist">
    <button class="sp-nav-btn active" onclick="SP.switchTab('pos')" id="tab-btn-pos">🛒 Cashier POS</button>
    <button class="sp-nav-btn" onclick="SP.switchTab('float')" id="tab-btn-float">📱 GCash / Float Hub</button>
    <button class="sp-nav-btn" onclick="SP.switchTab('inventory')" id="tab-btn-inventory">📦 Inventory & BOM</button>
    <button class="sp-nav-btn" onclick="SP.switchTab('purchasing')" id="tab-btn-purchasing">🚚 Suppliers & POs</button>
    <button class="sp-nav-btn" onclick="SP.switchTab('admin')" id="tab-btn-admin">📊 Admin ERP & P&L</button>
    <button class="sp-nav-btn" onclick="SP.switchTab('cfd')" id="tab-btn-cfd">🖥️ CFD & Labels</button>
  </nav>

  <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
    <select class="sp-select" style="width:auto; padding:7px 12px; font-size:12px; font-weight:700;" id="sp-pack-select" onchange="SP.switchCountryPack(this.value)">
      <option value="pack-ph-retail">🇵🇭 PH • ₱ PHP (12% VAT)</option>
      <option value="pack-us-general">🇺🇸 US • $ USD (8.25% Tax)</option>
      <option value="pack-in-kirana">🇮🇳 IN • ₹ INR (18% GST)</option>
      <option value="pack-ke-duka">🇰🇪 KE • KSh KES (16% VAT)</option>
      <option value="pack-mx-abarrotes">🇲🇽 MX • Mex$ MXN (16% IVA)</option>
    </select>
    <button class="sp-btn sp-btn-tonal sp-btn-sm" onclick="SP.openPinModal()" id="sp-active-user-btn">👤 Jeff S. (ADMIN)</button>
    <button class="sp-btn sp-btn-outline sp-btn-sm" onclick="SP.toggleTheme()" title="Toggle Light/Dark M3 Expressive Theme">🌗 Theme</button>
  </div>
</header>

<!-- WORKSPACE 1: CASHIER POS TERMINAL -->
<main id="view-pos" class="sp-pos-layout">
  <section style="display:flex; flex-direction:column; gap:14px;">
    <div class="sp-card" style="padding:14px 18px; display:flex; align-items:center; justify-content:space-between; gap:12px; flex-wrap:wrap;">
      <div style="display:flex; gap:8px; flex:1; min-width:240px;">
        <input type="text" id="sp-pos-search" class="sp-input" placeholder="🔍 Scan barcode (e.g. 480001001) or search product / SKU..." oninput="SP.renderPosCatalog()" onkeydown="if(event.key==='Enter') SP.scanBarcodeInput(this.value)" />
        <button class="sp-btn sp-btn-primary sp-btn-sm" onclick="SP.scanBarcodeInput(document.getElementById('sp-pos-search').value)">Scan</button>
      </div>
      <div style="display:flex; gap:6px;">
        <button class="sp-btn sp-btn-tonal sp-btn-sm" onclick="SP.switchTab('float')">⚡ GCash / Maya / Load</button>
        <button class="sp-btn sp-btn-outline sp-btn-sm" onclick="SP.openReturnModal()">🔁 Lookup / Return</button>
      </div>
    </div>

    <div id="sp-pos-categories" style="display:flex; gap:8px; flex-wrap:wrap;"></div>
    <div id="sp-pos-product-grid" class="sp-product-grid"></div>
  </section>

  <!-- Right Column: Active Shift Bar + Cart + Tender Actions -->
  <aside class="sp-card" style="position:sticky; top:76px; display:flex; flex-direction:column; gap:14px;">
    <div class="sp-card-tonal" style="display:flex; align-items:center; justify-content:space-between; gap:8px; flex-wrap:wrap;">
      <div>
        <div style="font-size:11px; font-weight:700; color:var(--sp-text-muted);" id="sp-shift-label">SHIFT ACTIVE • SHF-2026-01</div>
        <div style="font-size:15px; font-weight:800;" id="sp-drawer-expected">Drawer Cash: ₱3,335.00</div>
      </div>
      <div style="display:flex; gap:6px;">
        <button class="sp-btn sp-btn-outline sp-btn-sm" onclick="SP.openPettyCashModal()">💵 In/Out</button>
        <button class="sp-btn sp-btn-danger sp-btn-sm" onclick="SP.openBlindCloseModal()">🔒 End Shift</button>
      </div>
    </div>

    <div style="display:flex; align-items:center; justify-content:space-between;">
      <h3 style="margin:0; font-size:16px;">🛒 Current Basket <span class="sp-badge sp-badge-info" id="sp-cart-count">0 items</span></h3>
      <div style="display:flex; gap:6px;">
        <button class="sp-btn sp-btn-tonal sp-btn-sm" onclick="SP.parkCurrentCart()" id="sp-park-btn">⏸️ Hold</button>
        <button class="sp-btn sp-btn-outline sp-btn-sm" onclick="SP.clearCart()">🗑️ Clear</button>
      </div>
    </div>

    <div id="sp-parked-banner"></div>
    <div id="sp-cart-list" style="display:flex; flex-direction:column; gap:10px; max-height:310px; overflow-y:auto;"></div>

    <div class="sp-card-tonal" style="display:flex; flex-direction:column; gap:6px; font-size:13px;">
      <div style="display:flex; justify-content:space-between;"><span>Subtotal</span><strong id="sp-cart-subtotal">₱0.00</strong></div>
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <span>Discount</span>
        <input type="number" id="sp-cart-discount" class="sp-input" style="width:95px; padding:4px 8px; text-align:right;" value="0" min="0" oninput="SP.renderCart()" />
      </div>
      <div style="display:flex; justify-content:space-between;"><span id="sp-tax-label">Net VATable Sales</span><span id="sp-cart-net">₱0.00</span></div>
      <div style="display:flex; justify-content:space-between;"><span id="sp-vat-rate-label">12% VAT</span><span id="sp-cart-tax">₱0.00</span></div>
      <div style="display:flex; justify-content:space-between; font-size:20px; font-weight:800; padding-top:6px; border-top:1px solid var(--sp-outline);">
        <span>TOTAL DUE</span><span id="sp-cart-total" style="color:var(--sp-primary);">₱0.00</span>
      </div>
    </div>

    <div style="display:grid; grid-template-columns:1fr 1fr; gap:8px;">
      <button class="sp-btn sp-btn-cash" onclick="SP.openCheckoutModal('CASH')">💵 Cash Checkout</button>
      <button class="sp-btn sp-btn-gcash" onclick="SP.openCheckoutModal('GCASH')">💙 GCash</button>
      <button class="sp-btn sp-btn-maya" onclick="SP.openCheckoutModal('MAYA')">🩵 Maya / Card</button>
      <button class="sp-btn sp-btn-credit" onclick="SP.openCheckoutModal('STORE_CREDIT')">📒 Charge Utang / AR</button>
    </div>
  </aside>
</main>

<!-- WORKSPACE 2: DIGITAL SERVICES & FLOAT LEDGER HUB -->
<main id="view-float" style="display:none; padding:20px; max-width:1360px; margin:0 auto; width:100%;">
  <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:12px; margin-bottom:16px;">
    <div>
      <h2 style="margin:0;">📱 Digital Financial Services & Auditable Float Ledgers</h2>
      <p style="margin:4px 0 0; color:var(--sp-text-muted); font-size:13px;">GCash Cash-In/Out, Maya Transfers, and Smart/Globe Prepaid Load with automated 2% retailer rebates and shift reconciliation.</p>
    </div>
  </div>
  <div id="sp-float-wallets-grid" class="sp-kpi-grid"></div>

  <div style="display:grid; grid-template-columns: 380px 1fr; gap:18px; align-items:start;">
    <div class="sp-card">
      <h3 style="margin-top:0;">⚡ Record Digital Service Transaction</h3>
      <div style="display:flex; flex-direction:column; gap:10px;">
        <label style="font-size:12px; font-weight:700;">Wallet / Float Account</label>
        <select id="sp-flt-wallet" class="sp-select"></select>
        <label style="font-size:12px; font-weight:700;">Transaction Type</label>
        <select id="sp-flt-type" class="sp-select">
          <option value="CUSTOMER_CASH_IN">Customer Cash-In (Send Digital Float, Collect Cash)</option>
          <option value="CUSTOMER_CASH_OUT">Customer Cash-Out (Receive Digital Float, Payout Cash)</option>
          <option value="TOPUP_SALE">Prepaid Airtime / E-Load Sale (Auto +2% Retailer Rebate)</option>
          <option value="OWNER_RELOAD">Owner Float Reload / Top-Up Wallet</option>
        </select>
        <label style="font-size:12px; font-weight:700;">Principal Amount</label>
        <input type="number" id="sp-flt-amount" class="sp-input" value="500" min="1" />
        <label style="font-size:12px; font-weight:700;">Customer Service Fee Collected</label>
        <input type="number" id="sp-flt-fee" class="sp-input" value="10" min="0" />
        <label style="font-size:12px; font-weight:700;">Customer Mobile / Reference No.</label>
        <input type="text" id="sp-flt-ref" class="sp-input" placeholder="e.g. 0917-555-0199 or Ref #99182" />
        <button class="sp-btn sp-btn-primary" onclick="SP.submitFloatTransaction()">Post to Auditable Float Ledger</button>
      </div>
    </div>
    <div class="sp-card">
      <h3 style="margin-top:0;">📜 Immutable Wallet Float Audit Ledger</h3>
      <table class="sp-table" id="sp-float-ledger-table"></table>
    </div>
  </div>
</main>

<!-- WORKSPACE 3: INVENTORY PORTAL, PRICE CHECKER, STOCKTAKE & BULK BOM REPACKAGING -->
<main id="view-inventory" style="display:none; padding:20px; max-width:1360px; margin:0 auto; width:100%;">
  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:18px; margin-bottom:18px;">
    <div class="sp-card">
      <h3 style="margin-top:0;">⚙️ Bulk-to-Retail Repackaging BOM Engine</h3>
      <p style="font-size:12.5px; color:var(--sp-text-muted); margin-top:0;">Convert bulk sacks/containers into retail packs with atomic raw deduction and exact Weighted Average Cost (WAC) valuation.</p>
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:10px;">
        <div>
          <label style="font-size:11px; font-weight:700;">Raw Material Bulk SKU</label>
          <select id="sp-bom-raw" class="sp-select"></select>
        </div>
        <div>
          <label style="font-size:11px; font-weight:700;">Target Retail Pack SKU</label>
          <select id="sp-bom-target" class="sp-select"></select>
        </div>
        <div>
          <label style="font-size:11px; font-weight:700;">Raw Base Units to Consume (e.g. 50 kg)</label>
          <input type="number" id="sp-bom-raw-qty" class="sp-input" value="50" min="1" />
        </div>
        <div>
          <label style="font-size:11px; font-weight:700;">Retail Units Produced (e.g. 50 bags)</label>
          <input type="number" id="sp-bom-out-qty" class="sp-input" value="50" min="1" />
        </div>
      </div>
      <div style="margin-top:12px; display:flex; justify-content:space-between; align-items:center;">
        <span style="font-size:12px; color:var(--sp-text-muted);">Packaging Bag Cost: <strong>1.00 / unit</strong></span>
        <button class="sp-btn sp-btn-primary" onclick="SP.executeBomConversion()">⚙️ Execute Atomic BOM Repackaging</button>
      </div>
    </div>

    <div class="sp-card">
      <h3 style="margin-top:0;">🏷️ Rapid Price Checker & New Multi-UOM Product</h3>
      <div style="display:flex; gap:8px; margin-bottom:12px;">
        <input type="text" id="sp-price-check-input" class="sp-input" placeholder="Scan or enter barcode (e.g. 480002010) to verify price..." oninput="SP.runPriceChecker(this.value)" />
      </div>
      <div id="sp-price-check-result" class="sp-card-tonal" style="min-height:86px; display:flex; align-items:center; justify-content:center; font-size:13px; color:var(--sp-text-muted);">
        Type or scan any barcode above to view all Multi-UOM tier prices & stock on hand.
      </div>
      <div style="margin-top:12px; display:flex; justify-content:flex-end;">
        <button class="sp-btn sp-btn-tonal sp-btn-sm" onclick="SP.openNewProductModal()">➕ Add New Multi-UOM Product</button>
      </div>
    </div>
  </div>

  <div class="sp-card" style="margin-bottom:18px;">
    <h3 style="margin-top:0;">📦 Multi-UOM Product Catalog, WAC Valuation & Physical Stocktake Audit</h3>
    <table class="sp-table" id="sp-inventory-table"></table>
  </div>

  <div class="sp-card">
    <h3 style="margin-top:0;">📜 Immutable Stock Movement Ledger</h3>
    <table class="sp-table" id="sp-stock-ledger-table"></table>
  </div>
</main>

<!-- WORKSPACE 4: SUPPLIERS, PURCHASE ORDERS & OVERHEAD CALENDAR -->
<main id="view-purchasing" style="display:none; padding:20px; max-width:1360px; margin:0 auto; width:100%;">
  <div style="display:grid; grid-template-columns: 1.2fr 1fr; gap:18px;">
    <div class="sp-card">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; flex-wrap:wrap; gap:8px;">
        <h3 style="margin:0;">🚚 Purchase Orders & Landed Cost Receiving</h3>
        <button class="sp-btn sp-btn-tonal sp-btn-sm" onclick="SP.generateSmartReorderPo()">✨ Auto-Generate Low-Stock Draft PO</button>
      </div>
      <table class="sp-table" id="sp-po-table"></table>
    </div>

    <div class="sp-card">
      <h3 style="margin-top:0;">🗓️ Store Overhead & Accounts Payable Calendar</h3>
      <div id="sp-overhead-list" style="display:flex; flex-direction:column; gap:10px;"></div>
    </div>
  </div>
</main>

<!-- WORKSPACE 5: ADMIN ERP, DOUBLE-ENTRY P&L, CUSTOMER CREDIT (AR) & V1 MIGRATION -->
<main id="view-admin" style="display:none; padding:20px; max-width:1360px; margin:0 auto; width:100%;">
  <div id="sp-admin-kpis" class="sp-kpi-grid"></div>

  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:18px; margin-bottom:18px;">
    <div class="sp-card">
      <h3 style="margin-top:0;">📒 Customer Credit (Utang / Accounts Receivable) Ledger</h3>
      <table class="sp-table" id="sp-customers-ar-table"></table>
    </div>
    <div class="sp-card">
      <h3 style="margin-top:0;">🧾 Recent Receipts & Item-Level Returns / Refunds</h3>
      <table class="sp-table" id="sp-transactions-table"></table>
    </div>
  </div>

  <div class="sp-card">
    <h3 style="margin-top:0;">⚖️ Double-Entry General Ledger (Auto-Posted from Sales, COGS, VAT & Overheads)</h3>
    <table class="sp-table" id="sp-gl-table"></table>
  </div>
</main>

<!-- WORKSPACE 6: CUSTOMER-FACING DISPLAY (CFD) & PRINTABLE BARCODE SHELF LABELS -->
<main id="view-cfd" style="display:none; padding:20px; max-width:1360px; margin:0 auto; width:100%;">
  <div style="display:grid; grid-template-columns: 420px 1fr; gap:18px;">
    <div class="sp-card" id="sp-cfd-live-card"></div>
    <div class="sp-card">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
        <h3 style="margin:0;">🏷️ Printable Multi-UOM Barcode Shelf Price Tags</h3>
        <button class="sp-btn sp-btn-primary sp-btn-sm" onclick="window.print()">🖨️ Print Label Sheet</button>
      </div>
      <div id="sp-shelf-labels-grid" style="display:grid; grid-template-columns:repeat(auto-fill, minmax(200px, 1fr)); gap:12px;"></div>
    </div>
  </div>
</main>

<!-- MODAL: CHECKOUT / RECEIPT / BLIND SHIFT CLOSE / PIN SWITCH -->
<div class="sp-modal-backdrop" id="sp-modal-backdrop">
  <div class="sp-modal" id="sp-modal-content"></div>
</div>
`;
}
