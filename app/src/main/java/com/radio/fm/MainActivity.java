package com.radio.fm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主界面 —— 复古收音机造型。
 *
 * 没有用 Fragment / RecyclerView / AndroidX，全部是 API 19 就有的原生控件：
 * 这样 APK 小，老机器上也快。
 */
public class MainActivity extends Activity implements RadioService.Listener {

    private RadioService service;
    private boolean bound;

    private Prefs prefs;
    private final List<Station> allStations = new ArrayList<Station>();
    private final List<Station> shown = new ArrayList<Station>();

    private TextView stationName, stationMeta, icyText, statusText;
    private ImageView tuneNeedle;
    private Button playBtn, stopBtn, favBtn;
    private ListView list;
    private StationAdapter adapter;
    private EditText filterBox;

    private int filterMode = 0;   // 0=全部 1=收藏
    private String query = "";

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            service = ((RadioService.LocalBinder) b).getService();
            service.addListener(MainActivity.this);
            bound = true;
            refreshUi();
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            bound = false;
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        Tls12.install();

        bindViews();
        loadStations();
        applyFilter();

        // 进页面就把焦点放到列表第一行（见 bindViews 里的说明）。
        // 放这儿而不是 bindViews 里，是因为此时数据已经加载完、ListView 有内容了。
        // 用 requestFocus 而不是 requestFocusFromTouch —— 后者是触摸模式专用的，
        // 遥控器场景下会把 ListView 拽进 touch mode，方向键反而失灵。
        if (list.getCount() > 0) {
            list.post(new Runnable() {
                @Override public void run() {
                    list.requestFocus();
                    list.setSelection(0);
                }
            });
        }

