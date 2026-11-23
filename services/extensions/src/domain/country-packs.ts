import type { CountryPackManifestDto } from "@storepoint/contracts";

/**
 * Official StorePoint v2 Country & Regional Packs.
 * Extracts Philippine-specific capabilities from StorePoint v1 into `pack-ph-retail`
 * while providing turnkey regional packs for stores worldwide.
 */
export const OFFICIAL_COUNTRY_PACKS: readonly CountryPackManifestDto[] = Object.freeze([
  {
    packId: "pack-ph-retail",
    name: "Philippines Sari-Sari & Mini-Mart Pack",
    region: "Southeast Asia",
    countryCode: "PH",
    defaultCurrencyCode: "PHP",
    taxIdLabel: "BIR TIN",
    defaultTaxRules: [
      { id: "ph_vat_12", name: "12% VAT (Inclusive)", rateBps: 1200, mode: "INCLUSIVE" }
    ],
    floatTemplates: [
      {
        providerCode: "GCASH",
        providerName: "GCash Cash-In / Cash-Out",
        category: "EWALLET",
        defaultServiceFeeMinor: 1000,
        commissionBps: 0
      },
      {
        providerCode: "MAYA",
        providerName: "Maya Wallet & Bank Transfer",
        category: "EWALLET",
        defaultServiceFeeMinor: 1000,
        commissionBps: 0
      },
      {
        providerCode: "SMART_LOAD",
        providerName: "Smart / TNT Prepaid E-Load",
        category: "AIRTIME_LOAD",
        defaultServiceFeeMinor: 200,
        commissionBps: 200
      },
      {
        providerCode: "GLOBE_LOAD",
        providerName: "Globe / TM Prepaid E-Load",
        category: "AIRTIME_LOAD",
        defaultServiceFeeMinor: 200,
        commissionBps: 200
      }
    ]
  },
  {
    packId: "pack-in-kirana",
    name: "India Kirana GST & UPI Pack",
    region: "South Asia",
    countryCode: "IN",
    defaultCurrencyCode: "INR",
    taxIdLabel: "GSTIN",
    defaultTaxRules: [
      { id: "in_cgst_9", name: "CGST 9%", rateBps: 900, mode: "EXCLUSIVE" },
      { id: "in_sgst_9", name: "SGST 9%", rateBps: 900, mode: "EXCLUSIVE" }
    ],
    floatTemplates: [
      {
        providerCode: "UPI_AGENT",
        providerName: "UPI Cash Transfer",
        category: "EWALLET",
        defaultServiceFeeMinor: 1000,
        commissionBps: 50
      },
      {
        providerCode: "MOBILE_RECHARGE_IN",
        providerName: "Jio / Airtel Prepaid Recharge",
        category: "AIRTIME_LOAD",
        defaultServiceFeeMinor: 0,
        commissionBps: 250
      }
    ]
  },
  {
    packId: "pack-ke-duka",
    name: "Kenya Duka & M-Pesa Agent Pack",
    region: "East Africa",
    countryCode: "KE",
    defaultCurrencyCode: "KES",
    taxIdLabel: "KRA PIN",
    defaultTaxRules: [
      { id: "ke_vat_16", name: "16% VAT (Inclusive)", rateBps: 1600, mode: "INCLUSIVE" }
    ],
    floatTemplates: [
      {
        providerCode: "MPESA",
        providerName: "M-Pesa Float Agent",
        category: "EWALLET",
        defaultServiceFeeMinor: 1500,
        commissionBps: 100
      },
      {
        providerCode: "SAFARICOM_AIRTIME",
        providerName: "Safaricom Airtime Top-Up",
        category: "AIRTIME_LOAD",
        defaultServiceFeeMinor: 0,
        commissionBps: 300
      }
    ]
  },
  {
    packId: "pack-mx-abarrotes",
    name: "Mexico Tienda de Abarrotes Pack",
    region: "Latin America",
    countryCode: "MX",
    defaultCurrencyCode: "MXN",
    taxIdLabel: "RFC",
    defaultTaxRules: [
      { id: "mx_iva_16", name: "16% IVA (Inclusive)", rateBps: 1600, mode: "INCLUSIVE" }
    ],
    floatTemplates: [
      {
        providerCode: "RECARGA_TELCEL",
        providerName: "Recarga Tiempo Aire",
        category: "AIRTIME_LOAD",
        defaultServiceFeeMinor: 200,
        commissionBps: 400
      }
    ]
  },
  {
    packId: "pack-us-general",
    name: "United States Small Retail & Bodega Pack",
    region: "North America",
    countryCode: "US",
    defaultCurrencyCode: "USD",
    taxIdLabel: "EIN",
    defaultTaxRules: [
      { id: "us_sales_825", name: "Sales Tax 8.25%", rateBps: 825, mode: "EXCLUSIVE" }
    ],
    floatTemplates: []
  }
]);
