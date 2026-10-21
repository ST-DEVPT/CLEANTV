package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PluginInstallerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File zip(String... names) throws IOException {
        File f = tmp.newFile();
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f))) {
            for (String name : names) {
                z.putNextEntry(new ZipEntry(name));
                z.write(("data of " + name).getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return f;
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void installReplacesTheTheme() throws IOException {
        File dir = tmp.newFolder();
        new File(dir, "wallpapers").mkdirs();
        Files.write(new File(dir, "wallpapers/old.jpg").toPath(), new byte[]{1});
        Files.write(new File(dir, "wallpaper.jpg").toPath(), new byte[]{1});

        PluginInstaller.install(zip("config.json", "wallpapers/a.jpg"), dir, "rev1");

        assertEquals("data of config.json", read(new File(dir, "config.json")));
        assertEquals("data of wallpapers/a.jpg", read(new File(dir, "wallpapers/a.jpg")));
        assertFalse(new File(dir, "wallpapers/old.jpg").exists());
        assertFalse(new File(dir, "wallpaper.jpg").exists());
        assertEquals("rev1", read(new File(dir, "theme_revision")));
    }

    @Test public void badZipLeavesTheCurrentThemeAlone() throws IOException {
        File dir = tmp.newFolder();
        Files.write(new File(dir, "config.json").toPath(), "{\"columns\":6}".getBytes(StandardCharsets.UTF_8));
        String[][] bad = {
                {"config.json", "../escape.json"},
                {"config.json", "cards.json"},
                {"config.json", "theme_revision"},
                {"config.json", "wallpapers/sub/a.jpg"},
                {"wallpaper.jpg"},
        };
        for (String[] names : bad) {
            try {
                PluginInstaller.install(zip(names), dir, "rev");
                fail("accepted " + String.join(", ", names));
            } catch (PluginInstaller.Invalid expected) {
                assertEquals("{\"columns\":6}", read(new File(dir, "config.json")));
            }
        }
    }

    @Test public void removeGoesBackToBuiltIn() throws IOException {
        File dir = tmp.newFolder();
        PluginInstaller.install(zip("config.json", "wallpaper.jpg"), dir, "rev1");
        PluginInstaller.remove(dir, "rev2");
        assertFalse(new File(dir, "config.json").exists());
        assertFalse(new File(dir, "wallpaper.jpg").exists());
        assertEquals("rev2", read(new File(dir, "theme_revision")));
    }

    @Test public void sha256MatchesKnownValue() throws IOException {
        File f = tmp.newFile();
        Files.write(f.toPath(), "abc".getBytes(StandardCharsets.US_ASCII));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", PluginInstaller.sha256(f));
        assertTrue(PluginInstaller.sha256(f).length() == 64);
    }
}
