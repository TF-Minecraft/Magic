package net.tfminecraft.magic.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class YamlFolder {

    private YamlFolder() {}

    public static List<File> listYamlFiles(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = folder.listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<File> yamlFiles = new ArrayList<>();
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isFile() && (name.endsWith(".yml") || name.endsWith(".yaml"))) {
                yamlFiles.add(file);
            }
        }
        return yamlFiles;
    }
}
