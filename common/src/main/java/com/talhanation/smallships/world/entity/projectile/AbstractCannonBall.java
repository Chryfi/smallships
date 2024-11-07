package com.talhanation.smallships.world.entity.projectile;


import com.talhanation.smallships.config.SmallShipsConfig;
import com.talhanation.smallships.world.entity.ship.Ship;
import com.talhanation.smallships.world.sound.ModSoundTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractCannonBall extends AbstractHurtingProjectile {
    public boolean inWater = false;
    public boolean wasShot = false;
    public int counter = 0;

    protected AbstractCannonBall(EntityType<? extends AbstractCannonBall> type, Level world) {
        super(type, world);
    }

    public AbstractCannonBall(EntityType<? extends AbstractCannonBall> type, LivingEntity owner, double d1, double d2, double d3, Level world) {
        super(type, owner, new Vec3(d1, d2, d3), world);
        this.moveTo(d1, d2, d3, this.getYRot(), this.getXRot());
    }

    @Override
    public void tick() {
        this.baseTick();

        /* TODO shoot particles are weirdly adjusted by spawning delayed after updating position to spawn at the cannons on a ship.
            This is bad practice and unflexible and does not work for ground cannons,
            but I want to finish this so we adjust the projectile movement...
            Better Solution would be to better calculate cannon positions on the ship and spawn
            the balls properly there instead of from the middle of the ship */
        boolean spawnedFromShip = this.getOwner().getVehicle() != null && this.getOwner().getVehicle() instanceof Ship;
        if (!spawnedFromShip && this.isAlive()) {
            this.setWasShot(true);
        }

        Vec3 vector3d = this.getDeltaMovement();
        HitResult raytraceresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

        if (raytraceresult.getType() != HitResult.Type.MISS) {
            this.onHit(raytraceresult);
        }

        double d0 = this.getX() + vector3d.x;
        double d1 = this.getY() + vector3d.y;
        double d2 = this.getZ() + vector3d.z;
        this.updateRotation();
        float f = 0.99F;
        float f1 = 0.06F;
        float f2 = -0.05F;
        this.setDeltaMovement(vector3d.scale(f));
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -f1, 0.0D));
        }
        this.setPos(d0, d1, d2);

        if(spawnedFromShip && this.isAlive()){
            this.setWasShot(true);
        }

        if(isInWater()){
            if (this.level().isClientSide() && !isUnderWater()) waterParticles();

            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -f2, 0.0D));
            this.setInWater(true);
        }

        if (wasShot){
            counter++;
        }

        if (counter < 4){
            if (this.level().isClientSide()) {
                tailParticles();
            }
        }

        if (this.level().isClientSide()) {
            for (int i = 0; i < 3; ++i) {
                this.level().addParticle(ParticleTypes.POOF, this.getX(), this.getY(), this.getZ() , 0, 0, 0);
            }
        }

        if (isInWater() && counter > 200){
            this.discard();
        }
    }

    public void setWasShot(boolean bool){
        if (bool != wasShot){
            wasShot = true;

            if (this.level().isClientSide()) {
                this.shootParticles();
            }
        }
    }

    public void setInWater(boolean bool){
        if (bool != inWater){
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 3.3F, 0.8F + 0.4F * this.random.nextFloat());
            inWater = true;
            //this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
        if (!this.level().isClientSide()) {
            boolean doesSpreadFire = false;

            if(!isInWater()) this.level().explode(this.getOwner(), getX(), getY(), getZ(), SmallShipsConfig.Common.shipGeneralCannonDestruction.get().floatValue(), doesSpreadFire, Level.ExplosionInteraction.MOB);
            this.remove(RemovalReason.KILLED);
        }
    }


    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        this.hitParticles();
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (!this.level().isClientSide()) {
            Entity hitEntity = hitResult.getEntity();
            Entity ownerEntity = this.getOwner();

            if (hitEntity instanceof Ship shipHitEntity) {
                shipHitEntity.hurt(this.damageSources().thrown(this, ownerEntity), random.nextInt(7) + 7);
                this.level().playSound(null, this.getX(), this.getY() + 4 , this.getZ(), ModSoundTypes.SHIP_HIT, this.getSoundSource(), 3.3F, 0.8F + 0.4F * this.random.nextFloat());
            }
            else if (ownerEntity instanceof LivingEntity livingOwnerEntity) {
                if(ownerEntity.getTeam() != null && ownerEntity.getTeam().isAlliedTo(hitEntity.getTeam()) && !ownerEntity.getTeam().isAllowFriendlyFire()) return;
                this.level().playSound(null, this.getX(), this.getY() + 4 , this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), this.getSoundSource(), 3.3F, 0.8F + 0.4F * this.random.nextFloat());
            }

            hitEntity.hurt(this.damageSources().thrown(this, ownerEntity), SmallShipsConfig.Common.shipGeneralCannonDamage.get().floatValue());
        }
    }

    public void hitParticles(){
        for (int i = 0; i < 300; ++i) {
            double d0 = this.random.nextGaussian() * 0.03D;
            double d1 = this.random.nextGaussian() * 0.03D;
            double d2 = this.random.nextGaussian() * 0.03D;
            double d3 = 20.0D;
            this.level().addParticle(ParticleTypes.POOF, this.getX(1.0D) - d0 * d3, this.getRandomY() - d1 * d3, this.getRandomZ(2.0D) - d2 * d3, d0, d1, d2);
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX(1.0D) - d0 * d3, this.getRandomY() - d1 * d3, this.getRandomZ(2.0D) - d2 * d3, d0, d1, d2);
        }
    }

    public void waterParticles(){
        for (int i = 0; i < 200; ++i) {
            double d0 = this.random.nextGaussian() * 0.03D;
            double d1 = this.random.nextGaussian() * 0.03D;
            double d2 = this.random.nextGaussian() * 0.03D;
            double d3 = 20.0D;
            this.level().addParticle(ParticleTypes.POOF, this.getX(1.0D) - d0 * d3, this.getRandomY() - d1 * d3  + i * 0.012, this.getRandomZ(2.0D) - d2 * d3, d0, d1, d2);
        }
    }


    public void shootParticles(){
        for (int i = 0; i < 100; ++i) {
            double d0 = this.random.nextGaussian() * 0.03D;
            double d1 = this.random.nextGaussian() * 0.03D;
            double d2 = this.random.nextGaussian() * 0.03D;
            double d3 = 20.0D;
            this.level().addParticle(ParticleTypes.POOF, this.getX(1.0D) - d0 * d3, this.getRandomY() - d1 * d3, this.getRandomZ(2.0D) - d2 * d3, d0, d1, d2);
        }

        for (int i = 0; i < 50; ++i) {
            double d00 = this.random.nextGaussian() * 0.03D;
            double d11 = this.random.nextGaussian() * 0.03D;
            double d22 = this.random.nextGaussian() * 0.03D;
            double d44 = 10.0D;
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX(1.0D) - d00 * d44, this.getRandomY() - d11 * d44, this.getRandomZ(2.0D) - d22 * d44, d00, d11, d22);
            this.level().addParticle(ParticleTypes.FLAME, this.getX(1.0D) - d00 * d44, this.getRandomY() - d11 * d44, this.getRandomZ(2.0D) - d22 * d44, 0, 0, 0);
        }
    }

    public void tailParticles(){
        for (int i = 0; i < 100; ++i) {
            this.level().addParticle(ParticleTypes.POOF, this.getX(), this.getY(), this.getZ() , 0, 0, 0);
        }

        for (int i = 0; i < 50; ++i) {
            this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource p_70097_1_, float p_70097_2_) {
        return false;
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected @NotNull ParticleOptions getTrailParticle() {
        return ParticleTypes.SMOKE;
    }

}
