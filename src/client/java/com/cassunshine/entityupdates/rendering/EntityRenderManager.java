package com.cassunshine.entityupdates.rendering;

import com.cassunshine.entityupdates.access.RenderLayerAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;

public class EntityRenderManager {

    private final CustomVertexConsumerProvider provider = new CustomVertexConsumerProvider();

    // STATE //

    /**
     * This position is the location around which entity positions are oriented.
     * <p>
     * Entity positions in vanilla are centered around the camera, to prevent floating point precision issues.
     * For EU, we can't do that, because of infrequent updates, so we have to change them to be centered around a different position.
     */
    private Vec3d entityRootPos = new Vec3d(0, 0, 0);

    // REFERENCES //

    private Camera camera;
    private WorldRenderer worldRenderer;
    private EntityRenderDispatcher dispatcher;
    private VertexConsumerProvider defaultProvider;

    public Entity currentEntity;


    public void renderStart(WorldRenderer worldRenderer, Camera camera, EntityRenderDispatcher entityRenderDispatcher, VertexConsumerProvider defaultProvider) {
        this.camera = camera;
        this.worldRenderer = worldRenderer;
        this.dispatcher = entityRenderDispatcher;
        this.defaultProvider = defaultProvider;


        provider.cleanRemoved();
    }

    /**
     * Queues an entity to be rendered.
     */
    public void renderEntity(Entity entity, float tickDelta, MatrixStack matrices) {

        //Write entity data as if it has no transforms (aside from being relative to the central pos)
        matrices.push();
        matrices.loadIdentity();
        currentEntity = entity;

        //Render entity 'normally'
        dispatcher.render(
                entity,
                MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()),
                MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()),
                MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()),
                MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()),
                tickDelta, matrices, provider,
                dispatcher.getLight(entity, tickDelta)
        );
        matrices.pop();
    }

    /**
     * Takes all the batched data from the rendering process and does the actual draw calls for it, ensuring the render state is correct.
     */
    public void renderBatches(Camera camera, MatrixStack matrixStack) {

        //Difference between camera and entity root pos.
        //We move all the model data by this much, basically a normal view matrix.
        Vec3d delta = entityRootPos.subtract(camera.getPos());

        var stack = RenderSystem.getModelViewStack();

        matrixStack.push();
        stack.push();

        DiffuseLighting.enableForLevel(stack.peek().getPositionMatrix());

        stack.multiplyPositionMatrix(matrixStack.peek().getPositionMatrix());
        stack.translate(delta.x, delta.y, delta.z);

        RenderSystem.applyModelViewMatrix();

        for (RenderLayerData value : provider.renderLayerDataCache.values())
            value.render();

        stack.pop();
        matrixStack.pop();

        RenderSystem.applyModelViewMatrix();
    }

    private class CustomVertexConsumerProvider implements VertexConsumerProvider {

        private HashMap<RenderLayerIdentifier, RenderLayerData> renderLayerDataCache = new HashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            //Fallback if this render layer isn't supported. This should make mods with custom render layers happier.
            if (!(layer instanceof RenderLayerAccess access) || layer.getDrawMode() != VertexFormat.DrawMode.QUADS)
                return defaultProvider.getBuffer(layer);

            var identifier = access.getRenderLayerIdentifier();

            //If there's any issue getting the identifier, just fallback to default instead.
            if (identifier == null)
                return defaultProvider.getBuffer(layer);

            var dataCache = renderLayerDataCache.computeIfAbsent(identifier, this::generateData);
            dataCache.lastRenderLayer = layer;

            return dataCache.getConsumerForEntity(currentEntity);
        }

        private RenderLayerData generateData(RenderLayerIdentifier identifier) {
            return new RenderLayerData(identifier);
        }

        public void cleanRemoved() {
            for (RenderLayerData value : renderLayerDataCache.values())
                value.cleanRemoved();
        }
    }
}