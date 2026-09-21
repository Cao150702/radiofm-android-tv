package com.radio.fm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
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

    private TextView stationName, stationMeta, icyText, statusText, listHint;
    /** 当前电台表里 HLS 台的数量（4.4 上隐藏了几个，给用户一个交代） */
    private int hlsCount;
    /** 动态频谱条（代替原来的静态调谐刻度）。见 SpectrumView。 */
    private SpectrumView spectrum;
    private Button playBtn, stopBtn, favBtn;
    private ListView list;
    private StationAdapter adapter;
    private EditText filterBox;

    /**
     * 本机能不能播 HLS。
     *
     * 按 API 21（Android 5.0）划界 —— 那是 MediaPlayer 开始支持 HLS 的版本，
     * 不是 26。取 21 而不是 26 是因为判断依据是**播放能力**，
     * 跟通知渠道（API 26）那件事无关，别把两个版本号混在一起。
     */
    private static final boolean HLS_OK = Build.VERSION.SDK_INT >= 21;

    /** 「只看收藏」按钮的文字 —— 只跟 favOnly 有关，不再受分类影响 */
    private static final String FAV_ON = "只看收藏";
    private static final String FAV_OFF = "显示全部";

    private static final String CAT_ALL = "全部";

    /**
     * 「央广·卫视」目录 —— 全国性频道。
     *
     * 三部分：中国之声 + 28 个省级卫视伴音（东方/北京/广东…）
     *        + 央广官方 13 个（经济/音乐/民族/维语…）
     *        + CCTV-1~17 / CETV-1~4 电视伴音。
     * 定义是**全国性覆盖**，不是字面的"央广" —— 卫视是省级台，只是覆盖全国。
     * 省级卫视放这里而不是各自省份，是因为它们和央广一样属于"电视伴音"，
     * 归一处好找，不用在 27 个省里翻。
     */
    private static final String CAT_CENTRAL = "央广·卫视";

    /**
     * 按钮在「全部频道」和「央广·卫视」之间对调。
     *
     * 早先这个按钮是「安卓9频道」开关（HLS 技术分类）。后来 HLS 那批台
     * 按内容归进了「央广·卫视」，安卓9 作为一个分类就失去意义了 ——
     * 剩下的全是全国性广播，跟央广·卫视完全重合。所以按钮改成切这个分类，
     * 那才是用户真正想一键到达的地方。
     */
    private static final String A9_ON  = "央广·卫视";
    private static final String A9_OFF = "全部频道";
    private Button a9Filter;

    /**
     * 收藏筛选：「只看收藏」按钮控制的是**这一路**，与[来源分类]完全独立。
     *
     * 之前是 filterMode 单变量，导致按下「安卓9频道」会把收藏按钮的文字也改掉
     * （用户报的第 4 个问题）。两个维度本来就正交 —— 「收藏里的央广台」是个
     * 正当组合 —— 所以拆成两个变量。
     */
    private boolean favOnly = false;

    /** 当前选中的来源分类（Spinner）。见 buildCategories()。 */
    private String category = CAT_ALL;

    private Spinner categorySpinner;
    private ArrayAdapter<String> categoryAdapter;
    private final List<String> categories = new ArrayList<String>();

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
        applyLauncherIcon();

        bindViews();
        loadStations();
        buildCategories();   // 目录要先于筛选建好
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

    /**
     * 桌面图标。
     *
     * Android 8.0+ 走自适应图标（mipmap-anydpi-v26/ic_launcher.xml）——
     * 系统会自己裁形状，不需要也不能在这里 setIcon。
     * 顺带一提：老版本"图标带白底"就是因为缺了那段声明，
     * 系统拿普通图标垫白底再裁。现在补上了。
     *
     * 8.0 以下没有自适应图标机制，直接 setIcon。如果配的是**位图**图标
     * 且带白底，用 IconUtil 把与边缘连通的白色抠掉再设上去 ——
     * 矢量图标没有背景色，不需要这一步。
     */
    private void applyLauncherIcon() {
        if (Build.VERSION.SDK_INT >= 26) return;   // 自适应图标已处理

        // 图标资源是矢量（drawable/ic_launcher.xml），背景本就透明，无需处理。
        // 若将来换成带白底的位图，把下面的调用打开即可：
        // Bitmap bmp = IconUtil.makeBackgroundTransparent(this, R.drawable.ic_launcher);
        // if (bmp != null) { getApplicationInfo().icon = ...; }
    }

    private void bindViews() {
        stationName = (TextView) findViewById(R.id.station_name);
        stationMeta = (TextView) findViewById(R.id.station_meta);
        icyText     = (TextView) findViewById(R.id.icy_text);
        statusText  = (TextView) findViewById(R.id.status_text);
        listHint    = (TextView) findViewById(R.id.list_hint);   // 竖屏布局没有，为 null 时跳过
        spectrum    = (SpectrumView) findViewById(R.id.spectrum);
        playBtn     = (Button) findViewById(R.id.btn_play);
        stopBtn     = (Button) findViewById(R.id.btn_stop);
        favBtn      = (Button) findViewById(R.id.btn_fav);
        list        = (ListView) findViewById(R.id.station_list);
        filterBox   = (EditText) findViewById(R.id.filter_box);
        categorySpinner = (Spinner) findViewById(R.id.category_spinner);

        // 电视布局（layout-land）里没有软键盘搜索框，改用一个「只看收藏」按钮。
        // 竖屏布局没有这个 id，findViewById 返回 null，这里跳过即可 ——
        // 两套布局共用这一份 Java，靠 null 判断分流。
        View favFilter = findViewById(R.id.btn_fav_filter);
        if (favFilter != null) {
            favFilter.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    favOnly = !favOnly;
                    ((Button) v).setText(favOnly ? FAV_OFF : FAV_ON);
                    applyFilter();
                }
            });
        }
        // 一键在「全部频道」和「央广·卫视」之间对调。
        //
        // 按钮文字显示的是**按下去会切到哪**，不是当前状态 ——
        // 用户报的「按了之后按钮还写着安卓9频道」就是这个：他以为是状态，
        // 实际得能一眼看出「再按一下会去哪」。
        a9Filter = (Button) findViewById(R.id.btn_a9_filter);
        if (a9Filter != null) {
            a9Filter.setText(A9_ON);
            a9Filter.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean toCentral = !CAT_CENTRAL.equals(category);
                    selectCategory(toCentral ? CAT_CENTRAL : CAT_ALL);
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

        hlsCount = 0;
        for (Station s : allStations) if (s.isHls()) hlsCount++;

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
            if (favOnly && !s.favorite) continue;
            if (!matchesCategory(s)) continue;
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

    /**
     * 电台是否属于当前选中的分类。
     *
     * 目录：全部频道 / 安卓4 / 央广·卫视 / 港澳台 / 各地（各省台）/ 网络 / 国际…
     * 分类按 region 聚合（见 buildCategories），三个跨 region 的合并项：
     *   · 央广·卫视 —— region「中央」，另含按名字匹配的 CCTV/CETV
     *   · 港澳台     —— 香港/澳门/台湾，各自只有几个台，单列太碎
     *   · 安卓4      —— 非 HLS 那批，**按 Station.isHls() 判定，不看 region**
     */
    private boolean matchesCategory(Station s) {
        if (category.equals(CAT_ALL)) {
            // 「全部」的含义随系统版本变：
            //   4.4  → 不含 HLS 台（点了不出声，会让人以为应用坏了）
            //   5.0+ → 全都算「能播的」，一起列出
            return HLS_OK || !s.isHls();
        }
        if ("安卓4".equals(category)) return !s.isHls();
        if (CAT_CENTRAL.equals(category)) {
            // 全国性频道：中国之声 + 28 个省级卫视伴音 + 央广官方 13 个 + CCTV/CETV
            // 它们 region 都标「中央」；HLS 与否是另一回事（见 Station.isHls），
            // 4.4 上的可见性由上面的 isHls() 判断负责，这里只管内容归类。
            if ("中央".equals(s.region)) return true;
            return s.name.startsWith("CCTV") || s.name.startsWith("CETV");
        }
        if ("港澳台".equals(category)) {
            return "香港".equals(s.region) || "澳门".equals(s.region) || "台湾".equals(s.region);
        }
        return category.equals(s.region);
    }

    /**
     * 按当前电台表构建分类目录。
     *
     * 顺序固定（全部→安卓4→央广·卫视→港澳台→各省…），不按数量排序 ——
     * 遥控器用户的肌肉记忆是「第几项」，目录顺序一变就白记了。
     */
    private void buildCategories() {
        categories.clear();
        categories.add(CAT_ALL);

        final java.util.LinkedHashSet<String> rest = new java.util.LinkedHashSet<String>();
        boolean hasCentral = false, hasHmt = false;
        for (Station s : allStations) {
            String r = s.region;
            if ("中央".equals(r)) { hasCentral = true; continue; }
            if ("香港".equals(r) || "澳门".equals(r) || "台湾".equals(r)) { hasHmt = true; continue; }
            if (r.length() > 0) rest.add(r);
        }
        // 「安卓4」= 本机能播的那批。它跟「全部频道」在 5.0+ 上内容相同，
        // 是给"我就想确认哪些能在老机器上放"留的一个明确入口。
        if (HLS_OK && !rest.contains("安卓4")) categories.add("安卓4");
        if (hasCentral) categories.add(CAT_CENTRAL);
        if (hasHmt) categories.add("港澳台");
        categories.addAll(rest);

        if (categorySpinner == null) return;   // 布局里没有分类控件时静默跳过
        if (categoryAdapter == null) {
            categoryAdapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_spinner_item, categories);
            categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            categorySpinner.setAdapter(categoryAdapter);
            categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    String picked = categories.get(pos);
                    if (!picked.equals(category)) {
                        category = picked;
                        syncA9Button();
                        applyFilter();
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> p) { }
            });
        } else {
            categoryAdapter.notifyDataSetChanged();
        }
        // 目录变化后原来的选择可能已不存在，回落到「全部」
        if (!categories.contains(category)) category = CAT_ALL;
        categorySpinner.setSelection(categories.indexOf(category));
    }

    /** 同步按钮文字 —— 它显示的是"按下去会去哪" */
    private void syncA9Button() {
        if (a9Filter == null) return;   // 竖屏布局没有这个按钮
        a9Filter.setText(CAT_CENTRAL.equals(category) ? A9_OFF : A9_ON);
    }

    /** 从菜单/按钮切分类：同时更新 Spinner 和按钮文字，避免两处显示打架 */
    private void selectCategory(String cat) {
        if (categories.contains(cat)) {
            category = cat;
            if (categorySpinner != null) categorySpinner.setSelection(categories.indexOf(cat));
        }
        syncA9Button();
        applyFilter();
    }

    /**
     * 启动 RadioService。
     *
     * 8.0 起后台不能再用 startService —— 会直接抛
     * IllegalStateException: Not allowed to start service Intent。
     * 本应用点列表就放音，这算「用户可见的播放行为」，用前台服务启动名正言顺；
     * RadioService.onCreate 里本来就 startForeground 了，正好对上。
     */
    private void startRadioService(Intent i) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void playStation(Station s) {
        String[] urls = s.urls;
        Intent i = new Intent(this, RadioService.class)
                .setAction(RadioService.ACTION_PLAY)
                .putExtra(RadioService.EXTRA_URLS, urls)
                .putExtra(RadioService.EXTRA_NAME, s.name);
        startRadioService(i);
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

        // 频谱只在**真正出声**时动。缓冲/暂停/停止/出错都静止 ——
        // 这是用户明确要的：暂停了还在跳会让人以为没停住。
        if (spectrum != null) spectrum.setActive(st == RadioService.STATE_PLAYING);

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

        // 原来的"调谐指针随播放状态摆动"已由动态频谱取代（见 SpectrumView）。
        updateStatus();
    }

    private void updateStatus() {
        String extra = (favOnly ? " · 收藏" : "")
                + (CAT_ALL.equals(category) ? "" : " · " + category);
        statusText.setHint("共 " + shown.size() + " 个电台" + extra);

        // 分类里一个台都没有时给个解释，别让用户以为是坏了。
        // 最容易撞上的场景：Android 4.4 上选「央广·卫视」—— 那一类 66 个台里
        // 65 个是 HLS，4.4 只能显示剩下的 1 个。不说明的话看着就像加载失败。
        if (listHint != null) {
            if (shown.isEmpty() && !favOnly) {
                listHint.setText(HLS_OK
                        ? "该分类暂无电台"
                        : "该分类的台多为 HLS 流，Android 4.4 放不了（选「全部频道」可跳过隐藏）");
            } else {
                listHint.setText(HLS_OK
                        ? "方向键选台 · 确认键播放"
                        : "方向键选台 · 确认键播放（已隐藏 " + hlsCount + " 个本机放不了的 HLS 台）");
            }
        }
    }

    // ---------------- 菜单 ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "全部电台");
        menu.add(0, 2, 1, FAV_ON);
        menu.add(0, 3, 2, "添加自定义电台");
        menu.add(0, 4, 3, "导入 M3U/PLS");
        menu.add(0, 5, 4, "浏览在线电台库");
        menu.add(0, 6, 5, "设置");
        menu.add(0, 7, 7, "彻底退出");
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
                                "导入 M3U/PLS", "浏览在线电台库", "设置", "彻底退出"};
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("更多")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0: favOnly = false; selectCategory(CAT_ALL); break;
                            case 1: favOnly = true; applyFilter(); break;
                            case 2: showAddDialog(); break;
                            case 3: importPlaylist(); break;
                            case 4: startActivity(new Intent(MainActivity.this, OnlineBrowserActivity.class)); break;
                            case 5: startActivity(new Intent(MainActivity.this, SettingsActivity.class)); break;
                            case 6: confirmExit(); break;
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
            case 1: favOnly = false; selectCategory(CAT_ALL); return true;
            case 2: favOnly = true; applyFilter(); return true;
            case 3: showAddDialog(); return true;
            case 4: importPlaylist(); return true;
            case 5: startActivity(new Intent(this, OnlineBrowserActivity.class)); return true;
            case 6: startActivity(new Intent(this, SettingsActivity.class)); return true;
            case 7: confirmExit(); return true;
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

    // ---------------- 彻底退出 ----------------

    /**
     * 彻底退出：停播放、停服务、并把这个进程真的杀掉。
     *
     * 只 finish() 是不够的 —— RadioService 是前台服务且握着 WakeLock，
     * Activity 关掉后它照旧在后台放音耗电。用户要的「退出」是这个意思，
     * 不是「退到后台接着放」。
     *
     * 用 Process.killProcess 而不是 System.exit：后者只是发个异常请虚拟机
     * 退出，有 finally 或未捕获异常处理器就能把它吞掉，前台服务也就跟着
     * 活下来。killProcess 直接了结进程，没有商量余地。整个过程没有需要
     * 落盘的状态（收藏、电台表都是每次操作即时写入 SharedPreferences 的）。
     */
    private void exitApp() {
        try {
            if (bound) {
                if (service != null) service.removeListener(this);
                unbindService(conn);
                bound = false;
            }
            stopService(new Intent(this, RadioService.class));
        } catch (Exception ignored) {
            // 服务已经没了也无所谓，下面照样要杀进程
        }
        finish();
        // 给系统一点时间走完服务的销毁流程，再了结进程
        new android.os.Handler().postDelayed(new Runnable() {
            @Override public void run() {
                Process.killProcess(Process.myPid());
            }
        }, 250);
    }

    /**
     * 退出前的确认框。
     *
     * 按遥控器返回键不会走到这里 —— 那个是「回上一层」，一按就退对电视用户
     * 太容易误触（正听着想调音量，手一滑就全关了）。退出只在「更多」菜单里
     * 给一次显式选择，并且再确认一道。
     */
    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("退出应用")
                .setMessage("将停止播放并关闭应用。")
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { exitApp(); }
                })
                .setNegativeButton("取消", null)
                .show();
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
