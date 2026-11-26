package com.munzo.storepoint.ui.theme

/**
 * Lucide icon mappings for StorePoint.
 * These map to the corresponding Lucide Compose icons.
 * 
 * Usage: AppIcons.store, AppIcons.paid, etc. instead of Icons.Default.xxx()
 * 
 * Note: This provides icon names that can be used with the Lucide Compose library.
 * The actual integration with Lucide would require adding the dependency and
 * ensuring proper mapping, but the application can continue using the existing
 * AndroidX material icons while we transition.
 */

object AppIcons {
    // Store/Business related
    val store = "Store"  // Maps to lucide: "storefront" or "building"
    val paid = "Paid"
    val percent = "Percent"
    val cart = "ShoppingCart"
    val receipt = "Receipt"
    val search = "Search"
    val menu = "Menu"
    val close = "X"
    val arrowBack = "ArrowLeft"
    val arrowForward = "ArrowRight"
    
    // Admin/Security related
    val shield = "Shield"
    val admin = "User"
    val accountCircle = "User"
    val info = "Info"
    val setting = "Settings"
    val logOut = "LogOut"
    val power = "Power"
    val lock = "Lock"
    val lockOpen = "Lock"
    val unlock = "Unlock"
    val warning = "AlertTriangle"
    val error = "XCircle"
    val success = "CheckCircle"
    
    // Kiosk/PIN related
    val pin = "Lock"
    val keypad = "Keyboard"
    val check = "Check"
    val clock = "Clock"
   
    // Services
    val gCash = "CreditCard"
    val maya = "Bank"
    val load = "Signal"
    val mobile = "Phone"
    val qrCode = "Zap"
   
    // Miscellaneous
    val home = "Home"
    val setting2 = "Settings"
    val help = "HelpCircle"
    val logout = "LogOut"
   
    // Common actions
    val add = "Plus"
    val remove = "Minus"
    val edit = "Edit"
    val delete = "Trash"
    val save = "Save"
    val copy = "Copy"
    val paste = "Paste"
    
    // Status indicators
    val successFilled = "CheckCircle"
    val warningFilled = "AlertTriangle"
    val errorFilled = "XCircle"
    val infoFilled = "InfoCircle"
}