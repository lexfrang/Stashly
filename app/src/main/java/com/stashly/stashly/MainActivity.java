package com.stashly.stashly;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private LinearLayout mMembersContainer;

    // Settings elements
    private Button mBtnLogout;
    private EditText mInputGroupBudgetCap;
    private EditText mInputActiveGroupCode;
    private Button mBtnSaveSettings;
    private Button mBtnJoinGroup;
    private TextView mSettingsCurrentCapLbl;
    private SwitchCompat mSwitchPref1, mSwitchPref2, mSwitchPref3;

    // Recent Activity Rows
    private View mRowActivity1, mRowActivity2, mRowActivity3;

    // Inventory Elements for Filtering
    private EditText mSearchBar;
    private TextView mFilterAll, mFilterFood, mFilterMedicine, mFilterCleaners;
    private LinearLayout mInventoryContainer;
    private LinearLayout mStagedContainer;

    // Staging Data
    private List<StagedItem> mStagedItems = new ArrayList<>();
    
    // AI Recognition (Groq)
    private GroqService mGroqService;
    private static final String GROQ_API_KEY = BuildConfig.GROQ_API_KEY;

    private static class StagedItem {
        String name;
        double price;
        String category;
        String emoji;

        StagedItem(String name, double price, String category, String emoji) {
            this.name = name;
            this.price = price;
            this.category = category;
            this.emoji = emoji;
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
    private double mCurrentBudgetSpent = 4250.0;
    private double mCurrentBudgetCap = 10000.0;
    private double mActiveAssetsValue = 2800.0;
    private double mLiquidCash = 5750.0;
    private String mInviteCode = "STSH9X";
    private String mWorkspaceName = "The BroHouse Crew";

    private List<String> mMembersList = new ArrayList<>();

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
        fetchGroupData();
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

        // Recent Activity Rows
        mRowActivity1 = findViewById(R.id.row_activity_1);
        mRowActivity2 = findViewById(R.id.row_activity_2);
        mRowActivity3 = findViewById(R.id.row_activity_3);

        // Inventory Filtering
        mSearchBar = findViewById(R.id.search_bar);
        mFilterAll = findViewById(R.id.filter_all);
        mFilterFood = findViewById(R.id.filter_food);
        mFilterMedicine = findViewById(R.id.filter_medicine);
        mFilterCleaners = findViewById(R.id.filter_cleaners);
        
        mInventoryContainer = findViewById(R.id.inventory_grid_container);
        mStagedContainer = findViewById(R.id.container_staged_items);

        // Profile widgets
        mToolbarProfileInitials = findViewById(R.id.toolbar_profile_initials);
        mSettingsProfileInitials = findViewById(R.id.settings_profile_initials);
        mSettingsProfileName = findViewById(R.id.settings_profile_name);
        mSettingsProfileEmail = findViewById(R.id.settings_profile_email);
        
        // Member Widgets
        mMemberAlexInitials = findViewById(R.id.member_alex_initials);
        mMemberAlexName = findViewById(R.id.member_alex_name);

        // Group workspace copy layouts
        mWrapCopyInvite = findViewById(R.id.wrap_copy_invite);
        mGroupInviteCode = findViewById(R.id.group_invite_code);
        mBtnInviteAction = findViewById(R.id.btn_invite_action);
        mBtnAddMemberMock = findViewById(R.id.btn_add_member_mock);
        mMembersContainer = findViewById(R.id.container_members);

        // Settings
        mBtnLogout = findViewById(R.id.btn_logout);
        mInputGroupBudgetCap = findViewById(R.id.input_group_budget_cap);
        mInputActiveGroupCode = findViewById(R.id.input_active_group_code);
        mBtnSaveSettings = findViewById(R.id.btn_save_settings);
        mBtnJoinGroup = findViewById(R.id.btn_join_group);
        mSettingsCurrentCapLbl = findViewById(R.id.settings_current_cap_lbl);
        mSwitchPref1 = findViewById(R.id.switch_preferences_1);
        mSwitchPref2 = findViewById(R.id.switch_preferences_2);
        mSwitchPref3 = findViewById(R.id.switch_preferences_3);
    }

    private TextView mMemberAlexInitials, mMemberAlexName;

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
        if (mBtnTileLog != null) {
            mBtnTileLog.setOnClickListener(v -> showScanOptionsBottomSheet());
        }

        if (mBtnTileAnalysis != null) {
            mBtnTileAnalysis.setOnClickListener(v -> showDetailedAnalysis());
        }
    }

    private void showDetailedAnalysis() {
        String analysisReport = "Q3 Performance Overview:\n\n" +
                "• Budget Utilization: " + String.format("%.1f%%", (mCurrentBudgetSpent / mCurrentBudgetCap) * 100) + "\n" +
                "• Asset Appreciation: +4.2% YoY\n" +
                "• Top Category: Office Equipment (56%)\n" +
                "• Predicted Waste: $14.50 (Steak expiring)\n\n" +
                "Recommendation: Reduce liquid cash holdings by 5% to increase asset coverage.";

        new AlertDialog.Builder(this)
                .setTitle("Financial Analysis")
                .setMessage(analysisReport)
                .setPositiveButton("Download PDF", (d, w) -> Toast.makeText(this, "Generating Report...", Toast.LENGTH_SHORT).show())
                .setNegativeButton("Close", null)
                .show();
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
            updateFilterUI(id);
        };

        if (mFilterAll != null) mFilterAll.setOnClickListener(filterClick);
        if (mFilterFood != null) mFilterFood.setOnClickListener(filterClick);
        if (mFilterMedicine != null) mFilterMedicine.setOnClickListener(filterClick);
        if (mFilterCleaners != null) mFilterCleaners.setOnClickListener(filterClick);
    }

    private void updateFilterUI(int activeId) {
        TextView[] filters = {mFilterAll, mFilterFood, mFilterMedicine, mFilterCleaners};
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
        View.OnClickListener activityClickListener = v -> {
            String title = "Activity Details";
            String desc = "No details available for this item.";
            
            int id = v.getId();
            if (id == R.id.row_activity_1) {
                title = "MacBook Pro M3 Added";
                desc = "Office Equipment added to inventory by Alex. \nValue: -$2,400.00 \nCategory: Electronics";
            } else if (id == R.id.row_activity_2) {
                title = "Monthly Allocation Reserved";
                desc = "System automatically allocated $10,000.00 for the October Cycle.";
            } else if (id == R.id.row_activity_3) {
                title = "Team Membership";
                desc = "Sarah joined 'The BroHouse Crew' household workspace.";
            }

            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(desc)
                    .setPositiveButton("Edit", (d, w) -> showEditActivityDialog(v))
                    .setNegativeButton("Delete", (d, w) -> {
                        v.setVisibility(View.GONE);
                        View parent = (View) v.getParent();
                        if (parent instanceof LinearLayout) {
                            int index = ((LinearLayout) parent).indexOfChild(v);
                            if (index + 1 < ((LinearLayout) parent).getChildCount()) {
                                ((LinearLayout) parent).getChildAt(index + 1).setVisibility(View.GONE);
                            }
                        }
                    })
                    .setNeutralButton("Close", null)
                    .show();
        };

        if (mRowActivity1 != null) mRowActivity1.setOnClickListener(activityClickListener);
        if (mRowActivity2 != null) mRowActivity2.setOnClickListener(activityClickListener);
        if (mRowActivity3 != null) mRowActivity3.setOnClickListener(activityClickListener);
    }

    private void showEditActivityDialog(View row) {
        EditText input = new EditText(this);
        input.setHint("Update title...");
        new AlertDialog.Builder(this)
                .setTitle("Edit Activity")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newTitle = input.getText().toString();
                    if (!newTitle.isEmpty()) {
                        // Implementation for updating title
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupGroupListeners() {
        if (mWrapCopyInvite != null) {
            mWrapCopyInvite.setOnClickListener(v -> {
                if (mGroupInviteCode == null) return;
                String code = mGroupInviteCode.getText().toString();
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Stashly Invite Code", code);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    com.google.android.material.snackbar.Snackbar.make(v, "Code " + code + " copied!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                }
            });
        }

        if (mBtnInviteAction != null) {
            mBtnInviteAction.setOnClickListener(v -> {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, "Join my Stashly workspace using code: " + mInviteCode);
                sendIntent.setType("text/plain");
                startActivity(Intent.createChooser(sendIntent, "Share Invite Code"));
            });
        }

        if (mBtnAddMemberMock != null) {
            mBtnAddMemberMock.setOnClickListener(v -> showAddMemberDialog());
        }
    }

    private void showAddMemberDialog() {
        EditText input = new EditText(this);
        input.setHint("Name or Email");
        new AlertDialog.Builder(this)
                .setTitle("Invite Member")
                .setMessage("Enter the identifier for the person you want to add.")
                .setView(input)
                .setPositiveButton("Send Invite", (d, w) -> {
                    String identifier = input.getText().toString();
                    if (!identifier.isEmpty()) {
                        if (isFirebaseAvailable && mGroupRef != null) {
                            mGroupRef.update("members", FieldValue.arrayUnion(identifier))
                                    .addOnSuccessListener(aVoid -> com.google.android.material.snackbar.Snackbar.make(mGroupView, "Invite sent to " + identifier, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show());
                        } else {
                            addMemberToUI(identifier);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addMemberToUI(String name) {
        if (mMembersContainer == null) return;
        
        float density = getResources().getDisplayMetrics().density;
        String initials = name.length() > 1 ? name.substring(0, 2).toUpperCase() : name.toUpperCase();
        
        LinearLayout memberLayout = new LinearLayout(this);
        LinearLayout.LayoutParams memberParams = new LinearLayout.LayoutParams((int) (72 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        memberParams.setMargins(0, 0, (int) (12 * density), 0);
        memberLayout.setLayoutParams(memberParams);
        memberLayout.setOrientation(LinearLayout.VERTICAL);
        memberLayout.setGravity(android.view.Gravity.CENTER);

        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutParams(new LinearLayout.LayoutParams((int) (54 * density), (int) (54 * density)));
        frame.setBackgroundResource(R.drawable.input_field_background);

        TextView initialsTv = new TextView(this);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        initialsTv.setLayoutParams(textParams);
        initialsTv.setGravity(android.view.Gravity.CENTER);
        initialsTv.setText(initials);
        initialsTv.setTextColor(getResources().getColor(R.color.primary));
        initialsTv.setTextSize(14);
        initialsTv.setTypeface(null, Typeface.BOLD);

        frame.addView(initialsTv);
        memberLayout.addView(frame);

        TextView nameTv = new TextView(this);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = (int) (6 * density);
        nameTv.setLayoutParams(nameParams);
        nameTv.setText(name);
        nameTv.setTextColor(getResources().getColor(R.color.primary));
        nameTv.setTextSize(11);
        
        memberLayout.addView(nameTv);
        mMembersContainer.addView(memberLayout, mMembersContainer.getChildCount() - 1);
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

        if (mBtnJoinGroup != null) {
            mBtnJoinGroup.setOnClickListener(v -> {
                if (mInputActiveGroupCode == null) return;
                String newCode = mInputActiveGroupCode.getText().toString().trim().toUpperCase();
                if (newCode.isEmpty()) {
                    mInputActiveGroupCode.setError("Code required");
                    return;
                }
                mInviteCode = newCode;
                if (mGroupInviteCode != null) mGroupInviteCode.setText(newCode);
                if (isFirebaseAvailable && mFirestore != null) {
                    if (mGroupListener != null) mGroupListener.remove();
                    mGroupRef = mFirestore.collection("groups").document(newCode);
                    fetchGroupData();
                    Toast.makeText(this, "Syncing to group " + newCode, Toast.LENGTH_SHORT).show();
                }
            });
        }

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
                mSettingsCurrentCapLbl.setText("Current limit: $" + String.format("%.0f", capValue) + "/month");
                if (mDashboardBudgetAllocated != null) {
                    mDashboardBudgetAllocated.setText(" / $" + String.format("%,.2f", capValue));
                }
                updateRatioBar();

                if (isFirebaseAvailable && mGroupRef != null) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("budgetCap", capValue);
                    mGroupRef.update(updates)
                        .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "Budget cap synced!", Toast.LENGTH_SHORT).show());
                }
            });
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

        new AlertDialog.Builder(this)
                .setTitle("Manual Asset Entry")
                .setView(dialogView)
                .setPositiveButton("Add to Stash", (d, w) -> {
                    String name = nameInput.getText().toString();
                    String priceStr = priceInput.getText().toString();
                    if (!name.isEmpty() && !priceStr.isEmpty()) {
                        double price = Double.parseDouble(priceStr);
                        confirmStagedItem(name, price, "General", "📦");
                        switchTab(2);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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

        GroqService.GroqRequest.Content textContent = new GroqService.GroqRequest.Content("text", "Analyze this receipt image and return a JSON object with a key 'items' containing an array of items. Each item should have 'name' (string) and 'price' (number). Only return the JSON object, no extra text.");
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
                    String errorBody = "Unknown error";
                    try {
                        if (response.errorBody() != null) {
                            errorBody = response.errorBody().string();
                        }
                        Log.e("Groq", "AI Error: " + response.code() + " - " + errorBody);
                    } catch (IOException e) {
                        Log.e("Groq", "AI Error: " + response.code());
                    }

                    String finalErrorBody = errorBody;
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
                mStagedItems.add(new StagedItem(name, price, "General", "📦"));
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
            final int index = i;
            StagedItem item = mStagedItems.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_staged_receipt, mStagedContainer, false);

            TextView nameTv = row.findViewById(R.id.staged_item_name);
            TextView priceTv = row.findViewById(R.id.staged_item_price);
            TextView emojiTv = row.findViewById(R.id.staged_item_emoji);
            TextView categoryTv = row.findViewById(R.id.staged_item_category);
            
            nameTv.setText(item.name);
            priceTv.setText("$" + String.format("%.2f", item.price));
            emojiTv.setText(item.emoji);
            categoryTv.setText(item.category);

            row.findViewById(R.id.btn_confirm_staged).setOnClickListener(v -> {
                confirmStagedItem(item.name, item.price, item.category, item.emoji);
                mStagedItems.remove(index);
                renderStagingArea();
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

    private void confirmStagedItem(String name, double price, String category, String emoji) {
        if (isFirebaseAvailable && mGroupRef != null) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", name);
            item.put("price", price);
            item.put("category", category);
            item.put("emoji", emoji);

            mGroupRef.collection("items").add(item);
            mGroupRef.update("budgetSpent", FieldValue.increment(price));
            mGroupRef.update("activeAssetsValue", FieldValue.increment(price));
            mGroupRef.update("liquidCash", FieldValue.increment(-price));
            Toast.makeText(this, name + " added to Stash!", Toast.LENGTH_SHORT).show();
        } else {
            mCurrentBudgetSpent += price;
            mActiveAssetsValue += price;
            mLiquidCash -= price;
            applyLocalMockData();
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
            if (mInputGroupBudgetCap != null) mInputGroupBudgetCap.setText(String.format("%.0f", mCurrentBudgetCap));
            if (mSettingsCurrentCapLbl != null) mSettingsCurrentCapLbl.setText("Current limit: $" + String.format("%.0f", mCurrentBudgetCap) + "/month");
            if (mDashboardBudgetAllocated != null) mDashboardBudgetAllocated.setText(" / $" + String.format("%,.2f", mCurrentBudgetCap));
            if (mDashboardBudgetSpentLbl != null) mDashboardBudgetSpentLbl.setText("$" + String.format("%,.0f", mCurrentBudgetSpent));
            if (mDashboardAssetsVal != null) mDashboardAssetsVal.setText("$" + String.format("%,.2f", mActiveAssetsValue));
            if (mDashboardLiquidVal != null) mDashboardLiquidVal.setText("$" + String.format("%,.2f", mLiquidCash));
            
            TextView groupTitle = findViewById(R.id.group_workspace_title);
            if (groupTitle != null) groupTitle.setText(mWorkspaceName);
            updateRatioBar();
        } catch (Throwable t) {}
    }

    private void fetchGroupData() {
        if (isFirebaseAvailable && mGroupRef != null) {
            mGroupListener = mGroupRef.addSnapshotListener((snapshot, e) -> {
                if (e != null || snapshot == null || !snapshot.exists()) {
                    if (snapshot != null && !snapshot.exists()) provisionFirestoreDefaults();
                    applyLocalMockData();
                    return;
                }
                mCurrentBudgetCap = snapshot.getDouble("budgetCap") != null ? snapshot.getDouble("budgetCap") : 10000.0;
                mCurrentBudgetSpent = snapshot.getDouble("budgetSpent") != null ? snapshot.getDouble("budgetSpent") : 0.0;
                mActiveAssetsValue = snapshot.getDouble("activeAssetsValue") != null ? snapshot.getDouble("activeAssetsValue") : 0.0;
                mLiquidCash = snapshot.getDouble("liquidCash") != null ? snapshot.getDouble("liquidCash") : 0.0;
                mWorkspaceName = snapshot.getString("workspaceName") != null ? snapshot.getString("workspaceName") : "Workspace";
                List<String> members = (List<String>) snapshot.get("members");
                if (members != null) { mMembersList = members; syncMembersToUI(); }
                applyLocalMockData();
            });
            mItemsListener = mGroupRef.collection("items").addSnapshotListener((snapshots, e) -> {
                if (e != null || snapshots == null) return;
                renderInventoryItems(snapshots);
            });
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

    private void renderInventoryItems(com.google.firebase.firestore.QuerySnapshot snapshots) {
        if (mInventoryContainer == null) return;
        mInventoryContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        LinearLayout currentRow = null;
        int count = 0;
        for (QueryDocumentSnapshot doc : snapshots) {
            String name = doc.getString("name");
            Double price = doc.getDouble("price");
            String category = doc.getString("category");
            String emoji = doc.getString("emoji");
            if (emoji == null) emoji = "📦";
            if (count % 2 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.setMargins(0, 0, 0, (int) (12 * density));
                currentRow.setLayoutParams(rowParams);
                mInventoryContainer.addView(currentRow);
            }
            currentRow.addView(createInventoryCard(doc.getId(), name, price, category, emoji));
            count++;
        }
    }

    private View createInventoryCard(String id, String name, Double price, String category, String emoji) {
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
        priceTv.setText("$" + String.format("%.2f", price != null ? price : 0.0));
        priceTv.setTextColor(getResources().getColor(R.color.text_muted));
        priceTv.setTextSize(11);
        card.addView(priceTv);

        card.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage("Remove this item from stash?")
                .setPositiveButton("Remove / Consume", (d, w) -> {
                    mGroupRef.collection("items").document(id).delete();
                    mGroupRef.update("activeAssetsValue", FieldValue.increment(-(price != null ? price : 0.0)));
                })
                .setNegativeButton("Cancel", null)
                .show());

        return card;
    }

    private void syncMembersToUI() {
        if (mMembersContainer == null) return;
        int childCount = mMembersContainer.getChildCount();
        if (childCount > 2) mMembersContainer.removeViews(1, childCount - 2);
        for (String member : mMembersList) addMemberToUI(member);
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
        if (mMemberAlexInitials != null) mMemberAlexInitials.setText(initials);
        if (mMemberAlexName != null) mMemberAlexName.setText(name);
        TextView greeting = findViewById(R.id.greeting_text);
        if (greeting != null) greeting.setText("Good morning, " + name);
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
                    int parentWidth = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
                    double percent = (mCurrentBudgetSpent / mCurrentBudgetCap);
                    if (percent > 1.0) percent = 1.0; if (percent < 0.0) percent = 0.0;
                    android.view.ViewGroup.LayoutParams params = mDashboardProgressFill.getLayoutParams();
                    if (params != null) { params.width = (int) (parentWidth * percent); mDashboardProgressFill.setLayoutParams(params); }
                    if (percent >= 0.9) { mDashboardProgressFill.setBackgroundColor(getResources().getColor(R.color.urgency_red)); mDashboardUtilizationLbl.setTextColor(getResources().getColor(R.color.urgency_red)); }
                    else { mDashboardProgressFill.setBackgroundColor(getResources().getColor(R.color.accent_blue)); mDashboardUtilizationLbl.setTextColor(getResources().getColor(R.color.text_muted)); }
                }
            } catch (Exception e) {}
        });
    }

    private TextView mDashboardBudgetAllocated;
    @Override
    protected void onResume() { super.onResume(); try { mDashboardBudgetAllocated = findViewById(R.id.dashboard_budget_allocated); applyLocalMockData(); } catch (Throwable t) {} }
}
