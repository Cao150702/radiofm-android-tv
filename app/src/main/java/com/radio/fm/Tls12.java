package com.radio.fm;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Android 4.4 的坑：系统支持 TLS 1.2，但默认只启用 TLS 1.0。
 * 实测我们的流媒体源（infomaniak / xdevel / dribbcast 等）全都要求 TLS 1.2，
 * 不打开这个开关，HTTPS 电台在 4.4 机器上会直接连不上。
 *
 * 解法是用 SSLSocketFactory 包装层，在握手前强行把 TLS 1.2 加进启用列表。
 * 只对 API < 21 生效，21+ 系统默认就启用了 TLS 1.2，不需要也不该干预。
 */
public final class Tls12 {

    private static SSLSocketFactory tls12Factory;
    private static boolean installed;

    private Tls12() {}

    public static void install() {
        if (installed) return;
        installed = true;
        if (android.os.Build.VERSION.SDK_INT >= 21) return;   // 系统已默认启用 TLS 1.2

        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new PermissiveTrustManager()}, new java.security.SecureRandom());
            final SSLSocketFactory base = ctx.getSocketFactory();

            tls12Factory = new SSLSocketFactory() {
                @Override public String[] getDefaultCipherSuites() { return base.getDefaultCipherSuites(); }
                @Override public String[] getSupportedCipherSuites() { return base.getSupportedCipherSuites(); }

                @Override
                public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                    return enable(base.createSocket(s, host, port, autoClose));
                }

                @Override public Socket createSocket(String host, int port) throws IOException {
                    return enable(base.createSocket(host, port));
                }

                @Override public Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws IOException {
                    return enable(base.createSocket(host, port, localHost, localPort));
                }

                @Override public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
                    return enable(base.createSocket(host, port));
                }

                @Override public Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws IOException {
                    return enable(base.createSocket(address, port, localAddress, localPort));
                }
            };
            HttpsURLConnection.setDefaultSSLSocketFactory(tls12Factory);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            tls12Factory = null;   // 退回系统默认，HTTPS 流可能失败，但 HTTP 流不受影响
        }
    }

    private static Socket enable(Socket s) {
        if (s instanceof SSLSocket) {
            SSLSocket ssl = (SSLSocket) s;
            // 关键：TLSv1.2 在 4.4 上存在于 supported 但不默认启用
            String[] supported = ssl.getSupportedProtocols();
            java.util.List<String> want = new java.util.ArrayList<String>();
            for (String p : supported) {
                if ("TLSv1.2".equals(p) || "TLSv1.1".equals(p) || "TLSv1".equals(p)) want.add(p);
            }
            if (!want.isEmpty()) {
                ssl.setEnabledProtocols(want.toArray(new String[want.size()]));
            }
        }
        return s;
    }

    /**
     * 只信任系统 CA —— 不做全放行（那会毁掉 HTTPS 的意义）。
     * 老机器上根证书可能过期，但那是系统问题，不该在应用层假装安全。
     */
    private static class PermissiveTrustManager implements X509TrustManager {
        private final X509TrustManager system;
        PermissiveTrustManager() {
            X509TrustManager found = null;
            try {
                javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory
                        .getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((java.security.KeyStore) null);
                for (TrustManager tm : tmf.getTrustManagers()) {
                    if (tm instanceof X509TrustManager) { found = (X509TrustManager) tm; break; }
                }
            } catch (Exception ignored) {}
            system = found;
        }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            if (system != null) system.checkClientTrusted(chain, authType);
        }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
            if (system != null) system.checkServerTrusted(chain, authType);
        }
        @Override public X509Certificate[] getAcceptedIssuers() {
            return system != null ? system.getAcceptedIssuers() : new X509Certificate[0];
        }
    }

    /** 供 StreamRecorder 里的 HttpsURLConnection 使用 */
    public static SSLSocketFactory factory() {
        return tls12Factory;
    }

    /** 便捷方法：为一个 URL 打开连接并套用兼容层 */
    public static void applyTo(HttpsURLConnection c) {
        if (tls12Factory != null) c.setSSLSocketFactory(tls12Factory);
    }
}
