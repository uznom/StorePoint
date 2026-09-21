# StorePoint User Guide & Cashier Manual

A guide for store owners, managers, and cashiers using the StorePoint Point of Sale and Inventory Management System.

---

## Table of Contents
1. [Initial Setup Wizard](#1-initial-setup-wizard)
2. [Cashier Operations](#2-cashier-operations)
   - [Starting a Shift (Opening Float)](#starting-a-shift)
   - [Adding Products to Cart](#adding-products-to-cart)
   - [Packaging Units & Tingi (Multi-UOM)](#packaging-units--tingi)
   - [Discounts & Senior/PWD Exemptions](#discounts--seniorpwd-exemptions)
   - [Parking & Resuming Transactions](#parking--resuming-transactions)
   - [Completing a Sale](#completing-a-sale)
   - [Handling Returns & Refunds](#handling-returns--refunds)
   - [Closing a Shift (Z-Reading)](#closing-a-shift)
3. [E-Wallet & Telco Load Ledgers](#3-e-wallet--telco-load-ledgers)
4. [Admin & Inventory Management](#4-admin--inventory-management)
   - [Adding & Editing Products](#adding--editing-products)
   - [Managing Product Variants (Pack Sizes)](#managing-product-variants)
   - [Suppliers & Purchase Orders](#suppliers--purchase-orders)
   - [User Accounts & Staff PINs](#user-accounts--staff-pins)
5. [Hardware Configuration](#5-hardware-configuration)
   - [Setting Up a Bluetooth Thermal Printer](#setting-up-a-bluetooth-thermal-printer)
   - [Configuring Barcode Scanners](#configuring-barcode-scanners)
6. [Data Backup & Maintenance](#6-data-backup--maintenance)
7. [Dedicated Terminal & Kiosk Lockdown (ADB Commands)](#7-dedicated-terminal--kiosk-lockdown-adb-commands)
   - [Method 1: True Kiosk Mode via Device Owner](#method-1-true-kiosk-mode-via-device-owner)
   - [Method 2: Set StorePoint as Default Home Launcher](#method-2-set-storepoint-as-default-home-launcher)
   - [Method 3: Terminal Hardware & Immersive Overrides](#method-3-terminal-hardware--immersive-overrides)

---

## 1. Initial Setup Wizard

When launching StorePoint for the first time, the **Setup Wizard** will guide you through the essentials:
1. **Store Name**: Enter your shop name (e.g., *Aling Nena's Sari-Sari Store*).
2. **Currency**: Set your store's currency symbol (defaults to `₱` Philippine Peso).
3. **Admin PIN**: Create a secure 4 to 6 digit Master PIN used to access inventory, reports, and administrative settings.
4. **Initial Services**: Enable GCash, Maya, or Telco Load features if your store offers digital cash-in/out services.

---

## 2. Cashier Operations

### Starting a Shift
1. Log in with your Cashier PIN or name.
2. Enter the **Opening Cash Float** (the cash bills and coins already inside the cash drawer before sales start).
3. Tap **Start Shift**.

### Adding Products to Cart
- **Tap Product Card**: Tap any item in the catalog grid.
- **Search Bar**: Type the product name or brand to filter instantly.
- **Barcode Scanner**: Tap the **Barcode Icon** in the top bar to activate the camera scanner, or simply trigger an external USB/Bluetooth scanner gun.

### Packaging Units & Tingi
For products sold in multiple formats (e.g. single stick vs. pack of 20, or single sachet vs. 12-pack strip):
1. Tap the product card bearing the **UOM** badge.
2. A variant modal will appear showing the available packaging units, multipliers, and prices.
3. Select the desired packaging format. The app automatically multiplies the price and accurately decrements the underlying stock count.

### Discounts
1. In the cart pane, tap **Add Discount**.
2. Choose between:
   - **Percentage (%)**: e.g., 5% or 10% off the total.
   - **Fixed Amount (₱)**: e.g., ₱20 off.

*(Statutory Senior Citizen / PWD VAT exemptions are planned and not yet implemented.)*

### Parking & Resuming Transactions
- If a customer needs to pick up more items while at the counter, tap the **Park / Hold** icon.
- Enter an optional note (e.g. *"Customer in blue shirt"*).
- The cart clears so you can serve the next customer.
- When ready, tap the **Parked Orders** button to resume the held cart.

### Completing a Sale
1. Tap **Checkout** at the bottom of the cart.
2. Select payment method:
   - **Cash**: Enter the amount tendered. The screen displays the exact change to give the customer.
   - **GCash / Maya**: Prompts for reference number and verifies digital wallet balance.
3. Tap **Complete Sale**. If a Bluetooth thermal printer is connected, a receipt will print automatically.

*(Store Credit / "Utang" split-payment is planned and not yet implemented.)*

### Handling Returns & Refunds
1. Open the drawer navigation menu or tap the **Returns Icon** in the top bar.
2. Search for the transaction using the Receipt ID or customer name.
3. Select the returned items and quantities.
4. Choose the return reason (*Defective / Spoiled*, *Wrong Item*, *Customer Return*).
5. Toggle **Restock to Inventory** if the item is undamaged and can be resold.
6. Select refund mode (**Cash**, **Store Credit**, or **Exchange**).

### Closing a Shift
1. At the end of the day or cashier rotation, open the drawer and tap **Close Shift**.
2. Count the physical cash in the drawer and enter the amount.
3. StorePoint performs automated reconciliation (Opening Float + Cash Sales + Paid In - Paid Out) and highlights any cash overage or shortage.
4. Tap **Finalize & Print Z-Reading** to produce the end-of-shift report.

---

## 3. E-Wallet & Telco Load Ledgers

StorePoint includes dedicated cash-in and cash-out ledgers for micro-retailers:
- **GCash / Maya Cash-In**: Customer hands cash to the store; store sends digital money to the customer's mobile number. The app adds the service fee and increases physical cash while decreasing the store's digital balance.
- **GCash / Maya Cash-Out**: Customer sends digital money to the store; store hands out cash bills. The app deducts the physical cash drawer and increases the digital balance.

---

## 4. Admin & Inventory Management

Access the **Admin Dashboard** via the side menu (requires Admin PIN).

### Adding & Editing Products
1. Go to **Products** tab.
2. Tap **+ Add Product**.
3. Fill in:
   - **Name** & **Category**
   - **Cost Price** & **Selling Price** (The profit margin percentage updates live)
   - **Stock Count** & **Low Stock Alert Threshold**
   - **Expiration Date** (optional)
   - **Barcode** (scan with camera or type)

### Managing Product Variants
In the product edit screen, tap **Manage Variants** to add custom unit conversions (e.g., *Sachet*, *Box of 10*, *Case of 50*). Set specific prices and multipliers so that selling a "Box of 10" automatically deducts 10 units from single-piece stock.

### Suppliers & Purchase Orders
1. Go to **Suppliers & POs** in the Admin Dashboard.
2. **Suppliers**: Add contact details, address, and credit terms for distributors.
3. **Purchase Orders**: Tap **+ New PO**, select a supplier, and add order line items with expected costs.
4. **Receiving Deliveries**: When the shipment arrives, open the PO, tap **Receive Items**, verify quantities, and confirm. Stock levels update immediately.

### User Accounts & Staff PINs
Navigate to **Users & Security** to manage staff accounts. Assign roles:
- **Cashier**: Can only access the POS sales terminal and their own shift drawer.
- **Manager / Admin**: Has full access to inventory, costs, supplier orders, reports, and settings.

---

## 5. Hardware Configuration

### Setting Up a Bluetooth Thermal Printer
1. Turn on your 58mm or 80mm Bluetooth thermal receipt printer.
2. On your Android tablet or phone, open **Android Settings → Bluetooth** and pair with the printer (PIN is typically `0000` or `1234`).
3. In StorePoint, open **Admin Dashboard → Printer Settings**.
4. Tap **Scan for Paired Printers** and select your printer from the list.
5. Tap **Print Test Receipt** to verify connection.

### Configuring Barcode Scanners
- **Camera Scanning**: No configuration required. Ensure camera permissions are granted.
- **Physical USB / Bluetooth Scanners**: Plug in the USB dongle or pair the Bluetooth scanner gun. StorePoint detects incoming scanner inputs natively without extra drivers.

---

## 6. Data Backup & Maintenance

### Creating a Backup
1. In Admin Dashboard, go to **Settings → Database Backup & Restore**.
2. Tap **Export Backup (JSON)**.
3. Save the file to your device storage, an SD card, or upload it to Google Drive / flash drive.

### Restoring a Backup
1. On the new or reset device, open **Settings → Database Backup & Restore**.
2. Tap **Import Backup**.
3. Select the previously exported `.json` file and confirm.

---

## 7. Dedicated Terminal & Kiosk Lockdown (ADB Commands)

For dedicated retail countertops and POS terminals, StorePoint can be permanently locked so that staff or customers cannot exit the application, access Android settings, or bypass the terminal.

### Method 1: True Kiosk Mode via Device Owner (Recommended)

When StorePoint is provisioned as the Android **Device Owner**, standard screen pinning is upgraded to enterprise **Dedicated Device (COSU) Lock Task Mode**:
- The exit gesture (holding *Back + Overview*) is completely disabled.
- System status bar pull-down, navigation bar buttons, and the recent apps switcher are suppressed.
- The app cannot be force-stopped, uninstalled, or cleared of data from Android Settings (`UserManager.DISALLOW_APPS_CONTROL` and `DISALLOW_UNINSTALL_APPS`).

```bash
# 1. Install StorePoint on the terminal
adb install -r -g StorePoint.apk

# 2. Grant Device Owner status to StorePoint
# Note: Remove all Google accounts and user accounts from Android Settings > Accounts before running this command.
adb shell dpm set-device-owner com.munzo.storepoint/.StorePointDeviceAdminReceiver
```

> **To remove Device Owner if testing on a personal device:**
> ```bash
> adb shell dpm remove-active-admin com.munzo.storepoint/.StorePointDeviceAdminReceiver
> ```

---

### Method 2: Set StorePoint as Default Home Launcher

StorePoint declares `android.intent.category.HOME` in its manifest, allowing it to act as the primary Android desktop / launcher:

```bash
# Set StorePoint as the permanent default Home launcher
adb shell cmd package set-home-activity com.munzo.storepoint/.MainActivity

# Clear and restore default Android launcher
adb shell cmd package set-home-activity --clear
```

---

### Method 3: Terminal Hardware & Immersive Overrides

```bash
# Enable permanent full-screen immersive mode (hides navigation bar and status bar system-wide)
adb shell settings put global policy_control immersive.full=*

# Restore default navigation bar and status bar behavior
adb shell settings put global policy_control null

# Keep screen permanently awake while plugged into AC power (recommended for POS counters)
adb shell settings put global stay_on_while_plugged_in 3

# Grant runtime permissions directly without on-screen prompts
adb shell pm grant com.munzo.storepoint android.permission.CAMERA
adb shell pm grant com.munzo.storepoint android.permission.POST_NOTIFICATIONS
```
