package com.radio.fm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 设置：定时关闭、定时唤醒、曲目信息 */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private TextView sleepStatus, alarmStationLabel;
    private CheckBox alarmEnabled, showIcy, bootResume;
    private EditText alarmHour, alarmMinute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle("设置");

        prefs = new Prefs(this);

        sleepStatus       = (TextView) findViewById(R.id.sleep_status);
        alarmStationLabel = (TextView) findViewById(R.id.alarm_station_label);
        alarmEnabled      = (CheckBox) findViewById(R.id.alarm_enabled);
        showIcy           = (CheckBox) findViewById(R.id.show_icy);
        bootResume        = (CheckBox) findViewById(R.id.boot_resume);
        alarmHour         = (EditText) findViewById(R.id.alarm_hour);
        alarmMinute       = (EditText) findViewById(R.id.alarm_minute);

        bindSleepButtons();

        alarmEnabled.setChecked(prefs.isAlarmEnabled());
        alarmHour.setText(String.format("%02d", prefs.getAlarmHour()));
        alarmMinute.setText(String.format("%02d", prefs.getAlarmMinute()));
        showIcy.setChecked(prefs.isShowIcy());
        bootResume.setChecked(prefs.isBootResume());

        updateSleepStatus();
        updateAlarmLabel();

        alarmEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                applyAlarm(checked);
            }
        });

        findViewById(R.id.alarm_pick).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickStation(); }
        });

        findViewById(R.id.save_btn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });

        bindTimeSteppers();

        // 电视上给个明确的初始焦点。不设的话焦点落在第一个可聚焦控件上，
        // 用户进来不知道光标在哪，得先按一下方向键才看得见。
        // 竖屏布局没有 time_hour_up，回落到保存按钮。
        View first = findViewById(R.id.time_hour_up);
        if (first == null) first = findViewById(R.id.save_btn);
        if (first != null) {
            final View target = first;
            target.post(new Runnable() {
                @Override public void run() { target.requestFocus(); }
            });
        }
    }

    /**
     * 电视版的时间加减。
     *
     * 手机上时间是两个 EditText，敲数字即可；但电视遥控器要弹软键盘才能输入数字，
     * 基本没法用。电视布局（layout-land/activity_settings.xml）把 EditText 换成
     * 不可聚焦的显示框，另配 ▲▼ 按钮，靠这里加减。
     *
     * 竖屏布局没有这几个按钮的 id，findViewById 返回 null，自动跳过 ——
     * 两套布局共用这一份 Java。改完立即回写 EditText 并调 applyAlarm，
     * 这样上下的保存逻辑完全不用动。
     */
    private void bindTimeSteppers() {
        step(R.id.time_hour_up,   R.id.alarm_hour,   1, 0, 23);
        step(R.id.time_hour_down, R.id.alarm_hour,  -1, 0, 23);
        step(R.id.time_min_up,    R.id.alarm_minute, 1, 0, 59);
        step(R.id.time_min_down,  R.id.alarm_minute,-1, 0, 59);
    }

    private void step(int btnId, final int fieldId, final int delta, final int min, final int max) {
        View b = findViewById(btnId);
        if (b == null) return;                    // 竖屏布局没有这个按钮
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                EditText field = (EditText) findViewById(fieldId);
                int cur = parseInt(field, delta > 0 ? min : max, min, max);
                int next = cur + delta;
                if (next > max) next = min;           // 循环，从 23 再按 ▲ 回到 0
                if (next < min) next = max;
                field.setText(String.format("%02d", next));
                applyAlarm(alarmEnabled.isChecked());
            }
        });
    }

    // ---------------- 定时关闭 ----------------

    private void bindSleepButtons() {
        int[] ids = {R.id.sleep_15, R.id.sleep_30, R.id.sleep_60, R.id.sleep_90, R.id.sleep_off};
        final int[] mins = {15, 30, 60, 90, 0};
        for (int i = 0; i < ids.length; i++) {
            final int m = mins[i];
            findViewById(ids[i]).setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.setSleepMinutes(m);
                    TimerScheduler.scheduleSleep(SettingsActivity.this, m);
                    updateSleepStatus();
                    Toast.makeText(SettingsActivity.this,
                            m == 0 ? "已取消定时关闭" : m + " 分钟后自动停止",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateSleepStatus() {
        int m = prefs.getSleepMinutes();
        sleepStatus.setText(m > 0 ? ("已设置：" + m + " 分钟后停止") : "");
    }

    // ---------------- 定时唤醒 ----------------

    private void applyAlarm(boolean on) {
        int h = parseInt(alarmHour, prefs.getAlarmHour(), 0, 23);
        int mi = parseInt(alarmMinute, prefs.getAlarmMinute(), 0, 59);
        alarmHour.setText(String.format("%02d", h));
        alarmMinute.setText(String.format("%02d", mi));

        if (on && prefs.getAlarmUrl().length() == 0) {
            Toast.makeText(this, "还没选唤醒电台，先点下面的按钮选一个", Toast.LENGTH_LONG).show();
            alarmEnabled.setChecked(false);
            return;
        }
        prefs.setAlarm(on, h, mi, prefs.getAlarmStationName(), prefs.getAlarmUrl());
        TimerScheduler.scheduleAlarm(this, h, mi, on);
        Toast.makeText(this, on ? ("每天 " + String.format("%02d:%02d", h, mi) + " 自动播放")
                : "已关闭定时唤醒", Toast.LENGTH_SHORT).show();
    }

    private int parseInt(EditText e, int def, int min, int max) {
        try {
            int v = Integer.parseInt(e.getText().toString().trim());
            return v < min ? min : (v > max ? max : v);
        } catch (Exception ex) {
            return def;
        }
    }

    private void pickStation() {
        final List<Station> all = StationData.builtIn();
        // 加上自定义的
        String custom = prefs.getString("custom_stations", "");
        if (custom.length() > 0) {
            for (String rec : custom.split("\n")) {
                String[] parts = rec.split("\t");
                if (parts.length >= 2) all.add(new Station(parts[0], "自定义", "我的", parts[1]));
            }
        }

        final String[] names = new String[all.size()];
        for (int i = 0; i < all.size(); i++) names[i] = all.get(i).name;

        new AlertDialog.Builder(this)
                .setTitle("选择唤醒电台")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Station s = all.get(which);
                        prefs.setAlarm(prefs.isAlarmEnabled(), prefs.getAlarmHour(),
                                prefs.getAlarmMinute(), s.name, s.primaryUrl());
                        updateAlarmLabel();
                    }
                })
                .show();
    }

    private void updateAlarmLabel() {
        String n = prefs.getAlarmStationName();
        alarmStationLabel.setText(n.length() > 0 ? ("唤醒电台：" + n) : "唤醒电台：未选择");
        ((Button) findViewById(R.id.alarm_pick)).setText(
                n.length() > 0 ? "更换唤醒电台" : "选择唤醒电台");
    }

    // ---------------- 保存 ----------------

    private void save() {
        prefs.setShowIcy(showIcy.isChecked());
        prefs.setBootResume(bootResume.isChecked());

        // 时间可能在没动 checkbox 的情况下被改了，这里再同步一次
        if (alarmEnabled.isChecked()) {
            applyAlarm(true);
        }

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
