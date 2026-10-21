package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Writes a plugin zip from the plugin store into the theme directory ({@link Config#dir}), the same files
 * tools/theme.sh pushes: config.json, wallpaper.jpg and wallpapers/<image>. Every entry is checked with
 * {@link ThemeFiles#path} before anything is touched, so a bad zip leaves the current theme as it was.
 * No Android classes, so it is tested on the JVM (PluginInstallerTest).
 */
final class PluginInstaller {
    static final int MAX_ENTRIES = 64;
    static final long MAX_UNPACKED = 64L * 1024 * 1024;

    private PluginInstaller() {}

    static final class Invalid extends IOException {
        Invalid(String message) { super(message); }
    }

    /** Replaces the theme in {@code dir} with the zip's files and writes {@code revision} to theme_revision. */
    static void install(File zip, File dir, String revision) throws IOException {
        try (ZipFile z = new ZipFile(zip)) {
            List<ZipEntry> entries = check(z);
            clear(dir);
            for (ZipEntry e : entries) {
                File out = new File(dir, e.getName());
                File parent = out.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("mkdir " + parent);
                try (InputStream in = z.getInputStream(e); OutputStream o = new FileOutputStream(out)) {
                    copy(in, o, MAX_UNPACKED);
                }
            }
        }
        writeRevision(dir, revision);
    }

    /** Back to the built-in config.xml look: the same as tools/theme.sh --reset. */
    static void remove(File dir, String revision) throws IOException {
        clear(dir);
        writeRevision(dir, revision);
    }

    /** The zip's file entries, or Invalid if any is not a theme file or the zip is too big. */
    static List<ZipEntry> check(ZipFile z) throws Invalid {
        List<ZipEntry> files = new ArrayList<>();
        boolean config = false;
        long total = 0;
        Enumeration<? extends ZipEntry> all = z.entries();
        while (all.hasMoreElements()) {
            ZipEntry e = all.nextElement();
            if (e.isDirectory()) continue;
            String name = e.getName();
            // theme_revision is the launcher's own marker, never part of a plugin.
            String path = name.endsWith("theme_revision") ? null : ThemeFiles.path(Arrays.asList(name.split("/", -1)));
            if (path == null || !path.equals(name)) throw new Invalid("not a theme file: " + name);
            if (files.size() >= MAX_ENTRIES) throw new Invalid("more than " + MAX_ENTRIES + " files");
            long size = e.getSize();
            if (size < 0 || (total += size) > MAX_UNPACKED) throw new Invalid("over " + (MAX_UNPACKED >> 20) + " MB unpacked");
            if (Config.FILE_NAME.equals(name)) config = true;
            files.add(e);
        }
        if (!config) throw new Invalid("no " + Config.FILE_NAME);
        return files;
    }

    private static void clear(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdir " + dir);
        new File(dir, Config.FILE_NAME).delete();
        new File(dir, Config.WALLPAPER_NAME).delete();
        File[] images = new File(dir, ThemeFiles.WALLPAPERS).listFiles((d, name) -> ThemeFiles.collectionImage(name));
        if (images != null) for (File f : images) f.delete();
    }

    private static void writeRevision(File dir, String revision) throws IOException {
        try (OutputStream o = new FileOutputStream(new File(dir, "theme_revision"))) {
            o.write(revision.getBytes(StandardCharsets.US_ASCII));
        }
    }

    /** Copies at most {@code limit} bytes; a stream that runs past the size its entry claimed is refused. */
    static long copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buf = new byte[16 * 1024];
        long n = 0;
        for (int r; (r = in.read(buf)) != -1; ) {
            if ((n += r) > limit) throw new Invalid("over " + (limit >> 20) + " MB");
            out.write(buf, 0, r);
        }
        return n;
    }

    static String sha256(File file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[16 * 1024];
            for (int r; (r = in.read(buf)) != -1; ) md.update(buf, 0, r);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
