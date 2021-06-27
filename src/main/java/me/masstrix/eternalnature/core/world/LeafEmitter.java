/*
 * Copyright 2019 Matthew Denton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.masstrix.eternalnature.core.world;

import me.masstrix.eternalnature.EternalNature;
import me.masstrix.eternalnature.config.Configurable;
import me.masstrix.eternalnature.core.EternalWorker;
import me.masstrix.eternalnature.core.particle.LeafParticle;
import me.masstrix.eternalnature.player.UserData;
import me.masstrix.eternalnature.util.BlockScanner;
import me.masstrix.eternalnature.util.EnumUtils;
import me.masstrix.eternalnature.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Configurable.Path("global.falling-leaves")
public class LeafEmitter implements EternalWorker, Configurable {

    /**
     * Defines the emitter type for leaf particles. If using a custom resource pack
     * it would be more performant to use the particles otherwise entities will give
     * a more 3D effect and work more universally with additional control for wind.
     */
    enum EmitterType {
        PARTICLE, ENTITY
    }

    private boolean enabled;
    private double spawnChance = 0.005;
    private int maxParticles = 200;
    private int scanDelay = 2;
    private final EternalNature PLUGIN;
    private final Set<Location> LOCATIONS;
    private final Set<LeafParticle> PARTICLES;
    private final BlockScanner SCANNER;
    private BukkitTask updater, spawner;
    private LeafParticle.LeafOptions options = LeafParticle.LeafOptions.DEFAULT;
    private EmitterType emitterType = EmitterType.ENTITY;

    // Particle fields
    private Particle particle = Particle.FALLING_WATER;


    public LeafEmitter(EternalNature plugin) {
        this.PLUGIN = plugin;
        this.SCANNER = new BlockScanner(plugin);
        LOCATIONS = Collections.newSetFromMap(new ConcurrentHashMap<>());
        PARTICLES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    @Override
    public void updateConfig(ConfigurationSection section) {
        enabled = section.getBoolean("enabled");
        scanDelay = section.getInt("scan-delay");
        maxParticles = section.getInt("max-particles");
        spawnChance = section.getDouble("spawn-chance");
        int range = section.getInt("range");
        int fidelity = section.getInt("fidelity");
        SCANNER.setScanScale(range, range + 4);
        SCANNER.setFidelity(fidelity);
        options = new LeafParticle.LeafOptions()
                .setLifeMax(section.getInt("life.max"))
                .setLifeMin(section.getInt("life.min"))
                .setForceReachGround(section.getBoolean("always-reach-ground"));

        // Set the particle emit type
        String type = section.getString("emitter-type");
        emitterType = EnumUtils.findMatch(EmitterType.values(), type, EmitterType.ENTITY);
        String part = section.getString("particle");
        particle = EnumUtils.findMatch(Particle.values(), part, Particle.ASH);
    }

    /**
     * Returns how many leaf particles are currently in the world. This will only work
     * if the emitter type is set to entity.
     *
     * @return how many particles are currently spawned in the world.
     */
    public int getParticleCount() {
        return PARTICLES.size();
    }

    /**
     * Returns the max number of particles allowed at any given time. This will only work
     * if the emitter type is set to entity.
     *
     * @return the max number of particles allowed at and given time in the world.
     */
    public int getMaxParticles() {
        return maxParticles;
    }

    /**
     * @return the chance of a leaf particle being spawned on a block every half a second.
     */
    public double getSpawnChance() {
        return spawnChance;
    }

    @Override
    public void start() {
        // Stop runnables if already started before
        if (updater != null) updater.cancel();
        if (spawner != null) spawner.cancel();

        updater = new BukkitRunnable() { // Auto scan around players for new leaves.
            int ticks = scanDelay;
            int passed = 0;
            @Override
            public void run() {
                if (passed++ >= ticks) {
                    passed = 0;
                    if (!enabled) return;
                    scan();
                    // Reduce the amount of scans being done as more players are online
                    int online = Bukkit.getOnlinePlayers().size();
                    if (online > 20) {
                        ticks = (online / 20) + scanDelay;
                    }
                }
            }
        }.runTaskTimerAsynchronously(PLUGIN, 10, 20);

        // Spawn leaf effects in valid locations found.
        spawner = new BukkitRunnable() {
            @Override
            public void run() {
                // Emit entities otherwise
                if (emitterType == EmitterType.PARTICLE) {
                    Set<LeafParticle> dead = new HashSet<>();
                    for (LeafParticle effect : PARTICLES) {
                        effect.tick();
                        if (!effect.isAlive())
                            dead.add(effect);
                    }
                    PARTICLES.removeAll(dead);
                    if (PARTICLES.size() >= maxParticles) return;
                }
                if (!enabled) return;
                for (Location loc : LOCATIONS) {
                    if (!MathUtil.chance(spawnChance)) continue;
                    double offsetX = MathUtil.randomDouble() - 0.5;
                    double offsetZ = MathUtil.randomDouble() - 0.5;
                    Location spawnLoc = loc.clone().add(offsetX, -0.5, offsetZ);

                    if (emitterType == EmitterType.PARTICLE) {
                        spawnLoc.getWorld().spawnParticle(particle,spawnLoc, 1, 0, 0, 0, 0.01);
                        continue;
                    }

                    WorldData worldData = PLUGIN.getEngine().getWorldProvider().getWorld(loc.getWorld());
                    LeafParticle particle = new LeafParticle(spawnLoc, PLUGIN.getEngine(), options);
                    particle.setForces(worldData.getWind());
                    PARTICLES.add(particle);
                }
            }
        }.runTaskTimerAsynchronously(PLUGIN, 10, 1);
    }

    /**
     * Spawns a new leaf particle already assigned to this emitter and returns it.
     *
     * @param loc location to spawn the particle at.
     * @return leaf particle that was spawned.
     */
    public LeafParticle spawn(Location loc) {
        LeafParticle particle = new LeafParticle(loc);
        PARTICLES.add(particle);
        return particle;
    }

    /**
     * Scans an area around all players and checks to see if there are any valid leaves to
     * spawn leafs from.
     */
    public void scan() {
        LOCATIONS.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Ignore afk players.
            UserData userData = PLUGIN.getEngine().getUserData(player.getUniqueId());
            if (userData.getPlayerIdleInfo().isAfk()) continue;

            // Set the location of the player and scan around them.
            SCANNER.setLocation(player.getLocation());
            SCANNER.scan(block -> {
                Block below = block.getRelative(BlockFace.DOWN);
                if (block.getBlockData() instanceof Leaves && below.isPassable() && !below.isLiquid())
                    LOCATIONS.add(block.getLocation().add(0.5, 0, 0.5));
            });
        }
    }

    /**
     * Clears all effects and ends all running tasks.
     */
    @Override
    public void end() {
        if (updater != null) {
            updater.cancel();
            updater = null;
        }
        if (spawner != null) {
            spawner.cancel();
            spawner = null;
        }
        LOCATIONS.clear();
        PARTICLES.forEach(LeafParticle::remove);
        PARTICLES.clear();
    }
}
