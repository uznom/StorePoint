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
3. **Admin PIN**: Create a secure 4-digit Master PIN used to access inventory, reports, and administrative settings. Avoid repeats (`0000`) and simple patterns (`1234`, `2580`) — StorePoint rejects them.
4. **Initial Services**: Enable GCash, Maya, or Telco Load features if your store offers digital cash-in/out services.

---

## 2. Cashier Operations

### Starting a Shift
1. Sign in — StorePoint offers three ways:
   - **Fingerprint** (fastest): if you have enrolled, the prompt appears automatically when the app opens and you just touch the sensor.
   - **4-digit PIN**: type your username, then your PIN. Sign-in submits automatically on the 4th digit.
   - **Badge scan**: tap **Tap to Scan Badge** and hold your staff barcode to the camera.
2. Enter the **Opening Cash Float** (the cash bills and coins already inside the cash drawer before sales start).
3. Tap **Start Shift**.

> **Upgrading from an older version?** Your account now needs a **4-digit PIN** instead of 6. Sign in once with your existing 6-digit PIN, choose a new 4-digit PIN, and you're done. Your old PIN still works for that one step — it is never accepted for normal sign-in afterwards.

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

### Reloading Your Own Wallet Float (Owners)

The balances above represent **your own float** - the money you personally loaded into
your GCash / Smart-TNT / Globe-TM accounts so the store can sell against it. When you
top up one of those wallets, record it in StorePoint so the register matches reality:

1. Open **Admin Dashboard > Security**.
2. Find the **Reload Wallet Float** card.
3. Select the wallet you funded (**GCash**, **Smart / TNT**, or **Globe / TM**).
4. Tap **Reload**, enter the amount you loaded, and an optional reference number.
5. Confirm. The float increases and a `RELOAD` entry is written to the ledger.

> **Admin only.** A cashier cannot reload a float. A staff member topping up their own
> float is a direct route to unaccounted cash, so the check is enforced in the app
> itself, not just hidden behind a disabled button.

### Viewing the Wallet Ledger

Tap **View ledger history** on the same card to see every movement for a wallet:
reloads, customer loads that drew the float down, and shift-close reconciliations -
each with the running balance. The **system-derived balance** shown at the top is the
sum of all recorded movements, and is the figure StorePoint reconciles against when you
count the physical wallet at the end of a shift.

This exists because a wallet balance alone cannot explain itself: without a ledger, a
peso removed by a customer load looks identical to a mistyped adjustment, and a wrong
keystroke silently overwrites the float with no trace. With the ledger, every peso in
and out is attributable.

### Reclaiming a Terminal (to wipe/reinstall)

If you need to deliberately remove StorePoint from a terminal you provisioned as device
owner, you must first revoke the device-owner role - the app blocks its own uninstall
while that role is active:

```bash
adb shell dpm remove-active-admin com.munzo.storepoint/.StorePointDeviceAdminReceiver
```

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

**PIN rules:** every PIN is exactly **4 digits**. Obvious values (`0000`, `1111`, `1234`, `4321`, `2580`, `1212`) are rejected for every account, including ones you create for staff. PINs are salted and hashed with PBKDF2 before storage — the app never writes a plaintext PIN to disk.

**Fingerprint sign-in:** any account (Admin or Cashier) can enrol a fingerprint. The first time a staff member signs in successfully with their PIN, StorePoint offers to bind a fingerprint to that account. Afterwards they reach the register in zero taps. Admins can revoke enrolment per user from **Admin Dashboard → Security → Biometric Security**.

**If someone forgets their PIN:** an Admin can reset it from the same Staff list. Because a 4-digit PIN is a short secret, StorePoint applies an escalating lockout after repeated wrong attempts (30s → 60s → 120s → 300s), tracked per account.

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

---

### Method 4: Provisioning a Terminal as Device Owner (REQUIRED for real Kiosk Lockdown)

> **Read this before relying on kiosk mode to secure a cash register.**

StorePoint has three security states. They are **not** interchangeable:

| Status shown in Admin > Security | What it actually means |
|---|---|
| `FULL TERMINAL LOCKDOWN` | StorePoint is the **device owner**. Kiosk lockdown is genuinely enforced. |
| `REDUCED - SCREEN PINNING ONLY` | Device Administrator is on, but the app is **not** device owner. Lockdown is dismissable. |
| `PROTECTION OFF` | No device protection. Nothing is enforced. |

**Why this matters:** enabling *Device Administrator* on its own does **not** secure the
terminal. A device admin cannot block its own uninstallation, cannot stop the user
clearing its data, and cannot enable true LockTask. Android only honours those when an
app is the **device owner**. Until that is done, "Kiosk Mode" on the Security tab runs in
reduced screen-pinning mode, which any user can dismiss with **Back + Overview**.

StorePoint now detects this and tells you - the Security tab shows a warning and a toast
appears when lockdown is entered in reduced mode. But it can only *report* the state; the
provisioning itself must be done once, on the terminal, by someone with ADB access.

#### Prerequisites

- A freshly reset / factory-fresh tablet (device owner can only be set with **no accounts
  on the device** - no Google account, no other apps signed in).
- USB debugging enabled (Settings > About > tap Build number 7x, then Developer options).
- ADB installed on your computer.

#### Steps

```bash
# 1. Install StorePoint while the device is still freshly reset
adb install StorePoint-v1.0.1.4.apk

# 2. Make StorePoint the device owner.
#    The receiver must be the only admin on a device with no accounts.
adb shell dpm set-device-owner com.munzo.storepoint/.StorePointDeviceAdminReceiver

# 3. Verify - must report "Device owner: com.munzo.storepoint"
adb shell dpm list-owners
```

#### Verify on the device

Open **Admin Dashboard > Security**. The status must read **`FULL TERMINAL LOCKDOWN`**.
Only then does activating Kiosk Mode produce real, non-dismissable lockdown.

#### Troubleshooting

```bash
# "Not allowed to set the device owner because there are already several users on the device"
# -> you must factory-reset the tablet, then provision BEFORE adding any Google account.

# "Trying to set the device owner, but device owner is already set"
adb shell dpm list-owners
adb shell dpm remove-active-admin com.munzo.storepoint/.StorePointDeviceAdminReceiver
# then re-run step 2 on a factory-fresh device

# Check the restrictions actually took effect
adb shell dpm list-user-restrictions
# expect DISALLOW_APPS_CONTROL and DISALLOW_UNINSTALL_APPS to be present
```
