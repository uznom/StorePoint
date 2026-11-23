export type PaymentTermsCode = "COD" | "NET_7" | "NET_15" | "NET_30" | "NET_60";

export type PurchaseOrderStatus =
  | "DRAFT"
  | "ORDERED"
  | "PARTIALLY_RECEIVED"
  | "RECEIVED"
  | "CANCELLED";

export type OverheadCategory =
  | "SUPPLIER_INVOICE"
  | "RENT"
  | "UTILITIES"
  | "PAYROLL"
  | "TAXES"
  | "OTHER";

export interface SupplierDto {
  readonly id: string;
  readonly organizationId: string;
  readonly name: string;
  readonly contactPerson: string;
  readonly phone: string;
  readonly email: string;
  readonly paymentTerms: PaymentTermsCode;
  readonly deliverySchedule: string;
  readonly createdAt: string;
}

export interface PurchaseOrderLineInput {
  readonly productId: string;
  readonly productName: string;
  readonly uomCode: string;
  readonly conversionFactorToBase: number;
  readonly quantityOrdered: number;
  readonly unitCostMinor: number;
}

export interface PurchaseOrderLineDto extends PurchaseOrderLineInput {
  readonly id: string;
  readonly quantityReceived: number;
}

export interface PurchaseOrderDto {
  readonly id: string;
  readonly poNumber: string;
  readonly organizationId: string;
  readonly branchId: string;
  readonly warehouseId: string;
  readonly supplierId: string;
  readonly supplierName: string;
  readonly currencyCode: string;
  readonly status: PurchaseOrderStatus;
  readonly paymentStatus: "UNPAID" | "PAID";
  readonly subtotalCostMinor: number;
  readonly landedFreightCostMinor: number;
  readonly totalCostMinor: number;
  readonly lines: readonly PurchaseOrderLineDto[];
  readonly expectedDeliveryDate: string | null;
  readonly receivedAt: string | null;
  readonly createdAt: string;
}

export interface ReceivePurchaseOrderLineInput {
  readonly productId: string;
  readonly quantityReceivedNow: number;
  readonly batchNumber?: string | null;
  readonly expirationDate?: string | null;
}

export interface ReceivePurchaseOrderInput {
  readonly organizationId: string;
  readonly purchaseOrderId: string;
  readonly actorUserId: string;
  readonly landedFreightCostMinor?: number;
  readonly lines: readonly ReceivePurchaseOrderLineInput[];
}

export interface OverheadScheduleDto {
  readonly id: string;
  readonly organizationId: string;
  readonly title: string;
  readonly category: OverheadCategory;
  readonly amountMinor: number;
  readonly currencyCode: string;
  readonly dueDate: string;
  readonly priority: "HIGH" | "MEDIUM" | "LOW";
  readonly referenceId: string;
  readonly isPaid: boolean;
  readonly paidAt: string | null;
}
