package com.cassunshine.entityupdates.rendering;

import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.function.Supplier;

public record RenderLayerIdentifier(VertexFormat format, VertexFormat.DrawMode drawMode,
                                    Optional<Identifier> texture,
                                    RenderPhase.Texturing texturing,
                                    Object shaderSupplier,
                                    RenderPhase.Transparency transparency,
                                    RenderPhase.DepthTest depthTest,
                                    RenderPhase.Cull cull,
                                    RenderPhase.WriteMaskState writeMaskState,
                                    RenderPhase.Lightmap lightmap,
                                    RenderPhase.Target target,
                                    RenderPhase.Layering layering,
                                    RenderPhase.LineWidth lineWidth) {
}
