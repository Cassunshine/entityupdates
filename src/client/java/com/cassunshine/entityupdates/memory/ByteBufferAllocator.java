package com.cassunshine.entityupdates.memory;

import net.minecraft.client.util.GlAllocationUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class ByteBufferAllocator {

    private final List<Section> allSlices = new ArrayList<>();

    private final List<Section> freeSlices = new ArrayList<>();

    private final List<Section> batchCache = new ArrayList<>();

    /**
     * The ByteBuffer that represents the entire allocated space for this allocator.
     */
    public ByteBuffer memoryBuffer;

    /**
     * All allocated blocks will be a multiple of this size.
     */
    public int blockSize;

    public ByteBufferAllocator(int blockSize, int initialCapacity) {
        this.blockSize = blockSize;


        memoryBuffer = GlAllocationUtils.allocateByteBuffer(blockSize * initialCapacity);

        var initialSlice = new Section(this, 0, memoryBuffer.capacity());
        allSlices.add(initialSlice);
        freeSlices.add(initialSlice);
    }

    private void grow() {
        var currentCapacity = memoryBuffer.capacity();
        memoryBuffer = GlAllocationUtils.resizeByteBuffer(memoryBuffer, memoryBuffer.capacity() * 2);

        var newSection = new Section(this, currentCapacity, currentCapacity);

        allSlices.add(newSection);
        freeSlices.add(newSection);

        mergeFree();
        verify();
    }


    private void split(Section section, int newSize) {
        int bytes = newSize * blockSize;

        Section newSection = new Section(this, section.offset + bytes, section.length - bytes);
        newSection.claimed = false;

        var index = allSlices.indexOf(section) + 1;

        allSlices.add(index, newSection);
        freeSlices.add(newSection);

        section.length = bytes;
        section.blocks = newSize;

        //verify();
    }

    /**
     * Merges all adjacent free blocks of memory.
     */
    public void mergeFree() {
        freeSlices.sort(Comparator.comparingLong(a -> a.offset));

        for (int i = freeSlices.size() - 2; i >= 0; i--) {
            var thisSlice = freeSlices.get(i);
            var nextSlice = freeSlices.get(i + 1);

            if (thisSlice.offset + thisSlice.length != nextSlice.offset)
                continue;

            thisSlice.length += nextSlice.length;

            allSlices.remove(nextSlice);
            freeSlices.remove(i + 1);
        }

        verify();
    }

    /**
     * Allocates a ByteBuffer that has as many blocks as the count passed in.
     */
    public Section allocateSlice(int count) {
        int requiredBytes = count * blockSize;

        while (true) {
            for (int i = 0; i < freeSlices.size(); i++) {
                var currentSlice = freeSlices.get(i);
                //Skip claimed entries.
                if (currentSlice.claimed)
                    continue;

                if (currentSlice.length == requiredBytes) {
                    currentSlice.claimed = true;
                    freeSlices.remove(currentSlice);

                    //verify();
                    return currentSlice;
                }
                if (currentSlice.length > requiredBytes) {
                    split(currentSlice, count);
                    currentSlice.claimed = true;

                    freeSlices.remove(currentSlice);
                    return currentSlice;
                }
            }

            //This will add a new free section, so re-do the loop.
            grow();
        }
    }

    public void freeSlice(Section buffer) {
        freeSlices.add(buffer);
        buffer.dirty = false;
        buffer.claimed = false;
    }

    public void close() {
        allSlices.clear();
        freeSlices.clear();
        GlAllocationUtils.free(memoryBuffer);
    }

    public List<Section> batchBy(Predicate<Section> predicate) {
        int batchStart = -1;
        int batchLength = -1;

        batchCache.clear();

        for (int i = 0; i < allSlices.size(); i++) {
            var slice = allSlices.get(i);
            if (predicate.test(slice)) {
                if (batchStart == -1) {
                    batchStart = slice.offset;
                    batchLength = 0;
                }

                batchLength += slice.length;
            } else if (batchStart != -1) {
                batchCache.add(new Section(this, batchStart, batchLength));
                batchStart = -1;
                batchLength = -1;
            }
        }

        return batchCache;
    }

    private void verify() {
        int expectedEnd = 0;

        for (int i = 0; i < allSlices.size(); i++) {
            Section slice = allSlices.get(i);

            var actualEnd = slice.offset + slice.length;
            expectedEnd += slice.length;

            if (actualEnd != expectedEnd)
                throw new RuntimeException("Verification failed");
        }

        if(expectedEnd != memoryBuffer.capacity())
            throw new RuntimeException("Verification failed");
    }

    public class Section {
        public final ByteBufferAllocator allocator;

        public int offset;
        public int length;

        public int blocks;

        public boolean claimed;
        public boolean dirty;

        public Section(ByteBufferAllocator allocator, int offset, int length) {
            this.allocator = allocator;
            this.offset = offset;
            this.length = length;

            this.blocks = length / blockSize;
        }

        public long getPtr(int position) throws Exception {
            int bytes = position * blockSize;
            if (bytes > length)
                throw new Exception("Read beyond end of memory section");

            return MemoryUtil.memAddress(allocator.memoryBuffer, offset + bytes);
        }

        public ByteBuffer getSlice() {
            try {
                return allocator.memoryBuffer.slice(offset, length);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
