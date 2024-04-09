package com.cassunshine.entityupdates.mixin.client;

import com.cassunshine.entityupdates.access.RenderLayerAccess;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderLayer.class)
public class RenderLayerMixin implements RenderLayerAccess {
    @Shadow
    @Final
    private boolean translucent;

    @Override
    public boolean isTranslucent() {
        return translucent;
    }
}