        bindService(new Intent(this, RadioService.class), conn, Context.BIND_AUTO_CREATE);
    }

    private void bindViews() {
        stationName = (TextView) findViewById(R.id.station_name);
        stationMeta = (TextView) findViewById(R.id.station_meta);
        icyText     = (TextView) findViewById(R.id.icy_text);
        statusText  = (TextView) findViewById(R.id.status_text);
        tuneNeedle  = (ImageView) findViewById(R.id.tune_needle);
        playBtn     = (Button) findViewById(R.id.btn_play);
        stopBtn     = (Button) findViewById(R.id.btn_stop);
        favBtn      = (Button) findViewById(R.id.btn_fav);
        list        = (ListView) findViewById(R.id.station_list);
        filterBox   = (EditText) findViewById(R.id.filter_box);

        // 电视布局（layout-land）里没有软键盘搜索框，改用一个「只看收藏」按钮。
        // 竖屏布局没有这个 id，findViewById 返回 null，这里跳过即可 ——
        // 两套布局共用这一份 Java，靠 null 判断分流。
        View favFilter = findViewById(R.id.btn_fav_filter);
        if (favFilter != null) {
            favFilter.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    filterMode = (filterMode == 1) ? 0 : 1;
                    ((Button) v).setText(filterMode == 1 ? "显示全部" : "只看收藏");
                    applyFilter();
                }
            });
        }

        // 电视布局的「更多」按钮。
        //
        // 原先调 openOptionsMenu()，但实测在电视上按下去什么都不发生 ——
        // options menu 依赖 ActionBar 的存在和系统对菜单键的处理，
        // 在没有硬件菜单键的设备上并不可靠。改成弹一个明确的列表对话框：
        // 行为确定，而且 AlertDialog 的列表项自带焦点高亮，遥控器直接能用。
        View moreBtn = findViewById(R.id.btn_more);
        if (moreBtn != null) {
            moreBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showTvMenu(); }
            });
        }

        adapter = new StationAdapter();
        list.setAdapter(adapter);
        // 遥控器一进页面就有明确落点：焦点直接给列表第一行（见 onCreate 里的说明）。
        // 注意 ListView 的焦点机制：列表本身拿到焦点后，由它用 listSelector
        // 画「当前行」高亮 —— 行的 state_focused 在这种模式下不触发。
        // 所以布局里 listSelector 必须是个看得见的东西，设成透明就会出现
        // 「有焦点但看不见落在哪、确认键也没反应」的情况（踩过）。
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                playStation(shown.get(pos));
            }
        });
        // 遥控器按键处理。
        //
        // 为什么不用 ListView 自带的点击：方向和确认键确实都送达了
        // （实测 selectedPos 会随方向键变化），但 DPAD_CENTER 不会触发
        // onItemClick —— 列表能选中、却按不动，电视上等于不可用。
        // 这里直接接管确认键，用 selectedPos 自己调 playStation，
        // 行为确定、不依赖 ListView 内部实现。
        list.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                boolean confirm = (code == KeyEvent.KEYCODE_DPAD_CENTER
                        || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER);
                if (confirm) {
                    int pos = list.getSelectedItemPosition();
                    if (pos >= 0 && pos < shown.size()) {
                        playStation(shown.get(pos));
                        return true;      // 已处理，不要再往下传
                    }
                    return false;
                }
                // 列表内的 ↑ 本来是滚动列表用的，所以焦点出不去 ——
                // 而「往上走回按钮区」是遥控器用户最自然的动作（← 虽然也能出去，
                // 但没人会先想到按左）。折中：已经在第一行还按 ↑，就把焦点交给
                // 按钮区。列表内部移动不受影响。
                if (code == KeyEvent.KEYCODE_DPAD_UP
                        && list.getSelectedItemPosition() <= 0) {
                    View target = findViewById(R.id.btn_fav_filter);
                    if (target == null) target = playBtn;   // 竖屏布局没有「只看收藏」
                    if (target != null) { target.requestFocus(); return true; }
                }
                return false;
            }
        });

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (service == null) return;
                service.toggle();
                if (service.getState() == RadioService.STATE_IDLE
                        && (service.getCurrentName() == null || service.getCurrentName().length() == 0)) {
                    Station s = shown.isEmpty() ? null : shown.get(0);
                    if (s != null) playStation(s);
                }
                refreshUi();
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (service != null) service.stopPlayback();
                refreshUi();
            }
        });

        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleFavoriteOfCurrent();
            }
        });

        filterBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    query = v.getText().toString().trim();
                    applyFilter();
                    return true;
                }
                return false;
            }
        });
        // 边打字边过滤
        filterBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().trim();
                applyFilter();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // ---------------- 电台列表 ----------------

    private void loadStations() {
        allStations.clear();
        allStations.addAll(StationData.builtIn());

        // 恢复收藏
        String favs = prefs.getString("favorites", "");
        for (Station s : allStations) {
            s.favorite = favs.contains("|" + s.name + "|");
        }

        // 用户自己加的电台
        String custom = prefs.getString("custom_stations", "");
        if (custom.length() > 0) {
            for (String rec : custom.split("\n")) {
                String[] parts = rec.split("\t");
                if (parts.length >= 2 && parts[0].length() > 0 && parts[1].length() > 0) {
                    Station s = new Station(parts[0], "自定义", "我的",
                            parts[1].split("\\|"));
                    s.favorite = true;      // 自定义的默认进收藏
                    allStations.add(s);
                }
            }
        }
    }

    private void applyFilter() {
        shown.clear();
        for (Station s : allStations) {
            if (filterMode == 1 && !s.favorite) continue;
            if (query.length() > 0) {
                String q = query.toLowerCase();
                if (!s.name.toLowerCase().contains(q)
                        && !s.genre.toLowerCase().contains(q)
                        && !s.region.toLowerCase().contains(q)) continue;
            }
            shown.add(s);
        }
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void playStation(Station s) {
        String[] urls = s.urls;
        Intent i = new Intent(this, RadioService.class)
                .setAction(RadioService.ACTION_PLAY)
                .putExtra(RadioService.EXTRA_URLS, urls)
                .putExtra(RadioService.EXTRA_NAME, s.name);
        startService(i);
        stationName.setText(s.name);
        stationMeta.setText(s.subtitle());
    }

    private void toggleFavoriteOfCurrent() {
        if (service == null) return;
        String cur = service.getCurrentName();
        if (cur == null || cur.length() == 0) {
            Toast.makeText(this, "先选一个电台", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean nowFav = false;
        for (Station s : allStations) {
            if (s.name.equals(cur)) {
                s.favorite = !s.favorite;
                nowFav = s.favorite;
                break;
            }
        }
        saveFavorites();
        adapter.notifyDataSetChanged();
        Toast.makeText(this, nowFav ? "已加入收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    private void saveFavorites() {
        StringBuilder sb = new StringBuilder("|");
        for (Station s : allStations) if (s.favorite) sb.append(s.name).append("|");
        prefs.setString("favorites", sb.toString());
    }

    // ---------------- 服务回调 ----------------

    @Override public void onStateChanged(int state, String name, String icy) {
        runOnUiThread(new Runnable() {
            @Override public void run() { refreshUi(); }
        });
    }

    @Override public void onError(final String message) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                refreshUi();
            }
        });
    }

    private void refreshUi() {
        if (service == null) return;
        int st = service.getState();
        String name = service.getCurrentName();
        String icy = service.getIcyTitle();

        if (name != null && name.length() > 0) stationName.setText(name);

        switch (st) {
            case RadioService.STATE_PREPARING:
                statusText.setText(getString(R.string.buffering));
                playBtn.setText(R.string.pause);
                break;
            case RadioService.STATE_PLAYING:
                statusText.setText("正在播放");
                playBtn.setText(R.string.pause);
                break;
            case RadioService.STATE_ERROR:
                statusText.setText("错误：" + service.getLastError());
                playBtn.setText(R.string.play);
                break;
            default:
                statusText.setText("已停止");
                playBtn.setText(R.string.play);
                break;
        }

        if (prefs.isShowIcy() && icy != null && icy.length() > 0) {
            icyText.setVisibility(View.VISIBLE);
            icyText.setText("♪ " + icy);
        } else {
            icyText.setVisibility(View.GONE);
        }

        // 调谐指针随播放状态轻微摆动，纯装饰
        tuneNeedle.setVisibility(st == RadioService.STATE_PLAYING ? View.VISIBLE : View.INVISIBLE);
        updateStatus();
    }

    private void updateStatus() {
        String extra = filterMode == 1 ? " · 收藏" : "";
        statusText.setHint("共 " + shown.size() + " 个电台" + extra);
    }

    // ---------------- 菜单 ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "全部电台");
        menu.add(0, 2, 1, "只看收藏");
        menu.add(0, 3, 2, "添加自定义电台");
        menu.add(0, 4, 3, "导入 M3U/PLS");
        menu.add(0, 5, 4, "浏览在线电台库");
        menu.add(0, 6, 5, "设置");
        return true;
    }

    /**
     * 电视版的「更多」菜单。
     *
     * 不用 openOptionsMenu() —— 实测在电视上按下去毫无反应（依赖 ActionBar 和
     * 系统对菜单键的处理，无菜单键的设备上不可靠）。
     * 改成显式对话框：AlertDialog 的列表项本身就可聚焦、有系统高亮，
     * 遥控器方向键 + 确认键直接能用，行为完全确定。
     *
     * 菜单项和 onCreateOptionsMenu 保持一致 —— 改一边记得改另一边。
     */
    private void showTvMenu() {
        final String[] items = {"全部电台", "只看收藏", "添加自定义电台",
                                "导入 M3U/PLS", "浏览在线电台库", "设置"};
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("更多")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: filterMode = 0; applyFilter(); break;
                            case 1: filterMode = 1; applyFilter(); break;
                            case 2: showAddDialog(); break;
                            case 3: importPlaylist(); break;
                            case 4: startActivity(new Intent(MainActivity.this, OnlineBrowserActivity.class)); break;
                            case 5: startActivity(new Intent(MainActivity.this, SettingsActivity.class)); break;
                        }
                    }
                })
                .create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                // AlertDialog 的列表默认不一定拿到焦点（触摸设备上没人关心，
                // 电视上就表现为"方向键没反应"）。主动把第一项选上。
                final ListView lv = dlg.getListView();
                if (lv != null) {
                    lv.setFocusableInTouchMode(false);
                    lv.setFocusable(true);
                    lv.requestFocus();
                    lv.setSelection(0);
                }
            }
        });
        dlg.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1: filterMode = 0; applyFilter(); return true;
            case 2: filterMode = 1; applyFilter(); return true;
            case 3: showAddDialog(); return true;
            case 4: importPlaylist(); return true;
            case 5: startActivity(new Intent(this, OnlineBrowserActivity.class)); return true;
            case 6: startActivity(new Intent(this, SettingsActivity.class)); return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddDialog() {
        final View v = LayoutInflater.from(this).inflate(R.layout.dialog_add, null);
        new AlertDialog.Builder(this)
                .setTitle("添加自定义电台")
                .setView(v)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        EditText n = (EditText) v.findViewById(R.id.add_name);
                        EditText u = (EditText) v.findViewById(R.id.add_url);
                        String name = n.getText().toString().trim();
                        String url = u.getText().toString().trim();
                        if (name.length() == 0 || url.length() == 0) {
                            Toast.makeText(MainActivity.this, "名称和地址都要填", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            Toast.makeText(MainActivity.this, "地址要以 http:// 或 https:// 开头",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        String old = prefs.getString("custom_stations", "");
                        prefs.setString("custom_stations",
                                old + (old.length() > 0 ? "\n" : "") + name + "\t" + url);
                        loadStations();
                        applyFilter();
                        Toast.makeText(MainActivity.this, "已添加", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void importPlaylist() {
        final EditText input = new EditText(this);
        input.setHint("粘贴 M3U/PLS 内容，或 http:// 开头的播放列表地址");
        input.setMinLines(4);
        new AlertDialog.Builder(this)
                .setTitle("导入播放列表")
                .setView(input)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        String text = input.getText().toString().trim();
                        if (text.length() == 0) return;
                        if (text.startsWith("http://") || text.startsWith("https://")) {
                            new PlaylistLoader(MainActivity.this).load(text);
                        } else {
                            applyPlaylist(IcyMetadata.parsePlaylist(text, null));
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    void applyPlaylist(Map<String, String> entries) {
        if (entries.isEmpty()) {
            Toast.makeText(this, "没解析出电台", Toast.LENGTH_SHORT).show();
            return;
        }
        String old = prefs.getString("custom_stations", "");
        StringBuilder sb = new StringBuilder(old);
        int n = 0;
        for (Map.Entry<String, String> e : entries.entrySet()) {
            sb.append(sb.length() > 0 ? "\n" : "").append(e.getKey()).append("\t").append(e.getValue());
            n++;
        }
        prefs.setString("custom_stations", sb.toString());
        loadStations();
        applyFilter();
        Toast.makeText(this, "导入 " + n + " 个电台", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStations();
        applyFilter();
        refreshUi();
        // 回到界面时把焦点还给列表 —— 从设置页返回、或系统抢走焦点后，
        // 遥控器需要有个确定的位置可以继续操作。
        list.postDelayed(new Runnable() {
            @Override public void run() {
                if (list.getCount() > 0 && list.getSelectedItemPosition() < 0) {
                    list.requestFocus();
                    list.setSelection(0);
                }
            }
        }, 300);
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            if (service != null) service.removeListener(this);
            unbindService(conn);
            bound = false;
        }
        super.onDestroy();
    }

    // ---------------- 列表适配器 ----------------

    private class StationAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.row_station, parent, false);
            }
            Station s = shown.get(pos);
            TextView name = (TextView) v.findViewById(R.id.row_name);
            TextView sub  = (TextView) v.findViewById(R.id.row_sub);
            TextView star = (TextView) v.findViewById(R.id.row_star);
            TextView now  = (TextView) v.findViewById(R.id.row_now);

            name.setText(s.name);
            sub.setText(s.subtitle());
            star.setVisibility(s.favorite ? View.VISIBLE : View.GONE);

            boolean isCurrent = service != null && s.name.equals(service.getCurrentName());
            now.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
            if (isCurrent) {
                int st = service.getState();
                now.setText(st == RadioService.STATE_PLAYING ? "▶"
                        : st == RadioService.STATE_PREPARING ? "…" : "■");
            }
            return v;
        }
    }
}
