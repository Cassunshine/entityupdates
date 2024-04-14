package com.cassunshine.entityupdates.memory;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatRegistry;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;

public class CustomDrawObject implements AutoCloseable {

    private int vertexBufferId;
    private int vertexArrayId;

    private final VertexFormat format;
    private final VertexFormatDescription description;
    private final VertexFormat.DrawMode drawMode;

    private RenderSystem.ShapeIndexBuffer indexBuffer;

    private int storedBytes = 0;
    private int vertexCount = 0;

    public CustomDrawObject(VertexFormat format, VertexFormat.DrawMode mode) {
        this.format = format;
        this.drawMode = mode;
        this.description = VertexFormatRegistry.INSTANCE.get(format);
        vertexBufferId = GlStateManager._glGenBuffers();
        vertexArrayId = GlStateManager._glGenVertexArrays();
    }

    /**
     * Re-sizes the GPU buffer to ensure it fits the required data.
     *
     * @return True if the buffer was resized (and re-uploaded), false otherwise.
     */
    public boolean ensureSize(ByteBuffer buffer) {
        if (storedBytes >= buffer.capacity())
            return false;

        GlStateManager._glBindVertexArray(vertexArrayId);

        //Bind and re-upload
        GlStateManager._glBindBuffer(GlConst.GL_ARRAY_BUFFER, this.vertexBufferId);
        format.setupState();
        RenderSystem.glBufferData(GlConst.GL_ARRAY_BUFFER, buffer, GlConst.GL_DYNAMIC_DRAW);

        storedBytes = buffer.capacity();
        vertexCount = storedBytes / description.stride();

        indexBuffer = RenderSystem.getSequentialBuffer(drawMode);
        indexBuffer.bindAndGrow(drawMode.getIndexCount(vertexCount));

        GlStateManager._glBindVertexArray(0);
        return true;
    }

    public void uploadSection(int offset, ByteBuffer buffer) {
        GlStateManager._glBindBuffer(GlConst.GL_ARRAY_BUFFER, this.vertexBufferId);
        GL20.glBufferSubData(GlConst.GL_ARRAY_BUFFER, offset, buffer);
    }

    @Override
    public void close() throws Exception {

    }

    public void drawAll() {
        drawSection(0, storedBytes);
    }

    public void drawSection(int start, int bytes) {
        GlStateManager._glBindVertexArray(vertexArrayId);

        int drawnVertices = bytes / description.stride();
        int drawnIndexes = drawMode.getIndexCount(drawnVertices);

        int skippedVertices = start / description.stride();
        int skippedIndecies = drawMode.getIndexCount(skippedVertices);

        GlStateManager._drawElements(drawMode.glMode, drawnIndexes, indexBuffer.getIndexType().glType, (long) skippedIndecies * indexBuffer.getIndexType().size);
        GlStateManager._glBindVertexArray(0);
    }
}
