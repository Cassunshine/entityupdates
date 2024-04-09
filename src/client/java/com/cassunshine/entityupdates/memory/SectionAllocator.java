package com.cassunshine.entityupdates.memory;

import net.minecraft.client.util.GlAllocationUtils;
import net.minecraft.entity.ai.goal.BreakDoorGoal;
import net.minecraft.util.math.MathHelper;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class SectionAllocator {

    public final int blockSize;

    public ByteBuffer buffer;

    //All sections, in the order they appear in the buffer.
    private ArrayList<Section> allSections = new ArrayList<>();
    //All free sections, not ordered.
    private ArrayList<Section> freeSections = new ArrayList<>();

    public SectionAllocator(int initialSize, int blockSize) {
        this.blockSize = blockSize;
        buffer = GlAllocationUtils.allocateByteBuffer(initialSize);
    }

    public void freeAll() {
        allSections.clear();
        freeSections.clear();

        Section mainSection = new Section(this, 0, buffer.capacity());

        allSections.add(mainSection);
        freeSections.add(mainSection);
    }

    private void grow() {
        int previousCapacity = buffer.capacity();
        buffer = GlAllocationUtils.resizeByteBuffer(buffer, buffer.capacity() * 2);
        Section newSection = new Section(this, previousCapacity, previousCapacity);

        allSections.add(newSection);
        freeSections.add(newSection);
    }

    private Section split(Section section, int newLength) {
        var oldLength = section.length;
        var newSection = new Section(this, section.offset + newLength, oldLength - newLength);

        section.length = newLength;

        allSections.add(allSections.indexOf(section) + 1, newSection);
        freeSections.add(newSection);
        return section;
    }

    public Section allocate(int length) {

        var rounded = MathHelper.ceilDiv(length, blockSize) * blockSize;
        if (rounded != length) {
            return null;
        }

        Section result = null;

        for (int i = 0; i < freeSections.size() && result == null; i++) {
            var section = freeSections.get(i);

            if (section.length > length) {
                result = split(section, length);
            }
            if (section.length == length)
                result = section;
        }

        if (result == null) {
            grow();
            return allocate(length);
        }

        freeSections.remove(result);
        result.isClaimed = true;
        return result;
    }

    public void free(Section section) {
        section.isClaimed = false;
        freeSections.add(section);

        for (int i = allSections.size() - 1; i >= 1; i--) {
            Section current = allSections.get(i);
            Section previousSection = allSections.get(i - 1);

            if (current.isClaimed || previousSection.isClaimed)
                continue;

            previousSection.length += current.length;

            allSections.remove(current);
            freeSections.remove(current);
        }
    }
}
