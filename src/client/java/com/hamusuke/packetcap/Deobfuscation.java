package com.hamusuke.packetcap;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

public class Deobfuscation {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path path;
    private final Predicate<String> shouldDeobfuscate;
    private final Deobfuscater deobfuscater;
    private final Map<String, String> deobMap;
    private boolean deobEnabled = true;

    public Deobfuscation(Path path, Predicate<String> shouldDeobfuscate, Deobfuscater deobfuscater) {
        this.path = path;
        this.shouldDeobfuscate = shouldDeobfuscate;
        this.deobfuscater = deobfuscater;

        this.deobMap = this.deobfuscate();
    }

    public String deobfuscate(String name) {
        if (!this.deobEnabled) {
            return name;
        }

        return this.deobMap.getOrDefault(name, name);
    }

    protected Map<String, String> deobfuscate() {
        if (!this.path.toFile().exists() || this.path.toFile().isDirectory()) {
            this.deobEnabled = false;
            return Map.of();
        }

        Map<String, String> map = Maps.newHashMap();
        try {
            var lines = Files.readAllLines(this.path, StandardCharsets.UTF_8)
                    .stream()
                    .map(String::trim)
                    .toList();

            for (var line : lines) {
                if (this.shouldDeobfuscate.test(line)) {
                    var e = this.deobfuscater.deobfuscate(line);
                    map.put(e.key(), e.value());
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to map for deobfuscation", e);
        }

        return map;
    }

    public interface Deobfuscater {
        Pair<String, String> deobfuscate(String text);
    }
}
