package com.stashly.stashly;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.moshi.MoshiConverterFactory;

public class MainActivity extends AppCompatActivity {

    // View panes (TABS)
    private LinearLayout mDashboardView;
    private LinearLayout mInventoryView;
    private FrameLayout mGroupView;
    private LinearLayout mSettingsView;

    // Bottom Navigation Elements
    private LinearLayout mTabDashboard;
    private LinearLayout mTabInventory;
    private View mTabScan;
    private LinearLayout mTabGroup;
    private LinearLayout mTabSettings;

    private TextView mLblDashboard;
    private TextView mLblInventory;
    private TextView mLblGroup;
    private TextView mLblSettings;

    // Interactive Dashboard Widgets
    private TextView mDashboardBudgetSpentLbl;
    private TextView mDashboardUtilizationLbl;
    private TextView mDashboardAllocatedLbl;
    private LinearLayout mDashboardProgressContainer;
    private TextView mDashboardAssetsVal;
    private TextView mDashboardLiquidVal;
    private TextView mDashboardBudgetAllocated;
    private TextView mBtnEditBudget;
    private LinearLayout mBtnTileAllocate, mBtnTileAnalysis, mBtnTileAllocatedFunds;

    // Group Widget Elements
    private LinearLayout mRecentActivityContainer;

    // Settings elements
    private Button mBtnLogout;
    private Button mBtnResetSpent;

    // Inventory Elements for Filtering
    private EditText mSearchBar;
    private TextView mFilterAll, mFilterFood, mFilterMedicine, mFilterCleaners, mFilterElectronics, mFilterClothes, mFilterFurniture, mFilterServices, mFilterTools;
    private LinearLayout mInventoryContainer;
    private LinearLayout mStagedContainer;

    // Expiry Widgets
    private LinearLayout mExpiryContainer;

    // Staging Data
    private List<StagedItem> mStagedItems = new ArrayList<>();
    private List<Map<String, Object>> mLocalItems = new ArrayList<>();
    
    // AI Recognition (Groq)
    private GroqService mGroqService;
    private static final String GROQ_API_KEY = BuildConfig.GROQ_API_KEY;

    private static class StagedItem {
        String name;
        double price;
        String category;
        String emoji;
        String productionDate;
        String expiryDate;

        StagedItem(String name, double price, String category, String emoji, String productionDate, String expiryDate) {
            this.name = name;
            this.price = price;
            this.category = category;
            this.emoji = emoji;
            this.productionDate = productionDate;
            this.expiryDate = expiryDate;
        }
    }

    // Profile Widgets
    private TextView mToolbarProfileInitials;
    private TextView mSettingsProfileInitials, mSettingsProfileName, mSettingsProfileEmail;

    // Firebase References
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private boolean isFirebaseAvailable = false;
    private DocumentReference mGroupRef;
    private com.google.firebase.firestore.ListenerRegistration mGroupListener;
    private com.google.firebase.firestore.ListenerRegistration mItemsListener;

    // Local state simulation fallbacks
    private double mCurrentBudgetSpent = 0.0;
    private double mCurrentBudgetCap = 0.0;
    private double mBudgetReserved = 0.0;
    private String mCurrentFilter = "All";
    private com.google.firebase.firestore.QuerySnapshot mLastInventorySnapshot;
    private com.google.firebase.firestore.QuerySnapshot mLastActivitiesSnapshot;
    private double mActiveAssetsValue = 0.0;
    private double mLiquidCash = 0.0;
    private String mInviteCode = "STSH9X";
    private String mWorkspaceName = "The BroHouse Crew";
    
    // Joint Workspace System State
    private String mUserRole = "casual"; // admin or casual
    private String mCurrentGroupId = null;
    private com.google.firebase.firestore.ListenerRegistration mUserListener;

    private List<Map<String, Object>> mAllocationsList = new ArrayList<>();
    private static final int[] ALLOCATION_COLORS = {
            0xFF003EC6, // secondary / deep blue
            0xFF00993B, // growth green
            0xFFBA1A1A, // urgency red
            0xFF6200EE, // primary variant
            0xFF03DAC5, // teal
            0xFFFFB74D, // orange
            0xFF9575CD  // purple
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind Views
        initViews();

        // Safe Firebase & AI setup
        initFirebase();
        initGroq();

        // Default to Dashboard tab
        switchTab(1);

        // Set up Listeners
        setupNavigationListeners();
        setupDashboardListeners();
        setupRecentActivityListeners();
        setupGroupListeners();
        setupSettingsListeners();
        setupInventoryLogic();

        // Query cloud states or apply local mock levels
        observeUserState();
        if (mCurrentGroupId == null) {
            applyLocalMockData();
            renderInventoryItems(null);
        }
        updateProfileUI();
    }

    private void initViews() {
        // Main view layers
        mDashboardView = findViewById(R.id.view_dashboard);
        mInventoryView = findViewById(R.id.view_inventory);
        mGroupView = findViewById(R.id.view_group);
        mSettingsView = findViewById(R.id.view_settings);

        // Navigation elements
        mTabDashboard = findViewById(R.id.tab_dashboard);
        mTabInventory = findViewById(R.id.tab_inventory);
        mTabScan = findViewById(R.id.tab_scan);
        mTabGroup = findViewById(R.id.tab_group);
        mTabSettings = findViewById(R.id.tab_settings);

        mLblDashboard = findViewById(R.id.lbl_tab_dashboard);
        mLblInventory = findViewById(R.id.lbl_tab_inventory);
        mLblGroup = findViewById(R.id.lbl_tab_group);
        mLblSettings = findViewById(R.id.lbl_tab_settings);

        // Dashboard outputs
        mDashboardBudgetSpentLbl = findViewById(R.id.dashboard_budget_spent);
        mDashboardUtilizationLbl = findViewById(R.id.dashboard_utilization_text);
        mDashboardAllocatedLbl = findViewById(R.id.dashboard_allocated_text);
        mDashboardProgressContainer = findViewById(R.id.dashboard_progress_container);
        mDashboardAssetsVal = findViewById(R.id.dashboard_assets_val);
        mDashboardLiquidVal = findViewById(R.id.dashboard_liquid_val);
        mDashboardBudgetAllocated = findViewById(R.id.dashboard_budget_allocated);
        mBtnEditBudget = findViewById(R.id.btn_edit_budget);
        mBtnTileAllocate = findViewById(R.id.btn_tile_allocate);
        mBtnTileAnalysis = findViewById(R.id.btn_tile_analysis);
        mBtnTileAllocatedFunds = findViewById(R.id.btn_tile_allocated_funds);

        // Inventory Filtering
        mSearchBar = findViewById(R.id.search_bar);
        mFilterAll = findViewById(R.id.filter_all);
        mFilterFood = findViewById(R.id.filter_food);
        mFilterMedicine = findViewById(R.id.filter_medicine);
        mFilterCleaners = findViewById(R.id.filter_cleaners);
        mFilterElectronics = findViewById(R.id.filter_electronics);
        mFilterClothes = findViewById(R.id.filter_clothes);
        mFilterFurniture = findViewById(R.id.filter_furniture);
        mFilterServices = findViewById(R.id.filter_services);
        mFilterTools = findViewById(R.id.filter_tools);
        
        mInventoryContainer = findViewById(R.id.inventory_grid_container);
        mStagedContainer = findViewById(R.id.container_staged_items);

        mExpiryContainer = findViewById(R.id.expiry_warning_container);

        // Profile widgets
        mToolbarProfileInitials = findViewById(R.id.toolbar_profile_initials);
        mSettingsProfileInitials = findViewById(R.id.settings_profile_initials);
        mSettingsProfileName = findViewById(R.id.settings_profile_name);
        mSettingsProfileEmail = findViewById(R.id.settings_profile_email);

        mRecentActivityContainer = findViewById(R.id.container_recent_activities);

        // Settings
        mBtnLogout = findViewById(R.id.btn_logout);
        mBtnResetSpent = findViewById(R.id.btn_reset_spent);
    }

    private void initFirebase() {
        try {
            mAuth = FirebaseAuth.getInstance();
            mFirestore = FirebaseFirestore.getInstance();
            if (mFirestore != null) {
                isFirebaseAvailable = true;
                mGroupRef = mFirestore.collection("groups").document("STSH9X");
            }
        } catch (Throwable t) {
            isFirebaseAvailable = false;
        }
    }

