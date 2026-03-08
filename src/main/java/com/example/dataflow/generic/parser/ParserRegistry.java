package com.example.dataflow.generic.parser;

import com.example.dataflow.generic.parser.config.ParserRegistryConfig;
import com.example.dataflow.generic.parser.config.ParserRegistryConfig.ParserConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.beam.sdk.io.FileSystems;
import org.apache.beam.sdk.io.fs.ResourceId;
import org.yaml.snakeyaml.Yaml;

public class ParserRegistry {
    private final Map<String, MessageParser> parserByFormat;

    private ParserRegistry(Map<String, MessageParser> parserByFormat) {
        this.parserByFormat = parserByFormat;
    }

    public static ParserRegistry load(String configPath) {
        try {
            ParserRegistryConfig config = readConfig(configPath);
            Map<String, MessageParser> map = new HashMap<>();
            List<ParserConfig> parsers = config.getParsers();
            if (parsers == null || parsers.isEmpty()) {
                throw new IllegalArgumentException("No parser entries found in registry config");
            }

            for (ParserConfig parserConfig : parsers) {
                String formatKey = parserConfig.getFormat().toLowerCase(Locale.ROOT).trim();
                Class<?> clazz = Class.forName(parserConfig.getClassName());
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (!(instance instanceof MessageParser)) {
                    throw new IllegalArgumentException("Class does not implement MessageParser: " + parserConfig.getClassName());
                }
                MessageParser parser = (MessageParser) instance;
                parser.configure(parserConfig.getConfig());
                map.put(formatKey, parser);
            }

            return new ParserRegistry(map);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load parser registry from: " + configPath, ex);
        }
    }

    public JsonNode parse(String format, byte[] payload) throws Exception {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Format is required");
        }
        MessageParser parser = parserByFormat.get(format.toLowerCase(Locale.ROOT).trim());
        if (parser == null) {
            throw new IllegalArgumentException("No parser configured for format: " + format);
        }
        return parser.parse(payload);
    }

    public boolean hasParser(String format) {
        if (format == null || format.isBlank()) {
            return false;
        }
        return parserByFormat.containsKey(format.toLowerCase(Locale.ROOT).trim());
    }

    public Set<String> getConfiguredFormats() {
        return new LinkedHashSet<>(parserByFormat.keySet());
    }

    @SuppressWarnings("unchecked")
    private static ParserRegistryConfig readConfig(String configPath) throws Exception {
        String extension = extractConfigName(configPath).toLowerCase(Locale.ROOT);
        try (InputStream stream = openConfigStream(configPath)) {
            if (extension.endsWith(".yaml") || extension.endsWith(".yml")) {
                Yaml yaml = new Yaml();
                Map<String, Object> raw = yaml.load(stream);
                ObjectMapper mapper = new ObjectMapper();
                return mapper.convertValue(raw, ParserRegistryConfig.class);
            }
            ObjectMapper mapper = extension.endsWith(".xml") ? new XmlMapper() : new ObjectMapper();
            return mapper.readValue(stream, ParserRegistryConfig.class);
        }
    }

    private static InputStream openConfigStream(String configPath) throws Exception {
        if (configPath.startsWith("classpath:")) {
            String resourcePath = configPath.substring("classpath:".length());
            while (resourcePath.startsWith("/")) {
                resourcePath = resourcePath.substring(1);
            }
            InputStream stream = ParserRegistry.class.getClassLoader().getResourceAsStream(resourcePath);
            if (stream == null) {
                throw new IllegalArgumentException("Classpath parser registry resource not found: " + configPath);
            }
            return stream;
        }
        if (configPath.startsWith("gs://")) {
            ResourceId resourceId = FileSystems.matchNewResource(configPath, false);
            return Channels.newInputStream(FileSystems.open(resourceId));
        }
        return new FileInputStream(configPath);
    }

    private static String extractConfigName(String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return "";
        }
        String normalized = configPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        int colon = normalized.lastIndexOf(':');
        int index = Math.max(slash, colon);
        return index >= 0 && index < normalized.length() - 1
                ? normalized.substring(index + 1)
                : normalized;
    }
}
