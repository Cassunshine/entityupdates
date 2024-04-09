package com.cassunshine.entityupdates;

import com.cassunshine.entityupdates.memory.SectionedBufferBuilder;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityRenderManager {

    private final List<Entity> rendersThisFrame = new ArrayList<>();
    private final CachedVertexConsumerProvider cvcp = new CachedVertexConsumerProvider();


    private EntityRenderDispatcher dispatcher;
    private VertexConsumerProvider defaultProvider;


    public void renderStart(WorldRenderer worldRenderer, EntityRenderDispatcher entityRenderDispatcher) {
        dispatcher = entityRenderDispatcher;
    }

    public void renderBatches() {
        cvcp.drawBuffers();
    }

    public void renderEntity(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        defaultProvider = vertexConsumers;
        cvcp.setEntity(entity);

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

        private class EntityEntry {
            public final Entity entity;

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
        }
    }
}