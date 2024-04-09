package com.cassunshine.entityupdates.mixin.client;

import com.cassunshine.entityupdates.access.MatrixStackExtension;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Deque;

@Mixin(MatrixStack.class)
public class MatrixStackMixin implements MatrixStackExtension {
    @Shadow
    @Final
    private Deque<MatrixStack.Entry> stack;

    @Override
    public void copyTop(MatrixStack other) {
        other.push();
        var entry = other.peek();
        other.pop();
        stack.push(entry);
    }
}
