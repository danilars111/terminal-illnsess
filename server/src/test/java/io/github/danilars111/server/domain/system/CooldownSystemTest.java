package io.github.danilars111.server.domain.system;

import io.github.danilars111.server.domain.component.CooldownComponent;
import io.github.danilars111.server.domain.ecs.GameState;
import io.github.danilars111.server.domain.ecs.records.EntityId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CooldownSystemTest {

    GameState gameState;
    CooldownSystem cooldownSystem;

    @BeforeEach
    void setUp() {
        gameState = new GameState();
        cooldownSystem = new CooldownSystem();
    }

    @Test
    @DisplayName("Decrements remaining ticks each system update")
    void decrementsActiveCooldown() {
        EntityId entity = gameState.createEntity();
        gameState.addComponent(entity, CooldownComponent.empty()
                .withCooldown("dummy:cooldown_1", 3));

        // Tick 1 -> remaining 2
        cooldownSystem.update(gameState, 1L);
        CooldownComponent cd1 = gameState.getComponent(entity, CooldownComponent.class).orElseThrow();
        assertEquals(2, cd1.getCooldown("dummy:cooldown_1"));
        assertTrue(cd1.isOnCooldown("dummy:cooldown_1"));

        // Tick 2 -> remaining 1
        cooldownSystem.update(gameState, 2L);
        CooldownComponent cd2 = gameState.getComponent(entity, CooldownComponent.class).orElseThrow();
        assertEquals(1, cd2.getCooldown("dummy:cooldown_1"));
        assertTrue(cd2.isOnCooldown("dummy:cooldown_1"));

        // Tick 3 -> remaining 0 (expired)
        cooldownSystem.update(gameState, 3L);
        CooldownComponent cd3 = gameState.getComponent(entity, CooldownComponent.class).orElseThrow();
        assertEquals(0, cd3.getCooldown("dummy:cooldown_1"));
        assertFalse(cd3.isOnCooldown("dummy:cooldown_1"));
    }

    @Test
    @DisplayName("Handles multiple separate cooldown channels simultaneously")
    void decrementsMultipleChannelsIndependently() {
        EntityId id = gameState.createEntity();
        gameState.addComponent(id, CooldownComponent.empty()
                .withCooldown("dummy:cooldown_1", 10)
                .withCooldown("dummy:cooldown_2", 3));

        //  Tick 1 -> remaning 9 & 2
        cooldownSystem.update(gameState, 1L);
        CooldownComponent cooldown = gameState.getComponent(id, CooldownComponent.class).orElseThrow();
        assertEquals(9, cooldown.getCooldown("dummy:cooldown_1"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_1"));
        assertEquals(2, cooldown.getCooldown("dummy:cooldown_2"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_2"));

        // Tick 2 -> remaning 8 & 1
        cooldownSystem.update(gameState, 1L);
        cooldown = gameState.getComponent(id, CooldownComponent.class).orElseThrow();
        assertEquals(8, cooldown.getCooldown("dummy:cooldown_1"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_1"));
        assertEquals(1, cooldown.getCooldown("dummy:cooldown_2"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_2"));


        // Tick 3 -> remaning 7 & 0
        cooldownSystem.update(gameState, 1L);
        cooldown = gameState.getComponent(id, CooldownComponent.class).orElseThrow();
        assertEquals(7, cooldown.getCooldown("dummy:cooldown_1"));
        assertTrue(cooldown.isOnCooldown("dummy:cooldown_1"));
        assertEquals(0, cooldown.getCooldown("dummy:cooldown_2"));
        assertFalse(cooldown.isOnCooldown("dummy:cooldown_2"));
    }


}
