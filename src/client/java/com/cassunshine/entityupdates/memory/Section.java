package com.cassunshine.entityupdates.memory;

import org.lwjgl.system.MemoryUtil;

public class Section {

    public final SectionAllocator allocator;
    public final int offset;
    public int length;

    public boolean isClaimed = false;

    public Section(SectionAllocator allocator, int offset, int length) {

        if(offset + length > allocator.buffer.capacity())
            System.out.println("WOW");

        this.allocator = allocator;
        this.offset = offset;
        this.length = length;
    }

    public long getPtr(int position) {
        if (position >= length)
            return getPtr(0);

        return MemoryUtil.memAddress(allocator.buffer, offset);
    }
}
