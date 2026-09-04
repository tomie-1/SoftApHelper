package com.xhy.xp.softaphelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    // 严格 IPv4 逐字节校验
    public static final Pattern IP_PATTERN =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    // 下拉选项文本
    private static final int SEG_CUSTOM = 0;
    private static final int SEG_A = 1;   // 10.x.x.x
    private static final int SEG_B = 2;   // 172.16-31.x.x
    private static final int SEG_C = 3;   // 192.168.x.x

    // [id, View id...]
    private final EditText[] fields = new EditText[5];
    private final Spinner[] spinners = new Spinner[5];
    private final TextView[] prefixViews = new TextView[5];
    private EditText etPrefix;

    private static final String[] FIELD_TAGS = {"WIFI", "USB", "蓝牙", "P2P", "以太网"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fields[0] = findViewById(R.id.et_wifi);
        fields[1] = findViewById(R.id.et_usb);
        fields[2] = findViewById(R.id.et_bt);
        fields[3] = findViewById(R.id.et_p2p);
        fields[4] = findViewById(R.id.et_eth);

        spinners[0] = findViewById(R.id.sp_seg_wifi);
        spinners[1] = findViewById(R.id.sp_seg_usb);
        spinners[2] = findViewById(R.id.sp_seg_bt);
        spinners[3] = findViewById(R.id.sp_seg_p2p);
        spinners[4] = findViewById(R.id.sp_seg_eth);

        prefixViews[0] = findViewById(R.id.tv_prefix_wifi);
        prefixViews[1] = findViewById(R.id.tv_prefix_usb);
        prefixViews[2] = findViewById(R.id.tv_prefix_bt);
        prefixViews[3] = findViewById(R.id.tv_prefix_p2p);
        prefixViews[4] = findViewById(R.id.tv_prefix_eth);

        // 加载已保存配置
        SharedPreferences sp = getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE);
        String[] savedIps = {
                sp.getString(Config.KEY_WIFI_IP, Config.DEFAULT_WIFI_IP),
                sp.getString(Config.KEY_USB_IP, Config.DEFAULT_USB_IP),
                sp.getString(Config.KEY_BT_IP, Config.DEFAULT_BT_IP),
                sp.getString(Config.KEY_P2P_IP, Config.DEFAULT_P2P_IP),
                sp.getString(Config.KEY_ETH_IP, Config.DEFAULT_ETH_IP)
        };

        for (int i = 0; i < 5; i++) {
            fields[i].setText(savedIps[i]);
            setupSegmentSpinner(spinners[i], fields[i], prefixViews[i], savedIps[i]);
        }

        etPrefix = findViewById(R.id.et_prefix);
        etPrefix.setText(String.valueOf(sp.getInt(Config.KEY_PREFIX_LEN, Config.DEFAULT_PREFIX_LEN)));

        TextView pkgView = findViewById(R.id.pkg_status);
        StringBuilder sb = new StringBuilder("已安装的 Tethering 应用:\n");
        for (String pkg : installedPackages()) {
            sb.append(pkg).append('\n');
        }
        if (installedPackages().isEmpty()) {
            sb.append("(未检测到，请在作用域中勾选 android 或相关 Tethering 包)\n");
        }
        pkgView.setText(sb.toString());

        TextView btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        TextView btnReset = findViewById(R.id.btn_reset);
        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < 5; i++) {
                    String def = defaultIpFor(i);
                    String fixed = segmentPrefix(detectSegment(def));
                    // 先切 spinner（触发 listener 清空），再恢复默认值
                    spinners[i].setSelection(detectSegment(def));
                    if (fixed != null) {
                        prefixViews[i].setText(fixed);
                        prefixViews[i].setVisibility(View.VISIBLE);
                        fields[i].setText(def.substring(fixed.length()));
                    } else {
                        prefixViews[i].setVisibility(View.GONE);
                        fields[i].setText(def);
                    }
                }
                etPrefix.setText(String.valueOf(Config.DEFAULT_PREFIX_LEN));
                Toast.makeText(MainActivity.this, "已恢复到默认（记得点保存）", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 为单个 IP 输入行配置网段下拉。 */
    private void setupSegmentSpinner(final Spinner spinner, final EditText field,
                                     final TextView prefixView, String initialIp) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.ip_segment_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        final boolean[] initialized = {false};
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!initialized[0]) {
                    return; // 初始 setSelection 触发，跳过
                }
                // 用户主动切换：清空可变部分，从新网段开始
                applySegment(prefixView, field, position, true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 初始选中：此时 listener 会触发一次但不处理（initialized=false）
        spinner.setSelection(detectSegment(initialIp));
        // 手动应用一次，保留已存 IP 的可变部分
        applySegment(prefixView, field, spinner.getSelectedItemPosition(), false);
        // 标记初始化完成，之后的选择变化都视为用户操作
        initialized[0] = true;

        // 手动编辑时联动提示
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { updateHint(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * 应用网段选择。
     *
     * @param clearRest true=清空可变部分（用户主动切换网段时），false=保留可变部分（初始加载/恢复默认时）
     */
    private void applySegment(TextView prefixView, EditText field, int position, boolean clearRest) {
        String newFixed = segmentPrefix(position);
        String oldFixed = prefixView.getText().toString().trim();
        boolean hadPrefix = prefixView.getVisibility() == View.VISIBLE && oldFixed.length() > 0;
        String cur = field.getText().toString().trim();
        // 当前完整 IP = 旧前缀 + 输入框
        String full = hadPrefix ? oldFixed + cur : cur;

        if (newFixed != null) {
            prefixView.setText(newFixed);
            prefixView.setVisibility(View.VISIBLE);
            if (clearRest) {
                // 用户切换：清空，从新网段开始填
                field.setText("");
            } else if (oldFixed.length() > 0 && full.startsWith(oldFixed)) {
                // 初始/恢复：剥离旧前缀，保留可变部分
                field.setText(full.substring(oldFixed.length()));
            } else if (full.startsWith(newFixed)) {
                field.setText(full.substring(newFixed.length()));
            } else {
                field.setText("");
            }
        } else {
            // 自定义：隐藏前缀，输入框保留完整 IP
            prefixView.setVisibility(View.GONE);
            field.setText(full);
        }
    }

    /** 取一行当前完整的 IP（前缀块 + 输入框可变部分）。 */
    private String fullIpFor(int i) {
        String fixed = prefixViews[i].getText().toString().trim();
        String rest = fields[i].getText().toString().trim();
        if (prefixViews[i].getVisibility() == View.VISIBLE && fixed.length() > 0) {
            return fixed + rest;
        }
        return rest;
    }

    /** 判断一个已存 IP 应落在哪个下拉网段。 */
    private int detectSegment(String ip) {
        if (ip.startsWith("10.")) return SEG_A;
        // 172.16 ~ 172.31
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                if (second >= 16 && second <= 31) return SEG_B;
            } catch (Exception ignored) {
            }
        }
        if (ip.startsWith("192.168.")) return SEG_C;
        return SEG_CUSTOM;
    }

    /** 返回网段前缀（不含尾点后的提示）。 */
    private String segmentPrefix(int position) {
        switch (position) {
            case SEG_A: return "10.";
            case SEG_B: return "172.";
            case SEG_C: return "192.168.";
            default: return null;
        }
    }

    private void updateHint() {
        // 规则说明已静态写在布局中，无需动态覆盖
    }

    private String defaultIpFor(int i) {
        switch (i) {
            case 0: return Config.DEFAULT_WIFI_IP;
            case 1: return Config.DEFAULT_USB_IP;
            case 2: return Config.DEFAULT_BT_IP;
            case 3: return Config.DEFAULT_P2P_IP;
            default: return Config.DEFAULT_ETH_IP;
        }
    }

    private boolean isValidIp(String ip) {
        return ip != null && IP_PATTERN.matcher(ip).matches();
    }

    /** RFC1918 私网判断。 */
    private boolean isPrivateIp(String ip) {
        String[] p = ip.split("\\.");
        int a = Integer.parseInt(p[0]);
        int b = Integer.parseInt(p[1]);
        if (a == 10) return true;
        if (a == 172 && b >= 16 && b <= 31) return true;
        if (a == 192 && b == 168) return true;
        return false;
    }

    private void save() {
        String[] ips = new String[5];
        for (int i = 0; i < 5; i++) {
            ips[i] = fullIpFor(i);
        }
        String prefixStr = etPrefix.getText().toString().trim();

        // 1) 校验 IP 格式
        for (int i = 0; i < 5; i++) {
            if (!isValidIp(ips[i])) {
                Toast.makeText(this, FIELD_TAGS[i] + " IP 地址格式不正确", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // 2) 校验前缀
        int prefix;
        try {
            prefix = Integer.parseInt(prefixStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "网段前缀必须是数字", Toast.LENGTH_LONG).show();
            return;
        }
        if (prefix < 8 || prefix > 31) {
            Toast.makeText(this, "网段前缀必须为 8~31", Toast.LENGTH_LONG).show();
            return;
        }

        // 3) 是否含有非私网地址（警告但不阻断）
        final boolean hasNonPrivate = hasNonPrivate(ips);

        final String[] finalIps = ips;
        final int finalPrefix = prefix;
        Runnable doSave = new Runnable() {
            @Override
            public void run() {
                SharedPreferences sp = getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE);
                sp.edit()
                        .putString(Config.KEY_WIFI_IP, finalIps[0])
                        .putString(Config.KEY_USB_IP, finalIps[1])
                        .putString(Config.KEY_BT_IP, finalIps[2])
                        .putString(Config.KEY_P2P_IP, finalIps[3])
                        .putString(Config.KEY_ETH_IP, finalIps[4])
                        .putInt(Config.KEY_PREFIX_LEN, finalPrefix)
                        .apply();
                Toast.makeText(MainActivity.this, "已保存，请重启热点以生效", Toast.LENGTH_SHORT).show();
            }
        };

        if (hasNonPrivate) {
            new AlertDialog.Builder(this)
                    .setTitle("检测到非私网地址")
                    .setMessage("某些地址不在内外网段（10./172.16-31./192.168.），可能导致回程异常或路由问题。确定要保存吗？")
                    .setPositiveButton("仍保存", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            doSave.run();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            doSave.run();
        }
    }

    private boolean hasNonPrivate(String[] ips) {
        for (String ip : ips) {
            if (!isPrivateIp(ip)) return true;
        }
        return false;
    }

    private ArrayList<String> installedPackages() {
        ArrayList<String> pkgNameList = new ArrayList<>(
                Arrays.asList(
                        "com.android.networkstack.tethering.inprocess",
                        "com.android.networkstack.tethering",
                        "com.google.android.networkstack.tethering.inprocess",
                        "com.google.android.networkstack.tethering"
                ));
        ArrayList<String> installed = new ArrayList<>();
        for (String pkg : pkgNameList) {
            if (isInstalled(pkg)) {
                installed.add(pkg);
            }
        }
        return installed;
    }

    public boolean isInstalled(String pkgName) {
        try {
            getPackageManager().getApplicationInfo(pkgName, 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }
}