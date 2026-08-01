package com.aibuild.installedappsviewer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> visibleApps = new ArrayList<>();

    private AppListAdapter adapter;
    private EditText searchBox;
    private Switch systemSwitch;
    private TextView countText;
    private TextView emptyText;
    private ProgressBar progressBar;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("インストール済みアプリ一覧");
        buildUi();
        loadInstalledApps();
    }

    private void buildUi() {
        int padding = dp(16);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 248, 250));
        root.setPadding(padding, dp(12), padding, dp(8));

        TextView title = new TextView(this);
        title.setText("インストール済みアプリ一覧");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(25, 28, 35));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView privacy = new TextView(this);
        privacy.setText("端末内だけで処理します。通信権限はありません。");
        privacy.setTextSize(13);
        privacy.setTextColor(Color.rgb(85, 92, 105));
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyParams.topMargin = dp(3);
        privacyParams.bottomMargin = dp(10);
        root.addView(privacy, privacyParams);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        searchBox = new EditText(this);
        searchBox.setHint("アプリ名・パッケージ名で検索");
        searchBox.setSingleLine(true);
        searchBox.setTextSize(15);
        searchBox.setBackgroundColor(Color.WHITE);
        searchBox.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                0, dp(48), 1f);
        searchRow.addView(searchBox, searchParams);

        Button refreshButton = new Button(this);
        refreshButton.setText("更新");
        refreshButton.setAllCaps(false);
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                dp(76), dp(48));
        refreshParams.leftMargin = dp(8);
        searchRow.addView(refreshButton, refreshParams);
        root.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout optionRow = new LinearLayout(this);
        optionRow.setOrientation(LinearLayout.HORIZONTAL);
        optionRow.setGravity(Gravity.CENTER_VERTICAL);
        optionRow.setPadding(0, dp(8), 0, dp(5));

        systemSwitch = new Switch(this);
        systemSwitch.setText("システムアプリも表示");
        systemSwitch.setTextSize(14);
        optionRow.addView(systemSwitch, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        countText = new TextView(this);
        countText.setText("読み込み中…");
        countText.setTextSize(13);
        countText.setTextColor(Color.rgb(75, 82, 94));
        optionRow.addView(countText);
        root.addView(optionRow);

        LinearLayout contentFrame = new LinearLayout(this);
        contentFrame.setOrientation(LinearLayout.VERTICAL);
        contentFrame.setBackgroundColor(Color.WHITE);

        progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = dp(42);
        contentFrame.addView(progressBar, progressParams);

        emptyText = new TextView(this);
        emptyText.setText("該当するアプリはありません");
        emptyText.setTextSize(16);
        emptyText.setTextColor(Color.rgb(95, 100, 110));
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setVisibility(View.GONE);
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(120));
        contentFrame.addView(emptyText, emptyParams);

        listView = new ListView(this);
        listView.setDividerHeight(1);
        listView.setBackgroundColor(Color.WHITE);
        adapter = new AppListAdapter(this, visibleApps);
        listView.setAdapter(adapter);
        contentFrame.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f));

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f);
        root.addView(contentFrame, contentParams);
        setContentView(root);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        systemSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilter());
        refreshButton.setOnClickListener(v -> loadInstalledApps());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry entry = visibleApps.get(position);
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + entry.packageName));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "アプリ情報画面を開けませんでした", Toast.LENGTH_SHORT).show();
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            AppEntry entry = visibleApps.get(position);
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("packageName", entry.packageName));
            Toast.makeText(this, "パッケージ名をコピーしました", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void loadInstalledApps() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        listView.setVisibility(View.GONE);
        countText.setText("読み込み中…");

        new Thread(() -> {
            List<AppEntry> loaded = new ArrayList<>();
            PackageManager packageManager = getPackageManager();

            try {
                List<ApplicationInfo> applications;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    applications = packageManager.getInstalledApplications(
                            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
                } else {
                    //noinspection deprecation
                    applications = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
                }

                for (ApplicationInfo appInfo : applications) {
                    String label;
                    try {
                        label = packageManager.getApplicationLabel(appInfo).toString();
                    } catch (Exception ignored) {
                        label = appInfo.packageName;
                    }

                    boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            || (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

                    String versionName = "不明";
                    long versionCode = 0;
                    try {
                        PackageInfo packageInfo;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageInfo = packageManager.getPackageInfo(
                                    appInfo.packageName,
                                    PackageManager.PackageInfoFlags.of(0));
                        } else {
                            //noinspection deprecation
                            packageInfo = packageManager.getPackageInfo(appInfo.packageName, 0);
                        }
                        if (packageInfo.versionName != null && !packageInfo.versionName.isEmpty()) {
                            versionName = packageInfo.versionName;
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            versionCode = packageInfo.getLongVersionCode();
                        } else {
                            //noinspection deprecation
                            versionCode = packageInfo.versionCode;
                        }
                    } catch (Exception ignored) { }

                    Drawable icon;
                    try {
                        icon = packageManager.getApplicationIcon(appInfo);
                    } catch (Exception ignored) {
                        icon = getApplicationInfo().loadIcon(packageManager);
                    }

                    loaded.add(new AppEntry(label, appInfo.packageName,
                            versionName, versionCode, isSystem, icon));
                }

                Collator collator = Collator.getInstance(Locale.JAPANESE);
                Collections.sort(loaded, (left, right) -> collator.compare(left.label, right.label));

                mainHandler.post(() -> {
                    allApps.clear();
                    allApps.addAll(loaded);
                    progressBar.setVisibility(View.GONE);
                    listView.setVisibility(View.VISIBLE);
                    applyFilter();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    listView.setVisibility(View.GONE);
                    emptyText.setText("アプリ一覧を読み込めませんでした");
                    emptyText.setVisibility(View.VISIBLE);
                    countText.setText("読み込み失敗");
                    Toast.makeText(this, "読み込みに失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "installed-app-loader").start();
    }

    private void applyFilter() {
        if (adapter == null) return;

        String query = searchBox == null ? "" : searchBox.getText().toString()
                .trim().toLowerCase(Locale.ROOT);
        boolean includeSystem = systemSwitch != null && systemSwitch.isChecked();

        visibleApps.clear();
        int userCount = 0;
        int systemCount = 0;

        for (AppEntry entry : allApps) {
            if (entry.isSystem) systemCount++; else userCount++;
            if (!includeSystem && entry.isSystem) continue;

            String searchable = (entry.label + " " + entry.packageName)
                    .toLowerCase(Locale.ROOT);
            if (query.isEmpty() || searchable.contains(query)) {
                visibleApps.add(entry);
            }
        }

        adapter.notifyDataSetChanged();
        emptyText.setText("該当するアプリはありません");
        emptyText.setVisibility(visibleApps.isEmpty() && progressBar.getVisibility() != View.VISIBLE
                ? View.VISIBLE : View.GONE);
        listView.setVisibility(visibleApps.isEmpty() ? View.GONE : View.VISIBLE);
        countText.setText("表示 " + visibleApps.size() + " / 全 " + allApps.size()
                + "（ユーザー " + userCount + "・システム " + systemCount + "）");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final String versionName;
        final long versionCode;
        final boolean isSystem;
        final Drawable icon;

        AppEntry(String label, String packageName, String versionName,
                 long versionCode, boolean isSystem, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.isSystem = isSystem;
            this.icon = icon;
        }
    }

    private final class AppListAdapter extends BaseAdapter {
        private final Context context;
        private final List<AppEntry> items;

        AppListAdapter(Context context, List<AppEntry> items) {
            this.context = context;
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public AppEntry getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(9), dp(12), dp(9));
                row.setMinimumHeight(dp(72));

                ImageView icon = new ImageView(context);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
                iconParams.rightMargin = dp(12);
                row.addView(icon, iconParams);

                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setGravity(Gravity.CENTER_VERTICAL);

                TextView name = new TextView(context);
                name.setTextSize(16);
                name.setTextColor(Color.rgb(28, 31, 38));
                name.setTypeface(null, android.graphics.Typeface.BOLD);
                name.setSingleLine(true);
                texts.addView(name, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView packageName = new TextView(context);
                packageName.setTextSize(12);
                packageName.setTextColor(Color.rgb(86, 92, 103));
                packageName.setSingleLine(true);
                texts.addView(packageName, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView detail = new TextView(context);
                detail.setTextSize(12);
                detail.setTextColor(Color.rgb(104, 111, 123));
                detail.setSingleLine(true);
                texts.addView(detail, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                row.addView(texts, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                holder = new ViewHolder(icon, name, packageName, detail);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppEntry entry = getItem(position);
            holder.icon.setImageDrawable(entry.icon);
            holder.name.setText(entry.label);
            holder.packageName.setText(entry.packageName);
            String type = entry.isSystem ? "システムアプリ" : "ユーザーアプリ";
            holder.detail.setText(type + "　バージョン " + entry.versionName
                    + "（" + entry.versionCode + "）");
            return convertView;
        }
    }

    private static final class ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView packageName;
        final TextView detail;

        ViewHolder(ImageView icon, TextView name, TextView packageName, TextView detail) {
            this.icon = icon;
            this.name = name;
            this.packageName = packageName;
            this.detail = detail;
        }
    }
}
