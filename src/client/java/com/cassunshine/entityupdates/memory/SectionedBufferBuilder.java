package com.cassunshine.entityupdates.memory;

import com.cassunshine.entityupdates.access.RenderLayerAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializerRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.Window;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class SectionedBufferBuilder implements AutoCloseable {

    public final RenderLayer layer;
    public final VertexFormat vertexFormat;
    public final VertexFormatDescription vertexDescription;
    public final boolean isTranslucent;

    private final CustomDrawObject drawObject;

    private final SectionAllocator allocator;

    public SectionedBufferBuilder(RenderLayer layer) {
        this.layer = layer;
        vertexFormat = layer.getVertexFormat();
        vertexDescription = VertexFormatRegistry.instance().get(layer.getVertexFormat());
        this.isTranslucent = ((RenderLayerAccess) layer).isTranslucent();

        drawObject = new CustomDrawObject(vertexFormat, layer.getDrawMode());

        allocator = new SectionAllocator(vertexDescription.stride() * 1024 * 24 * 10, vertexDescription.stride());
    }

    public void uploadAndDrawSections() {
        try {
            allocator.merge();
            uploadChanges();
            drawBatches();

            //allocator.freeAll();
            //allocator.buffer.clear();
        } catch (Exception e) {
            //Ignore
            System.out.println(e);
        }
    }

    private void uploadChanges() {
        //Re-sizes re-upload the entire buffer.
        if (drawObject.ensureSize(allocator.buffer))
            return;

        //drawObject.uploadSection(0, allocator.buffer);

        for (Section section : allocator.batchBy((s) -> {
            var result = s.isDirty;
            s.isDirty = false;
            return result;
        })) {
            drawObject.uploadSection(section.offset, allocator.buffer.slice(section.offset, section.length));
        }
    }

    private void drawBatches() {
        layer.startDrawing();
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
        if (shader.lineWidth != null && (layer.getDrawMode() == VertexFormat.DrawMode.LINES || layer.getDrawMode() == VertexFormat.DrawMode.LINE_STRIP)) {
            shader.lineWidth.set(RenderSystem.getShaderLineWidth());
        }
        RenderSystem.setupShaderLights(shader);
        shader.bind();

        for (Section section : allocator.batchBy((s) -> s.isClaimed)) {
            drawObject.drawSection(section.offset, section.length);
        }

        //drawObject.drawAll();

        layer.endDrawing();
        shader.unbind();
    }

    public SectionVertexConsumer getSectionConsumer() {
        return new SectionVertexConsumer();
    }

    @Override
    public void close() throws Exception {
        drawObject.close();
    }

    public class SectionVertexConsumer implements VertexConsumer, VertexBufferWriter, AutoCloseable {

        @Nullable
        private Section section;

        private int position;

        public void reset() {
            position = 0;
            //MemoryUtil.memSet(section.getPtr(0), 0, section.length);
        }

        /**
         * Returns a pointer to the address of the section at the current position, then advances by the number of bytes passed in.
         */
        public long pushPtr(int bytes) {
            //Create or reallocate section.
            if (section == null) {
                section = allocator.allocate(bytes);
            } else if (position + bytes > section.length) {
                //Get a new section.
                var newSection = allocator.allocate(position + bytes);

                //Copy old section into new section.
                MemoryUtil.memCopy(section.getPtr(0), newSection.getPtr(0), section.length);

                allocator.free(section);
                //Update properties.
                section = newSection;
            }

            var result = section.getPtr(position);
            position += bytes;
            return result;
        }

        @Override
        public void push(MemoryStack stack, long src, int count, VertexFormatDescription format) {
            int byteCount = vertexDescription.stride() * count;
            var dst = pushPtr(byteCount);
            section.markDirty();

            if (format == vertexDescription) {
                MemoryIntrinsics.copyMemory(src, dst, byteCount);
            } else {
                //Slow copy (thanks jellysquid)
                VertexSerializerRegistry.instance()
                        .get(format, vertexDescription)
                        .serialize(src, dst, count);
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

        public void close() {
            if (section != null)
                allocator.free(section);
        }
    }
}
