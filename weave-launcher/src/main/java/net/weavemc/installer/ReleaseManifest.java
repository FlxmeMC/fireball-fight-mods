package net.weavemc.installer;

import java.util.Collections;
import java.util.List;

final class ReleaseManifest {
    int schemaVersion;
    String releaseVersion;
    String installerVersion;
    InstallerInfo installer;
    List<ModInfo> mods = Collections.emptyList();
    List<FileInfo> files = Collections.emptyList();

    static final class ModInfo {
        String id;
        String name;
        String version;
        String description;
        List<String> dependencies = Collections.emptyList();
    }

    static final class FileInfo {
        String id;
        String component;
        String url;
        String destination;
        String sha256;
        long size;
    }

    static final class InstallerInfo {
        String url;
        String sha256;
    }
}
