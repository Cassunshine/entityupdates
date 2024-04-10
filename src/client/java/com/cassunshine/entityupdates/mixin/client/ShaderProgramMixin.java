package com.cassunshine.entityupdates.mixin.client;

import com.cassunshine.entityupdates.access.ShaderProgramAccess;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;
import java.util.function.Supplier;

@Mixin(RenderPhase.ShaderProgram.class)
public class ShaderProgramMixin implements ShaderProgramAccess {


    @Shadow
    @Final
    private Optional<Supplier<ShaderProgram>> supplier;

    @Override
    public Optional<Supplier<ShaderProgram>> getSupplierExposed() {
        return supplier;
    }
}
