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
    private void bindCountryButtons() {
        final int[] ids = {R.id.cn_cn, R.id.cn_hk, R.id.cn_tw,
                           R.id.cn_us, R.id.cn_jp, R.id.cn_all};
        final String[] codes = {"CN", "HK", "TW", "US", "JP", ""};
        final String[] names = {"中国大陆", "香港", "台湾", "美国", "日本", "全部"};
        for (int i = 0; i < ids.length; i++) {
            final int idx = i;
            View b = findViewById(ids[i]);
            if (b == null) continue;
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    country = codes[idx];
                    ((Button) findViewById(R.id.online_country)).setText(names[idx]);
                    doSearch(searchBox.getText().toString().trim());
                    list.requestFocus();
                    list.setSelection(0);
                }
            });
        }
    }

    private void pickCountry() {
        final String[] names = {"中国大陆", "香港", "台湾", "美国", "日本", "全部"};
        final String[] codes = {"CN", "HK", "TW", "US", "JP", ""};
        new AlertDialog.Builder(this)
                .setTitle("选择地区")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        country = codes[which];
                        ((Button) findViewById(R.id.online_country)).setText(names[which]);
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

    private void doSearch(final String q) {
        hint.setText("查询中…");
        new Thread(new Runnable() {
            @Override public void run() {
                List<Station> found = query(q);
                final List<Station> f = found;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        results.clear();
                        results.addAll(f);
                        adapter.notifyDataSetChanged();
                        hint.setText(f.isEmpty()
                                ? "没有结果（换个关键词，或该地区源不支持）"
                                : "共 " + f.size() + " 个（已过滤 HLS 流）");
                    }
                });
            }
        }, "online-search").start();
    }

    private List<Station> query(String q) {
        List<Station> out = new ArrayList<Station>();
        StringBuilder sb = new StringBuilder();
        sb.append("/json/stations/search?hidebroken=true&limit=100&order=votes&reverse=true");
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
                    if (o.optInt("hls", 0) == 1) continue;          // 4.4 播不了 HLS
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
