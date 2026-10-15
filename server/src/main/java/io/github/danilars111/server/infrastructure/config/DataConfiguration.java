package io.github.danilars111.server.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.server.domain.data.DataLoader;
import io.github.danilars111.server.domain.data.DefinitionRegistry;
import io.github.danilars111.server.domain.data.DefinitionRepository;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

@Configuration
public class DataConfiguration {

    @Bean
    public DataLoader dataLoader(
            ObjectMapper objectMapper,
            @Value("${game.data.install-dir:./data")
            String installDirPath
    ) {
        return new DataLoader(objectMapper, Path.of(installDirPath));
    }

    @Bean
    public DefinitionRegistry definitionRegistry(DataLoader dataLoader) throws IOException {
        DefinitionRegistry registry = new DefinitionRegistry(dataLoader, "io.github.danilars111.contracts");
        registry.discoverAndLoadAll();
        return registry;
    }

    @Bean
    public TileIndex tileIndex(DefinitionRegistry registry) {
        DefinitionRepository<TileDefinition> tileRepo = registry.getRepository(TileDefinition.class);
        TileIndex tileIndex = new TileIndex();

      //  tileRepo.getAll().keySet().stream().sorted().forEach(tileIndex::register);

        return tileIndex;
    }

    @Bean
    public TilePalette tilePalette(DefinitionRegistry registry, TileIndex tileIndex) {
        DefinitionRepository<TileDefinition> tileRepo = registry.getRepository(TileDefinition.class);
        TilePalette tilePalette = new TilePalette();

        //Initial build on startup
        tilePalette.rebuild(tileIndex, tileRepo);

        // Bind hot-reload listener so reloads rebuild the array automatically
        registry.onReload(TileDefinition.class, () -> tilePalette.rebuild(tileIndex, tileRepo));

        return tilePalette;
    }
}