    private void initGroq() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.groq.com/openai/")
                .addConverterFactory(MoshiConverterFactory.create())
                .client(client)
                .build();
        mGroqService = retrofit.create(GroqService.class);
    }

    private void setupNavigationListeners() {
        if (mTabDashboard != null) mTabDashboard.setOnClickListener(v -> switchTab(1));
        if (mTabInventory != null) mTabInventory.setOnClickListener(v -> switchTab(2));
        if (mTabScan != null) mTabScan.setOnClickListener(v -> showScanOptionsBottomSheet());
        if (mTabGroup != null) mTabGroup.setOnClickListener(v -> switchTab(3));
        if (mTabSettings != null) mTabSettings.setOnClickListener(v -> switchTab(4));
    }

    private void switchTab(int tabIndex) {
        if (mDashboardView != null) mDashboardView.setVisibility(View.GONE);
        if (mInventoryView != null) mInventoryView.setVisibility(View.GONE);
        if (mGroupView != null) mGroupView.setVisibility(View.GONE);
        if (mSettingsView != null) mSettingsView.setVisibility(View.GONE);

        int inactiveMuted = getResources().getColor(R.color.text_muted);
        int activeBlue = getResources().getColor(R.color.accent_blue);

        if (mLblDashboard != null) {
            mLblDashboard.setTextColor(inactiveMuted);
            mLblDashboard.setTypeface(null, Typeface.NORMAL);
        }
        if (mLblInventory != null) {
            mLblInventory.setTextColor(inactiveMuted);
            mLblInventory.setTypeface(null, Typeface.NORMAL);
        }
        if (mLblGroup != null) {
            mLblGroup.setTextColor(inactiveMuted);
            mLblGroup.setTypeface(null, Typeface.NORMAL);
        }
        if (mLblSettings != null) {
            mLblSettings.setTextColor(inactiveMuted);
            mLblSettings.setTypeface(null, Typeface.NORMAL);
        }

        switch (tabIndex) {
            case 1:
                if (mDashboardView != null) mDashboardView.setVisibility(View.VISIBLE);
                if (mLblDashboard != null) {
                    mLblDashboard.setTextColor(activeBlue);
                    mLblDashboard.setTypeface(null, Typeface.BOLD);
                }
                break;
            case 2:
                if (mInventoryView != null) mInventoryView.setVisibility(View.VISIBLE);
                if (mLblInventory != null) {
                    mLblInventory.setTextColor(activeBlue);
                    mLblInventory.setTypeface(null, Typeface.BOLD);
                }
                break;
            case 3:
                if (mGroupView != null) mGroupView.setVisibility(View.VISIBLE);
                if (mLblGroup != null) {
                    mLblGroup.setTextColor(activeBlue);
                    mLblGroup.setTypeface(null, Typeface.BOLD);
                }
                break;
            case 4:
                if (mSettingsView != null) mSettingsView.setVisibility(View.VISIBLE);
                if (mLblSettings != null) {
                    mLblSettings.setTextColor(activeBlue);
                    mLblSettings.setTypeface(null, Typeface.BOLD);
                }
                break;
        }
    }

    private void setupDashboardListeners() {
        if (mBtnEditBudget != null) {
            mBtnEditBudget.setOnClickListener(v -> showBudgetUpdateDialog());
        }
        if (mBtnTileAllocate != null) {
            mBtnTileAllocate.setOnClickListener(v -> showAllocateBudgetDialog());
        }

        if (mBtnTileAnalysis != null) {
            mBtnTileAnalysis.setOnClickListener(v -> showDetailedAnalysis());
        }

        if (mBtnTileAllocatedFunds != null) {
            mBtnTileAllocatedFunds.setOnClickListener(v -> showAllocationsDetailDialog());
        }

        if (mDashboardAllocatedLbl != null) {
            mDashboardAllocatedLbl.setOnClickListener(v -> showAllocationsDetailDialog());
        }
    }

    private void showAllocationsDetailDialog() {
        if (mAllocationsList == null || mAllocationsList.isEmpty()) {
            Toast.makeText(this, "No funds currently allocated.", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        float density = getResources().getDisplayMetrics().density;
        container.setPadding((int)(20 * density), (int)(16 * density), (int)(20 * density), (int)(16 * density));

        TextView infoTv = new TextView(this);
        infoTv.setText("Tap an allocation below to edit or delete it.");
        infoTv.setTextColor(getResources().getColor(R.color.text_muted));
        infoTv.setTextSize(12);
        infoTv.setPadding(0, 0, 0, (int)(12 * density));
        container.addView(infoTv);

        final AlertDialog detailDialog = new AlertDialog.Builder(this)
                .setTitle("Allocated Funds Breakdown")
                .setView(container)
                .setPositiveButton("Close", null)
                .create();

        for (int aIdx = 0; aIdx < mAllocationsList.size(); aIdx++) {
            Map<String, Object> alloc = mAllocationsList.get(aIdx);
            if (alloc == null) continue;
            String title = (String) alloc.get("title");
            double amount = 0.0;
            if (alloc.get("amount") instanceof Number) {
                amount = ((Number) alloc.get("amount")).doubleValue();
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding((int)(8 * density), (int)(12 * density), (int)(8 * density), (int)(12 * density));
            row.setClickable(true);
            row.setFocusable(true);

            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            row.setBackgroundResource(outValue.resourceId);

            TextView titleTv = new TextView(this);
            titleTv.setText(title);
            titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            titleTv.setTextColor(getResources().getColor(R.color.primary));
            titleTv.setTypeface(null, Typeface.BOLD);

            TextView amountTv = new TextView(this);
            amountTv.setText(String.format(Locale.US, "$%,.2f", amount));
            amountTv.setTextColor(ALLOCATION_COLORS[aIdx % ALLOCATION_COLORS.length]);
            amountTv.setTypeface(null, Typeface.BOLD);

            row.addView(titleTv);
            row.addView(amountTv);

            final int index = aIdx;
            final String finalTitle = title;
            final double finalAmount = amount;
            row.setOnClickListener(v -> {
                detailDialog.dismiss();
                showEditAllocationDialog(index, finalTitle, finalAmount);
            });

            container.addView(row);
        }

        detailDialog.show();
    }

    private void showEditAllocationDialog(final int index, final String currentTitle, final double currentAmount) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_manual_input, null);
        final EditText amountInput = dialogView.findViewById(R.id.input_item_price);
        final EditText reasonInput = dialogView.findViewById(R.id.input_item_name);
        
        // Hide irrelevant fields
        dialogView.findViewById(R.id.layout_category_selection).setVisibility(View.GONE);
        dialogView.findViewById(R.id.layout_date_selection).setVisibility(View.GONE);

        TextView nameLabel = dialogView.findViewById(R.id.label_item_name);
        if (nameLabel != null) nameLabel.setText("Allocation Name");
        TextView priceLabel = dialogView.findViewById(R.id.label_item_price);
        if (priceLabel != null) priceLabel.setText("Allocated Amount ($)");

        reasonInput.setHint("What are you allocating for? (e.g. Travel)");
        reasonInput.setText(currentTitle);
        amountInput.setHint("Enter amount to allocate");
        amountInput.setText(String.valueOf(currentAmount));

        new AlertDialog.Builder(this)
                .setTitle("Edit Allocation")
                .setView(dialogView)
                .setPositiveButton("Update", (d, w) -> {
                    String newTitle = reasonInput.getText().toString().trim();
                    String amountStr = amountInput.getText().toString().trim();
                    if (!newTitle.isEmpty() && !amountStr.isEmpty()) {
                        try {
                            double newAmount = Double.parseDouble(amountStr);
                            
                            if (isFirebaseAvailable && mGroupRef != null && mCurrentGroupId != null) {
                                List<Map<String, Object>> updatedAllocations = new ArrayList<>(mAllocationsList);
                                if (index >= 0 && index < updatedAllocations.size()) {
                                    Map<String, Object> alloc = new HashMap<>(updatedAllocations.get(index));
                                    alloc.put("title", newTitle);
                                    alloc.put("amount", newAmount);
                                    updatedAllocations.set(index, alloc);
                                    
                                    double newBudgetReserved = 0.0;
                                    for (Map<String, Object> a : updatedAllocations) {
                                        if (a != null && a.get("amount") instanceof Number) {
                                            newBudgetReserved += ((Number) a.get("amount")).doubleValue();
                                        }
                                    }
                                    
                                    mGroupRef.update("allocations", updatedAllocations, "budgetReserved", newBudgetReserved);
                                    
                                    // Log activity
                                    Map<String, Object> activity = new HashMap<>();
                                    activity.put("title", "Allocation Edited");
                                    activity.put("desc", "Updated allocation: " + newTitle + " ($" + newAmount + ")");
                                    activity.put("amount", 0.0);
                                    activity.put("emoji", "✏️");
                                    activity.put("timestamp", FieldValue.serverTimestamp());
                                    mGroupRef.collection("activities").add(activity);
                                    
                                    Toast.makeText(this, "Allocation updated!", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                // Local fallback
                                if (index >= 0 && index < mAllocationsList.size()) {
                                    mBudgetReserved -= currentAmount;
                                    mBudgetReserved += newAmount;
                                    
                                    Map<String, Object> alloc = mAllocationsList.get(index);
                                    if (alloc != null) {
                                        alloc.put("title", newTitle);
                                        alloc.put("amount", newAmount);
                                    }
                                    
                                    applyLocalMockData();
                                    Toast.makeText(this, "Allocation updated (Local)", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton("Delete", (d, w) -> {
                    if (isFirebaseAvailable && mGroupRef != null && mCurrentGroupId != null) {
                        List<Map<String, Object>> updatedAllocations = new ArrayList<>(mAllocationsList);
                        if (index >= 0 && index < updatedAllocations.size()) {
                            updatedAllocations.remove(index);
                            
                            double newBudgetReserved = 0.0;
                            for (Map<String, Object> a : updatedAllocations) {
                                if (a != null && a.get("amount") instanceof Number) {
                                    newBudgetReserved += ((Number) a.get("amount")).doubleValue();
                                }
                            }
                            
                            mGroupRef.update("allocations", updatedAllocations, "budgetReserved", newBudgetReserved);
                            
                            // Log activity
                            Map<String, Object> activity = new HashMap<>();
                            activity.put("title", "Allocation Deleted");
                            activity.put("desc", "Removed allocation: " + currentTitle);
                            activity.put("amount", 0.0);
                            activity.put("emoji", "🗑️");
                            activity.put("timestamp", FieldValue.serverTimestamp());
                            mGroupRef.collection("activities").add(activity);
                            
                            Toast.makeText(this, "Allocation deleted!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Local fallback
                        if (index >= 0 && index < mAllocationsList.size()) {
                            mBudgetReserved -= currentAmount;
                            mAllocationsList.remove(index);
                            applyLocalMockData();
                            Toast.makeText(this, "Allocation deleted (Local)", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAllocateBudgetDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_manual_input, null);
        EditText amountInput = dialogView.findViewById(R.id.input_item_price);
        EditText reasonInput = dialogView.findViewById(R.id.input_item_name);
        
        // Hide irrelevant fields
        dialogView.findViewById(R.id.layout_category_selection).setVisibility(View.GONE);
        dialogView.findViewById(R.id.layout_date_selection).setVisibility(View.GONE);

        reasonInput.setHint("What are you allocating for? (e.g. Travel)");
        amountInput.setHint("Enter amount to allocate");

        new AlertDialog.Builder(this)
                .setTitle("Allocate Monthly Budget")
                .setView(dialogView)
                .setPositiveButton("Allocate", (d, w) -> {
                    String reason = reasonInput.getText().toString().trim();
                    String amountStr = amountInput.getText().toString().trim();
                    if (!reason.isEmpty() && !amountStr.isEmpty()) {
                        try {
                            double amount = Double.parseDouble(amountStr);
                            
                            if (isFirebaseAvailable && mGroupRef != null && mCurrentGroupId != null) {
                                Map<String, Object> allocation = new HashMap<>();
                                allocation.put("title", reason);
                                allocation.put("amount", amount);
                                mGroupRef.update("budgetReserved", FieldValue.increment(amount),
                                               "allocations", FieldValue.arrayUnion(allocation));
                                
                                // Log activity
                                Map<String, Object> activity = new HashMap<>();
                                activity.put("title", "Budget Allocation");
                                activity.put("desc", "Reserved $" + amount + " for " + reason);
                                activity.put("amount", 0.0);
                                activity.put("emoji", "🎯");
                                activity.put("timestamp", FieldValue.serverTimestamp());
                                mGroupRef.collection("activities").add(activity);
                            } else {
                                mBudgetReserved += amount;
                                Map<String, Object> localAlloc = new HashMap<>();
                                localAlloc.put("title", reason);
                                localAlloc.put("amount", amount);
                                mAllocationsList.add(localAlloc);
                                applyLocalMockData();
                                Toast.makeText(this, "Allocated $" + amount + " for " + reason + " (Local)", Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDetailedAnalysis() {
        if (!isFirebaseAvailable || mGroupRef == null) {
            Toast.makeText(this, "Cloud data unavailable for analysis.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (GROQ_API_KEY == null || GROQ_API_KEY.isEmpty() || GROQ_API_KEY.contains("PASTE_YOUR")) {
            Toast.makeText(this, "AI API Key not configured.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "AI analyzing your stash...", Toast.LENGTH_SHORT).show();

        // 1. Build Data Summary
        StringBuilder summary = new StringBuilder();
        summary.append("Financial Overview:\n");
        summary.append("- Monthly Budget: $").append(mCurrentBudgetCap).append("\n");
        summary.append("- Budget Spent: $").append(mCurrentBudgetSpent).append("\n");
        summary.append("- Active Assets Value: $").append(mActiveAssetsValue).append("\n");
        summary.append("- Liquid Cash remaining: $").append(mLiquidCash).append("\n\n");

        summary.append("Recent Activities:\n");
        if (mLastActivitiesSnapshot != null) {
            for (QueryDocumentSnapshot doc : mLastActivitiesSnapshot) {
                summary.append("- ").append(doc.getString("title")).append(": ")
                        .append(doc.getString("desc")).append(" (")
                        .append(doc.getDouble("amount")).append(")\n");
            }
        } else {
            summary.append("No recent activities found.\n");
        }

        summary.append("\nInventory Status:\n");
        if (mLastInventorySnapshot != null) {
            summary.append("- Total items in stash: ").append(mLastInventorySnapshot.size()).append("\n");
            // Check for expiring items (logic simplified here)
            int expiringCount = 0;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar threshold = Calendar.getInstance();
            threshold.add(Calendar.DAY_OF_YEAR, 2);
            for (QueryDocumentSnapshot doc : mLastInventorySnapshot) {
                String expDateStr = doc.getString("expiryDate");
                if (expDateStr != null && !expDateStr.isEmpty()) {
                    try {
                        Date expDate = sdf.parse(expDateStr);
                        if (expDate != null && expDate.before(threshold.getTime())) expiringCount++;
                    } catch (Exception ignored) {}
                }
            }
            summary.append("- Items expiring soon: ").append(expiringCount).append("\n");
        }

        // 2. Call Groq
        String prompt = "You are a professional financial and household efficiency advisor. Analyze the following household data and provide: " +
                "1. A brief analysis of the recent financial trend. " +
                "2. Specific, actionable advice to reduce unnecessary financial losses or waste. " +
                "Return the response in JSON format with two keys: 'analysis' (string) and 'advice' (string). Use clear, professional, and encouraging tone.";

        GroqService.GroqRequest.Content textContent = new GroqService.GroqRequest.Content("text", prompt + "\n\nData Summary:\n" + summary.toString());
        GroqService.GroqRequest.Message message = new GroqService.GroqRequest.Message("user", Collections.singletonList(textContent));
        GroqService.GroqRequest request = new GroqService.GroqRequest("meta-llama/llama-4-scout-17b-16e-instruct", Collections.singletonList(message));

        mGroqService.generateContent("Bearer " + GROQ_API_KEY, request).enqueue(new Callback<GroqService.GroqResponse>() {
            @Override
            public void onResponse(@NonNull Call<GroqService.GroqResponse> call, @NonNull Response<GroqService.GroqResponse> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                    String resultJson = response.body().choices.get(0).message.content;
                    runOnUiThread(() -> displayAiAnalysis(resultJson));
                } else {
                    String errorMsg = "Unknown error";
                    try {
                        if (response.errorBody() != null) {
                            errorMsg = response.errorBody().string();
                        }
                    } catch (IOException ignored) {}
                    Log.e("Groq", "Analysis failed: " + response.code() + " - " + errorMsg);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "AI Analysis failed: " + response.code(), Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(@NonNull Call<GroqService.GroqResponse> call, @NonNull Throwable t) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void displayAiAnalysis(String json) {
        try {
            JSONObject root = new JSONObject(json.replaceAll("```json", "").replaceAll("```", "").trim());
            String analysis = root.optString("analysis", "Unable to analyze.");
            String advice = root.optString("advice", "No specific advice available.");

            View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_analysis_result, null);
            TextView analysisTv = dialogView.findViewById(R.id.text_analysis_body);
            TextView adviceTv = dialogView.findViewById(R.id.text_advice_body);

            analysisTv.setText(analysis);
            adviceTv.setText(advice);

            new AlertDialog.Builder(this)
                    .setTitle("Smart Stash Analysis")
                    .setView(dialogView)
                    .setPositiveButton("Got it", null)
                    .show();
        } catch (Exception e) {
            Log.e("Groq", "Analysis parsing error", e);
            Toast.makeText(this, "AI generated an invalid report format.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupInventoryLogic() {
        if (mSearchBar != null) {
            mSearchBar.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        View.OnClickListener filterClick = v -> {
            int id = v.getId();
            if (id == R.id.filter_all) mCurrentFilter = "All";
            else if (id == R.id.filter_food) mCurrentFilter = "Food";
            else if (id == R.id.filter_medicine) mCurrentFilter = "Medicine";
            else if (id == R.id.filter_cleaners) mCurrentFilter = "Cleaners";
            else if (id == R.id.filter_electronics) mCurrentFilter = "Electronics";
            else if (id == R.id.filter_clothes) mCurrentFilter = "Clothes";
            else if (id == R.id.filter_furniture) mCurrentFilter = "Furniture";
            else if (id == R.id.filter_services) mCurrentFilter = "Services";
            else if (id == R.id.filter_tools) mCurrentFilter = "Tools & Parts";
            
            updateFilterUI(id);
            if (mLastInventorySnapshot != null) renderInventoryItems(mLastInventorySnapshot);
        };

        if (mFilterAll != null) mFilterAll.setOnClickListener(filterClick);
        if (mFilterFood != null) mFilterFood.setOnClickListener(filterClick);
        if (mFilterMedicine != null) mFilterMedicine.setOnClickListener(filterClick);
        if (mFilterCleaners != null) mFilterCleaners.setOnClickListener(filterClick);
        if (mFilterElectronics != null) mFilterElectronics.setOnClickListener(filterClick);
        if (mFilterClothes != null) mFilterClothes.setOnClickListener(filterClick);
        if (mFilterFurniture != null) mFilterFurniture.setOnClickListener(filterClick);
        if (mFilterServices != null) mFilterServices.setOnClickListener(filterClick);
        if (mFilterTools != null) mFilterTools.setOnClickListener(filterClick);
    }

    private void renderActivities(com.google.firebase.firestore.QuerySnapshot snapshots) {
        if (mRecentActivityContainer == null) return;
        mRecentActivityContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (QueryDocumentSnapshot doc : snapshots) {
            String docId = doc.getId();
            String title = doc.getString("title");
            String desc = doc.getString("desc");
            String emoji = doc.getString("emoji");
            Double amount = doc.getDouble("amount");
            com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");

            RelativeLayout row = new RelativeLayout(this);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setPadding((int)(16 * density), (int)(12 * density), (int)(16 * density), (int)(12 * density));
            row.setBackgroundResource(R.drawable.card_background);
            row.setClickable(true);
            row.setFocusable(true);

            TextView emojiTv = new TextView(this);
            emojiTv.setId(View.generateViewId());
            emojiTv.setText(emoji != null ? emoji : "🔔");
            emojiTv.setTextSize(22);
            RelativeLayout.LayoutParams emojiParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            emojiParams.addRule(RelativeLayout.CENTER_VERTICAL);
            emojiTv.setLayoutParams(emojiParams);
            row.addView(emojiTv);

            LinearLayout textLayout = new LinearLayout(this);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            RelativeLayout.LayoutParams textParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textParams.addRule(RelativeLayout.END_OF, emojiTv.getId());
            textParams.setMarginStart((int)(14 * density));
            textLayout.setLayoutParams(textParams);

            TextView titleTv = new TextView(this);
            titleTv.setText(title);
            titleTv.setTextColor(getResources().getColor(R.color.primary));
            titleTv.setTextSize(14);
            titleTv.setTypeface(null, Typeface.BOLD);
            textLayout.addView(titleTv);

            TextView descTv = new TextView(this);
            String timeStr = ts != null ? android.text.format.DateUtils.getRelativeTimeSpanString(ts.toDate().getTime()).toString() : "Just now";
            descTv.setText(desc + " • " + timeStr);
            descTv.setTextColor(getResources().getColor(R.color.text_muted));
            descTv.setTextSize(11);
            textLayout.addView(descTv);
            row.addView(textLayout);

            if (amount != null && amount != 0) {
                TextView amountTv = new TextView(this);
                amountTv.setText((amount > 0 ? "+" : "") + String.format(Locale.US, "$%,.2f", amount));
                amountTv.setTextColor(getResources().getColor(amount > 0 ? R.color.growth_green : R.color.urgency_red));
                amountTv.setTextSize(14);
                amountTv.setTypeface(null, Typeface.BOLD);
                RelativeLayout.LayoutParams amountParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                amountParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                amountParams.addRule(RelativeLayout.CENTER_VERTICAL);
                amountTv.setLayoutParams(amountParams);
                row.addView(amountTv);
            }

            row.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Delete Activity")
                        .setMessage("Are you sure you want to remove this notification from the feed?")
                        .setPositiveButton("Delete", (d, w) -> {
                            if (isFirebaseAvailable && mGroupRef != null) {
                                mGroupRef.collection("activities").document(docId).delete();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            mRecentActivityContainer.addView(row);
        }
    }

    private void updateFilterUI(int activeId) {
        TextView[] filters = {mFilterAll, mFilterFood, mFilterMedicine, mFilterCleaners, mFilterElectronics, mFilterClothes, mFilterFurniture, mFilterServices, mFilterTools};
        for (TextView f : filters) {
            if (f == null) continue;
            if (f.getId() == activeId) {
                f.setBackgroundResource(R.drawable.btn_dark_rounded);
                f.setTextColor(getResources().getColor(R.color.white));
            } else {
                f.setBackgroundResource(R.drawable.input_field_background);
                f.setTextColor(getResources().getColor(R.color.primary));
            }
        }
    }

    private void setupRecentActivityListeners() {
        // No-op for now as rows are dynamic
    }

    private void setupGroupListeners() {
        // Now handled dynamically in render methods
    }

    private void setupSettingsListeners() {
        if (mBtnLogout != null) {
            mBtnLogout.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to sign out?")
                    .setPositiveButton("Yes", (d, w) -> {
                        if (isFirebaseAvailable && mAuth != null) {
                            mAuth.signOut();
                        }
                        Intent intent = new Intent(MainActivity.this, AuthActivity.class);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("No", null)
                    .show());
        }

        if (mBtnResetSpent != null) {
            mBtnResetSpent.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Reset Spent Money")
                    .setMessage("Are you sure you want to reset this month's spending to zero?")
                    .setPositiveButton("Reset", (d, w) -> {
                        mCurrentBudgetSpent = 0.0;
                        if (mDashboardBudgetSpentLbl != null) {
                            mDashboardBudgetSpentLbl.setText("$0.00");
                        }
                        updateRatioBar();

                        if (isFirebaseAvailable && mGroupRef != null && mCurrentGroupId != null) {
                            mGroupRef.update("budgetSpent", 0.0)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(MainActivity.this, "Spent money reset!", Toast.LENGTH_SHORT).show();

                                    // Log activity
                                    Map<String, Object> activity = new HashMap<>();
                                    activity.put("title", "Budget Reset");
                                    activity.put("desc", "Spending tracker cleared to zero");
                                    activity.put("amount", 0.0);
                                    activity.put("emoji", "🔄");
                                    activity.put("timestamp", FieldValue.serverTimestamp());
                                    mGroupRef.collection("activities").add(activity);
                                });
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
        }
    }

    private void showBudgetUpdateDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_manual_input, null);
        EditText budgetInput = dialogView.findViewById(R.id.input_item_price);
        EditText nameInput = dialogView.findViewById(R.id.input_item_name);
        
        // Hide irrelevant fields for budget update
        nameInput.setVisibility(View.GONE);
        dialogView.findViewById(R.id.label_item_name).setVisibility(View.GONE);
        
        // Use the new containers to hide extra fields
        View categoryLayout = dialogView.findViewById(R.id.layout_category_selection);
        if (categoryLayout != null) categoryLayout.setVisibility(View.GONE);
        
        View dateLayout = dialogView.findViewById(R.id.layout_date_selection);
        if (dateLayout != null) dateLayout.setVisibility(View.GONE);

        budgetInput.setHint("Enter new monthly budget");
        budgetInput.setText(String.format(Locale.US, "%.0f", mCurrentBudgetCap));

        new AlertDialog.Builder(this)
                .setTitle("Update Monthly Budget")
                .setView(dialogView)
                .setPositiveButton("Update", (d, w) -> {
                    String budgetStr = budgetInput.getText().toString().trim();
                    if (!budgetStr.isEmpty()) {
                        try {
                            double newBudget = Double.parseDouble(budgetStr);
                            performBudgetUpdate(newBudget);
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performBudgetUpdate(double newBudget) {
        if (isFirebaseAvailable && mGroupRef != null && mCurrentGroupId != null) {
            // Role check: Only admins can update the group budget
            if (!"admin".equals(mUserRole)) {
                Toast.makeText(this, "Only admins can change the shared budget.", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("monthlyBudget", newBudget);
            
            mGroupRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MainActivity.this, "Group Budget updated!", Toast.LENGTH_SHORT).show();
                    
                    // Log activity
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("title", "Budget Updated");
                    activity.put("desc", "New shared limit: $" + newBudget);
                    activity.put("amount", 0.0);
                    activity.put("emoji", "💰");
                    activity.put("timestamp", FieldValue.serverTimestamp());
                    mGroupRef.collection("activities").add(activity);
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Failed to update budget.", Toast.LENGTH_SHORT).show());
        } else {
            // Local fallback
            mCurrentBudgetCap = newBudget;
            applyLocalMockData();
            Toast.makeText(this, "Individual budget updated!", Toast.LENGTH_SHORT).show();
        }
    }

    private void showScanOptionsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.layout_scan_options, null);
        
        view.findViewById(R.id.option_manual).setOnClickListener(v -> {
            dialog.dismiss();
            showManualInputDialog();
        });
        
        view.findViewById(R.id.option_camera).setOnClickListener(v -> {
            dialog.dismiss();
            triggerCameraScan();
        });
        
        view.findViewById(R.id.option_file).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Receipt Image"), 101);
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void showManualInputDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_manual_input, null);
        EditText nameInput = dialogView.findViewById(R.id.input_item_name);
        EditText priceInput = dialogView.findViewById(R.id.input_item_price);
        android.widget.Spinner categorySpinner = dialogView.findViewById(R.id.input_item_category);
        EditText productionDateInput = dialogView.findViewById(R.id.input_item_production_date);
        EditText expiryDateInput = dialogView.findViewById(R.id.input_item_expiry_date);

        setupDatePicker(productionDateInput);
        setupDatePicker(expiryDateInput);

        String[] categories = {"Food", "Medicine", "Cleaners", "Electronics", "Clothes", "Furniture", "Services", "Tools & Parts", "General"};
        String[] emojis = {"🍎", "💊", "🧼", "💻", "👕", "🛋️", "🛠️", "🔧", "📦"};
        
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
        categorySpinner.setAdapter(adapter);

        new AlertDialog.Builder(this)
                .setTitle("Manual Asset Entry")
                .setView(dialogView)
                .setPositiveButton("Add to Stash", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String priceStr = priceInput.getText().toString().trim();
                    if (!name.isEmpty() && !priceStr.isEmpty()) {
                        try {
                            double price = Double.parseDouble(priceStr);
                            int selectedIdx = categorySpinner.getSelectedItemPosition();
                            String category = categories[selectedIdx];
                            String emoji = emojis[selectedIdx];
                            String prodDate = productionDateInput.getText().toString();
                            String expDate = expiryDateInput.getText().toString();
                            confirmStagedItem(name, price, category, emoji, prodDate, expDate);
                            switchTab(2);
                        } catch (Exception e) {
                            Toast.makeText(this, "Invalid entry", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupDatePicker(EditText editText) {
        editText.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            new android.app.DatePickerDialog(this, (view, year1, monthOfYear, dayOfMonth) -> {
                String date = String.format(Locale.US, "%d-%02d-%02d", year1, monthOfYear + 1, dayOfMonth);
                editText.setText(date);
            }, year, month, day).show();
        });
    }

    private void triggerCameraScan() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, 102);
        } else {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void analyzeReceipt(Bitmap bitmap) {
        if (GROQ_API_KEY == null || GROQ_API_KEY.isEmpty() || GROQ_API_KEY.contains("PASTE_YOUR")) {
            Toast.makeText(this, "Please set your Groq API Key in the .env file", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "AI analyzing receipt...", Toast.LENGTH_SHORT).show();

        // Convert bitmap to Base64
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        String encodedImage = Base64.encodeToString(byteArray, Base64.NO_WRAP);
        String dataUrl = "data:image/jpeg;base64," + encodedImage;

        GroqService.GroqRequest.Content textContent = new GroqService.GroqRequest.Content("text", "Analyze this receipt image and return a JSON object with a key 'items' containing an array of items. Each item should have 'name' (string), 'price' (number), 'category' (string: one of 'Food', 'Medicine', 'Cleaners', 'Electronics', 'Clothes', 'Furniture', 'Services', 'Tools & Parts', or 'General'), and 'emoji' (string: matching emoji like 🍎, 💊, 🧼, 💻, 👕, 🛋️, 🛠️, 🔧, 📦). Only return the JSON object, no extra text.");
        GroqService.GroqRequest.Content imageContent = new GroqService.GroqRequest.Content("image_url", new GroqService.GroqRequest.ImageUrl(dataUrl));
        
        GroqService.GroqRequest.Message message = new GroqService.GroqRequest.Message("user", java.util.Arrays.asList(textContent, imageContent));
        GroqService.GroqRequest request = new GroqService.GroqRequest("meta-llama/llama-4-scout-17b-16e-instruct", java.util.Collections.singletonList(message));

        mGroqService.generateContent("Bearer " + GROQ_API_KEY, request).enqueue(new Callback<GroqService.GroqResponse>() {
            @Override
            public void onResponse(@NonNull Call<GroqService.GroqResponse> call, @NonNull Response<GroqService.GroqResponse> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().choices.isEmpty()) {
                    String text = response.body().choices.get(0).message.content;
                    runOnUiThread(() -> parseAiTextToStagedItems(text));
                } else {
                    String errorMsgFinalLocal = "Unknown error";
                    try {
                        if (response.errorBody() != null) {
                            errorMsgFinalLocal = response.errorBody().string();
                        }
                    } catch (IOException ignored) {}
                    Log.e("Groq", "AI Error: " + response.code() + " - " + errorMsgFinalLocal);

                    String finalErrorBody = errorMsgFinalLocal;
                    runOnUiThread(() -> {
                        if (finalErrorBody.contains("model_decommissioned")) {
                            Toast.makeText(MainActivity.this, "Model decommissioned. Check model ID.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "AI failed: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onFailure(@NonNull Call<GroqService.GroqResponse> call, @NonNull Throwable t) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "AI Error: " + t.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void parseAiTextToStagedItems(String text) {
        try {
            String jsonStr = text.replaceAll("```json", "").replaceAll("```", "").trim();
            JSONObject root = new JSONObject(jsonStr);
            JSONArray array = root.getJSONArray("items");
            mStagedItems.clear();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String name = obj.getString("name");
                double price = obj.getDouble("price");
                String category = obj.optString("category", "General");
                String emoji = obj.optString("emoji", "📦");
                mStagedItems.add(new StagedItem(name, price, category, emoji, "", ""));
            }
            renderStagingArea();
            switchTab(2);
        } catch (Exception e) {
            Log.e("Groq", "Parsing error: " + text, e);
            Toast.makeText(this, "Failed to parse receipt data.", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderStagingArea() {
        if (mStagedContainer == null) return;
        mStagedContainer.removeAllViews();

        for (int i = 0; i < mStagedItems.size(); i++) {
            StagedItem item = mStagedItems.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_staged_receipt, mStagedContainer, false);

            TextView nameTv = row.findViewById(R.id.staged_item_name);
            TextView priceTv = row.findViewById(R.id.staged_item_price);
            TextView emojiTv = row.findViewById(R.id.staged_item_emoji);
            TextView categoryTv = row.findViewById(R.id.staged_item_category);
            
            nameTv.setText(item.name);
            priceTv.setText("$" + String.format(Locale.US, "%.2f", item.price));
            emojiTv.setText(item.emoji);
            categoryTv.setText(item.category);

            // Edit Button Logic
            row.findViewById(R.id.btn_edit_staged).setOnClickListener(v -> {
                View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_manual_input, null);
                EditText nameIn = dialogView.findViewById(R.id.input_item_name);
                EditText priceIn = dialogView.findViewById(R.id.input_item_price);
                android.widget.Spinner catSpinner = dialogView.findViewById(R.id.input_item_category);
                EditText prodIn = dialogView.findViewById(R.id.input_item_production_date);
                EditText expIn = dialogView.findViewById(R.id.input_item_expiry_date);

                setupDatePicker(prodIn);
                setupDatePicker(expIn);

                String[] categories = {"Food", "Medicine", "Cleaners", "Electronics", "Clothes", "Furniture", "Services", "Tools & Parts", "General"};
                String[] emojis = {"🍎", "💊", "🧼", "💻", "👕", "🛋️", "🛠️", "🔧", "📦"};
                
                android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
                catSpinner.setAdapter(adapter);

                nameIn.setText(item.name);
                priceIn.setText(String.valueOf(item.price));
                prodIn.setText(item.productionDate);
                expIn.setText(item.expiryDate);
                
                // Set current selection
                for (int j = 0; j < categories.length; j++) {
                    if (categories[j].equalsIgnoreCase(item.category)) {
                        catSpinner.setSelection(j);
                        break;
                    }
                }

                new AlertDialog.Builder(this)
                        .setTitle("Edit Staged Item")
                        .setView(dialogView)
                        .setPositiveButton("Update", (d, w) -> {
                            String newName = nameIn.getText().toString().trim();
                            String newPriceStr = priceIn.getText().toString().trim();
                            if (!newName.isEmpty() && !newPriceStr.isEmpty()) {
                                try {
                                    item.name = newName;
                                    item.price = Double.parseDouble(newPriceStr);
                                    int selectedIdx = catSpinner.getSelectedItemPosition();
                                    item.category = categories[selectedIdx];
                                    item.emoji = emojis[selectedIdx];
                                    item.productionDate = prodIn.getText().toString();
                                    item.expiryDate = expIn.getText().toString();
                                    renderStagingArea();
                                } catch (Exception e) {
                                    Toast.makeText(this, "Invalid price", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            // Delete Button Logic
            row.findViewById(R.id.btn_delete_staged).setOnClickListener(v -> {
                mStagedItems.remove(item);
                renderStagingArea();
                if (mStagedItems.isEmpty()) {
                    switchTab(1); // Go back to dashboard if no items left
                }
            });

            // Confirm Button Logic
            row.findViewById(R.id.btn_confirm_staged).setOnClickListener(v -> {
                confirmStagedItem(item.name, item.price, item.category, item.emoji, item.productionDate, item.expiryDate);
                mStagedItems.remove(item);
                renderStagingArea();
                if (mStagedItems.isEmpty()) {
                    switchTab(2); // Switch to inventory tab to see the items
                }
            });

            mStagedContainer.addView(row);
            if (i < mStagedItems.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(getResources().getColor(R.color.border_gray));
                mStagedContainer.addView(divider);
            }
        }
    }

    private void confirmStagedItem(String name, double price, String category, String emoji, String prodDate, String expDate) {
        String currentUserName = (mAuth.getCurrentUser() != null) ? mAuth.getCurrentUser().getEmail().split("@")[0] : "Someone";

        if (isFirebaseAvailable && mGroupRef != null && mCurrentGroupId != null) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", name);
            item.put("price", price);
            item.put("category", category);
            item.put("emoji", emoji);
            item.put("productionDate", prodDate);
            item.put("expiryDate", expDate);
            item.put("addedDate", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));

            mGroupRef.collection("items").add(item);
            mGroupRef.update("budgetSpent", FieldValue.increment(price));
            mGroupRef.update("activeAssetsValue", FieldValue.increment(price));
            mGroupRef.update("liquidCash", FieldValue.increment(-price));

            // Log activity
            Map<String, Object> activity = new HashMap<>();
            activity.put("title", currentUserName + " confirmed " + name);
            activity.put("desc", category + " item added to stash");
            activity.put("amount", -price);
            activity.put("emoji", emoji);
            activity.put("timestamp", FieldValue.serverTimestamp());
            mGroupRef.collection("activities").add(activity);

            Toast.makeText(this, name + " added to Stash!", Toast.LENGTH_SHORT).show();
        } else {
            mCurrentBudgetSpent += price;
            mActiveAssetsValue += price;
            
            Map<String, Object> localItem = new HashMap<>();
            localItem.put("id", String.valueOf(System.currentTimeMillis()));
            localItem.put("name", name);
            localItem.put("price", price);
            localItem.put("category", category);
            localItem.put("emoji", emoji);
            localItem.put("productionDate", prodDate);
            localItem.put("expiryDate", expDate);
            localItem.put("addedDate", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
            
            mLocalItems.add(localItem);
            
            applyLocalMockData();
            renderInventoryItems(null);
            Toast.makeText(this, name + " added (Local Mode)", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 101 && data != null && data.getData() != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                    analyzeReceipt(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (requestCode == 102 && data != null && data.getExtras() != null) {
                Bitmap bitmap = (Bitmap) data.getExtras().get("data");
                if (bitmap != null) analyzeReceipt(bitmap);
            }
        }
    }

    private void applyLocalMockData() {
        try {
            if (mDashboardBudgetAllocated != null) mDashboardBudgetAllocated.setText(" / $" + String.format(Locale.US, "%,.2f", mCurrentBudgetCap));
            if (mDashboardBudgetSpentLbl != null) mDashboardBudgetSpentLbl.setText("$" + String.format(Locale.US, "%,.0f", mCurrentBudgetSpent));
            if (mDashboardAssetsVal != null) mDashboardAssetsVal.setText("$" + String.format(Locale.US, "%,.2f", mBudgetReserved));
            
            // Role based visibility for Home Edit Button
            if (mBtnEditBudget != null) {
                if (mCurrentGroupId != null) {
                    mBtnEditBudget.setVisibility("admin".equals(mUserRole) ? View.VISIBLE : View.GONE);
                } else {
                    mBtnEditBudget.setVisibility(View.VISIBLE);
                }
            }

            // Calculate Liquid Cash: Total Budget - Allocated Funds - Confirmed Products (Spent)
            double calculatedLiquid = mCurrentBudgetCap - mBudgetReserved - mCurrentBudgetSpent;
            if (mDashboardLiquidVal != null) mDashboardLiquidVal.setText("$" + String.format(Locale.US, "%,.2f", calculatedLiquid));
            
            updateRatioBar();
        } catch (Throwable t) {}
    }

    private void observeUserState() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            applyLocalMockData();
            return;
        }

        DocumentReference userRef = mFirestore.collection("users").document(user.getUid());
        
        // Ensure user document exists
        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!documentSnapshot.exists()) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("email", user.getEmail());
                userData.put("currentGroupId", null);
                userRef.set(userData);
            }
        });

        mUserListener = userRef.addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null) return;
            
            mCurrentGroupId = snapshot.getString("currentGroupId");
            if (mCurrentGroupId == null || mCurrentGroupId.isEmpty()) {
                mAllocationsList = new ArrayList<>();
                renderGroupOnboarding();
            } else {
                mGroupRef = mFirestore.collection("groups").document(mCurrentGroupId);
                fetchGroupData();
                renderGroupDashboard();
            }
        });
    }

    private void renderGroupOnboarding() {
        if (mGroupView == null) return;
        mGroupView.removeAllViews();
        View onboarding = getLayoutInflater().inflate(R.layout.layout_group_onboarding, mGroupView, false);
        
        final EditText nameInput = onboarding.findViewById(R.id.input_create_group_name);
        onboarding.findViewById(R.id.btn_action_create_group).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError("Name required");
                return;
            }
            createGroup(name);
        });

        final EditText codeInput = onboarding.findViewById(R.id.input_join_group_code);
        onboarding.findViewById(R.id.btn_action_join_group).setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim().toUpperCase();
            if (code.length() != 6) {
                codeInput.setError("Invalid code");
                return;
            }
            joinGroup(code);
        });

        mGroupView.addView(onboarding);
    }

    private void createGroup(String name) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String code = generateGroupCode();
        String groupId = mFirestore.collection("groups").document().getId();
        String userName = user.getEmail().split("@")[0];

        Map<String, String> members = new HashMap<>();
        members.put(user.getUid(), "admin");

        Map<String, String> memberNames = new HashMap<>();
        memberNames.put(user.getUid(), userName);

        Map<String, Object> groupData = new HashMap<>();
        groupData.put("groupId", groupId);
        groupData.put("groupCode", code);
        groupData.put("groupName", name);
        groupData.put("adminId", user.getUid());
        groupData.put("monthlyBudget", 0.0);
        groupData.put("budgetSpent", 0.0);
        groupData.put("budgetReserved", 0.0);
        groupData.put("activeAssetsValue", 0.0);
        groupData.put("liquidCash", 0.0);
        groupData.put("members", members);
        groupData.put("memberNames", memberNames);

        mFirestore.collection("groups").document(groupId).set(groupData)
                .addOnSuccessListener(aVoid -> {
                    mFirestore.collection("users").document(user.getUid())
                            .update("currentGroupId", groupId);
                    Toast.makeText(this, "Workspace Created: " + code, Toast.LENGTH_LONG).show();
                });
    }

    private void joinGroup(String code) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String userName = user.getEmail().split("@")[0];

        mFirestore.collection("groups").whereEqualTo("groupCode", code).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "Group not found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String groupId = queryDocumentSnapshots.getDocuments().get(0).getId();
                    mFirestore.collection("groups").document(groupId)
                            .update("members." + user.getUid(), "casual",
                                   "memberNames." + user.getUid(), userName)
                            .addOnSuccessListener(aVoid -> {
                                mFirestore.collection("users").document(user.getUid())
                                        .update("currentGroupId", groupId);
                                Toast.makeText(this, "Joined Workspace!", Toast.LENGTH_SHORT).show();
                            });
                });
    }

    private String generateGroupCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        while (code.length() < 6) {
            code.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return code.toString();
    }

    private void renderGroupDashboard() {
        if (mGroupView == null) return;
        mGroupView.removeAllViews();
        View dashboard = getLayoutInflater().inflate(R.layout.fragment_group_dashboard, mGroupView, false);
        mGroupView.addView(dashboard);
        
        // Listeners for group dashboard elements will be setup in fetchGroupData
    }

    private void fetchGroupData() {
        if (isFirebaseAvailable && mGroupRef != null) {
            // Remove previous listeners if they exist
            if (mGroupListener != null) mGroupListener.remove();
            if (mItemsListener != null) mItemsListener.remove();

            mGroupListener = mGroupRef.addSnapshotListener((snapshot, e) -> {
                if (e != null || snapshot == null || !snapshot.exists()) {
                    return;
                }
                
                String name = snapshot.getString("groupName");
                String code = snapshot.getString("groupCode");
                Double budget = snapshot.getDouble("monthlyBudget");
                Double spent = snapshot.getDouble("budgetSpent");
                Double reserved = snapshot.getDouble("budgetReserved");
                Double assets = snapshot.getDouble("activeAssetsValue");
                
                if (budget == null) budget = 0.0;
                if (spent == null) spent = 0.0;
                if (reserved == null) reserved = 0.0;
                if (assets == null) assets = 0.0;

                // Liquid Cash = Total Budget - Spent - Reserved (Allocated)
                double liquid = budget - spent - reserved;

                Object allocsObj = snapshot.get("allocations");
                if (allocsObj instanceof List) {
                    mAllocationsList = new ArrayList<>((List<Map<String, Object>>) allocsObj);
                } else {
                    mAllocationsList = new ArrayList<>();
                }

                Object membersObj = snapshot.get("members");
                Map<String, String> members = null;
                if (membersObj instanceof Map) {
                    members = (Map<String, String>) membersObj;
                } else if (membersObj instanceof List) {
                    // Fallback for corrupted data (converted to array by arrayUnion)
                    members = new HashMap<>();
                    List<?> memberList = (List<?>) membersObj;
                    for (Object m : memberList) {
                        if (m != null) members.put(m.toString(), "casual");
                    }
                }
                
                if (members == null) members = new HashMap<>();

                Object namesObj = snapshot.get("memberNames");
                Map<String, String> memberNames = null;
                if (namesObj instanceof Map) {
                    memberNames = (Map<String, String>) namesObj;
                }
                if (memberNames == null) memberNames = new HashMap<>();
                
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    mUserRole = members.get(user.getUid());
                }

                // Update shared dashboard UI
                updateGroupUI(name, code, budget, spent, assets, liquid, members, memberNames);
                
                // Also update local state for the main dashboard if this is the active group
                mCurrentBudgetCap = budget;
                mCurrentBudgetSpent = spent;
                mActiveAssetsValue = assets;
                mLiquidCash = liquid;
                mBudgetReserved = reserved;
                mWorkspaceName = name;
                applyLocalMockData();
            });

            mItemsListener = mGroupRef.collection("items").addSnapshotListener((snapshots, e) -> {
                if (e != null || snapshots == null) return;
                renderInventoryItems(snapshots);
            });

            mGroupRef.collection("activities")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    mLastActivitiesSnapshot = snapshots;
                    renderActivities(snapshots);
                    // Update the timeline in the group tab
                    LinearLayout activitiesContainer = mGroupView.findViewById(R.id.container_group_activities);
                    if (activitiesContainer != null) {
                        renderActivitiesTimeline(activitiesContainer, snapshots);
                    }
                });
        }
    }

    private void updateGroupUI(String name, String code, Double budget, Double spent, Double assets, Double liquid, Map<String, String> members, Map<String, String> memberNames) {
        View v = mGroupView.getChildAt(0);
        if (v == null || v.findViewById(R.id.group_display_name) == null) return; // Not the dashboard

        TextView nameTv = v.findViewById(R.id.group_display_name);
        TextView codeTv = v.findViewById(R.id.group_display_code);
        View adminControls = v.findViewById(R.id.admin_budget_controls);
        View adminBadge = v.findViewById(R.id.admin_badge);

        if (nameTv != null) nameTv.setText(name);
        if (codeTv != null) codeTv.setText(code);
        
        // Financial Metrics
        double bVal = (budget != null) ? budget : 0.0;
        double sVal = (spent != null) ? spent : 0.0;
        
        TextView spentTv = v.findViewById(R.id.group_budget_spent);
        TextView totalTv = v.findViewById(R.id.group_budget_total);
        View progress = v.findViewById(R.id.group_budget_progress);
        TextView liquidTv = v.findViewById(R.id.group_liquid_cash);

        if (spentTv != null) spentTv.setText(String.format(Locale.US, "$%,.0f", sVal));
        if (totalTv != null) totalTv.setText(String.format(Locale.US, " / $%,.2f", bVal));
        
        if (progress != null) {
            float ratio = (bVal > 0) ? (float)(sVal / bVal) : 0;
            progress.post(() -> {
                ViewGroup.LayoutParams lp = progress.getLayoutParams();
                lp.width = (int) (progress.getParent() instanceof View ? ((View)progress.getParent()).getWidth() * Math.min(ratio, 1.0f) : 0);
                progress.setLayoutParams(lp);
            });
        }

        if (liquidTv != null) liquidTv.setText(String.format(Locale.US, "$%,.2f", liquid));

        // Role based visibility
        boolean isAdmin = "admin".equals(mUserRole);
        if (adminBadge != null) adminBadge.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        if (adminControls != null) adminControls.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        // Sync Logic
        final EditText syncInput = v.findViewById(R.id.input_group_code_sync);
        Button syncBtn = v.findViewById(R.id.btn_sync_group);
        if (syncBtn != null && syncInput != null) {
            syncBtn.setOnClickListener(view -> {
                String newCode = syncInput.getText().toString().trim().toUpperCase();
                if (newCode.length() == 6) {
                    joinGroup(newCode);
                    syncInput.setText("");
                } else {
                    syncInput.setError("Invalid Code");
                }
            });
        }

        // Budget Update Logic (Admin Only)
        if (isAdmin && adminControls != null) {
            final EditText budgetInput = v.findViewById(R.id.input_group_budget);
            v.findViewById(R.id.btn_update_group_budget).setOnClickListener(view -> {
                String val = budgetInput.getText().toString().trim();
                if (!val.isEmpty()) {
                    try {
                        double newBudget = Double.parseDouble(val);
                        performBudgetUpdate(newBudget);
                        budgetInput.setText("");
                    } catch (Exception ex) {}
                }
            });
        }

        // Horizontal Members List
        LinearLayout membersContainer = v.findViewById(R.id.container_group_members_horizontal);
        if (membersContainer != null && members != null) {
            renderGroupMembersHorizontal(membersContainer, members, memberNames, isAdmin);
        }

        // Activity Feed Rendering (Timeline)
        LinearLayout activitiesContainer = v.findViewById(R.id.container_group_activities);
        if (activitiesContainer != null && mLastActivitiesSnapshot != null) {
            renderActivitiesTimeline(activitiesContainer, mLastActivitiesSnapshot);
        }

        v.findViewById(R.id.btn_leave_group).setOnClickListener(view -> leaveGroup());
        
        v.findViewById(R.id.group_code_container).setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Group Code", code);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Code Copied", Toast.LENGTH_SHORT).show();
        });
    }

    private void renderActivitiesTimeline(LinearLayout container, com.google.firebase.firestore.QuerySnapshot snapshots) {
        container.removeAllViews();

        for (int i = 0; i < snapshots.size(); i++) {
            QueryDocumentSnapshot doc = (QueryDocumentSnapshot) snapshots.getDocuments().get(i);
            String title = doc.getString("title");
            String desc = doc.getString("desc");
            String emoji = doc.getString("emoji");
            Double amount = doc.getDouble("amount");
            com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");

            View row = LayoutInflater.from(this).inflate(R.layout.item_group_activity_timeline, container, false);
            
            TextView emojiTv = row.findViewById(R.id.activity_emoji);
            TextView titleTv = row.findViewById(R.id.activity_title);
            TextView descTv = row.findViewById(R.id.activity_desc);
            TextView amountTv = row.findViewById(R.id.activity_amount);
            View topConnector = row.findViewById(R.id.timeline_connector_top);
            View bottomConnector = row.findViewById(R.id.timeline_connector_bottom);

            if (emojiTv != null) emojiTv.setText(emoji != null ? emoji : "🔔");
            if (titleTv != null) titleTv.setText(title);
            
            String timeStr = ts != null ? android.text.format.DateUtils.getRelativeTimeSpanString(ts.toDate().getTime()).toString() : "Just now";
            if (descTv != null) descTv.setText((desc != null ? desc : "") + " • " + timeStr);

            if (amountTv != null) {
                if (amount != null && amount != 0) {
                    amountTv.setVisibility(View.VISIBLE);
                    amountTv.setText((amount > 0 ? "+" : "") + String.format(Locale.US, "$%,.2f", amount));
                    amountTv.setTextColor(getResources().getColor(amount > 0 ? R.color.growth_green : R.color.urgency_red));
                } else {
                    amountTv.setVisibility(View.GONE);
                }
            }

            // Hide connectors for first/last items
            if (i == 0 && topConnector != null) topConnector.setVisibility(View.INVISIBLE);
            if (i == snapshots.size() - 1 && bottomConnector != null) bottomConnector.setVisibility(View.INVISIBLE);

            container.addView(row);
        }
    }

    private void renderGroupMembersHorizontal(LinearLayout container, Map<String, String> members, Map<String, String> memberNames, boolean isAdmin) {
        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (Map.Entry<String, String> entry : members.entrySet()) {
            String uid = entry.getKey();
            String role = entry.getValue();
            String displayName = memberNames.get(uid);
            if (displayName == null || displayName.isEmpty()) {
                displayName = uid.substring(0, Math.min(uid.length(), 6));
            }

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(android.view.Gravity.CENTER);
            item.setPadding(0, 0, (int)(20 * density), 0);

            // Avatar Frame
            FrameLayout frame = new FrameLayout(this);
            frame.setLayoutParams(new LinearLayout.LayoutParams((int)(54 * density), (int)(54 * density)));
            
            View circle = new View(this);
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            shape.setColor(0xFFE8EDF2);
            circle.setBackground(shape);
            frame.addView(circle);

            TextView initialsTv = new TextView(this);
            initialsTv.setText(displayName.length() > 1 ? displayName.substring(0, 2).toUpperCase() : displayName.substring(0, 1).toUpperCase());
            initialsTv.setTextColor(0xFF191C1E);
            initialsTv.setTypeface(null, Typeface.BOLD);
            initialsTv.setGravity(android.view.Gravity.CENTER);
            frame.addView(initialsTv);

            // Role indicator dot
            View dot = new View(this);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams((int)(12 * density), (int)(12 * density));
            dotParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
            dot.setLayoutParams(dotParams);
            android.graphics.drawable.GradientDrawable dotShape = new android.graphics.drawable.GradientDrawable();
            dotShape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dotShape.setColor(role.equals("admin") ? 0xFF00993B : 0xFFBDBDBD);
            dotShape.setStroke((int)(2*density), 0xFFFFFFFF);
            dot.setBackground(dotShape);
            frame.addView(dot);

            item.addView(frame);

            TextView nameTv = new TextView(this);
            nameTv.setText(displayName);
            nameTv.setTextSize(11);
            nameTv.setTextColor(0xFF191C1E);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int)(8 * density);
            nameTv.setLayoutParams(lp);
            item.addView(nameTv);

            if (isAdmin && !uid.equals(mAuth.getUid())) {
                final String finalDisplayName = displayName;
                item.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                        .setTitle("Manage Member")
                        .setMessage("Update role for " + finalDisplayName + "?")
                        .setPositiveButton(role.equals("admin") ? "Make Casual" : "Make Admin", (d, w) -> {
                            mGroupRef.update("members." + uid, role.equals("admin") ? "casual" : "admin");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                });
            }

            container.addView(item);
        }
    }

    private void leaveGroup() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || mCurrentGroupId == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Leave Workspace")
                .setMessage("Are you sure you want to disconnect from this shared stash?")
                .setPositiveButton("Leave", (d, w) -> {
                    mFirestore.collection("groups").document(mCurrentGroupId)
                            .update("members." + user.getUid(), FieldValue.delete())
                            .addOnSuccessListener(aVoid -> {
                                mFirestore.collection("users").document(user.getUid())
                                        .update("currentGroupId", null);
                                mCurrentGroupId = null;
                                mGroupRef = null;
                                if (mGroupListener != null) mGroupListener.remove();
                                renderGroupOnboarding();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void provisionFirestoreDefaults() {
        if (isFirebaseAvailable && mGroupRef != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("budgetCap", mCurrentBudgetCap);
            data.put("budgetSpent", mCurrentBudgetSpent);
            data.put("activeAssetsValue", mActiveAssetsValue);
            data.put("liquidCash", mLiquidCash);
            data.put("inviteCode", mInviteCode);
            data.put("workspaceName", mWorkspaceName);
            mGroupRef.set(data);
        }
    }

    private void renderInventoryItems(com.google.firebase.firestore.QuerySnapshot snapshots) {
        if (mInventoryContainer == null) return;
        mLastInventorySnapshot = snapshots;
        mInventoryContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        LinearLayout currentRow = null;
        int count = 0;

        List<Object> itemsToRender = new ArrayList<>();
        if (snapshots != null) {
            for (QueryDocumentSnapshot doc : snapshots) itemsToRender.add(doc);
        } else {
            itemsToRender.addAll(mLocalItems);
        }

        List<Object> expiringSoon = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar threshold = Calendar.getInstance();
        threshold.add(Calendar.DAY_OF_YEAR, 2); // 48 hours

        for (Object itemObj : itemsToRender) {
            String name, category, emoji, expDateStr, addedDateStr, id;
            Double price;

            if (itemObj instanceof QueryDocumentSnapshot) {
                QueryDocumentSnapshot doc = (QueryDocumentSnapshot) itemObj;
                id = doc.getId();
                name = doc.getString("name");
                price = doc.getDouble("price");
                category = doc.getString("category");
                emoji = doc.getString("emoji");
                expDateStr = doc.getString("expiryDate");
                addedDateStr = doc.getString("addedDate");
            } else {
                Map<String, Object> map = (Map<String, Object>) itemObj;
                id = (String) map.get("id");
                name = (String) map.get("name");
                price = (Double) map.get("price");
                category = (String) map.get("category");
                emoji = (String) map.get("emoji");
                expDateStr = (String) map.get("expiryDate");
                addedDateStr = (String) map.get("addedDate");
            }

            // Expiry check
            if (expDateStr != null && !expDateStr.isEmpty()) {
                try {
                    Date expDate = sdf.parse(expDateStr);
                    if (expDate != null && expDate.before(threshold.getTime())) {
                        expiringSoon.add(itemObj);
                    }
                } catch (Exception ignored) {}
            }

            // Apply filter
            if (!mCurrentFilter.equals("All") && !mCurrentFilter.equalsIgnoreCase(category)) {
                continue;
            }

            if (emoji == null) emoji = "📦";
            if (count % 2 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, 0, 0, (int) (12 * density));
                currentRow.setLayoutParams(rowParams);
                mInventoryContainer.addView(currentRow);
            }
            
            View card = createInventoryCard(id, name, price, category, emoji, expDateStr, addedDateStr);
            if (snapshots == null) {
                card.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(name)
                    .setMessage("Remove this item from your individual stash?")
                    .setPositiveButton("Remove", (d, w) -> {
                        mLocalItems.remove(itemObj);
                        mCurrentBudgetSpent -= (price != null ? price : 0.0);
                        mActiveAssetsValue -= (price != null ? price : 0.0);
                        renderInventoryItems(null);
                        applyLocalMockData();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            }
            currentRow.addView(card);
            count++;
        }

        updateExpiryWidgetUnified(expiringSoon);
    }

    private void updateExpiryWidgetUnified(List<Object> expiringItems) {
        if (mExpiryContainer == null) return;
        mExpiryContainer.removeAllViews();

        if (expiringItems.isEmpty()) {
            mExpiryContainer.setVisibility(View.GONE);
            return;
        }

        mExpiryContainer.setVisibility(View.VISIBLE);
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < expiringItems.size(); i++) {
            Object itemObj = expiringItems.get(i);
            String name, expDate;
            if (itemObj instanceof QueryDocumentSnapshot) {
                name = ((QueryDocumentSnapshot) itemObj).getString("name");
                expDate = ((QueryDocumentSnapshot) itemObj).getString("expiryDate");
            } else {
                name = (String) ((Map<String, Object>) itemObj).get("name");
                expDate = (String) ((Map<String, Object>) itemObj).get("expiryDate");
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i > 0) row.setPadding(0, (int)(12 * density), 0, 0);

            TextView dot = new TextView(this);
            dot.setText("•");
            dot.setTextColor(getResources().getColor(R.color.urgency_red));
            dot.setTextSize(24);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dotParams.setMarginEnd((int)(10 * density));
            dotParams.topMargin = (int)(-4 * density);
            dot.setLayoutParams(dotParams);
            row.addView(dot);

            LinearLayout textLayout = new LinearLayout(this);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            
            TextView title = new TextView(this);
            title.setText("Expiry Alert: " + name);
            title.setTextColor(getResources().getColor(R.color.primary));
            title.setTypeface(null, Typeface.BOLD);
            title.setTextSize(14);
            textLayout.addView(title);

            TextView desc = new TextView(this);
            desc.setText("Urgent: Item expires on " + expDate);
            desc.setTextColor(getResources().getColor(R.color.urgency_red));
            desc.setTextSize(12);
            textLayout.addView(desc);

            row.addView(textLayout);
            mExpiryContainer.addView(row);
            
            if (i < expiringItems.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(getResources().getColor(R.color.border_gray));
                divider.setAlpha(0.5f);
                LinearLayout.LayoutParams divParams = (LinearLayout.LayoutParams) divider.getLayoutParams();
                divParams.topMargin = (int)(12 * density);
                mExpiryContainer.addView(divider);
            }
        }
    }

    private View createInventoryCard(String id, String name, Double price, String category, String emoji, String expDate, String addedDate) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        card.setLayoutParams(params);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (12 * density), (int) (12 * density), (int) (12 * density), (int) (12 * density));
        card.setBackgroundResource(R.drawable.card_background);
        card.setElevation(2 * density);
        
        TextView emojiTv = new TextView(this);
        emojiTv.setText(emoji);
        emojiTv.setTextSize(36);
        emojiTv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        card.addView(emojiTv);

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTypeface(null, Typeface.BOLD);
        nameTv.setTextColor(getResources().getColor(R.color.primary));
        nameTv.setTextSize(13);
        card.addView(nameTv);

        TextView priceTv = new TextView(this);
        priceTv.setText("$" + String.format(Locale.US, "%.2f", price != null ? price : 0.0));
        priceTv.setTextColor(getResources().getColor(R.color.text_muted));
        priceTv.setTextSize(11);
        card.addView(priceTv);

        if (addedDate != null && !addedDate.isEmpty()) {
            TextView addedTv = new TextView(this);
            addedTv.setText("Added: " + addedDate);
            addedTv.setTextColor(getResources().getColor(R.color.text_muted));
            addedTv.setTextSize(10);
            card.addView(addedTv);
        }

        if (expDate != null && !expDate.isEmpty()) {
            TextView expTv = new TextView(this);
            expTv.setText("Exp: " + expDate);
            expTv.setTextColor(getResources().getColor(R.color.urgency_red));
            expTv.setTextSize(10);
            card.addView(expTv);
        }

        card.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage("Remove this item from stash?")
                .setPositiveButton("Remove / Consume", (d, w) -> {
                    mGroupRef.collection("items").document(id).delete();
                    mGroupRef.update("activeAssetsValue", FieldValue.increment(-(price != null ? price : 0.0)));

                    // Log activity
                    Map<String, Object> activity = new HashMap<>();
                    activity.put("title", name + " Removed");
                    activity.put("desc", "Item removed or consumed");
                    activity.put("amount", 0.0);
                    activity.put("emoji", "✅");
                    activity.put("timestamp", FieldValue.serverTimestamp());
                    mGroupRef.collection("activities").add(activity);
                })
                .setNegativeButton("Cancel", null)
                .show());

        return card;
    }

    private void updateProfileUI() {
        FirebaseUser user = (mAuth != null) ? mAuth.getCurrentUser() : null;
        String name = "Alex", email = "alex@brohousecrew.com", initials = "AL";
        if (user != null) {
            name = (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) ? user.getDisplayName() : user.getEmail().split("@")[0];
            email = user.getEmail();
            String[] parts = name.split(" ");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                initials = String.valueOf(parts[0].charAt(0));
                if (parts.length > 1 && !parts[1].isEmpty()) initials += String.valueOf(parts[1].charAt(0));
            } else initials = "U";
            initials = initials.toUpperCase();
        }
        if (mToolbarProfileInitials != null) mToolbarProfileInitials.setText(initials);
        if (mSettingsProfileInitials != null) mSettingsProfileInitials.setText(initials);
        if (mSettingsProfileName != null) mSettingsProfileName.setText(name);
        if (mSettingsProfileEmail != null) mSettingsProfileEmail.setText(email);
        TextView greeting = findViewById(R.id.greeting_text);
        if (greeting != null) greeting.setText("Good morning, " + name);
    }

    private void updateRatioBar() {
        if (mDashboardProgressContainer == null || mDashboardUtilizationLbl == null) return;
        
        double cap = mCurrentBudgetCap > 0 ? mCurrentBudgetCap : 1.0; // Avoid division by zero
        double spentRatio = (mCurrentBudgetSpent / cap) * 100.0;
        mDashboardUtilizationLbl.setText(String.format(Locale.US, "%.1f%% Utilized", Math.min(100.0, spentRatio)));

        double reservedRatio = (mBudgetReserved / cap) * 100.0;
        if (mDashboardAllocatedLbl != null) {
            mDashboardAllocatedLbl.setText(String.format(Locale.US, "%.1f%% Allocated", Math.min(100.0, reservedRatio)));
        }

        mDashboardProgressContainer.post(() -> {
            try {
                mDashboardProgressContainer.removeAllViews();
                
                // 1. Add Allocations from the left
                for (int k = 0; k < mAllocationsList.size(); k++) {
                    Map<String, Object> alloc = mAllocationsList.get(k);
                    Double amount = 0.0;
                    if (alloc.get("amount") instanceof Double) amount = (Double) alloc.get("amount");
                    else if (alloc.get("amount") instanceof Long) amount = ((Long) alloc.get("amount")).doubleValue();
                    
                    if (amount == null || amount <= 0) continue;
                    
                    float weight = (float) (amount / cap);
                    View segment = new View(this);
                    segment.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
                    segment.setBackgroundColor(ALLOCATION_COLORS[k % ALLOCATION_COLORS.length]);
                    
                    final int index = k;
                    final String title = (String) alloc.get("title");
                    final double finalAmount = amount;
                    segment.setOnClickListener(v -> showEditAllocationDialog(index, title, finalAmount));

                    mDashboardProgressContainer.addView(segment);
                }
                
                // 2. Add Spent part
                if (mCurrentBudgetSpent > 0) {
                    float spentWeight = (float) (mCurrentBudgetSpent / cap);
                    View spentSegment = new View(this);
                    spentSegment.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, spentWeight));
                    
                    if (spentRatio >= 90.0) {
                        spentSegment.setBackgroundColor(getResources().getColor(R.color.urgency_red));
                        mDashboardUtilizationLbl.setTextColor(getResources().getColor(R.color.urgency_red));
                    } else {
                        spentSegment.setBackgroundColor(getResources().getColor(R.color.accent_blue));
                        mDashboardUtilizationLbl.setTextColor(getResources().getColor(R.color.text_muted));
                    }
                    mDashboardProgressContainer.addView(spentSegment);
                }
            } catch (Exception e) {
                Log.e("UI", "Error updating ratio bar", e);
            }
        });
    }

    @Override
    protected void onResume() { super.onResume(); try { applyLocalMockData(); } catch (Throwable t) {} }
}
