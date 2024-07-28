package com.puddingkc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.material.Colorable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class KcMobStack extends JavaPlugin implements Listener {

    private List<String> mobTypes;
    private List<String> excludedWorlds;
    private int mergeRadius;
    private int taskPeriod;
    private int maxEntityCount;
    private ChatColor nameColor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        new MergeTask().runTaskTimer(this, 0L, taskPeriod * 20L);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("插件启用成功，作者QQ: 3116078709");
    }

    private void loadConfigValues() {
        mobTypes = getConfig().getStringList("mobs");
        excludedWorlds = getConfig().getStringList("blacklist-world");
        mergeRadius = getConfig().getInt("settings.radius",10);
        taskPeriod = getConfig().getInt("settings.period",5);
        maxEntityCount = getConfig().getInt("settings.limit",50);
        nameColor = ChatColor.valueOf(getConfig().getString("settings.name-color","GREEN"));
    }

    private class MergeTask extends BukkitRunnable {
        @Override
        public void run() {
            Bukkit.getScheduler().runTask(KcMobStack.this, () -> getServer().getWorlds().stream()
                    .filter(world -> !excludedWorlds.contains(world.getName()))
                    .forEach(KcMobStack.this::processWorld));
        }
    }

    private void processWorld(World world) {
        List<Entity> entities = world.getEntities();
        int batchSize = maxEntityCount / 2;
        for (int i = 0; i < entities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entities.size());
            List<Entity> batch = entities.subList(i, end);
            Bukkit.getScheduler().runTask(this, () -> processBatch(batch));
        }
    }

    private void processBatch(List<Entity> batch) {
        batch.stream()
                .filter(entity -> entity instanceof LivingEntity && entity.isValid())
                .filter(entity -> mobTypes.contains(entity.getType().name()))
                .forEach(entity -> mergeNearbyEntities((LivingEntity) entity));
    }

    private void mergeNearbyEntities(LivingEntity entity) {
        int originalCount = getEntityCount(entity);
        final int[] removedCount = {0};

        entity.getNearbyEntities(mergeRadius, mergeRadius, mergeRadius).stream()
                .filter(other -> other instanceof LivingEntity && other.isValid())
                .filter(other -> matchEntities(entity, other))
                .forEach(other -> {
                    int otherCount = getEntityCount((LivingEntity) other);
                    if (originalCount + removedCount[0] + otherCount < maxEntityCount) {
                        other.remove();
                        removedCount[0] += otherCount;
                    }
                });

        if (removedCount[0] > 0) {
            entity.setCustomName(nameColor + Integer.toString(originalCount + removedCount[0]));
        }
    }

    private boolean matchEntities(Entity a, Entity b) {
        if (a.getType() == b.getType()) {
            if (a instanceof Ageable && b instanceof Ageable && ((Ageable) a).isAdult() != ((Ageable) b).isAdult()) {
                return false;
            }
            return !(a instanceof Colorable) || !(b instanceof Colorable) || ((Colorable) a).getColor() == ((Colorable) b).getColor();
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        int entityCount = getEntityCount(entity);
        if (entityCount > 1) {
            LivingEntity clone = (LivingEntity) entity.getWorld().spawnEntity(entity.getLocation(), entity.getType());
            if (entityCount > 2) {
                clone.setCustomName(nameColor + Integer.toString(entityCount - 1));
            }
        }
    }

    private int getEntityCount(LivingEntity entity) {
        String customName = entity.getCustomName();
        if (customName != null && customName.startsWith(nameColor.toString())) {
            try {
                return Integer.parseInt(ChatColor.stripColor(customName));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }
}
