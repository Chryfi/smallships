package com.talhanation.smallships.client.renderer.entity;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import com.talhanation.smallships.client.model.CannonModel;
import com.talhanation.smallships.client.model.ShipModel;
import com.talhanation.smallships.client.model.sail.BriggSailModel;
import com.talhanation.smallships.client.model.sail.CogSailModel;
import com.talhanation.smallships.client.model.sail.SailModel;
import com.talhanation.smallships.world.entity.ship.*;
import com.talhanation.smallships.world.entity.ship.abilities.Bannerable;
import com.talhanation.smallships.world.entity.ship.abilities.Cannonable;
import com.talhanation.smallships.world.entity.ship.abilities.Paddleable;
import com.talhanation.smallships.world.entity.ship.abilities.Sailable;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class ShipRenderer<T extends Ship> extends EntityRenderer<T> {
    protected final Map<Boat.Type, Pair<ResourceLocation, ShipModel<T>>> boatResources;

    public ShipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8F;

        this.boatResources = Stream.of(Boat.Type.values()).collect(ImmutableMap.toImmutableMap(
                (type) -> type,
                (type) -> Pair.of(
                        this.getTextureLocation(type),
                        this.createBoatModel(context, type))));
    }

    protected abstract ShipModel<T> createBoatModel(EntityRendererProvider.Context context, Boat.Type type);

    protected abstract ResourceLocation getTextureLocation(Boat.Type type);

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull T shipEntity) {
        return this.boatResources.get(shipEntity.getBoatType()).getFirst();
    }

    @Override
    public void render(T shipEntity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int packedLight) {
        Attributes shipAttributes = shipEntity.getAttributes();
        float h = ((float) shipEntity.getHurtTime() - partialTicks) / ((shipAttributes.maxHealth * shipEntity.getBbWidth()) / 40.0F);
        float j = shipEntity.getDamage() - partialTicks;
        if (j < 0.0F) {
            j = 0.0F;
        } else {
            if (j > shipAttributes.maxHealth * 0.5F) {
                shipEntity.getLevel().addParticle(ParticleTypes.LARGE_SMOKE, shipEntity.getRandomX(0.5D), shipEntity.getY() + 1.0D, shipEntity.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
            }
        }

        if (h > 0.0F) {
            poseStack.mulPose(Vector3f.XP.rotationDegrees(Mth.sin(h) * h * j / 10.0F * (float) shipEntity.getHurtDir()));
        }

        float k = shipEntity.getBubbleAngle(partialTicks);
        if (!Mth.equal(k, 0.0F)) {
            poseStack.mulPose(new Quaternion(new Vector3f(1.0F, 0.0F, 1.0F), k, true));
        }

        float l = shipEntity.getWaveAngle(partialTicks);
        if (!Mth.equal(l, 0.0F)) {
            poseStack.mulPose(Vector3f.XP.rotationDegrees(l));
        }

        Pair<ResourceLocation, ShipModel<T>> pair = this.boatResources.get(shipEntity.getBoatType());
        ResourceLocation resourceLocation = pair.getFirst();
        ShipModel<T> shipModel = pair.getSecond();
        poseStack.scale(-1.3F, -1.3F, 1.3F);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(90.0F + 180.0F));
        shipModel.setupAnim(shipEntity, partialTicks, 0.0F, -0.1F, 0.0F, 0.0F);

        if (shipEntity instanceof Cannonable cannonShipEntity) {
            renderCannon(cannonShipEntity, entityYaw, partialTicks, poseStack, multiBufferSource, packedLight);
        }
        if (shipEntity instanceof Bannerable bannerShipEntity) {
            renderBanner(bannerShipEntity, entityYaw, partialTicks, poseStack, multiBufferSource, packedLight);
        }
        if (shipEntity instanceof Paddleable paddleShipEntity) {
            renderPaddle(paddleShipEntity, entityYaw, partialTicks, poseStack, multiBufferSource, packedLight);
        }
        if (shipEntity instanceof Sailable sailShipEntity) {
            renderSail(sailShipEntity, entityYaw, partialTicks, poseStack, multiBufferSource, packedLight);
        }

        VertexConsumer vertexConsumer = multiBufferSource.getBuffer(shipModel.renderType(resourceLocation));
        shipModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
        super.render(shipEntity, entityYaw, partialTicks, poseStack, multiBufferSource, packedLight);
    }

    @SuppressWarnings("unused")
    private void renderCannon(Cannonable cannonShipEntity, float entityYaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int packedLight) {
        Cannonable.CannonPosition cannonPosition = cannonShipEntity.getCannonPosition();
        Consumer<Pair<Double, Float>> renderCannon = args -> {
            poseStack.pushPose();
            poseStack.mulPose(Vector3f.YP.rotationDegrees(cannonPosition.angle + args.getSecond()));
            poseStack.translate(args.getFirst(), cannonPosition.offsetY, cannonPosition.offsetZ);

            //scale
            poseStack.scale(0.75F, 0.75F, 0.75F);

            CannonModel cannonModel = new CannonModel();
            cannonModel.setupAnim(((Ship) cannonShipEntity), partialTicks, 0.0F, -0.1F, 0.0F, 0.0F);
            VertexConsumer vertexConsumer = multiBufferSource.getBuffer(cannonModel.renderType(cannonShipEntity.getTextureLocation()));
            cannonModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            poseStack.popPose();
        };

        for (int i = 0; i < cannonShipEntity.getCannonCountRight(); i++) {
            double offsetX = switch (i) {
                case 0 -> -1;
                case 1 -> 0.2;
                case 2 -> 1.5;
                default -> 0;
            };
            renderCannon.accept(new Pair<>(offsetX, 180.0F));
        }

        for (int i = 0; i < cannonShipEntity.getCannonCountLeft(); i++) {
            double offsetX = switch (i) {
                case 0 -> 1;
                case 1 -> -0.2;
                case 2 -> -1.5;
                default -> 0;
            };
            renderCannon.accept(new Pair<>(offsetX, 0.0F));
        }
    }


    private static final ModelPart bannerModel;
    static {
        ModelPart model = BannerRenderer.createBodyLayer().bakeRoot();
        model.getChild("pole").visible = false;
        model.getChild("bar").visible = false;
        bannerModel = model;
    }
    @SuppressWarnings("unused")
    private void renderBanner(Bannerable bannerShipEntity, float entityYaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int packedLight) {
        ItemStack item = bannerShipEntity.self().getData(Ship.BANNER);
        if (item.getItem() instanceof BannerItem bannerItem) {
            poseStack.pushPose();
            Bannerable.BannerPosition pos = bannerShipEntity.getBannerPosition();
            poseStack.mulPose(Vector3f.YP.rotationDegrees(pos.yp));
            poseStack.mulPose(Vector3f.ZP.rotationDegrees(pos.zp));
            poseStack.translate(pos.x, pos.y, pos.z);
            poseStack.scale(0.5F, 0.5F, 0.5F);

            float bannerWaveAngle = bannerShipEntity.getBannerWaveAngle(partialTicks);
            if (!Mth.equal(bannerWaveAngle, 0F)) poseStack.mulPose(Vector3f.XP.rotationDegrees(bannerWaveAngle));

            List<Pair<Holder<BannerPattern>, DyeColor>> patterns = BannerBlockEntity.createPatterns(bannerItem.getColor(), BannerBlockEntity.getItemPatterns(item));
            BannerRenderer.renderPatterns(poseStack, multiBufferSource, packedLight, OverlayTexture.NO_OVERLAY, bannerModel, ModelBakery.BANNER_BASE, true, patterns);
            poseStack.popPose();
        }
    }

    @SuppressWarnings("unused")
    private void renderPaddle(Paddleable paddleShipEntity, float entityYaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int packedLight) {
    }

    private static final Map<Class<? extends Ship>, SailModel> sailModels = new HashMap<>();
    static {
        sailModels.put(CogEntity.class, new CogSailModel());
        sailModels.put(BriggEntity.class, new BriggSailModel());
        sailModels.put(KhufuEntity.class, new CogSailModel());
    }
    @SuppressWarnings("unused")
    private void renderSail(Sailable sailShipEntity, float entityYaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, int packedLight) {
        SailModel sailModel = sailModels.get(sailShipEntity.getClass());
        sailModel.setupAnim(((Ship)sailShipEntity), partialTicks, 0.0F, -0.1F, 0.0F, 0.0F);
        VertexConsumer vertexConsumer = multiBufferSource.getBuffer(sailModel.renderType(SailModel.getSailColor(sailShipEntity.self().getData(Ship.SAIL_COLOR)).location));
        sailModel.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static String getNameFromType(Boat.Type type) {
        return type.getName().replace(":", "/");
    }
}
