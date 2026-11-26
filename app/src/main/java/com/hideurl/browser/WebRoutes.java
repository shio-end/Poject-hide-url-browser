package com.hideurl.browser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.net.Uri;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class WebRoutes {
    // This port is reserved but never listens. A denied request cannot reach a local proxy app.
    private static Socket reserved;
    private static Context app;
    private static boolean ready, configuring;
    private static int generation;
    private static final List<Consumer<Boolean>> waiting = new ArrayList<>();

    private static synchronized String denyEndpoint() throws IOException {
        if (reserved == null) {
            Socket socket = new Socket();
            try {
                socket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), 0));
                reserved = socket;
            } catch (IOException failure) {
                socket.close();
                throw failure;
            }
        }
        return "127.0.0.1:" + reserved.getLocalPort();
    }

    static ProxyConfig closed() throws IOException {
        return new ProxyConfig.Builder().addProxyRule(denyEndpoint()).removeImplicitRules().build();
    }

    static ProxyConfig isolated(ProxyInfo system, String origin) throws IOException {
        return new ProxyConfig.Builder(secureConfig(system, denyEndpoint()))
                .addBypassRule(origin).build();
    }

    // WebView proxy overrides are process-wide. The primary process never installs an HTTP exception.
    static void secure(Context context, Consumer<Boolean> onReady) {
        if (ready) { onReady.accept(true); return; }
        waiting.add(onReady);
        if (app == null) {
            app = context.getApplicationContext();
            BroadcastReceiver listener = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { refresh(); }
            };
            IntentFilter changes = new IntentFilter(android.net.Proxy.PROXY_CHANGE_ACTION);
            if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(listener, changes, Context.RECEIVER_NOT_EXPORTED);
            else app.registerReceiver(listener, changes);
        }
        if (configuring) return;
        refresh();
    }

    private static void refresh() {
        int revision = ++generation;
        ready = false;
        configuring = true;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            complete(false);
            return;
        }
        try {
            ProxyInfo system = app.getSystemService(ConnectivityManager.class).getDefaultProxy();
            ProxyConfig routes = secureConfig(system, denyEndpoint());
            ProxyController.getInstance().setProxyOverride(routes, app.getMainExecutor(), () -> {
                if (revision != generation) return;
                complete(true);
            });
        } catch (IOException | RuntimeException failure) {
            complete(false);
        }
    }

    static ProxyConfig secureConfig(ProxyInfo system, String deny) {
        ProxyConfig.Builder config = new ProxyConfig.Builder().removeImplicitRules();
        if (system == null) {
            // Keep secure WebSockets as well as ordinary TLS traffic on the device network.
            return config.addProxyRule(deny).addBypassRule("https://*").addBypassRule("wss://*").build();
        }
        // Android exposes its PAC resolver as a local HTTP proxy once the PAC is ready.
        String host = system.getHost();
        boolean pac = !Uri.EMPTY.equals(system.getPacFileUrl());
        if (pac && (host == null || host.isEmpty())) host = "localhost";
        int port = system.getPort();
        String upstream = deny;
        if (host != null && !host.isEmpty() && port > 0 && port <= 65535) {
            if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
            upstream = host + ":" + port;
        }
        config.addProxyRule(deny, ProxyConfig.MATCH_HTTP).addProxyRule(upstream, ProxyConfig.MATCH_HTTPS);
        for (String excluded : system.getExclusionList()) {
            if (excluded.matches("[a-zA-Z0-9.*_-]+")) {
                config.addBypassRule("https://" + excluded).addBypassRule("wss://" + excluded);
            }
        }
        return config.build();
    }

    private static void complete(boolean success) {
        ready = success;
        configuring = false;
        List<Consumer<Boolean>> pending = new ArrayList<>(waiting);
        waiting.clear();
        for (Consumer<Boolean> action : pending) action.accept(success);
    }
}
