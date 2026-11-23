/**
 * Interactive Client Runtime for StorePoint v2.
 * Powers the Cashier POS cart, Multi-UOM variant selector, Park/Resume cart, Multi-Tender Checkout,
 * 58mm/80mm ESC/POS Thermal Receipt & RJ11 Drawer Kick preview, Item Returns, Digital Float Ledger,
 * Bulk Repackaging BOM Engine, Purchase Order Landed-Cost Receiving, Customer AR Credit Tabs,
 * Double-Entry General Ledger P&L, and Country Pack switching.
 */
export const STOREPOINT_CLIENT_RUNTIME_SCRIPT = `
(function() {
  const S = JSON.parse(JSON.stringify(window.STOREPOINT_DEFAULT_STATE));
  let selectedCategoryId = "ALL";

  const COUNTRY_PRESETS = {
    "pack-ph-retail": { storeName: "StorePoint Sari-Sari & Mini-Mart", currencyCode: "PHP", symbol: "₱", taxRate: 12, mode: "INCLUSIVE", tin: "123-456-789-000 BIR VAT", rateMul: 1 },
    "pack-us-general": { storeName: "StorePoint Neighborhood Market & Deli", currencyCode: "USD", symbol: "$", taxRate: 8.25, mode: "EXCLUSIVE", tin: "EIN 94-3829101", rateMul: 0.04 },
    "pack-in-kirana": { storeName: "StorePoint Kirana & Supermart", currencyCode: "INR", symbol: "₹", taxRate: 18, mode: "EXCLUSIVE", tin: "GSTIN 27AABCU9603R1ZM", rateMul: 1.5 },
    "pack-ke-duka": { storeName: "StorePoint Duka & M-Pesa Center", currencyCode: "KES", symbol: "KSh", taxRate: 16, mode: "INCLUSIVE", tin: "KRA PIN P051293841Q", rateMul: 2.3 },
    "pack-mx-abarrotes": { storeName: "StorePoint Tienda de Abarrotes", currencyCode: "MXN", symbol: "Mex$", taxRate: 16, mode: "INCLUSIVE", tin: "RFC STA261123AB1", rateMul: 0.35 }
  };

  function fmt(n) {
    const val = Number(n || 0);
    return S.config.currencySymbol + val.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function calcCartTotals() {
    const subtotal = S.cart.reduce((sum, i) => sum + (i.price * i.qty - (i.discount || 0)), 0);
    const cartDisc = Math.max(0, Number(document.getElementById("sp-cart-discount")?.value || 0));
    const afterDisc = Math.max(0, subtotal - cartDisc);
    const rate = S.config.taxRatePercent / 100;
    let net = afterDisc, tax = 0, total = afterDisc;
    if (S.config.taxMode === "INCLUSIVE") {
      net = afterDisc / (1 + rate);
      tax = afterDisc - net;
      total = afterDisc;
    } else {
      net = afterDisc;
      tax = afterDisc * rate;
      total = net + tax;
    }
    return { subtotal, cartDisc, net, tax, total };
  }

  function expectedDrawerCash() {
    return S.shift.openingFloat + S.shift.cashSales + S.shift.pettyCashIn - S.shift.pettyCashOut - S.shift.cashRefunds;
  }

  const SP = {
    switchTab(tabId) {
      ["pos", "float", "inventory", "purchasing", "admin", "cfd"].forEach(id => {
        const view = document.getElementById("view-" + id);
        const btn = document.getElementById("tab-btn-" + id);
        if (view) view.style.display = (id === tabId) ? (id === "pos" ? "grid" : "block") : "none";
        if (btn) btn.classList.toggle("active", id === tabId);
      });
      this.renderAll();
    },

    toggleTheme() {
      const html = document.documentElement;
      html.setAttribute("data-theme", html.getAttribute("data-theme") === "dark" ? "light" : "dark");
    },

    switchCountryPack(packId) {
      const preset = COUNTRY_PRESETS[packId];
      if (!preset) return;
      const prevPreset = COUNTRY_PRESETS[S.config.activeCountryPack] || COUNTRY_PRESETS["pack-ph-retail"];
      const ratio = preset.rateMul / prevPreset.rateMul;
      S.config.activeCountryPack = packId;
      S.config.storeName = preset.storeName;
      S.config.currencyCode = preset.currencyCode;
      S.config.currencySymbol = preset.symbol;
      S.config.taxRatePercent = preset.taxRate;
      S.config.taxMode = preset.mode;
      S.config.tin = preset.tin;
      S.products.forEach(p => {
        p.costBase = +(p.costBase * ratio).toFixed(2);
        p.uoms.forEach(u => { u.price = +(u.price * ratio).toFixed(2); });
      });
      S.cart.forEach(c => { c.price = +(c.price * ratio).toFixed(2); });
      this.renderAll();
    },

    selectCategory(catId) {
      selectedCategoryId = catId;
      this.renderPosCatalog();
    },

    addToCart(productId, uomCode) {
      const prod = S.products.find(p => p.id === productId);
      if (!prod) return;
      const uom = prod.uoms.find(u => u.code === uomCode) || prod.uoms[0];
      const existing = S.cart.find(c => c.productId === productId && c.uomCode === uom.code);
      const currentBaseInCart = S.cart.filter(c => c.productId === productId).reduce((s, c) => s + c.qty * c.factor, 0);
      if (currentBaseInCart + uom.factor > prod.stockBase) {
        alert("Insufficient stock on hand (" + prod.stockBase + " " + prod.baseUom + " available)");
        return;
      }
      if (existing) {
        existing.qty += 1;
      } else {
        S.cart.push({ productId: prod.id, name: prod.name, uomCode: uom.code, uomName: uom.name, factor: uom.factor, qty: 1, price: uom.price, discount: 0 });
      }
      this.renderAll();
    },

    updateCartQty(idx, delta) {
      const item = S.cart[idx];
      if (!item) return;
      const next = item.qty + delta;
      if (next <= 0) S.cart.splice(idx, 1);
      else item.qty = next;
      this.renderAll();
    },

    clearCart() {
      S.cart = [];
      this.renderAll();
    },

    parkCurrentCart() {
      if (S.cart.length === 0) return;
      const note = prompt("Enter customer name or note for held cart:", "Walk-in Customer") || "Held Order";
      S.parkedCarts.push({ id: "PRK-" + (S.parkedCarts.length + 1), note, time: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }), items: S.cart });
      S.cart = [];
      this.renderAll();
    },

    resumeParkedCart(idx) {
      const parked = S.parkedCarts[idx];
      if (!parked) return;
      S.cart = parked.items;
      S.parkedCarts.splice(idx, 1);
      this.renderAll();
    },

    scanBarcodeInput(raw) {
      const code = (raw || "").trim();
      if (!code) return;
      for (const p of S.products) {
        const matchedUom = p.uoms.find(u => u.barcode === code || p.sku.toLowerCase() === code.toLowerCase());
        if (matchedUom) {
          this.addToCart(p.id, matchedUom.code);
          document.getElementById("sp-pos-search").value = "";
          this.renderPosCatalog();
          return;
        }
      }
      alert("No product or UOM barcode matched: " + code);
    },

    openModal(html) {
      document.getElementById("sp-modal-content").innerHTML = html;
      document.getElementById("sp-modal-backdrop").classList.add("open");
    },

    closeModal() {
      document.getElementById("sp-modal-backdrop").classList.remove("open");
    },

    openCheckoutModal(tender) {
      if (S.cart.length === 0) { alert("Basket is empty. Click any product UOM to add items."); return; }
      const t = calcCartTotals();
      const custOptions = S.customers.map(c => "<option value='" + c.id + "'>" + c.name + " (Avail Credit: " + fmt(c.creditLimit - c.balance) + ")</option>").join("");
      this.openModal(
        "<h3 style='margin-top:0;'>💳 Complete Sale — " + tender + "</h3>" +
        "<div class='sp-card-tonal' style='margin-bottom:14px; display:flex; justify-content:space-between; align-items:center;'>" +
        "<span>Total Amount Due</span><strong style='font-size:22px; color:var(--sp-primary);'>" + fmt(t.total) + "</strong></div>" +
        (tender === "STORE_CREDIT"
          ? "<label style='font-size:12px; font-weight:700;'>Charge to Customer Account (Utang / AR Ledger)</label><select id='sp-chk-customer' class='sp-select' style='margin:8px 0 14px;'>" + custOptions + "</select>"
          : "<label style='font-size:12px; font-weight:700;'>Amount Tendered</label><input type='number' id='sp-chk-paid' class='sp-input' style='margin:8px 0 14px; font-size:18px; font-weight:800;' value='" + Math.ceil(t.total) + "' />") +
        "<div style='display:flex; justify-content:flex-end; gap:8px;'>" +
        "<button class='sp-btn sp-btn-outline' onclick='SP.closeModal()'>Cancel</button>" +
        "<button class='sp-btn sp-btn-primary' onclick=\"SP.confirmSale('" + tender + "')\">✅ Confirm & Print 58mm Receipt</button></div>"
      );
    },

    confirmSale(tender) {
      const t = calcCartTotals();
      let paid = t.total;
      let customerName = "Walk-in Customer";
      if (tender === "STORE_CREDIT") {
        const custId = document.getElementById("sp-chk-customer").value;
        const cust = S.customers.find(c => c.id === custId);
        if (!cust || cust.balance + t.total > cust.creditLimit) {
          alert("Credit limit exceeded for " + (cust ? cust.name : "customer") + "!");
          return;
        }
        cust.balance += t.total;
        cust.points += Math.floor(t.total / 10);
        customerName = cust.name;
      } else {
        paid = Number(document.getElementById("sp-chk-paid")?.value || t.total);
        if (paid < t.total) { alert("Amount tendered is less than Total Due!"); return; }
      }
      const change = Math.max(0, paid - t.total);
      let cogs = 0;
      const saleItems = S.cart.map(c => {
        const prod = S.products.find(p => p.id === c.productId);
        const baseDeducted = c.qty * c.factor;
        const lineCost = (prod ? prod.costBase : 0) * baseDeducted;
        cogs += lineCost;
        if (prod) {
          prod.stockBase = Math.max(0, prod.stockBase - baseDeducted);
          S.stockMovements.unshift({ time: "Just now", product: prod.name, reason: "POS_SALE", delta: -baseDeducted, balance: prod.stockBase, wac: prod.costBase, ref: "OR-000" + (101 + S.transactions.length) });
        }
        return { productId: c.productId, name: c.name, uomName: c.uomName, factor: c.factor, qty: c.qty, price: c.price, cost: lineCost };
      });
      if (tender === "CASH") S.shift.cashSales += t.total;
      const orNum = "OR-000" + (101 + S.transactions.length);
      const tx = { id: orNum, time: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }), cashier: S.activeUser.displayName, customerName, tender, subtotal: t.subtotal, discount: t.cartDisc, netSales: t.net, tax: t.tax, total: t.total, cogs, cashPaid: paid, change, status: "COMPLETED", items: saleItems };
      S.transactions.unshift(tx);
      S.glJournals.unshift({
        id: "GL-" + orNum, time: tx.time, ref: orNum, desc: "POS " + tender + " Sale + Tax + WAC COGS",
        debitAccount: (tender === "STORE_CREDIT" ? "1100_AR" : "1000_CASH") + " (" + fmt(t.total) + ") / 5000_COGS (" + fmt(cogs) + ")",
        creditAccount: "4000_SALES (" + fmt(t.net) + ") / 2100_TAX (" + fmt(t.tax) + ") / 1200_INV (" + fmt(cogs) + ")",
        amount: t.total
      });
      S.cart = [];
      if (document.getElementById("sp-cart-discount")) document.getElementById("sp-cart-discount").value = "0";
      this.renderAll();
      this.showReceiptModal(tx.id);
    },

    showReceiptModal(txId) {
      const tx = S.transactions.find(t => t.id === txId) || S.transactions[0];
      if (!tx) return;
      const lines = [
        S.config.storeName.toUpperCase(),
        S.config.branchName,
        "TIN: " + S.config.tin,
        "--------------------------------",
        "RCPT: " + tx.id + "    " + tx.time,
        "CASHIER: " + tx.cashier,
        "CUSTOMER: " + tx.customerName,
        "--------------------------------"
      ];
      tx.items.forEach(i => {
        lines.push(i.name + " (" + i.uomName + ")");
        lines.push("  " + i.qty + " x " + fmt(i.price) + " = " + fmt(i.qty * i.price));
      });
      lines.push(
        "--------------------------------",
        "NET SALES:      " + fmt(tx.netSales),
        "TAX (" + S.config.taxRatePercent + "%):       " + fmt(tx.tax),
        "TOTAL:          " + fmt(tx.total),
        "TENDER (" + tx.tender + "):  " + fmt(tx.cashPaid),
        "CHANGE:         " + fmt(tx.change),
        "--------------------------------",
        "ESC/POS 58mm • RJ11 Drawer Pulse: 1B700019FA",
        "Thank you for shopping at StorePoint!"
      );
      this.openModal(
        "<h3 style='margin-top:0;'>🖨️ Thermal Receipt — " + tx.id + "</h3>" +
        "<div class='sp-receipt-paper'>" + lines.join("\\n") + "</div>" +
        "<div style='display:flex; justify-content:space-between; margin-top:14px; flex-wrap:wrap; gap:8px;'>" +
        "<button class='sp-btn sp-btn-danger sp-btn-sm' onclick=\"SP.refundTransaction('" + tx.id + "')\">🔁 Return / Refund Receipt</button>" +
        "<div style='display:flex; gap:8px;'>" +
        "<button class='sp-btn sp-btn-tonal sp-btn-sm' onclick='window.print()'>🖨️ Print 58mm</button>" +
        "<button class='sp-btn sp-btn-primary sp-btn-sm' onclick='SP.closeModal()'>Done</button></div></div>"
      );
    },

    openReturnModal() {
      if (S.transactions.length === 0) return;
      this.showReceiptModal(S.transactions[0].id);
    },

    refundTransaction(txId) {
      const tx = S.transactions.find(t => t.id === txId);
      if (!tx || tx.status === "REFUNDED") { alert("Receipt already refunded."); return; }
      const restock = confirm("Restock returned items back into active warehouse inventory? (Cancel = Write off as Damaged/Shrinkage)");
      tx.status = "REFUNDED";
      if (tx.tender === "CASH") S.shift.cashRefunds += tx.total;
      if (restock) {
        tx.items.forEach(i => {
          const prod = S.products.find(p => p.id === i.productId);
          if (prod) {
            const restored = i.qty * i.factor;
            prod.stockBase += restored;
            S.stockMovements.unshift({ time: "Just now", product: prod.name, reason: "POS_RETURN_RESTOCK", delta: restored, balance: prod.stockBase, wac: prod.costBase, ref: tx.id });
          }
        });
      }
      this.closeModal();
      this.renderAll();
    },

    openPettyCashModal() {
      const amt = Number(prompt("Enter Petty Cash Out amount (or negative for Cash-In):", "100") || 0);
      if (!amt) return;
      const reason = prompt("Reason for cash drawer movement:", "Store supplies / Ice delivery") || "Petty Cash";
      if (amt > 0) S.shift.pettyCashOut += amt;
      else S.shift.pettyCashIn += Math.abs(amt);
      S.shift.drawerLogs.unshift({ time: "Just now", type: amt > 0 ? "CASH_OUT" : "CASH_IN", amount: -amt, reason });
      this.renderAll();
    },

    openBlindCloseModal() {
      const exp = expectedDrawerCash();
      this.openModal(
        "<h3 style='margin-top:0;'>🔒 Shift Handover & Blind Cash Count Reconciliation</h3>" +
        "<p style='font-size:13px; color:var(--sp-text-muted);'>Enter physical cash counted in drawer to compute variance and print Z-Reading slip.</p>" +
        "<label style='font-size:12px; font-weight:700;'>Physical Counted Cash in Drawer</label>" +
        "<input type='number' id='sp-blind-counted' class='sp-input' style='margin:8px 0 14px; font-size:18px; font-weight:800;' value='" + exp + "' />" +
        "<div style='display:flex; justify-content:flex-end; gap:8px;'>" +
        "<button class='sp-btn sp-btn-outline' onclick='SP.closeModal()'>Cancel</button>" +
        "<button class='sp-btn sp-btn-primary' onclick='SP.confirmBlindClose()'>Print Shift Z-Reading Slip</button></div>"
      );
    },

    confirmBlindClose() {
      const counted = Number(document.getElementById("sp-blind-counted")?.value || 0);
      const exp = expectedDrawerCash();
      const variance = counted - exp;
      this.openModal(
        "<h3 style='margin-top:0;'>🧾 Shift Z-Reconciliation Slip</h3>" +
        "<div class='sp-receipt-paper'>" +
        "SHIFT Z-RECONCILIATION REPORT\\n" +
        "================================\\n" +
        "SHIFT ID:       " + S.shift.id + "\\n" +
        "CASHIER:        " + S.activeUser.displayName + "\\n" +
        "OPENING FLOAT:  " + fmt(S.shift.openingFloat) + "\\n" +
        "CASH SALES:     " + fmt(S.shift.cashSales) + "\\n" +
        "PETTY CASH OUT: -" + fmt(S.shift.pettyCashOut) + "\\n" +
        "CASH REFUNDS:   -" + fmt(S.shift.cashRefunds) + "\\n" +
        "--------------------------------\\n" +
        "EXPECTED CASH:  " + fmt(exp) + "\\n" +
        "COUNTED CASH:   " + fmt(counted) + "\\n" +
        "VARIANCE:       " + fmt(variance) + " (" + (variance === 0 ? "EXACT MATCH" : (variance > 0 ? "OVER" : "SHORT")) + ")\\n" +
        "================================" +
        "</div><div style='margin-top:14px; display:flex; justify-content:flex-end;'><button class='sp-btn sp-btn-primary' onclick='SP.closeModal()'>Close</button></div>"
      );
    },

    openPinModal() {
      const userBtns = S.users.map(u =>
        "<button class='sp-btn sp-btn-tonal' style='width:100%; justify-content:space-between; margin-bottom:8px;' onclick=\"SP.switchUser('" + u.username + "')\">" +
        "<span>👤 " + u.displayName + "</span><span class='sp-badge sp-badge-info'>" + u.role + " • PIN " + u.pin + "</span></button>"
      ).join("");
      this.openModal(
        "<h3 style='margin-top:0;'>🔐 Fast Cashier & Role PIN Switcher</h3>" +
        "<p style='font-size:13px; color:var(--sp-text-muted);'>PBKDF2-HMAC-SHA256 (120,000 iterations) • Progressive per-account lockout enabled.</p>" +
        userBtns +
        "<div style='display:flex; justify-content:flex-end; margin-top:10px;'><button class='sp-btn sp-btn-outline' onclick='SP.closeModal()'>Close</button></div>"
      );
    },

    switchUser(username) {
      const u = S.users.find(x => x.username === username);
      if (u) S.activeUser = u;
      this.closeModal();
      this.renderAll();
    },

    submitFloatTransaction() {
      const walletCode = document.getElementById("sp-flt-wallet").value;
      const type = document.getElementById("sp-flt-type").value;
      const principal = Number(document.getElementById("sp-flt-amount").value || 0);
      const fee = Number(document.getElementById("sp-flt-fee").value || 0);
      const ref = document.getElementById("sp-flt-ref").value || "REF-" + Math.floor(Math.random() * 90000 + 10000);
      const w = S.floatWallets.find(x => x.code === walletCode);
      if (!w || principal <= 0) return;

      const rebate = type === "TOPUP_SALE" ? +(principal * (w.commissionBps / 10000)).toFixed(2) : 0;
      const delta = (type === "OWNER_RELOAD" || type === "CUSTOMER_CASH_OUT") ? principal : -(principal - rebate);
      if (w.balance + delta < 0) { alert("Insufficient " + w.name + " float balance!"); return; }
      w.balance = +(w.balance + delta).toFixed(2);
      S.floatLedger.unshift({ id: "FL-" + (101 + S.floatLedger.length), time: "Just now", wallet: w.code, type, principal, delta, fee: type === "OWNER_RELOAD" ? 0 : fee, rebate, balanceAfter: w.balance, ref });
      if (fee > 0 || rebate > 0) {
        S.glJournals.unshift({ id: "GL-FLT-" + S.floatLedger.length, time: "Just now", ref, desc: w.code + " Service Fee & Rebate Income", debitAccount: "1000_CASH / 1300_FLOAT (" + fmt(fee + rebate) + ")", creditAccount: "4100_DIGITAL_SERVICE_INCOME (" + fmt(fee + rebate) + ")", amount: fee + rebate });
      }
      this.renderAll();
    },

    executeBomConversion() {
      const rawId = document.getElementById("sp-bom-raw").value;
      const targetId = document.getElementById("sp-bom-target").value;
      const rawQty = Number(document.getElementById("sp-bom-raw-qty").value || 0);
      const outQty = Number(document.getElementById("sp-bom-out-qty").value || 0);
      const rawProd = S.products.find(p => p.id === rawId);
      const targetProd = S.products.find(p => p.id === targetId);
      if (!rawProd || !targetProd || rawQty <= 0 || outQty <= 0 || rawId === targetId) return;
      if (rawProd.stockBase < rawQty) { alert("Insufficient raw material stock (" + rawProd.stockBase + " available)!"); return; }

      const totalRawCost = rawQty * rawProd.costBase + outQty * 1.0;
      const derivedUnitCost = +(totalRawCost / outQty).toFixed(2);
      rawProd.stockBase -= rawQty;
      const oldTotalVal = targetProd.stockBase * targetProd.costBase;
      targetProd.stockBase += outQty;
      targetProd.costBase = +((oldTotalVal + totalRawCost) / targetProd.stockBase).toFixed(2);

      const bomId = "BOM-2026-0" + (S.bomLogs.length + 1);
      S.bomLogs.unshift({ id: bomId, time: "Just now", rawName: rawProd.name, rawUsedKg: rawQty, targetName: targetProd.name, producedBags: outQty, bagCost: 1.0, derivedUnitCogs: derivedUnitCost });
      S.stockMovements.unshift({ time: "Just now", product: targetProd.name, reason: "BOM_PRODUCE", delta: outQty, balance: targetProd.stockBase, wac: targetProd.costBase, ref: bomId });
      S.stockMovements.unshift({ time: "Just now", product: rawProd.name, reason: "BOM_CONSUME", delta: -rawQty, balance: rawProd.stockBase, wac: rawProd.costBase, ref: bomId });
      this.renderAll();
      alert("BOM Repackaging Complete! Produced " + outQty + " units of " + targetProd.name + " at exact derived COGS of " + fmt(derivedUnitCost) + "/unit.");
    },

    runPriceChecker(code) {
      const box = document.getElementById("sp-price-check-result");
      const q = (code || "").trim().toLowerCase();
      if (!q) { box.innerHTML = "Type or scan any barcode above to view all Multi-UOM tier prices & stock on hand."; return; }
      const prod = S.products.find(p => p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q) || p.uoms.some(u => u.barcode.includes(q)));
      if (!prod) { box.innerHTML = "<span style='color:var(--sp-danger); font-weight:700;'>No product matched: " + code + "</span>"; return; }
      box.innerHTML = "<div style='width:100%; display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px;'>" +
        "<div><strong style='font-size:15px;'>" + prod.name + "</strong><div style='font-size:12px;'>On Hand: " + prod.stockBase + " " + prod.baseUom + " • WAC: " + fmt(prod.costBase) + "</div></div>" +
        "<div style='display:flex; gap:6px; flex-wrap:wrap;'>" + prod.uoms.map(u => "<span class='sp-badge sp-badge-info'>" + u.name + ": " + fmt(u.price) + "</span>").join("") + "</div></div>";
    },

    adjustStocktake(productId) {
      const prod = S.products.find(p => p.id === productId);
      if (!prod) return;
      const counted = Number(prompt("Physical Stocktake Count for " + prod.name + " (System shows " + prod.stockBase + " " + prod.baseUom + "):", prod.stockBase));
      if (Number.isNaN(counted) || counted < 0) return;
      const delta = counted - prod.stockBase;
      if (delta === 0) return;
      prod.stockBase = counted;
      S.stockMovements.unshift({ time: "Just now", product: prod.name, reason: "STOCKTAKE_RECONCILIATION", delta, balance: counted, wac: prod.costBase, ref: "AUDIT" });
      this.renderAll();
    },

    openNewProductModal() {
      const name = prompt("New Product Name:", "Bear Brand Fortified Powdered Milk 33g");
      if (!name) return;
      const basePrice = Number(prompt("Single Sachet / Base Unit Selling Price:", "14") || 14);
      const packPrice = Number(prompt("Dozen Tie (12s) Selling Price:", "158") || 158);
      const stock = Number(prompt("Initial Stock (in base pieces):", "120") || 120);
      S.products.unshift({
        id: "prd_" + Date.now(), sku: "SKU-" + Math.floor(Math.random() * 9000 + 1000), name, categoryId: "cat_coffee", baseUom: "sachet", stockBase: stock, reorderPoint: 24, costBase: +(basePrice * 0.75).toFixed(2), expiryDate: "2027-12-31",
        uoms: [
          { code: "sachet", name: "1 Sachet (Tingi)", factor: 1, price: basePrice, barcode: "48099" + Math.floor(Math.random() * 900 + 100) },
          { code: "tie12", name: "Dozen Tie (12s)", factor: 12, price: packPrice, barcode: "48099" + Math.floor(Math.random() * 900 + 100) }
        ]
      });
      this.renderAll();
    },

    generateSmartReorderPo() {
      const low = S.products.filter(p => p.stockBase <= p.reorderPoint);
      const target = low[0] || S.products[0];
      const poId = "PO-00" + (101 + S.purchaseOrders.length);
      S.purchaseOrders.unshift({
        id: poId, supplierId: "sup_2", supplierName: "Metro FMCG Wholesale Distributors",
        productId: target.id, productName: target.name, uomName: target.uoms[target.uoms.length - 1].name,
        factor: target.uoms[target.uoms.length - 1].factor, qty: 2, unitCost: +(target.costBase * target.uoms[target.uoms.length - 1].factor).toFixed(2),
        freight: 80.0, status: "ORDERED", date: "2026-11-23"
      });
      this.renderAll();
    },

    receivePo(poId) {
      const po = S.purchaseOrders.find(x => x.id === poId);
      if (!po || po.status === "RECEIVED") return;
      po.status = "RECEIVED";
      const prod = S.products.find(p => p.id === po.productId);
      if (prod) {
        const receivedBase = po.qty * po.factor;
        const totalLanded = po.qty * po.unitCost + po.freight;
        const oldVal = prod.stockBase * prod.costBase;
        prod.stockBase += receivedBase;
        prod.costBase = +((oldVal + totalLanded) / prod.stockBase).toFixed(2);
        S.stockMovements.unshift({ time: "Just now", product: prod.name, reason: "PO_RECEIPT", delta: receivedBase, balance: prod.stockBase, wac: prod.costBase, ref: po.id });
      }
      S.overheads.unshift({ id: "ovh_" + Date.now(), title: po.id + " — " + po.supplierName, category: "SUPPLIER_INVOICE", amount: po.qty * po.unitCost + po.freight, dueDate: "2026-12-08", priority: "HIGH", isPaid: false });
      this.renderAll();
    },

    payOverhead(id) {
      const o = S.overheads.find(x => x.id === id);
      if (!o || o.isPaid) return;
      o.isPaid = true;
      S.glJournals.unshift({ id: "GL-EXP-" + S.glJournals.length, time: "Just now", ref: o.id, desc: "Paid Overhead: " + o.title, debitAccount: "6000_OPERATING_EXPENSES (" + fmt(o.amount) + ")", creditAccount: "1000_CASH_AND_BANK (" + fmt(o.amount) + ")", amount: o.amount });
      this.renderAll();
    },

    collectCustomerAr(custId) {
      const c = S.customers.find(x => x.id === custId);
      if (!c || c.balance <= 0) return;
      const pay = Number(prompt("Collect Credit Repayment from " + c.name + " (Balance: " + fmt(c.balance) + "):", c.balance) || 0);
      if (pay <= 0) return;
      c.balance = Math.max(0, +(c.balance - pay).toFixed(2));
      S.shift.cashSales += pay;
      this.renderAll();
    },

    renderPosCatalog() {
      document.getElementById("sp-store-title").textContent = S.config.storeName;
      document.getElementById("sp-store-subtitle").textContent = S.config.branchName + " • TIN: " + S.config.tin;
      document.getElementById("sp-active-user-btn").textContent = "👤 " + S.activeUser.displayName + " (" + S.activeUser.role + ")";
      document.getElementById("sp-drawer-expected").textContent = "Drawer Cash: " + fmt(expectedDrawerCash());

      const catHtml = [{ id: "ALL", name: "All Items", icon: "✨" }, ...S.categories].map(c =>
        "<button class='sp-btn " + (selectedCategoryId === c.id ? "sp-btn-primary" : "sp-btn-tonal") + " sp-btn-sm' onclick=\"SP.selectCategory('" + c.id + "')\">" + c.icon + " " + c.name + "</button>"
      ).join("");
      document.getElementById("sp-pos-categories").innerHTML = catHtml;

      const q = (document.getElementById("sp-pos-search")?.value || "").trim().toLowerCase();
      const filtered = S.products.filter(p =>
        (selectedCategoryId === "ALL" || p.categoryId === selectedCategoryId) &&
        (!q || p.name.toLowerCase().includes(q) || p.sku.toLowerCase().includes(q) || p.uoms.some(u => u.barcode.includes(q)))
      );

      document.getElementById("sp-pos-product-grid").innerHTML = filtered.map(p => {
        const isLow = p.stockBase <= p.reorderPoint;
        const uomBtns = p.uoms.map(u =>
          "<button class='sp-uom-btn' onclick=\"SP.addToCart('" + p.id + "', '" + u.code + "')\">" +
          u.name + "<br/><strong style='color:var(--sp-primary); font-size:12.5px;'>" + fmt(u.price) + "</strong></button>"
        ).join("");
        return "<div class='sp-prod-card'>" +
          "<div><div style='display:flex; justify-content:space-between; gap:6px; align-items:start;'>" +
          "<span style='font-size:10.5px; font-weight:700; color:var(--sp-text-muted);'>" + p.sku + "</span>" +
          "<span class='sp-badge " + (isLow ? "sp-badge-danger" : "sp-badge-success") + "'>" + p.stockBase + " " + p.baseUom + (isLow ? " • LOW" : "") + "</span></div>" +
          "<div style='font-weight:800; font-size:14.5px; margin:6px 0 4px;'>" + p.name + "</div>" +
          "<div style='font-size:11px; color:var(--sp-text-muted);'>WAC Cost: " + fmt(p.costBase) + "/" + p.baseUom + " • Exp: " + p.expiryDate + "</div></div>" +
          "<div><div style='font-size:10.5px; font-weight:700; color:var(--sp-text-muted); margin-bottom:5px;'>Tap UOM / Packaging to Add:</div>" +
          "<div class='sp-uom-pills'>" + uomBtns + "</div></div></div>";
      }).join("");
    },

    renderCart() {
      const count = S.cart.reduce((s, i) => s + i.qty, 0);
      document.getElementById("sp-cart-count").textContent = count + " items";
      document.getElementById("sp-parked-banner").innerHTML = S.parkedCarts.map((pk, idx) =>
        "<div class='sp-card-tonal' style='padding:8px 12px; margin-bottom:6px; display:flex; justify-content:space-between; align-items:center; font-size:12px;'>" +
        "<span>⏸️ <strong>" + pk.note + "</strong> (" + pk.items.length + " lines)</span>" +
        "<button class='sp-btn sp-btn-primary sp-btn-sm' onclick='SP.resumeParkedCart(" + idx + ")'>Resume</button></div>"
      ).join("");

      document.getElementById("sp-cart-list").innerHTML = S.cart.length === 0
        ? "<div style='text-align:center; padding:28px 10px; color:var(--sp-text-muted); font-size:13px;'>Cart is empty. Tap any product UOM pill on the left to add items.</div>"
        : S.cart.map((item, idx) =>
          "<div class='sp-card-tonal' style='padding:10px 12px; display:flex; justify-content:space-between; align-items:center; gap:8px;'>" +
          "<div style='flex:1;'><div style='font-weight:700; font-size:13px;'>" + item.name + "</div>" +
          "<span class='sp-badge sp-badge-purple'>" + item.uomName + " (x" + item.factor + " base)</span> " +
          "<span style='font-size:12px; color:var(--sp-text-muted);'>" + fmt(item.price) + "</span></div>" +
          "<div style='display:flex; align-items:center; gap:6px;'>" +
          "<button class='sp-btn sp-btn-outline sp-btn-sm' style='padding:2px 9px;' onclick='SP.updateCartQty(" + idx + ", -1)'>−</button>" +
          "<strong style='min-width:20px; text-align:center;'>" + item.qty + "</strong>" +
          "<button class='sp-btn sp-btn-outline sp-btn-sm' style='padding:2px 9px;' onclick='SP.updateCartQty(" + idx + ", 1)'>+</button>" +
          "<strong style='min-width:72px; text-align:right;'>" + fmt(item.price * item.qty) + "</strong></div></div>"
        ).join("");

      const t = calcCartTotals();
      document.getElementById("sp-cart-subtotal").textContent = fmt(t.subtotal);
      document.getElementById("sp-vat-rate-label").textContent = S.config.taxRatePercent + "% Tax (" + S.config.taxMode + ")";
      document.getElementById("sp-cart-net").textContent = fmt(t.net);
      document.getElementById("sp-cart-tax").textContent = fmt(t.tax);
      document.getElementById("sp-cart-total").textContent = fmt(t.total);
    },

    renderWorkspaces() {
      // Float Hub
      document.getElementById("sp-float-wallets-grid").innerHTML = S.floatWallets.map(w =>
        "<div class='sp-card' style='border-top:4px solid " + w.color + ";'>" +
        "<div style='display:flex; justify-content:space-between;'><span style='font-size:11px; font-weight:700; color:var(--sp-text-muted);'>" + w.category + "</span>" +
        "<span class='sp-badge sp-badge-info'>Fee: " + fmt(w.fee) + (w.commissionBps ? " • +" + (w.commissionBps / 100) + "% Rebate" : "") + "</span></div>" +
        "<div style='font-weight:800; font-size:15px; margin:6px 0;'>" + w.name + "</div>" +
        "<div style='font-size:22px; font-weight:900; color:" + w.color + ";'>" + fmt(w.balance) + "</div></div>"
      ).join("");
      document.getElementById("sp-flt-wallet").innerHTML = S.floatWallets.map(w => "<option value='" + w.code + "'>" + w.name + " (" + fmt(w.balance) + ")</option>").join("");
      document.getElementById("sp-float-ledger-table").innerHTML =
        "<thead><tr><th>ID / Time</th><th>Wallet</th><th>Type</th><th>Principal</th><th>Float Delta</th><th>Customer Fee</th><th>Rebate</th><th>Balance After</th><th>Reference</th></tr></thead><tbody>" +
        S.floatLedger.map(l => "<tr><td><strong>" + l.id + "</strong><br/><small>" + l.time + "</small></td><td><span class='sp-badge sp-badge-info'>" + l.wallet + "</span></td><td>" + l.type + "</td><td>" + fmt(l.principal) + "</td><td style='color:" + (l.delta >= 0 ? "var(--sp-cash)" : "var(--sp-danger)") + "; font-weight:700;'>" + (l.delta >= 0 ? "+" : "") + fmt(l.delta) + "</td><td>" + fmt(l.fee) + "</td><td>" + fmt(l.rebate) + "</td><td><strong>" + fmt(l.balanceAfter) + "</strong></td><td>" + l.ref + "</td></tr>").join("") + "</tbody>";

      // Inventory & BOM
      const prodOpts = S.products.map(p => "<option value='" + p.id + "'>" + p.name + " (" + p.stockBase + " " + p.baseUom + " @ " + fmt(p.costBase) + ")</option>").join("");
      document.getElementById("sp-bom-raw").innerHTML = prodOpts;
      document.getElementById("sp-bom-target").innerHTML = prodOpts;
      if (S.products.length > 1) document.getElementById("sp-bom-raw").value = "prd_rice_bulk50";

      document.getElementById("sp-inventory-table").innerHTML =
        "<thead><tr><th>SKU / Product</th><th>Base Stock On Hand</th><th>WAC Unit Cost</th><th>Total Valuation</th><th>Multi-UOM Barcodes & Tiers</th><th>Expiry</th><th>Stocktake</th></tr></thead><tbody>" +
        S.products.map(p => "<tr><td><strong>" + p.name + "</strong><br/><small>" + p.sku + "</small></td><td><span class='sp-badge " + (p.stockBase <= p.reorderPoint ? "sp-badge-danger" : "sp-badge-success") + "'>" + p.stockBase + " " + p.baseUom + "</span></td><td>" + fmt(p.costBase) + "</td><td><strong>" + fmt(p.stockBase * p.costBase) + "</strong></td><td>" + p.uoms.map(u => "<span class='sp-badge sp-badge-info' style='margin:2px;'>" + u.name + ": " + fmt(u.price) + " [" + u.barcode + "]</span>").join("") + "</td><td>" + p.expiryDate + "</td><td><button class='sp-btn sp-btn-outline sp-btn-sm' onclick=\"SP.adjustStocktake('" + p.id + "')\">📋 Count</button></td></tr>").join("") + "</tbody>";

      document.getElementById("sp-stock-ledger-table").innerHTML =
        "<thead><tr><th>Time</th><th>Product</th><th>Reason</th><th>Delta (Base)</th><th>Balance After</th><th>WAC Cost</th><th>Reference</th></tr></thead><tbody>" +
        S.stockMovements.map(m => "<tr><td>" + m.time + "</td><td><strong>" + m.product + "</strong></td><td><span class='sp-badge sp-badge-purple'>" + m.reason + "</span></td><td style='font-weight:800; color:" + (m.delta >= 0 ? "var(--sp-cash)" : "var(--sp-danger)") + ";'>" + (m.delta >= 0 ? "+" : "") + m.delta + "</td><td>" + m.balance + "</td><td>" + fmt(m.wac) + "</td><td>" + m.ref + "</td></tr>").join("") + "</tbody>";

      // Purchasing & Overheads
      document.getElementById("sp-po-table").innerHTML =
        "<thead><tr><th>PO #</th><th>Supplier</th><th>Item & Packaging</th><th>Qty</th><th>Landed Total</th><th>Status</th><th>Action</th></tr></thead><tbody>" +
        S.purchaseOrders.map(po => "<tr><td><strong>" + po.id + "</strong></td><td>" + po.supplierName + "</td><td>" + po.productName + " (" + po.uomName + ")</td><td>" + po.qty + "</td><td><strong>" + fmt(po.qty * po.unitCost + po.freight) + "</strong><br/><small>incl. " + fmt(po.freight) + " freight</small></td><td><span class='sp-badge " + (po.status === "RECEIVED" ? "sp-badge-success" : "sp-badge-warn") + "'>" + po.status + "</span></td><td>" + (po.status === "RECEIVED" ? "✅ Received" : "<button class='sp-btn sp-btn-primary sp-btn-sm' onclick=\"SP.receivePo('" + po.id + "')\">📦 Receive & Update WAC</button>") + "</td></tr>").join("") + "</tbody>";

      document.getElementById("sp-overhead-list").innerHTML = S.overheads.map(o =>
        "<div class='sp-card-tonal' style='display:flex; justify-content:space-between; align-items:center; gap:8px;'>" +
        "<div><strong>" + o.title + "</strong><div style='font-size:12px; color:var(--sp-text-muted);'>" + o.category + " • Due " + o.dueDate + "</div></div>" +
        "<div style='display:flex; align-items:center; gap:10px;'><strong>" + fmt(o.amount) + "</strong>" +
        (o.isPaid ? "<span class='sp-badge sp-badge-success'>PAID</span>" : "<button class='sp-btn sp-btn-cash sp-btn-sm' onclick=\"SP.payOverhead('" + o.id + "')\">Pay Bill</button>") + "</div></div>"
      ).join("");

      // Admin ERP & P&L
      const activeTxs = S.transactions.filter(t => t.status !== "REFUNDED");
      const grossSales = activeTxs.reduce((s, t) => s + t.total, 0);
      const netSales = activeTxs.reduce((s, t) => s + t.netSales, 0);
      const taxTotal = activeTxs.reduce((s, t) => s + t.tax, 0);
      const cogsTotal = activeTxs.reduce((s, t) => s + t.cogs, 0);
      const floatIncome = S.floatLedger.reduce((s, l) => s + l.fee + l.rebate, 0);
      const paidOverheads = S.overheads.filter(o => o.isPaid).reduce((s, o) => s + o.amount, 0);
      const grossProfit = netSales + floatIncome - cogsTotal;
      const netProfit = grossProfit - paidOverheads;
      const totalAr = S.customers.reduce((s, c) => s + c.balance, 0);

      document.getElementById("sp-admin-kpis").innerHTML = [
        { label: "Gross POS Sales", val: fmt(grossSales), sub: activeTxs.length + " completed receipts" },
        { label: "Net Sales + Float Fees", val: fmt(netSales + floatIncome), sub: "Float income: " + fmt(floatIncome) },
        { label: "Cost of Goods Sold (WAC)", val: fmt(cogsTotal), sub: "Gross Profit: " + fmt(grossProfit) },
        { label: "Net Operating Profit", val: fmt(netProfit), sub: "Tax Payable: " + fmt(taxTotal) + " • AR: " + fmt(totalAr) }
      ].map(k => "<div class='sp-card'><div style='font-size:12px; font-weight:700; color:var(--sp-text-muted);'>" + k.label + "</div><div style='font-size:24px; font-weight:900; color:var(--sp-primary); margin:6px 0;'>" + k.val + "</div><div style='font-size:11.5px; color:var(--sp-text-muted);'>" + k.sub + "</div></div>").join("");

      document.getElementById("sp-customers-ar-table").innerHTML =
        "<thead><tr><th>Customer</th><th>Balance (Utang)</th><th>Credit Limit</th><th>Loyalty</th><th>Action</th></tr></thead><tbody>" +
        S.customers.map(c => "<tr><td><strong>" + c.name + "</strong><br/><small>" + c.phone + "</small></td><td style='color:var(--sp-credit); font-weight:800;'>" + fmt(c.balance) + "</td><td>" + fmt(c.creditLimit) + "</td><td><span class='sp-badge sp-badge-purple'>" + c.points + " pts</span></td><td><button class='sp-btn sp-btn-cash sp-btn-sm' onclick=\"SP.collectCustomerAr('" + c.id + "')\">💵 Collect</button></td></tr>").join("") + "</tbody>";

      document.getElementById("sp-transactions-table").innerHTML =
        "<thead><tr><th>Receipt #</th><th>Customer & Tender</th><th>Total</th><th>Status</th><th>Action</th></tr></thead><tbody>" +
        S.transactions.map(t => "<tr><td><strong>" + t.id + "</strong><br/><small>" + t.time + "</small></td><td>" + t.customerName + " <span class='sp-badge sp-badge-info'>" + t.tender + "</span></td><td><strong>" + fmt(t.total) + "</strong></td><td><span class='sp-badge " + (t.status === "REFUNDED" ? "sp-badge-danger" : "sp-badge-success") + "'>" + t.status + "</span></td><td><button class='sp-btn sp-btn-outline sp-btn-sm' onclick=\"SP.showReceiptModal('" + t.id + "')\">🧾 Receipt / Return</button></td></tr>").join("") + "</tbody>";

      document.getElementById("sp-gl-table").innerHTML =
        "<thead><tr><th>Journal ID</th><th>Ref</th><th>Description</th><th>Debits</th><th>Credits</th></tr></thead><tbody>" +
        S.glJournals.map(g => "<tr><td><strong>" + g.id + "</strong><br/><small>" + g.time + "</small></td><td>" + g.ref + "</td><td>" + g.desc + "</td><td style='color:var(--sp-cash); font-weight:700;'>" + g.debitAccount + "</td><td style='color:var(--sp-primary); font-weight:700;'>" + g.creditAccount + "</td></tr>").join("") + "</tbody>";

      // CFD & Shelf Labels
      const ct = calcCartTotals();
      document.getElementById("sp-cfd-live-card").innerHTML =
        "<h3 style='margin-top:0;'>🖥️ Customer-Facing Display (Secondary Screen)</h3>" +
        "<div class='sp-card-tonal' style='margin-bottom:12px;'>" +
        (S.cart.length === 0 ? "<p>Welcome to " + S.config.storeName + "!</p>" : S.cart.map(i => "<div style='display:flex; justify-content:space-between; padding:6px 0; border-bottom:1px solid var(--sp-outline-variant);'><span>" + i.qty + "x " + i.name + " (" + i.uomName + ")</span><strong>" + fmt(i.qty * i.price) + "</strong></div>").join("")) +
        "<div style='display:flex; justify-content:space-between; font-size:22px; font-weight:900; padding-top:10px;'><span>TOTAL TO PAY</span><span style='color:var(--sp-primary);'>" + fmt(ct.total) + "</span></div></div>" +
        "<div class='sp-card-tonal' style='text-align:center;'><strong>📱 Scan Instant QR to Pay (" + S.config.currencyCode + ")</strong><div style='font-family:monospace; font-size:11px; margin-top:6px; color:var(--sp-text-muted);'>storepoint://pay?store=" + encodeURIComponent(S.config.storeName) + "&amount=" + ct.total.toFixed(2) + "</div></div>";

      document.getElementById("sp-shelf-labels-grid").innerHTML = S.products.flatMap(p => p.uoms.map(u =>
        "<div class='sp-card-tonal' style='border:1.5px dashed var(--sp-outline); text-align:center;'>" +
        "<div style='font-size:10px; font-weight:700; color:var(--sp-text-muted);'>" + S.config.storeName + "</div>" +
        "<div style='font-weight:800; font-size:13px; margin:4px 0;'>" + p.name + "</div>" +
        "<span class='sp-badge sp-badge-info'>" + u.name + "</span>" +
        "<div style='font-size:20px; font-weight:900; color:var(--sp-primary); margin:6px 0;'>" + fmt(u.price) + "</div>" +
        "<div style='font-family:monospace; font-size:11px; letter-spacing:2px;'>|| ||| | |||| ||| ||<br/>" + u.barcode + "</div></div>"
      )).join("");
    },

    renderAll() {
      this.renderPosCatalog();
      this.renderCart();
      this.renderWorkspaces();
    }
  };

  window.SP = SP;
  window.addEventListener("DOMContentLoaded", () => SP.renderAll());
  if (document.readyState !== "loading") SP.renderAll();
})();
`;
