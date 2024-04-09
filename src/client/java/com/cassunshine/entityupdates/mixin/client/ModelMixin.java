package com.cassunshine.entityupdates.mixin.client;

import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Mixin(Model.class)
public class ModelMixin {

    @Shadow @Final protected Function<Identifier, RenderLayer> layerFactory;
    private Map<Identifier, RenderLayer> renderLayerCache = new HashMap<>();


    @Inject(at = @At("HEAD"), cancellable = true, method = "getLayer")
    public final void getLayer(Identifier texture, CallbackInfoReturnable<RenderLayer> cir) {

    }

}
