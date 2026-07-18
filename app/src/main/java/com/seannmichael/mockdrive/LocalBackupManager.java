package com.seannmichael.mockdrive;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class LocalBackupManager {
    private LocalBackupManager() {}

    public static File createBackup(Context context) throws Exception {
        File dir = new File(context.getFilesDir(), "backups");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create backup folder");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File out = new File(dir, "MockDrive-Backup-" + stamp + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            addTree(zip, new File(context.getApplicationInfo().dataDir, "shared_prefs"), "shared_prefs");
            addTree(zip, context.getFilesDir(), "files");
        }
        return out;
    }

    public static File latestBackup(Context context) {
        File dir = new File(context.getFilesDir(), "backups");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".zip"));
        if (files == null || files.length == 0) return null;
        File latest = files[0];
        for (File file : files) if (file.lastModified() > latest.lastModified()) latest = file;
        return latest;
    }

    private static void addTree(ZipOutputStream zip, File root, String prefix) throws Exception {
        if (root == null || !root.exists()) return;
        File[] files = root.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[8192];
        for (File file : files) {
            if ("backups".equals(file.getName()) && file.getParentFile() != null && file.getParentFile().equals(root)) continue;
            String name = prefix + "/" + file.getName();
            if (file.isDirectory()) {
                addTree(zip, file, name);
            } else {
                zip.putNextEntry(new ZipEntry(name));
                try (FileInputStream input = new FileInputStream(file)) {
                    int read;
                    while ((read = input.read(buffer)) > 0) zip.write(buffer, 0, read);
                }
                zip.closeEntry();
            }
        }
    }
}
