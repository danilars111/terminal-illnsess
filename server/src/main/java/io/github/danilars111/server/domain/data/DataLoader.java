package io.github.danilars111.server.domain.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.danilars111.contracts.definition.interfaces.Definition;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class DataLoader {

    private static final Logger LOGGER = System.getLogger(DataLoader.class.getName());

    private final ObjectMapper objectMapper;
    private final Path baseDataDir;
    private final Path modsDataDir;

    private String baseDataHash = "";
    private boolean modsDetected = false;

    public DataLoader(ObjectMapper objectMapper, Path installDataDir) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        Objects.requireNonNull(installDataDir, "installDataDir cannot be null");

        this.baseDataDir = installDataDir.resolve("base");
        this.modsDataDir = installDataDir.resolve("mods");
    }

    public <T extends Definition> DefinitionRepository<T> loadDefinitions(
            String subfolder,
            Class<T> definitionClass
    ) throws IOException {
        DefinitionRepository<T> repository = new DefinitionRepository<>();
        reloadDefinitions(repository, subfolder, definitionClass);
        return repository;
    }

    public <T extends Definition> void reloadDefinitions(
            DefinitionRepository<T> repository,
            String subfolder,
            Class<T> definitionClass
    ) throws IOException {
        Objects.requireNonNull(repository, "repository cannot be null");
        Objects.requireNonNull(subfolder, "subfolder cannot be null");
        Objects.requireNonNull(definitionClass, "definitionClass cannot be null");

        Map<String, T> staging = new HashMap<>();

        Path baseSubdir = baseDataDir.resolve(subfolder);
        if (Files.isDirectory(baseSubdir)) {
            try (Stream<Path> stream = Files.walk(baseSubdir)) {
                List<Path> baseFiles = stream.filter(this::isJsonFile)
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();

                MessageDigest digest = createSha256Digest();

                for (Path file : baseFiles) {
                    byte[] fileBytes = Files.readAllBytes(file);

                    String relativePath = baseDataDir.relativize(file).toString().replace('\\', '/');
                    digest.update(relativePath.getBytes(StandardCharsets.UTF_8));
                    digest.update(fileBytes);

                    T definition = objectMapper.readValue(file.toFile(), definitionClass);
                    staging.put(definition.id(), definition);
                    LOGGER.log(Level.DEBUG, "Loaded base definition [{0}] from {1}", definition.id(), relativePath);
                }

                this.baseDataHash = HexFormat.of().formatHex(digest.digest());
            }
        }

        Path modsSubdir = modsDataDir.resolve(subfolder);
        if (Files.isDirectory(modsSubdir)) {
            try (Stream<Path> stream = Files.walk(modsSubdir)) {
                List<Path> modFiles = stream.filter(this::isJsonFile).toList();
                if (!modFiles.isEmpty()) {
                    this.modsDetected = true;
                }
                for (Path file : modFiles) {
                    T definition = objectMapper.readValue(Files.readAllBytes(file), definitionClass);
                    boolean isOverride = staging.containsKey(definition.id());
                    staging.put(definition.id(), definition);

                    if (isOverride) {
                        LOGGER.log(Level.INFO, "Mod override applied for [{0}] from {1}", definition.id(), file.getFileName());
                    } else {
                        LOGGER.log(Level.DEBUG, "Loaded mod definition [{0}] from {1}", definition.id(), file.getFileName());
                    }
                }
            }
        }

        repository.replaceAll(staging);
    }

    private boolean isJsonFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".json");
    }

    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    public boolean areModsDetected() {
        return this.modsDetected;
    }

    public String getBaseDataHash() {
        return this.baseDataHash;
    }
}
