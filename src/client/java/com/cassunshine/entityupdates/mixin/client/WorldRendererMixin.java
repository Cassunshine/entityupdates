package com.cassunshine.entityupdates.mixin.client;

import com.cassunshine.entityupdates.rendering.EntityRenderManager;
import com.cassunshine.entityupdates.EntityUpdatesClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;
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
    @Shadow
    private @Nullable ClientWorld world;
    @Shadow
    private Frustum frustum;
    @Shadow
    @Final
    private BufferBuilderStorage bufferBuilders;
    public EntityRenderManager entityupdates_entityRenderManager = new EntityRenderManager();

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getEntities()Ljava/lang/Iterable;"), method = "render")
    private void entityudpates_renderStart(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!EntityUpdatesClient.isEnabled)
            return;

        entityupdates_entityRenderManager.renderStart((WorldRenderer) (Object) this, camera, entityRenderDispatcher, bufferBuilders.getEntityVertexConsumers() /* Default to using immediate-mode rendering*/);

        var camPos = camera.getPos();
        var tickManager = world.getTickManager();
        float defaultedDelta = tickManager.shouldTick() ? tickDelta : 1.0f;

        for (Entity entity : world.getEntities()) {
            if (entity.age == 0) {
                entity.lastRenderX = entity.getX();
                entity.lastRenderY = entity.getY();
                entity.lastRenderZ = entity.getZ();
            }

            if (!entityRenderDispatcher.shouldRender(entity, frustum, camPos.x, camPos.y, camPos.z) || (entity == camera.getFocusedEntity() && !camera.isThirdPerson())) {

            } else {
                entityupdates_entityRenderManager.renderEntity(entity, tickManager.shouldSkipTick(entity) ? defaultedDelta : tickDelta, matrices);
            }
        }

        entityupdates_entityRenderManager.renderBatches(camera, matrices);
    }

    @Inject(at = @At("HEAD"), cancellable = true, method = "renderEntity")
    private void entityupdates_renderEntity(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (!EntityUpdatesClient.isEnabled)
            return;

        //Never call original entity rendering.
        ci.cancel();
    }
}
