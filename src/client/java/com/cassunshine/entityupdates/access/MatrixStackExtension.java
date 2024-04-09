package com.cassunshine.entityupdates.access;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3d;
import org.joml.Matrix3f;

public interface MatrixStackExtension {

    void copyTop(MatrixStack other);
}
