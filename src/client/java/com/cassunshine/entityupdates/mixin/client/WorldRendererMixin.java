package com.cassunshine.entityupdates.mixin.client;

import com.cassunshine.entityupdates.EntityRenderManager;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;
    public EntityRenderManager entityupdates_entityRenderManager = new EntityRenderManager();

    @Inject(at = @At("HEAD"), method = "render")
    private void entityudpates_renderStart(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        entityupdates_entityRenderManager.renderStart((WorldRenderer) (Object) this, entityRenderDispatcher);
    }

    @Inject(at = @At("RETURN"), method = "render")
    private void entityudpates_renderEnd(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        entityupdates_entityRenderManager.renderBatches();
    }

    @Inject(at = @At("HEAD"), cancellable = true, method = "renderEntity")
    private void entityupdates_renderEntity(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        //Never call original entity rendering.
        ci.cancel();

        entityupdates_entityRenderManager.renderEntity(entity, cameraX, cameraY, cameraZ, tickDelta, matrices, vertexConsumers);
    }
}
