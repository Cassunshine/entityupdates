package com.cassunshine.entityupdates.mixin.client;

import com.cassunshine.entityupdates.rendering.EntityRenderManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;onRemoved()V"), method = "removeEntity", locals = LocalCapture.CAPTURE_FAILSOFT)
    public void entityupdates_removeEntity(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci, Entity entity) {
        if (EntityRenderManager.instance != null)
            EntityRenderManager.instance.removeEntity(entity);
    }
}
