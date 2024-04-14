package com.cassunshine.entityupdates.rendering;

import com.cassunshine.entityupdates.memory.ByteBufferAllocator;
import com.cassunshine.entityupdates.memory.CustomDrawObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializerRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores all the data within a given
 */
public class RenderLayerData {

    public final RenderLayerIdentifier renderLayerIdentifier;
    public final VertexFormat vertexFormat;
    public final VertexFormatDescription vertexDescription;

    public final CustomDrawObject drawObject;
    public final ByteBufferAllocator allocator;

    private ArrayList<Entity> removeCache = new ArrayList<>();
    private Map<Entity, EntityVertexConsumer> entityVertexConsumers = new HashMap<>();

    /**
     * The last render layer that matched this render layer data.
     */
    public RenderLayer lastRenderLayer;


    public RenderLayerData(RenderLayerIdentifier identifier) {
        this.renderLayerIdentifier = identifier;
        this.vertexFormat = identifier.format();
        this.vertexDescription = VertexFormatRegistry.instance().get(vertexFormat);

        this.drawObject = new CustomDrawObject(vertexFormat, identifier.drawMode());
        this.allocator = new ByteBufferAllocator(vertexDescription.stride(), 64);
    }

    public void setLastRenderLayer(RenderLayer layer) {
        this.lastRenderLayer = layer;
    }

    public EntityVertexConsumer getConsumerForEntity(Entity e) {
        var result = entityVertexConsumers.computeIfAbsent(e, (entity) -> new EntityVertexConsumer());
        return result;
    }

    public void removeEntity(Entity entity) {
        var removed = entityVertexConsumers.remove(entity);

        if (removed != null)
            removed.clear();
    }

    public void clearAll() {
        for (Map.Entry<Entity, EntityVertexConsumer> entry : entityVertexConsumers.entrySet()) entry.getValue().clear();

        entityVertexConsumers.clear();
        allocator.mergeFree();
    }

    public void render() {
        allocator.mergeFree();

        uploadChanges();
        drawBatches();
    }

    public void uploadChanges() {
        //Re-sizes re-upload the entire buffer.
        if (drawObject.ensureSize(allocator.memoryBuffer))
            return;

        for (var section : allocator.batchBy((s) -> {
            var result = s.dirty;
            s.dirty = false;
            return result;
        })) {
            drawObject.uploadSection(section.offset, section.getSlice());
        }
    }

    public void drawBatches() {
        lastRenderLayer.startDrawing();
        var shader = RenderSystem.getShader();

        if (shader == null)
            return;

        for (int m = 0; m < 12; ++m) {
            int n = RenderSystem.getShaderTexture(m);
            shader.addSampler("Sampler" + m, n);
        }

        if (shader.modelViewMat != null) {
            shader.modelViewMat.set(RenderSystem.getModelViewMatrix());
        }
        if (shader.projectionMat != null) {
            shader.projectionMat.set(RenderSystem.getProjectionMatrix());
        }
        if (shader.viewRotationMat != null) {
            shader.viewRotationMat.set(RenderSystem.getInverseViewRotationMatrix());
        }
        if (shader.colorModulator != null) {
            shader.colorModulator.set(RenderSystem.getShaderColor());
        }
        if (shader.glintAlpha != null) {
            shader.glintAlpha.set(RenderSystem.getShaderGlintAlpha());
        }
        if (shader.fogStart != null) {
            shader.fogStart.set(RenderSystem.getShaderFogStart());
        }
        if (shader.fogEnd != null) {
            shader.fogEnd.set(RenderSystem.getShaderFogEnd());
        }
        if (shader.fogColor != null) {
            shader.fogColor.set(RenderSystem.getShaderFogColor());
        }
        if (shader.fogShape != null) {
            shader.fogShape.set(RenderSystem.getShaderFogShape().getId());
        }
        if (shader.textureMat != null) {
            shader.textureMat.set(RenderSystem.getTextureMatrix());
        }
        if (shader.gameTime != null) {
            shader.gameTime.set(RenderSystem.getShaderGameTime());
        }
        if (shader.screenSize != null) {
            Window window = MinecraftClient.getInstance().getWindow();
            shader.screenSize.set((float) window.getFramebufferWidth(), (float) window.getFramebufferHeight());
        }
        if (shader.lineWidth != null && (lastRenderLayer.getDrawMode() == VertexFormat.DrawMode.LINES || lastRenderLayer.getDrawMode() == VertexFormat.DrawMode.LINE_STRIP)) {
            shader.lineWidth.set(RenderSystem.getShaderLineWidth());
        }
        RenderSystem.setupShaderLights(shader);
        shader.bind();

        for (var section : allocator.batchBy((s) -> s.claimed))
            drawObject.drawSection(section.offset, section.length);

        lastRenderLayer.endDrawing();
        shader.unbind();
    }

    public void close() {
        allocator.close();
    }

    public void resetEntity(Entity entity) {
        var consumer = entityVertexConsumers.get(entity);
        if (consumer == null)
            return;
        consumer.reset();
    }


    public class EntityVertexConsumer implements VertexConsumer, VertexBufferWriter {

        private ByteBufferAllocator.Section section;
        private int position;

        public void reset() {
            position = 0;
            if (section != null) {
                allocator.freeSlice(section);
                section = null;
            }
        }

        public void clear() {
            if (section != null)
                allocator.freeSlice(section);
        }

        public long pushPtr(int count) throws Exception {
            if (section == null) {
                section = allocator.allocateSlice(count);
            } else if (section.blocks < position + count) {
                var newSection = allocator.allocateSlice(position + count);

                //Copy old to new.
                MemoryUtil.memCopy(section.getPtr(0), newSection.getPtr(0), section.length);

                allocator.freeSlice(section);
                section = newSection;
            }

            var ptr = section.getPtr(position);
            position += count;
            return ptr;
        }

        @Override
        public void push(MemoryStack stack, long ptr, int count, VertexFormatDescription format) {
            try {
                var dst = pushPtr(count);

                if (format == vertexDescription) {
                    int dstBytes = count * vertexDescription.stride();
                    MemoryUtil.memCopy(ptr, dst, dstBytes);

                    section.dirty = true;
                } else {
                    //Slow copy (thanks jellysquid)
                    VertexSerializerRegistry.instance()
                            .get(format, vertexDescription)
                            .serialize(ptr, dst, count);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void next() {

        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {

        }

        @Override
        public void unfixColor() {

        }
    }
}