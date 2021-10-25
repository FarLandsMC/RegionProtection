package com.kicas.rp.event;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.data.flagdata.EnumFilter;
import com.kicas.rp.data.FlagContainer;
import com.kicas.rp.data.RegionFlag;
import com.kicas.rp.data.flagdata.TrustLevel;
import com.kicas.rp.data.flagdata.TrustMeta;
import com.kicas.rp.util.Entities;
import com.kicas.rp.util.Materials;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Objects;

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
        if (RegionProtection.getDataManager().crossesRegions(event.getBlock().getLocation(), event.getToBlock().getLocation()))
            event.setCancelled(true);
        else {
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());

            if (Material.WATER == event.getBlock().getType())
                event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.WATER_FLOW));
            else
                event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.LAVA_FLOW));
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
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.MOUNT)
            return; // Prevents mobs disappearing in regions when a player logs out riding a vehicle

        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getEntity().getLocation());
        if (flags != null) {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.LIGHTNING && !flags.isAllowed(RegionFlag.LIGHTNING_MOB_DAMAGE)) {
                event.setCancelled(true);
            } else if (!Entities.isArtificialSpawn(event.getSpawnReason()) &&
                    flags.<EnumFilter.EntityFilter>getFlagMeta(RegionFlag.DENY_SPAWN).isBlocked(event.getEntity().getType())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Handles growth flag event. This includes block spreading, vines or other plants growing a new block,
     *  seed based plants advancing a stage, trees and similar structures growing.
     *
     * @param event    the event.
     * @param location the location of the event.
     * @param material the material of the block being grown.
     */
    public void handleGrowth(Cancellable event, Location location, Material material) {
        if (material == Material.AIR)
            return;

        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(location);
        event.setCancelled(flags != null &&
                flags.<EnumFilter.MaterialFilter>getFlagMeta(RegionFlag.DENY_GROWTH).isBlocked(material));
    }

    /**
     * Handles block spreading and vine growth.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        handleGrowth(event, event.getSource().getLocation(), event.getSource()  .getType());
        handleGrowth(event, event.getBlock() .getLocation(), event.getNewState().getType());
    }

    /**
     * Handles mushroom and tree growth.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        handleGrowth(event, event.getLocation(), event.getLocation().getBlock().getType());
    }

    /**
     * Handles mushroom and tree growth.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        handleGrowth(event, event.getBlock()   .getLocation(), event.getBlock()   .getType());
        handleGrowth(event, event.getNewState().getLocation(), event.getNewState().getType());
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

        switch (event.getBlock().getType()) {
            case ICE:
            case FROSTED_ICE:
                event.setCancelled(!flags.isAllowed(RegionFlag.ICE_CHANGE));
                return;

            case SNOW:
                event.setCancelled(!flags.isAllowed(RegionFlag.SNOW_CHANGE));
                return;

            case FARMLAND:
                event.setCancelled(!flags.isAllowed(RegionFlag.FARMLAND_MOISTURE_CHANGE));
                return;
        }

        if (Materials.isCoral(event.getBlock().getType()))
            event.setCancelled(!flags.isAllowed(RegionFlag.CORAL_DEATH));
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
     * Handles lightning strikes.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLightningStrike(LightningStrikeEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getLightning().getLocation());
        event.setCancelled(flags != null && event.getCause() != LightningStrikeEvent.Cause.COMMAND &&
                !flags.isAllowed(RegionFlag.LIGHTNING_STRIKES));
    }

    /**
     * Handle portal pair creation.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPortalCreated(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR) {
            event.setCancelled(event.getBlocks().stream()
                    .map(state -> RegionProtection.getDataManager().getFlagsAt(state.getLocation()))
                    .filter(Objects::nonNull)
                    .anyMatch(flags -> !flags.isAllowed(RegionFlag.PORTAL_PAIR_FORMATION))
            );
        }
    }

    /**
     * Handle farmland-moisture-change flag.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.FARMLAND_MOISTURE_CHANGE));
    }

    /**
     * Handle non-trusted players triggering raids in claims.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRaidTriggered(RaidTriggerEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getPlayer().getLocation());
        event.setCancelled(flags != null && !flags.<TrustMeta>getFlagMeta(RegionFlag.TRUST).hasTrust(
                event.getPlayer(),
                TrustLevel.BUILD,
                flags
        ));
    }

    /**
     * Handle fire-tick flag.
     *
     * @param event the event;
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockIgnited(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || event.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
            event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.FIRE_TICK));
        }
    }

    /**
     * Handle fire-tick flag by preventing blocks from fully burning.
     *
     * @param event the event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        FlagContainer flags = RegionProtection.getDataManager().getFlagsAt(event.getBlock().getLocation());
        event.setCancelled(flags != null && !flags.isAllowed(RegionFlag.FIRE_TICK));
    }
}