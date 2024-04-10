package com.cassunshine.entityupdates.mixin.client;

import com.cassunshine.entityupdates.access.RenderLayerAccess;
import com.cassunshine.entityupdates.access.ShaderProgramAccess;
import com.cassunshine.entityupdates.access.TextureBaseAccess;
import com.cassunshine.entityupdates.rendering.RenderLayerIdentifier;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;


@Mixin(RenderLayer.MultiPhase.class)
public class MultiPhaseMixin extends RenderLayer implements RenderLayerAccess {

    @Shadow
    @Final
    private RenderLayer.MultiPhaseParameters phases;

    private RenderLayerIdentifier entityupdates_identifier;

    public MultiPhaseMixin(String name, VertexFormat vertexFormat, VertexFormat.DrawMode drawMode, int expectedBufferSize, boolean hasCrumbling, boolean translucent, Runnable startAction, Runnable endAction) {
        super(name, vertexFormat, drawMode, expectedBufferSize, hasCrumbling, translucent, startAction, endAction);
    }

    public RenderLayerIdentifier getRenderLayerIdentifier() {

        if (entityupdates_identifier == null) {
            entityupdates_identifier = new RenderLayerIdentifier(
                    ((TextureBaseAccess) phases.texture).getIdExposed(),
                    phases.transparency != RenderPhase.NO_TRANSPARENCY,
                    ((ShaderProgramAccess) phases.program).getSupplierExposed(),
                    getVertexFormat(),
                    getDrawMode()
            );
        }

        return entityupdates_identifier;
    }
}
