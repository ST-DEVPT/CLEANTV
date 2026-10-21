package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The plugin store: lists the catalog this build was pointed at (plugin_catalog_url, set by the release
 * workflow to the repo's "plugins" release), downloads a plugin zip, checks its SHA-256 against the catalog
 * and installs it as the theme (PluginInstaller). The only part of the launcher that uses the network, and
 * only while this screen is open. RESULT_OK tells Home to reload.
 */
public final class PluginStoreActivity extends Activity {
    private static final String PREFS = "plugins";
    private static final int MAX_CATALOG = 256 * 1024;
    private static final int MAX_PLUGIN = 20 * 1024 * 1024;

    static final class Plugin {
        final String id, name, version, description, author, file, sha256;
        final long size;

        Plugin(JSONObject o) {
            id = o.optString("id");
            name = o.optString("name", id);
            version = o.optString("version");
            description = o.optString("description");
            author = o.optString("author");
            file = o.optString("file");
            sha256 = o.optString("sha256");
            size = o.optLong("size", -1);
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private TextView status;
    private LinearLayout list;
    private URL catalogUrl;
    private List<Plugin> plugins = new ArrayList<>();
    private boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_plugin_store);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        status = findViewById(R.id.pluginStatus);
        list = findViewById(R.id.pluginList);
        String url = getString(R.string.plugin_catalog_url).trim();
        if (url.isEmpty()) {
            status.setText(R.string.plugins_no_catalog);
            return;
        }
        try {
            catalogUrl = new URL(url);
        } catch (IOException e) {
            status.setText(R.string.plugins_no_catalog);
            return;
        }
        load();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        status.setText(R.string.plugins_loading);
        worker.execute(() -> {
            try {
                JSONArray array = new JSONObject(new String(fetch(catalogUrl, MAX_CATALOG), StandardCharsets.UTF_8))
                        .getJSONArray("plugins");
                List<Plugin> loaded = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    Plugin p = new Plugin(array.getJSONObject(i));
                    if (!p.id.isEmpty() && !p.file.isEmpty() && p.sha256.length() == 64) loaded.add(p);
                }
                runOnUiThread(() -> {
                    plugins = loaded;
                    status.setText(loaded.isEmpty() ? getString(R.string.plugins_empty) : "");
                    render();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText(getString(R.string.plugins_error, e.getMessage())));
            }
        });
    }

    /** The installed plugin's id, or null if none, or if a theme was pushed over it with tools/theme.sh since. */
    private String installedId() {
        File dir = Config.dir(this);
        String revision = prefs.getString("revision", null);
        if (dir == null || revision == null) return null;
        try {
            String onDisk = new String(fetchFile(new File(dir, "theme_revision")), StandardCharsets.US_ASCII);
            return revision.equals(onDisk) ? prefs.getString("id", null) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void render() {
        list.removeAllViews();
        String installed = installedId();
        String installedVersion = prefs.getString("version", "");
        LayoutInflater inflater = LayoutInflater.from(this);
        View first = null;
        for (Plugin p : plugins) {
            View row = inflater.inflate(R.layout.item_plugin, list, false);
            String state = !p.id.equals(installed) ? ""
                    : p.version.equals(installedVersion) ? getString(R.string.plugins_installed)
                    : getString(R.string.plugins_update_available);
            ((TextView) row.findViewById(R.id.pluginName)).setText(
                    state.isEmpty() ? p.name + "  " + p.version : p.name + "  " + p.version + "  ·  " + state);
            TextView description = row.findViewById(R.id.pluginDescription);
            description.setText(p.author.isEmpty() ? p.description
                    : getString(R.string.plugins_by, p.description, p.author));
            row.setSelected(p.id.equals(installed));
            row.setOnClickListener(v -> confirm(p, p.id.equals(installed)));
            list.addView(row);
            if (first == null) first = row;
        }
        if (installed != null) {
            View row = inflater.inflate(R.layout.item_plugin, list, false);
            ((TextView) row.findViewById(R.id.pluginName)).setText(R.string.plugins_remove);
            ((TextView) row.findViewById(R.id.pluginDescription)).setText(R.string.plugins_remove_hint);
            row.setOnClickListener(v -> remove());
            list.addView(row);
        }
        if (first != null) first.requestFocus();
    }

    private void confirm(Plugin p, boolean installed) {
        if (busy) return;
        String size = p.size > 0 ? Formatter.formatShortFileSize(this, p.size) : "?";
        new AlertDialog.Builder(this)
                .setTitle(p.name + " " + p.version)
                .setMessage(getString(R.string.plugins_confirm, p.description, size))
                .setPositiveButton(installed ? R.string.plugins_reinstall : R.string.plugins_install,
                        (d, w) -> install(p))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void install(Plugin p) {
        File dir = Config.dir(this);
        if (dir == null) {
            status.setText(R.string.plugins_no_storage);
            return;
        }
        busy = true;
        status.setText(getString(R.string.plugins_downloading, p.name));
        worker.execute(() -> {
            File zip = new File(getCacheDir(), "plugin.zip");
            try {
                URL url = new URL(catalogUrl, p.file);
                try (OutputStream out = new FileOutputStream(zip)) {
                    download(url, out, p.size > 0 ? Math.min(p.size, MAX_PLUGIN) : MAX_PLUGIN);
                }
                if (!p.sha256.equalsIgnoreCase(PluginInstaller.sha256(zip))) {
                    throw new IOException(getString(R.string.plugins_bad_checksum));
                }
                String revision = UUID.randomUUID().toString().replace("-", "");
                PluginInstaller.install(zip, dir, revision);
                prefs.edit().putString("id", p.id).putString("version", p.version)
                        .putString("revision", revision).commit();
                runOnUiThread(() -> done(getString(R.string.plugins_applied, p.name)));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    status.setText(getString(R.string.plugins_error, e.getMessage()));
                });
            } finally {
                zip.delete();
            }
        });
    }

    private void remove() {
        File dir = Config.dir(this);
        if (busy || dir == null) return;
        try {
            String revision = UUID.randomUUID().toString().replace("-", "");
            PluginInstaller.remove(dir, revision);
            prefs.edit().clear().commit();
            done(getString(R.string.plugins_removed));
        } catch (IOException e) {
            status.setText(getString(R.string.plugins_error, e.getMessage()));
        }
    }

    private void done(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private static byte[] fetch(URL url, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        download(url, out, limit);
        return out.toByteArray();
    }

    /** GET with redirects (GitHub release downloads redirect to their storage host), at most {@code limit} bytes. */
    private static void download(URL url, OutputStream out, long limit) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(true);
        try {
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                PluginInstaller.copy(in, out, limit);
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] fetchFile(File f) throws IOException {
        if (!f.isFile() || f.length() > 64) throw new IOException("no revision");
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            return n == b.length ? b : new byte[0];
        }
    }
}
