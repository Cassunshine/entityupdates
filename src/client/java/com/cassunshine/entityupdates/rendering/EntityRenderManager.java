package com.cassunshine.entityupdates.rendering;

import com.cassunshine.entityupdates.EntityUpdatesClient;
import com.cassunshine.entityupdates.access.RenderLayerAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.GlfwUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class EntityRenderManager {

    public static EntityRenderManager instance;

    private final Random random = new Random();
    private final CustomVertexConsumerProvider provider = new CustomVertexConsumerProvider();

    private int rendersThisFrame = 0;

    private int updateNumber = 0;
    private boolean shouldIncUpdate = false;

    // STATE //

    /**
     * This position is the location around which entity positions are oriented.
     * <p>
     * Entity positions in vanilla are centered around the camera, to prevent floating point precision issues.
     * For EU, we can't do that, because of infrequent updates, so we have to change them to be centered around a different position.
     */
    private Vec3d entityRootPos = new Vec3d(0, 0, 0);

    // REFERENCES //

    private final Map<Entity, EntityRenderStatus> statuses = new HashMap<>();

    private Camera camera;
    private WorldRenderer worldRenderer;
    private EntityRenderDispatcher dispatcher;
    private VertexConsumerProvider defaultProvider;

    public Entity currentEntity;

    private double startTime;

    private World lastWorld;


    public void renderStart(WorldRenderer worldRenderer, Camera camera, EntityRenderDispatcher entityRenderDispatcher, VertexConsumerProvider defaultProvider) {

        if (MinecraftClient.getInstance().world != lastWorld) {
            lastWorld = MinecraftClient.getInstance().world;
            provider.clearAll();
        }

        this.camera = camera;
        this.worldRenderer = worldRenderer;
        this.dispatcher = entityRenderDispatcher;
        this.defaultProvider = defaultProvider;

        startTime = GlfwUtil.getTime();

        instance = this;

        //Leftover from last frame.
        if (shouldIncUpdate) {
            updateNumber++;
        }

        rendersThisFrame = 0;
        shouldIncUpdate = true;
    }

    /**
     * Queues an entity to be rendered.
     */
    public void renderEntity(Entity entity, float tickDelta, MatrixStack matrices) {

        //Write entity data as if it has no transforms (aside from being relative to the central pos)
        matrices.push();
        matrices.loadIdentity();
        currentEntity = entity;

        var status = statuses.computeIfAbsent(currentEntity, EntityRenderStatus::new);

        if (status.shouldRender())
            status.render(tickDelta, matrices);

        matrices.pop();
    }

    public void removeEntity(Entity entity) {
        statuses.remove(entity);
        provider.removeEntity(entity);
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

        provider.sortedRenderLayers.sort((a, b) -> Boolean.compare(a.translucent, b.translucent));
        for (RenderLayerData value : provider.sortedRenderLayers)
            value.render();

        if (defaultProvider instanceof VertexConsumerProvider.Immediate immediate)
            immediate.drawCurrentLayer();

        stack.pop();
        matrixStack.pop();

        RenderSystem.applyModelViewMatrix();
    }

    private class EntityRenderStatus {
        public final Entity target;

        /**
         * The last time this entity was rendered.
         */
        public double lastRenderTime;

        public int entityUpdate;

        private EntityRenderStatus(Entity target) {
            this.target = target;

            lastRenderTime = 0;
        }

        public boolean shouldRender() {

            //If an entity has already rendered this update, it shouldn't render, no matter what.
            if (entityUpdate == updateNumber)
                return false;

            //If we've rendered too many entities, don't increment the update counter, since we didn't all get a chance to update.
            if (rendersThisFrame >= EntityUpdatesClient.maxRenders) {
                shouldIncUpdate = false;
                return false;
            }

            //Entity proceeds to update regardless of if they actually render or not. They had their chance this update!
            entityUpdate = updateNumber;

            double renderRate = (Math.floor(target.distanceTo(camera.getFocusedEntity()) / 64)) / 4;

            if (!(startTime - lastRenderTime > renderRate) && renderRate != 0)
                return false;

            return true;
        }

        public void render(float tickDelta, MatrixStack stack) {
            lastRenderTime = startTime - random.nextDouble();

            provider.resetEntity(target);

            //Render entity 'normally'
            dispatcher.render(
                    target,
                    MathHelper.lerp(tickDelta, target.lastRenderX, target.getX()),
                    MathHelper.lerp(tickDelta, target.lastRenderY, target.getY()),
                    MathHelper.lerp(tickDelta, target.lastRenderZ, target.getZ()),
                    MathHelper.lerp(tickDelta, target.prevYaw, target.getYaw()),
                    tickDelta, stack, provider,
                    dispatcher.getLight(target, tickDelta)
            );

            rendersThisFrame++;
        }
    }

    private class CustomVertexConsumerProvider implements VertexConsumerProvider {

        private HashMap<RenderLayerIdentifier, RenderLayerData> renderLayerDataCache = new HashMap<>();
        private List<RenderLayerData> sortedRenderLayers = new ArrayList<>();

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            //Fallback if this render layer isn't supported. This should make mods with custom render layers happier.
            if (!(layer instanceof RenderLayerAccess access))
                return defaultProvider.getBuffer(layer);

            var identifier = access.getRenderLayerIdentifier();

            var dataCache = renderLayerDataCache.computeIfAbsent(identifier, this::generateData);
            dataCache.lastRenderLayer = layer;

            return dataCache.getConsumerForEntity(currentEntity);
        }

        private RenderLayerData generateData(RenderLayerIdentifier identifier) {
            var newLayer = new RenderLayerData(identifier);
            sortedRenderLayers.add(newLayer);
            return newLayer;
        }

        public void resetEntity(Entity e){
            for (RenderLayerData data : renderLayerDataCache.values())
                data.resetEntity(e);
        }

        public void removeEntity(Entity entity) {
            for (RenderLayerData data : renderLayerDataCache.values())
                data.removeEntity(entity);
        }

        public void clearAll() {
            for (RenderLayerData data : renderLayerDataCache.values())
                data.clearAll();
        }
    }
}