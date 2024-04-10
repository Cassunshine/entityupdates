package com.cassunshine.entityupdates.rendering;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;

import java.util.Optional;

public record RenderLayerIdentifier(Optional<Identifier> texture, boolean translucent, Object shaderSupplier, VertexFormat format, VertexFormat.DrawMode drawMode) {
}
