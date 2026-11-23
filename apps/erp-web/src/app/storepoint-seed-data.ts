/**
 * Authentic StorePoint Retail Seed Data & Global Country Pack Presets.
 * Includes the signature Philippine Sari-Sari / Mini-Mart catalog (Multi-UOM Tingi/Pack/Ream/Case,
 * 50kg Rice Sack -> 1kg Retail Pack BOM, GCash/Maya/Smart/Globe Float Ledgers, Customer Utang/AR Tabs,
 * Suppliers, POs, and Overhead Bills) PLUS 1-click Country Pack profiles for Global Small Stores.
 */

export interface StorePointClientBootstrapData {
  readonly storeName: string;
  readonly branchName: string;
  readonly tin: string;
  readonly currencyCode: string;
  readonly currencySymbol: string;
  readonly taxRatePercent: number;
  readonly taxMode: "INCLUSIVE" | "EXCLUSIVE";
  readonly activeCountryPack: string;
}

export const STOREPOINT_INITIAL_SEED_SCRIPT = `
window.STOREPOINT_DEFAULT_STATE = {
  config: {
    storeName: "StorePoint Sari-Sari & Mini-Mart",
    branchName: "Main Counter • Terminal 01",
    tin: "123-456-789-000 VAT",
    currencyCode: "PHP",
    currencySymbol: "₱",
    taxRatePercent: 12,
    taxMode: "INCLUSIVE",
    activeCountryPack: "pack-ph-retail",
    paperWidthMm: 58,
    kioskLocked: false
  },
  activeUser: { username: "admin", displayName: "Jeff S. (Owner)", role: "ADMIN", pin: "8492" },
  users: [
    { username: "admin", displayName: "Jeff S. (Owner)", role: "ADMIN", pin: "8492", badge: "ADM-001" },
    { username: "maria", displayName: "Maria Santos (Cashier)", role: "CASHIER", pin: "5931", badge: "CSH-002" },
    { username: "carlo", displayName: "Carlo Reyes (Inventory)", role: "INVENTORY", pin: "7392", badge: "INV-003" }
  ],
  shift: {
    id: "SHF-2026-01",
    cashier: "Jeff S. (Owner)",
    status: "OPEN",
    openingFloat: 2000.00,
    cashSales: 1485.00,
    pettyCashIn: 0.00,
    pettyCashOut: 150.00,
    cashRefunds: 0.00,
    openedAt: "08:00 AM",
    drawerLogs: [
      { time: "08:00 AM", type: "OPEN_FLOAT", amount: 2000.00, reason: "Morning opening drawer float" },
      { time: "10:15 AM", type: "CASH_OUT", amount: -150.00, reason: "Ice delivery petty cash payout" }
    ]
  },
  categories: [
    { id: "cat_rice", name: "Rice & Bulk Grains", color: "#16A34A", icon: "🌾" },
    { id: "cat_coffee", name: "Beverages & Coffee", color: "#2D55D6", icon: "☕" },
    { id: "cat_noodles", name: "Instant Noodles", color: "#D97706", icon: "🍜" },
    { id: "cat_canned", name: "Canned Goods", color: "#DC2626", icon: "🥫" },
    { id: "cat_personal", name: "Sachets & Personal Care", color: "#7C3AED", icon: "🧴" },
    { id: "cat_tobacco", name: "Multi-UOM & Sundries", color: "#475569", icon: "📦" }
  ],
  products: [
    {
      id: "prd_rice_1kg",
      sku: "RICE-JAS-1KG",
      name: "Sinandomeng / Jasmine Rice (Retail)",
      categoryId: "cat_rice",
      baseUom: "kg",
      stockBase: 45,
      reorderPoint: 20,
      costBase: 43.00,
      expiryDate: "2027-08-15",
      uoms: [
        { code: "1kg", name: "1 kg Retail Bag", factor: 1, price: 54.00, barcode: "480001001" },
        { code: "5kg", name: "5 kg Family Pack", factor: 5, price: 260.00, barcode: "480001005" },
        { code: "25kg", name: "25 kg Half-Sack", factor: 25, price: 1250.00, barcode: "480001025" }
      ]
    },
    {
      id: "prd_rice_bulk50",
      sku: "RAW-RICE-50KG",
      name: "Bulk Jasmine Rice 50kg Master Sack (Raw BOM)",
      categoryId: "cat_rice",
      baseUom: "kg",
      stockBase: 150,
      reorderPoint: 50,
      costBase: 42.00,
      expiryDate: "2027-09-01",
      uoms: [
        { code: "kg", name: "1 kg Bulk", factor: 1, price: 50.00, barcode: "480009001" },
        { code: "sack50", name: "50 kg Master Sack", factor: 50, price: 2350.00, barcode: "480009050" }
      ]
    },
    {
      id: "prd_nescafe",
      sku: "COF-NES-3IN1",
      name: "Nescafé Original 3-in-1 Twin Pack",
      categoryId: "cat_coffee",
      baseUom: "sachet",
      stockBase: 180,
      reorderPoint: 48,
      costBase: 11.50,
      expiryDate: "2027-05-20",
      uoms: [
        { code: "sachet", name: "1 Sachet (Tingi)", factor: 1, price: 15.00, barcode: "480002001" },
        { code: "tie10", name: "10-Sachet Tie", factor: 10, price: 142.00, barcode: "480002010" },
        { code: "case120", name: "Master Case (120s)", factor: 120, price: 1620.00, barcode: "480002120" }
      ]
    },
    {
      id: "prd_canton",
      sku: "NDL-LM-CANTON",
      name: "Lucky Me! Pancit Canton Kalamansi",
      categoryId: "cat_noodles",
      baseUom: "pack",
      stockBase: 84,
      reorderPoint: 24,
      costBase: 12.25,
      expiryDate: "2027-02-10",
      uoms: [
        { code: "pack", name: "1 Pack (Tingi)", factor: 1, price: 16.00, barcode: "480003001" },
        { code: "pack6", name: "6-Pack Bundle", factor: 6, price: 92.00, barcode: "480003006" },
        { code: "case72", name: "Case (72 Packs)", factor: 72, price: 1050.00, barcode: "480003072" }
      ]
    },
    {
      id: "prd_tuna",
      sku: "CAN-CEN-TUNA",
      name: "Century Tuna Flakes in Oil 155g",
      categoryId: "cat_canned",
      baseUom: "can",
      stockBase: 14,
      reorderPoint: 18,
      costBase: 31.00,
      expiryDate: "2026-12-05",
      uoms: [
        { code: "can", name: "1 Can", factor: 1, price: 39.00, barcode: "480004001" },
        { code: "case48", name: "Case (48 Cans)", factor: 48, price: 1780.00, barcode: "480004048" }
      ]
    },
    {
      id: "prd_coke",
      sku: "BEV-COKE-15L",
      name: "Coca-Cola Original 1.5L Bottle",
      categoryId: "cat_coffee",
      baseUom: "bottle",
      stockBase: 36,
      reorderPoint: 12,
      costBase: 58.00,
      expiryDate: "2027-04-01",
      uoms: [
        { code: "bottle", name: "1 Bottle (1.5L)", factor: 1, price: 72.00, barcode: "480005001" },
        { code: "case12", name: "12-Bottle Crate", factor: 12, price: 820.00, barcode: "480005012" }
      ]
    },
    {
      id: "prd_shampoo",
      sku: "PER-SUN-SACHET",
      name: "Sunsilk Strong & Long Shampoo Sachet",
      categoryId: "cat_personal",
      baseUom: "sachet",
      stockBase: 144,
      reorderPoint: 36,
      costBase: 5.50,
      expiryDate: "2028-01-01",
      uoms: [
        { code: "sachet", name: "1 Sachet (Tingi)", factor: 1, price: 8.00, barcode: "480006001" },
        { code: "dozen12", name: "1 Dozen Strip (12s)", factor: 12, price: 88.00, barcode: "480006012" }
      ]
    },
    {
      id: "prd_marlboro",
      sku: "TOB-MENTHOL",
      name: "Menthol Filter Sticks (Multi-UOM Tiered)",
      categoryId: "cat_tobacco",
      baseUom: "stick",
      stockBase: 400,
      reorderPoint: 100,
      costBase: 7.20,
      expiryDate: "2028-06-01",
      uoms: [
        { code: "stick", name: "1 Stick (Tingi)", factor: 1, price: 10.00, barcode: "480007001" },
        { code: "pack10", name: "Half-Pack (10s)", factor: 10, price: 95.00, barcode: "480007010" },
        { code: "pack20", name: "Full Pack (20s)", factor: 20, price: 185.00, barcode: "480007020" },
        { code: "ream200", name: "1 Ream (200s)", factor: 200, price: 1790.00, barcode: "480007200" }
      ]
    }
  ],
  floatWallets: [
    { id: "flt_gcash", code: "GCASH", name: "GCash Cash-In / Cash-Out", category: "EWALLET", color: "#007DFE", balance: 18500.00, fee: 10.00, commissionBps: 0 },
    { id: "flt_maya", code: "MAYA", name: "Maya Wallet & Bank Transfer", category: "EWALLET", color: "#00B4D8", balance: 9200.00, fee: 10.00, commissionBps: 0 },
    { id: "flt_smart", code: "SMART", name: "Smart / TNT Prepaid E-Load", category: "AIRTIME_LOAD", color: "#16A34A", balance: 4350.00, fee: 2.00, commissionBps: 200 },
    { id: "flt_globe", code: "GLOBE", name: "Globe / TM Prepaid E-Load", category: "AIRTIME_LOAD", color: "#7C3AED", balance: 3820.00, fee: 2.00, commissionBps: 200 }
  ],
  floatLedger: [
    { id: "FL-102", time: "09:12 AM", wallet: "GCASH", type: "CUSTOMER_CASH_IN", principal: 1000.00, delta: -1000.00, fee: 20.00, rebate: 0.00, balanceAfter: 18500.00, ref: "GC-9981201" },
    { id: "FL-101", time: "08:45 AM", wallet: "SMART", type: "TOPUP_SALE", principal: 100.00, delta: -98.00, fee: 2.00, rebate: 2.00, balanceAfter: 4350.00, ref: "0919-555-0192" }
  ],
  customers: [
    { id: "cus_1", name: "Aling Nena Dela Cruz", phone: "0917-555-0143", creditLimit: 2500.00, balance: 650.00, points: 142 },
    { id: "cus_2", name: "Mang Jose Bautista", phone: "0918-555-0921", creditLimit: 1500.00, balance: 320.00, points: 88 },
    { id: "cus_3", name: "Sari-Sari Reseller — Lorna", phone: "0922-555-4410", creditLimit: 10000.00, balance: 2400.00, points: 510 }
  ],
  suppliers: [
    { id: "sup_1", name: "Golden Grain Rice Milling Corp.", contact: "Engr. Ramon Sy", phone: "0917-800-1122", terms: "NET_15", schedule: "Mondays & Thursdays" },
    { id: "sup_2", name: "Metro FMCG Wholesale Distributors", contact: "Grace Lim", phone: "0918-900-3344", terms: "NET_7", schedule: "Tuesdays & Fridays" },
    { id: "sup_3", name: "Coca-Cola Beverages Route Truck", contact: "Route #14 Driver", phone: "0920-111-2233", terms: "COD", schedule: "Wednesdays & Saturdays" }
  ],
  purchaseOrders: [
    { id: "PO-00101", supplierId: "sup_1", supplierName: "Golden Grain Rice Milling Corp.", productId: "prd_rice_bulk50", productName: "Bulk Jasmine Rice 50kg Master Sack (Raw BOM)", uomName: "50 kg Master Sack", factor: 50, qty: 3, unitCost: 2100.00, freight: 150.00, status: "ORDERED", date: "2026-11-23" }
  ],
  overheads: [
    { id: "ovh_1", title: "Store Space Monthly Rental", category: "RENT", amount: 8500.00, dueDate: "2026-11-30", priority: "HIGH", isPaid: false },
    { id: "ovh_2", title: "Meralco / Electric Utility Bill", category: "UTILITIES", amount: 3420.00, dueDate: "2026-11-28", priority: "HIGH", isPaid: false },
    { id: "ovh_3", title: "Metro FMCG Supplier Invoice #882", category: "SUPPLIER_INVOICE", amount: 6400.00, dueDate: "2026-11-26", priority: "MEDIUM", isPaid: false }
  ],
  transactions: [
    {
      id: "OR-000101",
      time: "11:20 AM",
      cashier: "Jeff S. (Owner)",
      customerName: "Walk-in Customer",
      tender: "CASH",
      subtotal: 294.00,
      discount: 0.00,
      netSales: 262.50,
      tax: 31.50,
      total: 294.00,
      cogs: 223.50,
      cashPaid: 500.00,
      change: 206.00,
      status: "COMPLETED",
      items: [
        { productId: "prd_rice_1kg", name: "Sinandomeng / Jasmine Rice (Retail)", uomName: "1 kg Retail Bag", factor: 1, qty: 2, price: 54.00, cost: 43.00 },
        { productId: "prd_canton", name: "Lucky Me! Pancit Canton Kalamansi", uomName: "6-Pack Bundle", factor: 6, qty: 1, price: 92.00, cost: 73.50 },
        { productId: "prd_coke", name: "Coca-Cola Original 1.5L Bottle", uomName: "1 Bottle (1.5L)", factor: 1, qty: 1, price: 72.00, cost: 58.00 },
        { productId: "prd_nescafe", name: "Nescafé Original 3-in-1 Twin Pack", uomName: "1 Sachet (Tingi)", factor: 1, qty: 1, price: 22.00, cost: 11.50 }
      ]
    }
  ],
  bomLogs: [
    { id: "BOM-2026-01", time: "08:30 AM", rawName: "Bulk Jasmine Rice 50kg Master Sack", rawUsedKg: 50, targetName: "Sinandomeng / Jasmine Rice (Retail)", producedBags: 50, bagCost: 1.00, derivedUnitCogs: 43.00 }
  ],
  stockMovements: [
    { time: "11:20 AM", product: "Sinandomeng / Jasmine Rice (Retail)", reason: "POS_SALE", delta: -2, balance: 45, wac: 43.00, ref: "OR-000101" },
    { time: "08:30 AM", product: "Sinandomeng / Jasmine Rice (Retail)", reason: "BOM_PRODUCE", delta: 50, balance: 47, wac: 43.00, ref: "BOM-2026-01" },
    { time: "08:30 AM", product: "Bulk Jasmine Rice 50kg Master Sack", reason: "BOM_CONSUME", delta: -50, balance: 150, wac: 42.00, ref: "BOM-2026-01" }
  ],
  glJournals: [
    { id: "GL-00101", time: "11:20 AM", ref: "OR-000101", desc: "POS Cash Sale + 12% VAT + WAC COGS", debitAccount: "1000_CASH_AND_BANK (₱294.00) / 5000_COGS (₱223.50)", creditAccount: "4000_SALES_REV (₱262.50) / 2100_VAT (₱31.50) / 1200_INV (₱223.50)", amount: 294.00 }
  ],
  cart: [
    { productId: "prd_rice_1kg", name: "Sinandomeng / Jasmine Rice (Retail)", uomCode: "1kg", uomName: "1 kg Retail Bag", factor: 1, qty: 2, price: 54.00, discount: 0 },
    { productId: "prd_nescafe", name: "Nescafé Original 3-in-1 Twin Pack", uomCode: "tie10", uomName: "10-Sachet Tie", factor: 10, qty: 1, price: 142.00, discount: 0 }
  ],
  parkedCarts: [
    {
      id: "PRK-01",
      note: "Neighboring Sari-Sari Order (Waiting for container)",
      time: "10:50 AM",
      items: [
        { productId: "prd_canton", name: "Lucky Me! Pancit Canton Kalamansi", uomCode: "pack6", uomName: "6-Pack Bundle", factor: 6, qty: 2, price: 92.00, discount: 0 }
      ]
    }
  ]
};
`;
