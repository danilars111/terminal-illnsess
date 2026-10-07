package io.github.danilars111.server.domain.component;

import io.github.danilars111.server.domain.component.interfaces.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record CooldownComponent(
        Map<String, Integer> cooldowns) implements Component
{
    public CooldownComponent {
        cooldowns = (cooldowns != null) ? Map.copyOf(cooldowns) : Map.of();
    }

    public static CooldownComponent empty() {
        return new CooldownComponent(Map.of());
    }

    public int getCooldown(String key) {
        return cooldowns.getOrDefault(key, 0);
    }

    public boolean isOnCooldown(String key) {
        return cooldowns.getOrDefault(key, 0) > 0;
    }

    public CooldownComponent withCooldown(String key, int ticks) {
        Objects.requireNonNull(key, "Key cannot be null");
       if(ticks <= 0) { return withCleared(key); }

       Map<String, Integer> next = new HashMap<String, Integer>(cooldowns);
       next.put(key, ticks);
       return new CooldownComponent(next);
    }

    public CooldownComponent withCleared(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if(!cooldowns.containsKey(key)) { return this; }
        Map<String, Integer> next = new HashMap<String, Integer>(cooldowns);
        next.remove(key);
        return new CooldownComponent(next);
    }

    public CooldownComponent tick() {
        if(cooldowns.isEmpty()) { return this; }
        Map<String, Integer> next = new HashMap<String, Integer>(cooldowns);
        for(Map.Entry<String, Integer> entry : cooldowns.entrySet()) {
            int remainingTicks = cooldowns.get(entry.getValue()) - 1;
            if(remainingTicks > 0) {
                next.put(entry.getKey(), remainingTicks);
            }
        }
        return new CooldownComponent(next);
    }
}
