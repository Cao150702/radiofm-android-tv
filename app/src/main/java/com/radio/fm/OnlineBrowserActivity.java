package com.radio.fm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 在线电台库 —— radio-browser.info 的全球电台目录（公共 API，无需 key）。
 *
 * 重要：这个接口返回的国内电台里有大量 HLS (m3u8) 流，
 * 而 Android 4.4 的 MediaPlayer 不支持 HLS，所以这里直接把 hls=1 的过滤掉，
 * 免得用户点了播不出来。
 *
 * 另一个现实问题：国内很多源会 403（蜻蜓FM 等），所以列表里标了"未验证"，
 * 播不出来时是源的问题不是应用的问题。
 */
public class OnlineBrowserActivity extends Activity {

    private static final String TAG = "OnlineBrowser";

    // radio-browser 的镜像节点，按顺序试
    /**
     * 本机能不能播 HLS（Android 5.0+ 的 MediaPlayer 才支持）。
     *
     * 之前这个界面**无条件过滤 HLS**（注释写着"4.4 播不了"），
     * 于是在安卓 9 手机上，在线库里能播的 HLS 台反而被一律滤掉了 ——
     * 那个注释描述的约束只对 4.4 成立。现在按运行时版本判断。
     */
    private static final boolean HLS_OK = android.os.Build.VERSION.SDK_INT >= 21;

    private static final String CAT_CENTRAL = "央广省级";
    private static final String CAT_LOCAL = "省市县";

    private static final String[] MIRRORS = {
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
    };

    private EditText searchBox;
    private TextView hint;
    private ListView list;
    private Button searchBtn;
    private OnlineAdapter adapter;
    private final List<Station> results = new ArrayList<Station>();
    private String country = "CN";

    /**
     * 是否只展示内置的「央广省级」。
     *
     * 内置那批 HLS 台（央广官方 / CCTV / 卫视伴音）固定在应用里，不该跟
     * 在线搜索的结果混在一起 —— 所以做成互斥的两种模式，进页面点按钮切换。
     */
    private boolean builtinMode = false;
    /** 当前正在看的内置分类；null = 未进入内置模式 */
    private String builtinCat = null;

    /**
     * 搜索序号。每发起一次搜索就 ++，回调里比对：对不上说明这次结果
     * 已经过期（用户又切了内置分类或搜了别的），直接丢弃。
     *
     * 没有这个保护会出现：点「内置·网络」后，**上一次 doSearch 的后台线程
     * 才返回**，无条件把 hint 和 results 覆盖成搜索内容 —— 表现就是
     * "列表是内置的台，标签却写着搜索的文案"。
     */
    private int searchSeq = 0;

