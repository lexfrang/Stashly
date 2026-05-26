package com.stashly.stashly;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    // View panes (TABS)
    private LinearLayout mDashboardView;
    private LinearLayout mInventoryView;
    private LinearLayout mGroupView;
    private LinearLayout mSettingsView;

    // Bottom Navigation Elements
    private LinearLayout mTabDashboard;
    private LinearLayout mTabInventory;
    private View mTabScan;
    private LinearLayout mTabGroup;
    private LinearLayout mTabSettings;

    private TextView mIconDashboard, mLblDashboard;
    private TextView mIconInventory, mLblInventory;
    private TextView mIconGroup, mLblGroup;
    private TextView mIconSettings, mLblSettings;

    // Interactive Dashboard Widgets
    private TextView mDashboardBudgetSpentLbl;
    private TextView mDashboardUtilizationLbl;
    private View mDashboardProgressFill;
    private TextView mDashboardAssetsVal;
    private TextView mDashboardLiquidVal;
    private LinearLayout mBtnTileLog, mBtnTileAnalysis;

    // Group Widget Elements
    private LinearLayout mWrapCopyInvite;
    private TextView mGroupInviteCode;
    private Button mBtnInviteAction;
    private LinearLayout mBtnAddMemberMock;

    // Settings elements
    private Button mBtnLogout;
    private EditText mInputGroupBudgetCap;
    private Button mBtnSaveSettings;
    private TextView mSettingsCurrentCapLbl;
    private SwitchCompat mSwitchPref1, mSwitchPref2, mSwitchPref3;

    // Firebase References
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private boolean isFirebaseAvailable = false;
    private DocumentReference mGroupRef;

    // Local state simulation fallbacks
    private double mCurrentBudgetSpent = 4250.0;
    private double mCurrentBudgetCap = 10000.0;
    private double mActiveAssetsValue = 2800.0;
    private double mLiquidCash = 5750.0;
    private String mInviteCode = "STSH9X";
    private String mWorkspaceName = "The BroHouse Crew";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind Views
        initViews();

        // Safe Firebase setup
        initFirebase();

        // Default to Dashboard tab
        switchTab(1);

        // Set up Listeners
        setupNavigationListeners();
        setupDashboardListeners();
        setupGroupListeners();
        setupSettingsListeners();

        // Query cloud states or apply local mock levels
        fetchGroupData();
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

        mIconDashboard = findViewById(R.id.icon_tab_dashboard);
        mLblDashboard = findViewById(R.id.lbl_tab_dashboard);
        mIconInventory = findViewById(R.id.icon_tab_inventory);
        mLblInventory = findViewById(R.id.lbl_tab_inventory);
        mIconGroup = findViewById(R.id.icon_tab_group);
        mLblGroup = findViewById(R.id.lbl_tab_group);
        mIconSettings = findViewById(R.id.icon_tab_settings);
        mLblSettings = findViewById(R.id.lbl_tab_settings);

        // Dashboard outputs
        mDashboardBudgetSpentLbl = findViewById(R.id.dashboard_budget_spent);
        mDashboardUtilizationLbl = findViewById(R.id.dashboard_utilization_text);
        mDashboardProgressFill = findViewById(R.id.dashboard_progress_fill);
        mDashboardAssetsVal = findViewById(R.id.dashboard_assets_val);
        mDashboardLiquidVal = findViewById(R.id.dashboard_liquid_val);
        mDashboardBudgetAllocated = findViewById(R.id.dashboard_budget_allocated);
        mBtnTileLog = findViewById(R.id.btn_tile_log);
        mBtnTileAnalysis = findViewById(R.id.btn_tile_analysis);

        // Group workspace copy layouts
        mWrapCopyInvite = findViewById(R.id.wrap_copy_invite);
        mGroupInviteCode = findViewById(R.id.group_invite_code);
        mBtnInviteAction = findViewById(R.id.btn_invite_action);
        mBtnAddMemberMock = findViewById(R.id.btn_add_member_mock);

        // Settings
        mBtnLogout = findViewById(R.id.btn_logout);
        mInputGroupBudgetCap = findViewById(R.id.input_group_budget_cap);
        mBtnSaveSettings = findViewById(R.id.btn_save_settings);
        mSettingsCurrentCapLbl = findViewById(R.id.settings_current_cap_lbl);
        mSwitchPref1 = findViewById(R.id.switch_preferences_1);
        mSwitchPref2 = findViewById(R.id.switch_preferences_2);
        mSwitchPref3 = findViewById(R.id.switch_preferences_3);
    }

    private void initFirebase() {
        try {
            mAuth = FirebaseAuth.getInstance();
            mFirestore = FirebaseFirestore.getInstance();
            if (mFirestore != null) {
                isFirebaseAvailable = true;
                // Query group matching our design system invite token STSH9X
                mGroupRef = mFirestore.collection("groups").document("STSH9X");
            }
        } catch (Throwable t) {
            isFirebaseAvailable = false;
        }
    }

    private void setupNavigationListeners() {
        if (mTabDashboard != null) mTabDashboard.setOnClickListener(v -> switchTab(1));
        if (mTabInventory != null) mTabInventory.setOnClickListener(v -> switchTab(2));
        if (mTabScan != null) mTabScan.setOnClickListener(v -> triggerCameraScanSimulation());
        if (mTabGroup != null) mTabGroup.setOnClickListener(v -> switchTab(3));
        if (mTabSettings != null) mTabSettings.setOnClickListener(v -> switchTab(4));
    }

    private void switchTab(int tabIndex) {
        // Reset all views & tabs to inactive neutral colors
        if (mDashboardView != null) mDashboardView.setVisibility(View.GONE);
        if (mInventoryView != null) mInventoryView.setVisibility(View.GONE);
        if (mGroupView != null) mGroupView.setVisibility(View.GONE);
        if (mSettingsView != null) mSettingsView.setVisibility(View.GONE);

        int primarySlate = getResources().getColor(R.color.primary);
        int inactiveMuted = getResources().getColor(R.color.text_muted);
        int activeBlue = getResources().getColor(R.color.accent_blue);

        if (mLblDashboard != null) mLblDashboard.setTextColor(inactiveMuted);
        if (mLblInventory != null) mLblInventory.setTextColor(inactiveMuted);
        if (mLblGroup != null) mLblGroup.setTextColor(inactiveMuted);
        if (mLblSettings != null) mLblSettings.setTextColor(inactiveMuted);

        switch (tabIndex) {
            case 1:
                if (mDashboardView != null) mDashboardView.setVisibility(View.VISIBLE);
                if (mLblDashboard != null) mLblDashboard.setTextColor(activeBlue);
                break;
            case 2:
                if (mInventoryView != null) mInventoryView.setVisibility(View.VISIBLE);
                if (mLblInventory != null) mLblInventory.setTextColor(activeBlue);
                break;
            case 3:
                if (mGroupView != null) mGroupView.setVisibility(View.VISIBLE);
                if (mLblGroup != null) mLblGroup.setTextColor(activeBlue);
                break;
            case 4:
                if (mSettingsView != null) mSettingsView.setVisibility(View.VISIBLE);
                if (mLblSettings != null) mLblSettings.setTextColor(activeBlue);
                break;
        }
    }

    private void setupDashboardListeners() {
        if (mBtnTileLog != null) {
            mBtnTileLog.setOnClickListener(v -> {
                Toast.makeText(this, "\u270F\ufe0f Opening Manual Asset Log staging sheet...", Toast.LENGTH_SHORT).show();
                switchTab(2); // Go to inventory to stage manual scan items
            });
        }

        if (mBtnTileAnalysis != null) {
            mBtnTileAnalysis.setOnClickListener(v -> {
                Toast.makeText(this, "\ud83d\udcc8 Presenting real-time Q3 Financial Trends forecast...", Toast.LENGTH_LONG).show();
            });
        }
    }

    private void setupGroupListeners() {
        // Dynamic clipboard copy trigger
        if (mWrapCopyInvite != null) {
            mWrapCopyInvite.setOnClickListener(v -> {
                if (mGroupInviteCode == null) return;
                String code = mGroupInviteCode.getText().toString();
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Stashly Invite Code", code);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "\ud83d\udccb Invite ID '" + code + "' copied to Android clipboard!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (mBtnInviteAction != null) {
            mBtnInviteAction.setOnClickListener(v -> {
                Toast.makeText(this, "\u2709\ufe0f Generated secure join link for 'The BroHouse Crew'!", Toast.LENGTH_SHORT).show();
            });
        }

        if (mBtnAddMemberMock != null) {
            mBtnAddMemberMock.setOnClickListener(v -> {
                Toast.makeText(this, "⚡ Sending notification payload to new pending members!", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupSettingsListeners() {
        // Logout configuration
        if (mBtnLogout != null) {
            mBtnLogout.setOnClickListener(v -> {
                if (isFirebaseAvailable && mAuth != null) {
                    mAuth.signOut();
                }
                Toast.makeText(MainActivity.this, "Sign-out successful. Goodbye!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, AuthActivity.class);
                startActivity(intent);
                finish();
            });
        }

        // Numerical workspace Cap adjustment
        if (mBtnSaveSettings != null) {
            mBtnSaveSettings.setOnClickListener(v -> {
                if (mInputGroupBudgetCap == null || mSettingsCurrentCapLbl == null) return;
                String capText = mInputGroupBudgetCap.getText().toString().trim();
                if (capText.isEmpty()) {
                    mInputGroupBudgetCap.setError("Limit cap value required");
                    return;
                }

                double capValue = Double.parseDouble(capText);
                mCurrentBudgetCap = capValue;
                String updatedLbl = "Current limit: $" + String.format("%.0f", capValue) + "/month";
                mSettingsCurrentCapLbl.setText(updatedLbl);

                // Fetch and set on dashboard too
                if (mDashboardBudgetAllocated != null) {
                    mDashboardBudgetAllocated.setText(" / $" + String.format("%,.2f", capValue));
                }
                updateRatioBar();

                // Synchronize asynchronously to Firebase Firestore database
                if (isFirebaseAvailable && mGroupRef != null) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("budgetCap", capValue);
                    mGroupRef.update(updates)
                        .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "\u2601\ufe0f Budget cap synced to cloud!", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Local update saved.", Toast.LENGTH_SHORT).show());
                } else {
                    Toast.makeText(this, "Local configurations saved successfully!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Preference Switch toasts
        if (mSwitchPref1 != null) {
            mSwitchPref1.setOnCheckedChangeListener((b, checked) -> 
                Toast.makeText(this, "Expiration Warnings (48h): " + (checked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show());
        }
        if (mSwitchPref2 != null) {
            mSwitchPref2.setOnCheckedChangeListener((b, checked) -> 
                Toast.makeText(this, "Daily Digests: " + (checked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show());
        }
        if (mSwitchPref3 != null) {
            mSwitchPref3.setOnCheckedChangeListener((b, checked) -> 
                Toast.makeText(this, "Weekly Waste Reports: " + (checked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show());
        }
    }

    private void triggerCameraScanSimulation() {
        Toast.makeText(this, "\ud83d\udcf7 Initializing ML Kit Ingestion Scanner...", Toast.LENGTH_SHORT).show();
        
        new Handler().postDelayed(() -> {
            Toast.makeText(this, "\u26A1 Extractions complete! Bounty Paper & Milk staged for review.", Toast.LENGTH_LONG).show();
            switchTab(2); // Jump straight to staging area to show the staged scanned purchases!
            
            // Increment budget spent local state as simulated purchase integration
            mCurrentBudgetSpent += 17.49; // Total of staging
            mActiveAssetsValue += 17.49;
            if (mDashboardBudgetSpentLbl != null) {
                mDashboardBudgetSpentLbl.setText("$" + String.format("%,.0f", mCurrentBudgetSpent));
            }
            if (mDashboardAssetsVal != null) {
                mDashboardAssetsVal.setText("$" + String.format("%,.2f", mActiveAssetsValue));
            }
            updateRatioBar();
        }, 1200);
    }

    private void applyLocalMockData() {
        try {
            if (mInputGroupBudgetCap != null) {
                mInputGroupBudgetCap.setText(String.format("%.0f", mCurrentBudgetCap));
            }
            if (mSettingsCurrentCapLbl != null) {
                mSettingsCurrentCapLbl.setText("Current limit: $" + String.format("%.0f", mCurrentBudgetCap) + "/month");
            }
            if (mDashboardBudgetAllocated != null) {
                mDashboardBudgetAllocated.setText(" / $" + String.format("%,.2f", mCurrentBudgetCap));
            }
            if (mDashboardBudgetSpentLbl != null) {
                mDashboardBudgetSpentLbl.setText("$" + String.format("%,.0f", mCurrentBudgetSpent));
            }
            if (mDashboardAssetsVal != null) {
                mDashboardAssetsVal.setText("$" + String.format("%,.2f", mActiveAssetsValue));
            }
            if (mDashboardLiquidVal != null) {
                mDashboardLiquidVal.setText("$" + String.format("%,.2f", mLiquidCash));
            }
            updateRatioBar();
        } catch (Throwable t) {
            // Safe fallback
        }
    }

    private void fetchGroupData() {
        if (isFirebaseAvailable && mGroupRef != null) {
            try {
                mGroupRef.addSnapshotListener((snapshot, e) -> {
                    try {
                        if (e != null) {
                            applyLocalMockData();
                            return;
                        }
                        if (snapshot != null && snapshot.exists()) {
                            Double cap = snapshot.getDouble("budgetCap");
                            Double spent = snapshot.getDouble("budgetSpent");
                            Double assets = snapshot.getDouble("activeAssetsValue");
                            Double liquid = snapshot.getDouble("liquidCash");

                            if (cap != null) mCurrentBudgetCap = cap;
                            if (spent != null) mCurrentBudgetSpent = spent;
                            if (assets != null) mActiveAssetsValue = assets;
                            if (liquid != null) mLiquidCash = liquid;

                            applyLocalMockData();
                        } else {
                            // Document empty/missing but firebase connected. Safely initialize.
                            provisionFirestoreDefaults();
                            applyLocalMockData();
                        }
                    } catch (Throwable innerEx) {
                        applyLocalMockData();
                    }
                });
            } catch (Throwable ex) {
                isFirebaseAvailable = false;
                applyLocalMockData();
            }
        } else {
            applyLocalMockData();
        }
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

    private void updateRatioBar() {
        if (mDashboardProgressFill == null || mDashboardUtilizationLbl == null) return;
        
        double ratio = (mCurrentBudgetSpent / mCurrentBudgetCap) * 100.0;
        if (ratio > 100.0) ratio = 100.0;
        
        mDashboardUtilizationLbl.setText(String.format("%.1f", ratio) + "% Utilized");
        
        mDashboardProgressFill.post(() -> {
            try {
                View parent = (View) mDashboardProgressFill.getParent();
                if (parent != null) {
                    int parentWidth = parent.getWidth();
                    if (parentWidth <= 0) {
                        parentWidth = parent.getMeasuredWidth();
                    }
                    double percent = (mCurrentBudgetSpent / mCurrentBudgetCap);
                    if (percent > 1.0) percent = 1.0;
                    if (percent < 0.0) percent = 0.0;
                    
                    android.view.ViewGroup.LayoutParams params = mDashboardProgressFill.getLayoutParams();
                    if (params != null) {
                        params.width = (int) (parentWidth * percent);
                        mDashboardProgressFill.setLayoutParams(params);
                    }
                    
                    // Programmatic color transition: red if budget exceeded, otherwise blue/green
                    if (percent >= 0.9) {
                        mDashboardProgressFill.setBackgroundColor(getResources().getColor(R.color.urgency_red));
                        mDashboardUtilizationLbl.setTextColor(getResources().getColor(R.color.urgency_red));
                    } else {
                        mDashboardProgressFill.setBackgroundColor(getResources().getColor(R.color.accent_blue));
                        mDashboardUtilizationLbl.setTextColor(getResources().getColor(R.color.text_muted));
                    }
                }
            } catch (Exception e) {
                // Prevent any layout exceptions from crashing the app
            }
        });
    }

    // Needed to modify this label from outside SNAPSHOT updates
    private TextView mDashboardBudgetAllocated;
    
    @Override
    protected void onResume() {
        super.onResume();
        try {
            // Fallback safety to map widgets in case snapshot isn't triggered
            mDashboardBudgetAllocated = findViewById(R.id.dashboard_budget_allocated);
            applyLocalMockData();
        } catch (Throwable t) {
            // Safe fallback
        }
    }
}
