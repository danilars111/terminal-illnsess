package io.github.danilars111.server.domain.system;

import io.github.danilars111.contracts.definition.world.TileDefinition;
import io.github.danilars111.server.domain.component.CooldownComponent;
import io.github.danilars111.server.domain.data.DefinitionRepository;
import io.github.danilars111.server.domain.ecs.EntityManager;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import io.github.danilars111.server.domain.service.indices.SpatialIndex;
import io.github.danilars111.server.domain.service.indices.TileIndex;
import io.github.danilars111.server.domain.service.indices.TilePalette;
import io.github.danilars111.server.domain.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CooldownSystemTest {

    private GameState gameState;
    private CooldownSystem cooldownSystem;

    @BeforeEach
    void setUp() {
        DefinitionRepository<?> emptyRepo = new DefinitionRepository<>();
        emptyRepo.replaceAll(Map.of());

        TileIndex tileIndex = new TileIndex();
        TilePalette palette = new TilePalette();
        palette.rebuild(tileIndex, (DefinitionRepository<TileDefinition>) emptyRepo);

        World world = new World(palette, tileIndex);
        gameState = new GameState(new EntityManager(), new SpatialIndex(), world);
        cooldownSystem = new CooldownSystem();
    }

    @Test
    @DisplayName("Decrements remaining ticks each system update")
    void decrementsActiveCooldown() {
        EntityId entity = gameState.entityManager().createEntity();
        gameState.entityManager().addComponent(entity, CooldownComponent.empty()
                .withCooldown("dummy:cooldown_1", 3));

        // Tick 1 -> remaining 2
        cooldownSystem.update(gameState, 1L);
        CooldownComponent cd1 = gameState.entityManager().getComponent(entity, CooldownComponent.class).orElseThrow();
        assertEquals(2, cd1.getCooldown("dummy:cooldown_1"));
        assertTrue(cd1.isOnCooldown("dummy:cooldown_1"));

        // Tick 2 -> remaining 1
        cooldownSystem.update(gameState, 2L);
        CooldownComponent cd2 = gameState.entityManager().getComponent(entity, CooldownComponent.class).orElseThrow();
        assertEquals(1, cd2.getCooldown("dummy:cooldown_1"));
        assertTrue(cd2.isOnCooldown("dummy:cooldown_1"));

        // Tick 3 -> remaining 0 (expired)
        cooldownSystem.update(gameState, 3L);
        CooldownComponent cd3 = gameState.entityManager().getComponent(entity, CooldownComponent.class).orElseThrow();
        assertEquals(0, cd3.getCooldown("dummy:cooldown_1"));
        assertFalse(cd3.isOnCooldown("dummy:cooldown_1"));
    }

    @Test
    @DisplayName("Handles multiple separate cooldown channels simultaneously")
    void decrementsMultipleChannelsIndependently() {
        EntityId id = gameState.entityManager().createEntity();
        gameState.entityManager().addComponent(id, CooldownComponent.empty()
                .withCooldown("dummy:cooldown_1", 10)
                .withCooldown("dummy:cooldown_2", 3));

        // Tick 1 -> remaining 9 & 2
        cooldownSystem.update(gameState, 1L);
        CooldownComponent cooldown = gameState.entityManager().getComponent(id, CooldownComponent.class).orElseThrow();
        assertEquals(9, cooldown.getCooldown("dummy:cooldown_1"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_1"));
        assertEquals(2, cooldown.getCooldown("dummy:cooldown_2"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_2"));

        // Tick 2 -> remaining 8 & 1
        cooldownSystem.update(gameState, 2L);
        cooldown = gameState.entityManager().getComponent(id, CooldownComponent.class).orElseThrow();
        assertEquals(8, cooldown.getCooldown("dummy:cooldown_1"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_1"));
        assertEquals(1, cooldown.getCooldown("dummy:cooldown_2"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_2"));

        // Tick 3 -> remaining 7 & 0
        cooldownSystem.update(gameState, 3L);
        cooldown = gameState.entityManager().getComponent(id, CooldownComponent.class).orElseThrow();
        assertEquals(7, cooldown.getCooldown("dummy:cooldown_1"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_1"));
        assertEquals(0, cooldown.getCooldown("dummy:cooldown_2"));
        assertFalse(cooldown.isOnCooldown("dummy:cooldown_2"));
    }
}