    /** 5 个内置分类按钮，用于高亮当前选中的那个 */
    private final java.util.Map<String, Button> builtinBtns =
            new java.util.LinkedHashMap<String, Button>();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online);
        setTitle("在线电台库");

        searchBox = (EditText) findViewById(R.id.online_search);
        hint      = (TextView) findViewById(R.id.online_hint);
        list      = (ListView) findViewById(R.id.online_list);
        searchBtn = (Button) findViewById(R.id.online_btn);

        adapter = new OnlineAdapter();
        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                showStationDialog(results.get(pos));
            }
        });

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doSearch(searchBox.getText().toString().trim()); }
        });

        // 国家切换：中国 / 香港 / 台湾 / 全球华语
        findViewById(R.id.online_country).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickCountry(); }
        });

        bindCountryButtons();
        bindBuiltinCategories();


        // 遥控器：确认键自己接管。
        // ListView 自带的 DPAD_CENTER 处理在这里不触发 onItemClick（主界面踩过同样的坑），
        // 用 selectedPos 直接调同一个点击逻辑。
        list.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_CENTER
                        || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    int pos = list.getSelectedItemPosition();
                    if (pos >= 0 && pos < results.size()) {
                        showStationDialog(results.get(pos));
                        return true;
                    }
                    return false;
                }
                // 和主界面同一处理：列表内的 ↑ 用于滚动，焦点出不去。
                // 在第一行还按 ↑ 就交还给地区按钮行，让遥控器能上去换地区。
                if (code == KeyEvent.KEYCODE_DPAD_UP
                        && list.getSelectedItemPosition() <= 0) {
                    View t = findViewById(R.id.cn_cn);
                    if (t != null) { t.requestFocus(); return true; }
                }
                return false;
            }
        });

        // 进页面焦点落到列表（电视上没有鼠标，得给个确定的起点）
        list.postDelayed(new Runnable() {
            @Override public void run() {
                if (list.getCount() > 0) { list.requestFocus(); list.setSelection(0); }
            }
        }, 600);

        doSearch("");
    }

    /**
     * 选中一个在线电台后弹的确认框。
     * 抽成独立方法是因为遥控器路径（setOnKeyListener 里接管确认键）和触摸路径
     * （onItemClick）都要用它 —— 不抽的话两边逻辑会漂移。
     */
    private void showStationDialog(final Station s) {
        new AlertDialog.Builder(OnlineBrowserActivity.this)
                .setTitle(s.name)
                .setMessage(s.primaryUrl()
                        + "\n\n编码: " + s.genre
                        + "\n\n若播不出来，多半是该源已失效（国内很多台有防盗链）")
                .setPositiveButton("播放", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        startService(new Intent(OnlineBrowserActivity.this, RadioService.class)
                                .setAction(RadioService.ACTION_PLAY)
                                .putExtra(RadioService.EXTRA_URL, s.primaryUrl())
                                .putExtra(RadioService.EXTRA_NAME, s.name));
                        Toast.makeText(OnlineBrowserActivity.this,
                                "正在播放 " + s.name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("加入我的电台", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { saveToCustom(s); }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 电视布局的「地区」按钮：直接切换地区，不用进弹窗。
     * 竖屏布局没有这些 id，findViewById 返回 null，自动跳过。
     */
    /**
     * 内置分类按钮 —— 过滤应用自带的 1398 个台，不联网。
     *
     * 与「地区」按钮的区别：地区是去 radio-browser 搜外部台，
     * 这里筛的是本地库。两排按钮各管一路，互不干扰。
     */
    private void bindBuiltinCategories() {
        final int[] ids = {R.id.bi_central, R.id.bi_local, R.id.bi_net,
                           R.id.bi_hmt, R.id.bi_intl};
        final String[] cats = {CAT_CENTRAL, CAT_LOCAL, "网络", "港澳台", "国际"};
        for (int i = 0; i < ids.length; i++) {
            final String cat = cats[i];
            View v = findViewById(ids[i]);
            if (v == null) continue;          // 竖屏布局没有这一行
            Button b = (Button) v;
            builtinBtns.put(cat, b);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { selectBuiltin(cat); }
            });
        }
        updateBuiltinButtons();
        updateCurrentLabel();
    }

    private void bindCountryButtons() {
        final int[] ids = {R.id.cn_cn, R.id.cn_hk, R.id.cn_tw, R.id.cn_all};
        final String[] codes = {"CN", "HK", "TW", ""};
        final String[] names = {"中国大陆", "香港", "台湾", "全部"};
        for (int i = 0; i < ids.length; i++) {
            final int idx = i;
            View b = findViewById(ids[i]);
            if (b == null) continue;
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    country = codes[idx];
                    doSearch(searchBox.getText().toString().trim());
                    list.requestFocus();
                    list.setSelection(0);
                }
            });
        }
    }

    private void pickCountry() {
        final String[] names = {"中国大陆", "香港", "台湾", "全部"};
        final String[] codes = {"CN", "HK", "TW", ""};
        new AlertDialog.Builder(this)
                .setTitle("选择地区")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        country = codes[which];
                        doSearch(searchBox.getText().toString().trim());
                    }
                }).show();
    }

    /** 把在线电台存进"我的电台"，之后在主界面可以直接点播 */
    private void saveToCustom(Station s) {
        Prefs prefs = new Prefs(this);
        String old = prefs.getString("custom_stations", "");
        String rec = s.name + "\t" + s.primaryUrl();
        if (old.contains(rec)) {
            Toast.makeText(this, "已经在我的电台里了", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.setString("custom_stations", old + (old.length() > 0 ? "\n" : "") + rec);
        Toast.makeText(this, "已加入我的电台", Toast.LENGTH_SHORT).show();
    }

    /**
     * 展示内置的「央广省级」（HLS 台）。
     *
     * 这批台固定在应用里，不走 radio-browser 搜索 —— 所以单独一个方法，
     * 而不是往 query() 里塞分支。
     */
    /**
     * 展示内置台，可按分类过滤。
     *
     * @param cat 分类名（"央广省级"/"省市县"/"网络"/"港澳台"/"国际"）；
     *            传 null 表示不过滤（等价于「央广省级（内置）」按钮的旧行为：
     *            只看 HLS 那批 —— 那是"内置的电视伴音"）
     */
    private void showBuiltin(String cat) {
        final List<Station> out = new ArrayList<Station>();
        for (Station s : StationData.builtIn()) {
            if (matchesBuiltinCat(s, cat)) out.add(s);
        }
        // 之前漏了这一步 —— 内置分类按钮点了没反应就是这个原因：
        // 列表填了，但 builtinMode 没置位，后续刷新会把它当搜索结果覆盖掉。
        builtinMode = true;
        builtinCat = cat;
        searchSeq++;      // 作废所有在飞的搜索，别让它们回写覆盖内置列表
        results.clear();
        results.addAll(out);
        adapter.notifyDataSetChanged();
        String label = (cat == null) ? "内置" : "内置 · " + cat;
        hint.setText(label + " · 共 " + out.size() + " 个"
                + (HLS_OK ? "" : "（" + countHls(out) + " 个 HLS 本机放不了）"));
        list.requestFocus();
        list.setSelection(0);
    }

    /**
     * 切到某个内置分类。同时更新按钮文字 —— 按钮上显示**当前分类**，
     * 让用户一眼看出正在看哪一类（之前按钮文字固定不动，看不出当前在哪）。
     */
    private void selectBuiltin(String cat) {
        showBuiltin(cat);
        updateBuiltinButtons();
        updateCurrentLabel();
    }

    /**
     * 第三排那个按钮的文案 —— 它是"当前在看什么"的标签，不是地区选择器。
     *
     * 之前它只跟着「地区」走（点地区按钮/地区弹窗才更新），
     * 内置分类是另一条独立路径，两边各管各的 ——
     * 于是按下内置分类时，列表换了、这个标签却还是旧地区名。
     *
     * 现在三条路径（地区按钮 / 地区弹窗 / 内置分类按钮）都调这里，
     * 保证标签和列表永远一致。
     */
    private void updateCurrentLabel() {
        Button b = (Button) findViewById(R.id.online_country);
        if (b == null) return;
        b.setText(builtinMode && builtinCat != null ? builtinCat : countryName(country));
    }

    /** 地区代码 → 显示名 */
    private String countryName(String code) {
        if ("CN".equals(code)) return "中国大陆";
        if ("HK".equals(code)) return "香港";
        if ("TW".equals(code)) return "台湾";
        return "全部";
    }

    /** 当前选中的分类按钮标上「·当前」，其余保持分类名 */
    private void updateBuiltinButtons() {
        for (java.util.Map.Entry<String, Button> e : builtinBtns.entrySet()) {
            boolean on = e.getKey().equals(builtinCat);
            e.getValue().setText(on ? e.getKey() + " ·当前" : e.getKey());
        }
    }

    /**
     * 台是否属于某个内置分类。
     *
     * **不能直接比 s.region** —— 分类名和数据层的 region 值不是一回事：
     *   · 「央广省级」→ 数据层是 region="中央"（还要兼容按名字判的 CCTV/CETV）
     *   · 「港澳台」  → 数据层是三个值：香港/澳门/台湾
     * 早先直接写 cat.equals(s.region)，于是这两个按钮永远拉不到台 ——
     * 用户报的"内置按钮不起作用"就是这个。
     *
     * 匹配规则与主界面的 MainActivity.matchesCategory 保持一致，
     * 改一处记得改另一处。
     */
    private boolean matchesBuiltinCat(Station s, String cat) {
        if (cat == null) return s.isHls();       // 不指定分类 = 内置的 HLS 台
        if (CAT_CENTRAL.equals(cat)) {
            return "中央".equals(s.region)
                    || s.name.startsWith("CCTV") || s.name.startsWith("CETV");
        }
        if ("港澳台".equals(cat)) {
            return "香港".equals(s.region) || "澳门".equals(s.region) || "台湾".equals(s.region);
        }
        return cat.equals(s.region);
    }

    private int countHls(List<Station> list) {
        int n = 0;
        for (Station s : list) if (s.isHls()) n++;
        return n;
    }

    private void doSearch(final String q) {
        final int seq = ++searchSeq;      // 认领本次搜索
        // 发起搜索即离开内置模式 —— 状态和标签一起转，
        // 不能只改列表不改标签（那就是用户看到的"标签跟分类不匹配"）。
        builtinMode = false;
        builtinCat = null;
        updateBuiltinButtons();
        updateCurrentLabel();
        hint.setText("查询中…");
        new Thread(new Runnable() {
            @Override public void run() {
                List<Station> found = query(q);
                final List<Station> f = found;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        // 过期结果直接丢 —— 用户已经切到内置分类/又搜了别的
                        if (seq != searchSeq) return;   // 过期结果，丢弃
                        results.clear();
                        results.addAll(f);
                        adapter.notifyDataSetChanged();
                        hint.setText(f.isEmpty()
                                ? "没有结果（换个关键词，或该地区源不支持）"
                                : "共 " + f.size() + " 个" + (HLS_OK ? "" : "（4.4 已过滤 HLS 流）"));
                    }
                });
            }
        }, "online-search").start();
    }

    private List<Station> query(String q) {
        List<Station> out = new ArrayList<Station>();
        StringBuilder sb = new StringBuilder();
        sb.append("/json/stations/search?hidebroken=true&limit=500&order=votes&reverse=true");
        if (country.length() > 0) sb.append("&countrycode=").append(country);
        if (q.length() > 0) sb.append("&name=").append(android.net.Uri.encode(q));

        for (String mirror : MIRRORS) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(mirror + sb).openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "RadioFM/1.0");
                if (conn instanceof javax.net.ssl.HttpsURLConnection) {
                    Tls12.applyTo((javax.net.ssl.HttpsURLConnection) conn);
                }
                if (conn.getResponseCode() != 200) continue;

                InputStream in = conn.getInputStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) body.append(line);

                JSONArray arr = new JSONArray(body.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String name = o.optString("name", "").trim();
                    String url  = o.optString("url_resolved", "");
                    if (url.length() == 0) url = o.optString("url", "");
                    if (name.length() == 0 || url.length() == 0) continue;
                    if (!HLS_OK && o.optInt("hls", 0) == 1) continue;   // 仅 4.4 需要滤掉 HLS
                    if (name.startsWith("http")) continue;           // 有些条目名字就是地址
                    int br = o.optInt("bitrate", 0);
                    out.add(new Station(name, o.optString("codec", "?"), br > 0 ? br + "kbps" : "未知码率", url));
                }
                break;   // 成功就不用试下一个镜像
            } catch (Exception e) {
                Log.w(TAG, "镜像失败 " + mirror, e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return out;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private class OnlineAdapter extends BaseAdapter {
        @Override public int getCount() { return results.size(); }
        @Override public Object getItem(int p) { return results.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            View v = cv;
            if (v == null) v = LayoutInflater.from(OnlineBrowserActivity.this)
                    .inflate(R.layout.row_station, parent, false);
            Station s = results.get(pos);
            ((TextView) v.findViewById(R.id.row_name)).setText(s.name);
            ((TextView) v.findViewById(R.id.row_sub)).setText(s.subtitle());
            v.findViewById(R.id.row_star).setVisibility(View.GONE);
            v.findViewById(R.id.row_now).setVisibility(View.GONE);
            return v;
        }
    }
}
