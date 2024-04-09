package com.cassunshine.entityupdates;

import com.cassunshine.entityupdates.memory.SectionedBufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityRenderManager {

    public final int MAX_UPDATES_PER_FRAME = 1;

    private final CachedVertexConsumerProvider cvcp = new CachedVertexConsumerProvider();

    private EntityRenderDispatcher dispatcher;
    private VertexConsumerProvider defaultProvider;

    private Vec3d entityCenterPos = new Vec3d(0, 0, 0);

    public void renderStart(WorldRenderer worldRenderer, EntityRenderDispatcher entityRenderDispatcher) {
        dispatcher = entityRenderDispatcher;
    }

    public void renderBatches() {
        cvcp.drawBuffers();

        cvcp.cleanup();
    }

    public void renderEntity(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        defaultProvider = vertexConsumers;

        cvcp.setEntity(entity);

        cvcp.setupEntity();

        //Render entity 'normally'
        dispatcher.render(
                entity,
                MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - cameraX,
                MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - cameraY,
                MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - cameraZ,
                MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()),
                tickDelta, matrices, cvcp,
                dispatcher.getLight(entity, tickDelta)
        );
    }

    private class CachedVertexConsumerProvider implements VertexConsumerProvider {

        private List<SectionedBufferBuilder> bufferList = new ArrayList<>();
        private Map<String, SectionedBufferBuilder> buffers = new HashMap<>();
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

        private class EntityEntry implements AutoCloseable {
            public final Entity entity;
            public double lastRenderTime;

            public final Map<String, SectionedBufferBuilder.SectionVertexConsumer> layers = new HashMap<>();

            private EntityEntry(Entity entity) {
                this.entity = entity;
            }

            public VertexConsumer getConsumer(RenderLayer layer) {
                var sectionConsumer = layers.computeIfAbsent(layer.toString(), (l) -> {
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