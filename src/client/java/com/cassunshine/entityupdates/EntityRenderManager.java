package com.cassunshine.entityupdates;

import com.cassunshine.entityupdates.access.MatrixStackExtension;
import com.cassunshine.entityupdates.memory.SectionedBufferBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.*;
import org.lwjgl.glfw.GLFW;

import java.lang.Math;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityRenderManager {

    public final int MAX_UPDATES_PER_FRAME = 1;

    private final CachedVertexConsumerProvider cvcp = new CachedVertexConsumerProvider();

    private EntityRenderDispatcher dispatcher;

    private Vec3d centralPos = new Vec3d(0, 0, 0);

    public void renderStart(WorldRenderer worldRenderer, EntityRenderDispatcher entityRenderDispatcher) {
        dispatcher = entityRenderDispatcher;
    }

    public void renderBatches(Camera camera, MatrixStack matrixStack) {

        centralPos = new Vec3d(0, 0, 0);
        Vec3d delta = camera.getPos().subtract(centralPos);

        var stack = RenderSystem.getModelViewStack();

        matrixStack.push();
        stack.push();

        DiffuseLighting.enableForLevel(stack.peek().getPositionMatrix());

        stack.multiplyPositionMatrix(matrixStack.peek().getPositionMatrix());
        stack.translate(-delta.x, -delta.y, -delta.z);

        RenderSystem.applyModelViewMatrix();

        cvcp.drawBuffers();
        cvcp.cleanup();

        stack.pop();
        matrixStack.pop();
    }

    public void renderEntity(Entity entity, float tickDelta, MatrixStack matrices) {
        cvcp.setEntity(entity);
        cvcp.setupEntity();

        //Write entity data as if it has no transforms (aside from being relative to the central pos)
        matrices.push();
        matrices.loadIdentity();

        //Render entity 'normally'
        dispatcher.render(
                entity,
                MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()),
                MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()),
                MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()),
                MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()),
                tickDelta, matrices, cvcp,
                dispatcher.getLight(entity, tickDelta)
        );
        matrices.pop();
    }

    public void clearEntity(Entity entity) {
        cvcp.remove(entity);
    }

    private class CachedVertexConsumerProvider implements VertexConsumerProvider {

        private List<SectionedBufferBuilder> bufferList = new ArrayList<>();
        private Map<RenderLayer, SectionedBufferBuilder> buffers = new HashMap<>();
        private Map<Entity, EntityEntry> entityEntries = new HashMap<>();

        private Entity currentEntity;
        private EntityEntry currentEntry;

        public void setEntity(Entity e) {
            currentEntity = e;
            currentEntry = entityEntries.computeIfAbsent(e, EntityEntry::new);
        }

        private void setupEntity() {

            currentEntry.lastRenderTime = GLFW.glfwGetTime();

            //Reset each layer in this entity, now that we're going to render it.
            for (SectionedBufferBuilder.SectionVertexConsumer value : currentEntry.layers.values()) {
                value.reset();
            }
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return currentEntry.getConsumer(layer);
        }

        private SectionedBufferBuilder generateSectionedBuffer(RenderLayer layer) {
            var newBuffer = new SectionedBufferBuilder(layer);
            bufferList.add(newBuffer);
            return newBuffer;
        }

        public void drawBuffers() {
            //Sort, keeping translucent last.
            bufferList.sort((a, b) -> Boolean.compare(a.isTranslucent, b.isTranslucent));

            for (SectionedBufferBuilder builder : bufferList) {
                builder.uploadAndDrawSections();
            }
        }

        public void cleanup() {
            ArrayList<Entity> _keys = new ArrayList<>();
            _keys.addAll(entityEntries.keySet());

            for (Entity entity : _keys) {
                if (entity.isRemoved()) {
                    var entry = entityEntries.get(entity);
                    entityEntries.remove(entity);

                    entry.close();
                }
            }
        }

        public void remove(Entity entity) {
            var entry = entityEntries.remove(entity);
            if (entry != null)
                entry.close();
        }

        private class EntityEntry implements AutoCloseable {
            public final Entity entity;
            public double lastRenderTime;

            public final Map<RenderLayer, SectionedBufferBuilder.SectionVertexConsumer> layers = new HashMap<>();

            private EntityEntry(Entity entity) {
                this.entity = entity;
            }

            public VertexConsumer getConsumer(RenderLayer layer) {

                var sectionConsumer = layers.computeIfAbsent(layer, (l) -> {
                    var buffer = buffers.computeIfAbsent(l, (s) -> generateSectionedBuffer(layer));
                    return buffer.getSectionConsumer();
                });

                return sectionConsumer;
            }

            @Override
            public void close() {
                for (SectionedBufferBuilder.SectionVertexConsumer value : layers.values())
                    value.close();
            }
        }
    }
}