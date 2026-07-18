package com.seannmichael.mockdrive;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class AppUpdateActivity extends Activity {
    private static final int EXPORT_BACKUP = 901;
    private TextView installed, latest, status;
    private Button install;
    private File downloadedApk;
    private File pendingExport;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "App updates", true);
        LinearLayout hero = UiKit.hero(this, root);
        hero.addView(UiKit.whiteText(this, "Local app updates", 27, true));
        hero.addView(UiKit.whiteText(this, "Check GitHub manually, back up local data, and install over the existing app without deleting campaigns or settings.", 14, false));

        LinearLayout versionCard = UiKit.card(this, root);
        versionCard.addView(UiKit.text(this, "Version", 20, true));
        installed = UiKit.text(this, "Installed: " + currentVersionLabel(), 15, false);
        latest = UiKit.text(this, "Latest: Not checked", 15, false);
        status = UiKit.text(this, "Your data remains stored locally on this phone.", 14, false);
        versionCard.addView(installed); versionCard.addView(latest); versionCard.addView(status);
        Button check = UiKit.button(this, "Check for update"); versionCard.addView(check);
        install = UiKit.secondaryButton(this, "Download and install update"); install.setEnabled(false); versionCard.addView(install);
        check.setOnClickListener(v -> checkForUpdate());
        install.setOnClickListener(v -> downloadAndInstall());

        LinearLayout backupCard = UiKit.card(this, root);
        backupCard.addView(UiKit.text(this, "Local data backup", 20, true));
        backupCard.addView(UiKit.text(this, "Backs up SharedPreferences, campaigns, schedules, history, settings, API keys, and files stored by Mock Drive.", 13, false));
        Button backup = UiKit.secondaryButton(this, "Create local backup now"); backupCard.addView(backup);
        Button export = UiKit.secondaryButton(this, "Export latest backup"); backupCard.addView(export);
        backup.setOnClickListener(v -> createBackup());
        export.setOnClickListener(v -> exportBackup());

        UiKit.setStickyScreen(this, root, "Settings");
    }

    private void checkForUpdate() {
        status.setText("Checking GitHub releases…");
        install.setEnabled(false);
        new Thread(() -> {
            try {
                JSONObject release = latestRelease();
                JSONObject asset = findApkAsset(release);
                long remoteCode = inspectRemoteVersion(asset.getString("browser_download_url"), false);
                String name = release.optString("name", release.optString("tag_name", "Latest build"));
                runOnUiThread(() -> {
                    latest.setText("Latest: " + name + " (version code " + remoteCode + ")");
                    if (remoteCode > currentVersionCode()) {
                        status.setText("Update available. A backup will be created before installation.");
                        install.setEnabled(true);
                        install.setTag(asset.optString("browser_download_url"));
                    } else {
                        status.setText("This phone already has the newest version available.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Update check failed: " + e.getMessage()));
            }
        }, "update-check").start();
    }

    private void downloadAndInstall() {
        Object tag = install.getTag();
        if (tag == null) { status.setText("Check for an update first."); return; }
        install.setEnabled(false);
        status.setText("Creating local backup…");
        new Thread(() -> {
            try {
                File backup = LocalBackupManager.createBackup(this);
                runOnUiThread(() -> status.setText("Backup created: " + backup.getName() + "\nDownloading update…"));
                downloadedApk = downloadApk(String.valueOf(tag));
                verifyApk(downloadedApk);
                runOnUiThread(() -> {
                    status.setText("APK verified. Android will ask you to approve the update. Existing data will remain in place.");
                    try { installApk(downloadedApk); }
                    catch (Exception e) { status.setText("Could not start installer: " + e.getMessage()); install.setEnabled(true); }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { status.setText("Update stopped: " + e.getMessage()); install.setEnabled(true); });
            }
        }, "update-download").start();
    }

    private JSONObject latestRelease() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/Seann-Michael/Mock-GPS-Locatation/releases?per_page=10").openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setRequestProperty("User-Agent", "MockDrive-Updater");
        int code = c.getResponseCode(); if (code != 200) throw new Exception("GitHub returned " + code);
        JSONArray releases = new JSONArray(read(c.getInputStream())); c.disconnect();
        for (int i = 0; i < releases.length(); i++) {
            JSONObject r = releases.getJSONObject(i);
            if (!r.optBoolean("draft", false) && findApkAssetOrNull(r) != null) return r;
        }
        throw new Exception("No installable APK release was found");
    }

    private JSONObject findApkAsset(JSONObject release) throws Exception {
        JSONObject result = findApkAssetOrNull(release);
        if (result == null) throw new Exception("Release does not contain an APK");
        return result;
    }

    private JSONObject findApkAssetOrNull(JSONObject release) {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && a.optString("name").toLowerCase().endsWith(".apk")) return a;
        }
        return null;
    }

    private long inspectRemoteVersion(String url, boolean keep) throws Exception {
        File apk = downloadApk(url);
        PackageInfo info = archiveInfo(apk);
        long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        if (!keep) apk.delete();
        return code;
    }

    private File downloadApk(String url) throws Exception {
        File out = new File(getCacheDir(), "MockDrive-update.apk");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true); c.setConnectTimeout(20000); c.setReadTimeout(120000); c.setRequestProperty("User-Agent", "MockDrive-Updater");
        int code = c.getResponseCode(); if (code < 200 || code >= 300) throw new Exception("APK download returned " + code);
        try (InputStream in = c.getInputStream(); FileOutputStream output = new FileOutputStream(out)) {
            byte[] buffer = new byte[16384]; int n; while ((n = in.read(buffer)) > 0) output.write(buffer, 0, n);
        }
        c.disconnect();
        if (out.length() < 100000) throw new Exception("Downloaded APK is incomplete");
        return out;
    }

    private void verifyApk(File apk) throws Exception {
        PackageInfo archive = archiveInfo(apk);
        if (!getPackageName().equals(archive.packageName)) throw new Exception("APK package does not match Mock Drive");
        long remote = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
        if (remote <= currentVersionCode()) throw new Exception("Downloaded APK is not newer than the installed version");
        PackageInfo current = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (!Arrays.equals(certDigest(current), certDigest(archive))) throw new Exception("APK signing certificate does not match the installed app");
    }

    private PackageInfo archiveInfo(File apk) throws Exception {
        PackageInfo info = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (info == null) throw new Exception("Android could not read the downloaded APK");
        return info;
    }

    private byte[] certDigest(PackageInfo info) throws Exception {
        android.content.pm.Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) signatures = info.signingInfo.getApkContentsSigners();
        else signatures = info.signatures;
        if (signatures == null || signatures.length == 0) throw new Exception("Signing certificate missing");
        return MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray());
    }

    private void installApk(File apk) throws Exception {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(permission);
            status.setText("Allow Mock Drive to install updates, then tap Download and install again.");
            install.setEnabled(true);
            return;
        }
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(getPackageName());
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id);
             InputStream input = new FileInputStream(apk);
             OutputStream output = session.openWrite("MockDrive.apk", 0, apk.length())) {
            byte[] buffer = new byte[16384]; int n; while ((n = input.read(buffer)) > 0) output.write(buffer, 0, n);
            session.fsync(output);
            Intent result = new Intent(this, UpdateInstallReceiver.class).setAction("com.seannmichael.mockdrive.UPDATE_RESULT");
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            IntentSender sender = PendingIntent.getBroadcast(this, 4412, result, flags).getIntentSender();
            session.commit(sender);
        }
    }

    private void createBackup() {
        status.setText("Creating backup…");
        new Thread(() -> {
            try { File file = LocalBackupManager.createBackup(this); runOnUiThread(() -> status.setText("Backup created locally: " + file.getName())); }
            catch (Exception e) { runOnUiThread(() -> status.setText("Backup failed: " + e.getMessage())); }
        }, "manual-backup").start();
    }

    private void exportBackup() {
        pendingExport = LocalBackupManager.latestBackup(this);
        if (pendingExport == null) { Toast.makeText(this, "Create a backup first", Toast.LENGTH_LONG).show(); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").putExtra(Intent.EXTRA_TITLE, pendingExport.getName());
        startActivityForResult(i, EXPORT_BACKUP);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_BACKUP || resultCode != RESULT_OK || data == null || pendingExport == null) return;
        try (InputStream in = new FileInputStream(pendingExport); OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new Exception("Could not open destination");
            byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            status.setText("Backup exported successfully.");
        } catch (Exception e) { status.setText("Backup export failed: " + e.getMessage()); }
    }

    private long currentVersionCode() {
        try { PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0); return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
        catch (Exception e) { return 0; }
    }

    private String currentVersionLabel() {
        try { PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0); return info.versionName + " (" + currentVersionCode() + ")"; }
        catch (Exception e) { return "Unknown"; }
    }

    private static String read(InputStream input) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); String line; while ((line = reader.readLine()) != null) out.append(line); reader.close(); return out.toString();
    }
}
