package net.atlas.antiProjectileGhosting.listeners;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.AbstractProjectile;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;

public class ProjectilePhaseListener implements Listener {
    private static final Set<Projectile> projectiles = new HashSet<>();
    private static final int cuts = 6;

    @EventHandler
    public void onServerTickStart(ServerTickStartEvent event) {
        for (Projectile proj : projectiles.stream().toList()) {
            World world = proj.getWorld();

            net.minecraft.world.entity.projectile.Projectile nmsProj = ((AbstractProjectile) proj).getHandle();
            Vec3 aVelocity = nmsProj.getDeltaMovement().add(0, nmsProj.getGravity(), 0);
            int functionalCuts = Math.toIntExact(Math.round(cuts * Math.max(1, aVelocity.length() / 2.5)));
            if (proj.isDead() || proj.isOnGround() || (proj instanceof Trident trident && trident.hasDealtDamage()) || nmsProj.getDeltaMovement().length() <= 0)
                continue;

            AABB projCurrentBox = nmsProj.getBoundingBox();
            AABB projNextBox = nmsProj.getBoundingBox().move(aVelocity);
            CraftPlayer playerShooter = proj.getShooter() instanceof CraftPlayer shooter ? shooter : null;
            float currentMargin = computeMargin(nmsProj, 0);
            float nextMargin = computeMargin(nmsProj, 1);

            for (Entity e : ((CraftWorld) world).getHandle().getEntitiesOfClass(Entity.class,
                    projCurrentBox.expandTowards(aVelocity).inflate(1.5),
                    target -> !target.isSpectator()
                            && (playerShooter == null || target != playerShooter.getHandle()))) {
                Vec3 eVelocity = e.getKnownMovement().add(0, e.getGravity(), 0);
                AABB oldBox = e.getBoundingBox().inflate(currentMargin);
                AABB currentBox = e.getBoundingBox().inflate(nextMargin).move(eVelocity);
                for (int cut = 0; cut <= functionalCuts; cut++) {
                    float progress = (float) cut / functionalCuts;
                    BoundingBox arrowBox = lerpBoxes(progress, projCurrentBox, projNextBox);
                    BoundingBox targetBox = lerpBoxes(progress, oldBox, currentBox);

                    if (boxesOverlap(arrowBox, targetBox) && nmsProj.canHitEntityPublic(e)) {
                        nmsProj.preHitTargetOrDeflectSelf(new EntityHitResult(e, nmsProj.position()));
                        nmsProj.needsSync = true;
                        break;
                    }
                }
            }
        }
    }

    public ProjectilePhaseListener() {

    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Projectile proj) {
            projectiles.add(proj);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Vector pVelocity = event.getTo().toVector().subtract(event.getFrom().toVector());
        org.bukkit.entity.Player p = event.getPlayer();
        Player nmsPlayer = ((CraftPlayer) p).getHandle();
        AABB pCurrentBox = nmsPlayer.getBoundingBoxAt(event.getFrom().x(), event.getFrom().y(), event.getFrom().z());
        AABB pNextBox = nmsPlayer.getBoundingBoxAt(event.getTo().x(), event.getTo().y(), event.getTo().z());
        for (net.minecraft.world.entity.projectile.Projectile nmsProj : nmsPlayer.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                pCurrentBox.expandTowards(pVelocity.getX(), pVelocity.getY(), pVelocity.getZ()).inflate(1.5),
                proj -> nmsPlayer != proj.getOwner()
                        && (proj.onGround() ? proj.getDeltaMovement().horizontalDistance() <= 0 : proj.getDeltaMovement().length() <= 0)
                        && proj.getBukkitEntity() instanceof Projectile bukkit
                        && projectiles.contains(bukkit)
                        && proj.isAlive()
                        && !proj.onGround()
                        && (!(proj instanceof ThrownTrident trident) || !trident.dealtDamage))) {
            Vec3 aVelocity = nmsProj.getDeltaMovement().subtract(0, nmsProj.getGravity(), 0);

            AABB projCurrentBox = nmsProj.getBoundingBox();
            AABB projNextBox = nmsProj.getBoundingBox().move(aVelocity);
            int functionalCuts = Math.toIntExact(Math.round(cuts * Math.max(1, aVelocity.length() / 2.5)));
            for (int cut = 0; cut <= functionalCuts; cut++) {
                float progress = (float) cut / functionalCuts;
                BoundingBox playerBox = lerpBoxes(progress, pCurrentBox.inflate(computeMargin(nmsProj, 0)), pNextBox.inflate(computeMargin(nmsProj, 1)));
                BoundingBox arrowBox = lerpBoxes(progress, projCurrentBox, projNextBox);

                if (boxesOverlap(arrowBox, playerBox) && nmsProj.canHitEntityPublic(nmsPlayer)) {
                    nmsProj.preHitTargetOrDeflectSelf(new EntityHitResult(nmsPlayer, nmsProj.position()));
                    nmsProj.needsSync = true;
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onRemoveEntity(EntityRemoveEvent event) {
        if (event.getEntity() instanceof Projectile proj) projectiles.remove(proj);
    }

    public static BoundingBox lerpBoxes(float delta, AABB oldBox, AABB newBox) {
        return new BoundingBox(Mth.lerp(delta, oldBox.minX, newBox.minX),
                Mth.lerp(delta, oldBox.minY, newBox.minY),
                Mth.lerp(delta, oldBox.minZ, newBox.minZ),
                Mth.lerp(delta, oldBox.maxX, newBox.maxX),
                Mth.lerp(delta, oldBox.maxY, newBox.maxY),
                Mth.lerp(delta, oldBox.maxZ, newBox.maxZ));
    }
    public static boolean boxesOverlap(BoundingBox a, BoundingBox b) {
        return (a.getMinX() <= b.getMaxX() && a.getMaxX() >= b.getMinX())
        && (a.getMinY() <= b.getMaxY() && a.getMaxY() >= b.getMinY())
        && (a.getMinZ() <= b.getMaxZ() && a.getMaxZ() >= b.getMinZ());
    }

    public static float computeMargin(Entity entity, int tickOffset) {
        return Math.max(0.0F, Math.min(0.3F, (float)((entity.tickCount + tickOffset) - 2) / 20.0F));
    }
}