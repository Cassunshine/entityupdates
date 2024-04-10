package com.cassunshine.entityupdates.access;

import net.minecraft.client.gl.ShaderProgram;

import java.util.Optional;
import java.util.function.Supplier;

public interface ShaderProgramAccess {

    Optional<Supplier<ShaderProgram>> getSupplierExposed();
}
