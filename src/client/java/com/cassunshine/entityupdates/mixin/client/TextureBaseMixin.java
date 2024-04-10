package com.cassunshine.entityupdates.mixin.client;

import com.cassunshine.entityupdates.access.TextureBaseAccess;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(RenderPhase.TextureBase.class)
public abstract class TextureBaseMixin implements TextureBaseAccess {


    @Shadow
    protected abstract Optional<Identifier> getId();

    @Override
    public Optional<Identifier> getIdExposed() {
        return getId();
    }
}
