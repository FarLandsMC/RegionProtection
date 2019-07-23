package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.flagdata.EnumFilter;
import com.kicas.rp.data.FlagContainer;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.util.Entities;
import com.kicas.rp.util.Materials;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Handles events generally unrelated to entities that take place in the world.
 */
public class WorldEventHandler implements Listener {
    /**
     * Handles fluid flow.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockMove(BlockFromToEvent event) {
        if (RegionProtection.getDataManager().crossesRegions(event.getBlock().getLocation(),
                event.getToBlock().getLocation()))
            event.setCancelled(true);
        else {
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());

            if (Material.WATER == event.getBlock().getType()) {
                event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.WATER_FLOW));
            } else {
                event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.LAVA_FLOW));
            }
        }
    }

    /**
     * Handles piston extensions crossing region boundaries.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.setCancelled(event.getBlocks().stream().anyMatch(block -> RegionProtection.getDataManager()
                .crossesRegions(event.getBlock().getLocation(),
                        block.getRelative(event.getDirection()).getLocation())));
    }

    /**
     * Handles piston retractions crossing region boundaries.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.setCancelled(event.getBlocks().stream().anyMatch(block -> RegionProtection.getDataManager()
                .crossesRegions(event.getBlock().getLocation(), block.getLocation())));
    }

    /**
     * Handles creature spawn flag restrictions and mob lightning damage.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags != null) {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.LIGHTNING &&
                    !flags.isAllowed(RegionFlag.LIGHTNING_MOB_DAMAGE)) {
                event.setCancelled(true);
            } else if (!Entities.isArtificialSpawn(event.getSpawnReason()) &&
                    !flags.<EnumFilter>getFlagMeta(RegionFlag.DENY_SPAWN).isAllowed(event.getEntity().getType())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handles block spreading and vine growth.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.GROWTH));
    }

    /**
     * Handles mushroom and tree growth.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getLocation());
        event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.GROWTH));
    }

    /**
     * Handles mushroom and tree growth.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.GROWTH));
    }

    /**
     * Handles ice and snow melting or forming.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        if (flags == null)
            return;

        if (!flags.isAllowed(RegionFlag.ICE_CHANGE) &&
                (event.getBlock().getType() == Material.ICE || event.getBlock().getType() == Material.FROSTED_ICE))
            event.setCancelled(true);
        else if (!flags.isAllowed(RegionFlag.SNOW_CHANGE) && event.getBlock().getType() == Material.SNOW)
            event.setCancelled(true);
            // 2DO: prevent fire decay
        else if (!flags.isAllowed(RegionFlag.CORAL_DEATH) && Materials.isCoral(event.getBlock().getType()))
            event.setCancelled(true);
    }

    /**
     * Handles leaf decay.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.LEAF_DECAY));
    }

    /**
     * Handles lightning damage on mobs.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        event.setCancelled(flags != null && event.getCause() == EntityDamageEvent.DamageCause.LIGHTNING &&
                !(event.getEntity() instanceof Player) && !flags.isAllowed(RegionFlag.LIGHTNING_MOB_DAMAGE));
    }

    /**
     * Handle portal pair creation.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPortalCreated(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(event.getBlocks().stream().map(state -> RegionProtection.getDataManager().getFlagsAt(
                    state.getLocation())).anyMatch(flags -> flags != null &&
                    !flags.isAllowed(RegionFlag.PORTAL_PAIR_FORMATION)));
        }
    }
}
