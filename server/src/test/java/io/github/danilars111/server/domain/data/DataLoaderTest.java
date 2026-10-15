package io.github.danilars111.server.domain.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.contracts.definition.world.enums.TraversalMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DataLoaderTest {

    private ObjectMapper objectMapper;

    @TempDir
    Path tempInstallDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Loads base definitions, verifies single composite hash, and flags no mods")
    void loadsBaseDefinitionsAndCalculatesCompositeHash() throws IOException {
        Path baseTileDir = tempInstallDir.resolve("base/tiles");
        Files.createDirectories(baseTileDir);

        String dirtJson = """
            {
              "id": "dirt_floor",
              "displayName": "Dirt Floor",
              "traversalModes": ["WALKING"],
              "effectIds": []
            }
            """;
        Files.writeString(baseTileDir.resolve("dirt.json"), dirtJson);

        DataLoader loader = new DataLoader(objectMapper, tempInstallDir);
        DefinitionRepository<TileDefinition> repo = loader.loadDefinitions("tiles", TileDefinition.class);

        assertEquals(1, repo.size());
        TileDefinition tile = repo.getRequired("dirt_floor");
        assertEquals("Dirt Floor", tile.displayName());
        assertTrue(tile.isTraversable(TraversalMode.WALKING));

        assertFalse(loader.areModsDetected());
        String initialHash = loader.getBaseDataHash();
        assertFalse(initialHash.isBlank());

        // Tampering with base content must change the single composite hash
        String modifiedDirtJson = """
            {
              "id": "dirt_floor",
              "displayName": "Muddy Dirt Floor",
              "traversalModes": ["WALKING"],
              "effectIds": []
            }
            """;
        Files.writeString(baseTileDir.resolve("dirt.json"), modifiedDirtJson);

        loader.reloadDefinitions(repo, "tiles", TileDefinition.class);
        assertNotEquals(initialHash, loader.getBaseDataHash());
        assertEquals("Muddy Dirt Floor", repo.getRequired("dirt_floor").displayName());
    }

    @Test
    @DisplayName("Mod file overrides base definition with identical ID and sets modsDetected")
    void modFileOverridesBaseDefinition() throws IOException {
        Path baseTileDir = tempInstallDir.resolve("base/tiles");
        Path modTileDir = tempInstallDir.resolve("mods/tiles");
        Files.createDirectories(baseTileDir);
        Files.createDirectories(modTileDir);

        String baseJson = """
            {
              "id": "stone_wall",
              "displayName": "Solid Stone Wall",
              "traversalModes": [],
              "effectIds": []
            }
            """;
        Files.writeString(baseTileDir.resolve("stone_wall.json"), baseJson);

        String modJson = """
            {
              "id": "stone_wall",
              "displayName": "Low Stone Wall",
              "traversalModes": ["FLYING"],
              "effectIds": []
            }
            """;
        Files.writeString(modTileDir.resolve("custom_wall.json"), modJson);

        DataLoader loader = new DataLoader(objectMapper, tempInstallDir);
        DefinitionRepository<TileDefinition> repo = loader.loadDefinitions("tiles", TileDefinition.class);

        assertEquals(1, repo.size());
        TileDefinition result = repo.getRequired("stone_wall");
        assertEquals("Low Stone Wall", result.displayName());
        assertTrue(result.isTraversable(TraversalMode.FLYING));
        assertFalse(result.isTraversable(TraversalMode.WALKING));

        assertTrue(loader.areModsDetected());
    }

    @Test
    @DisplayName("Hot reload updates live repository in place when disk content changes")
    void hotReloadUpdatesExistingRepositoryInPlace() throws IOException {
        Path baseTileDir = tempInstallDir.resolve("base/tiles");
        Files.createDirectories(baseTileDir);

        Path tileFile = baseTileDir.resolve("water.json");
        Files.writeString(tileFile, """
            {
              "id": "water",
              "displayName": "Shallow Water",
              "traversalModes": ["SWIMMING"],
              "effectIds": []
            }
            """);

        DataLoader loader = new DataLoader(objectMapper, tempInstallDir);
        DefinitionRepository<TileDefinition> repo = loader.loadDefinitions("tiles", TileDefinition.class);

        assertFalse(repo.getRequired("water").isTraversable(TraversalMode.WALKING));

        // Tweak file on disk (simulate editing definition during testing)
        Files.writeString(tileFile, """
            {
              "id": "water",
              "displayName": "Frozen Water",
              "traversalModes": ["WALKING", "SWIMMING"],
              "effectIds": []
            }
            """);

        loader.reloadDefinitions(repo, "tiles", TileDefinition.class);

        TileDefinition updated = repo.getRequired("water");
        assertEquals("Frozen Water", updated.displayName());
        assertTrue(updated.isTraversable(TraversalMode.WALKING));
        assertTrue(updated.isTraversable(TraversalMode.SWIMMING));
    }

    @Test
    @DisplayName("Failed JSON parsing leaves existing repository untouched")
    void malformedJsonDoesNotCorruptRepository() throws IOException {
        Path baseTileDir = tempInstallDir.resolve("base/tiles");
        Files.createDirectories(baseTileDir);

        Path tileFile = baseTileDir.resolve("grass.json");
        Files.writeString(tileFile, """
            {
              "id": "grass",
              "displayName": "Tall Grass",
              "traversalModes": ["WALKING"],
              "effectIds": []
            }
            """);

        DataLoader loader = new DataLoader(objectMapper, tempInstallDir);
        DefinitionRepository<TileDefinition> repo = loader.loadDefinitions("tiles", TileDefinition.class);

        assertEquals(1, repo.size());

        // Corrupt the JSON file syntax
        Files.writeString(tileFile, "{ \"id\": \"grass\", BROKEN_JSON }");

        assertThrows(IOException.class, () -> loader.reloadDefinitions(repo, "tiles", TileDefinition.class));

        // Original definition remains intact
        assertEquals(1, repo.size());
        assertEquals("Tall Grass", repo.getRequired("grass").displayName());
    }
}