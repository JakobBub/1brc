package dev.morling.onebrc;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeMap;
import sun.misc.Unsafe;

public class CalculateAverage {
    private static final String FILE = "./measurements.txt";

    private static final Unsafe UNSAFE;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final int TABLE_SIZE = 1 << 16;
    private static final int HASH_MASK = TABLE_SIZE - 1;

    static class Result {
        int[] keysLen = new int[TABLE_SIZE];
        byte[][] keys = new byte[TABLE_SIZE][100];
        long[] count = new long[TABLE_SIZE];
        int[] min = new int[TABLE_SIZE];
        int[] max = new int[TABLE_SIZE];
        long[] sum = new long[TABLE_SIZE];

        public Result() {
            Arrays.fill(min, Integer.MAX_VALUE);
            Arrays.fill(max, Integer.MIN_VALUE);
        }
    }

    static class AggregatedResult {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        long sum = 0;
        long count = 0;
        
        public String format() {
            return round(min) + "/" + round((double)sum / count) + "/" + round(max);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        RandomAccessFile file = new RandomAccessFile(FILE, "r");
        FileChannel channel = file.getChannel();
        long fileSize = channel.size();

        int numThreads = Runtime.getRuntime().availableProcessors();
        long chunkSize = fileSize / numThreads;

        Result[] results = new Result[numThreads];
        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            long start = i * chunkSize;
            long end = (i == numThreads - 1) ? fileSize : (start + chunkSize);
            final int tIdx = i;
            
            threads[i] = new Thread(() -> {
                try {
                    results[tIdx] = processChunk(channel, start, end, fileSize);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        TreeMap<String, AggregatedResult> finalMap = new TreeMap<>();
        for (int i = 0; i < numThreads; i++) {
            Result r = results[i];
            if (r == null) continue;
            for (int k = 0; k < TABLE_SIZE; k++) {
                if (r.keysLen[k] > 0) {
                    String name = new String(r.keys[k], 0, r.keysLen[k], StandardCharsets.UTF_8);
                    AggregatedResult agg = finalMap.computeIfAbsent(name, key -> new AggregatedResult());
                    agg.min = Math.min(agg.min, r.min[k]);
                    agg.max = Math.max(agg.max, r.max[k]);
                    agg.sum += r.sum[k];
                    agg.count += r.count[k];
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (java.util.Map.Entry<String, AggregatedResult> entry : finalMap.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.getKey()).append("=").append(entry.getValue().format());
        }
        sb.append("}");
        System.out.println(sb.toString());
        channel.close();
        file.close();
    }
    
    private static double round(double value) {
        return Math.round(value) / 10.0;
    }

    private static Result processChunk(FileChannel channel, long start, long targetEnd, long fileSize) throws Exception {
        long realStart = start;
        if (realStart > 0) {
            realStart = findNextNewline(channel, realStart, fileSize);
        }
        long realEnd = targetEnd;
        if (realEnd < fileSize) {
            realEnd = findNextNewline(channel, realEnd, fileSize);
        }

        Result result = new Result();
        if (realStart >= realEnd) return result;

        long offset = realStart;
        while (offset < realEnd) {
            long mapSize = Math.min(realEnd - offset, Integer.MAX_VALUE);
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, offset, mapSize);
            
            // Get address via hidden method access
            long address = ((sun.nio.ch.DirectBuffer)buffer).address();
            
            long startAddr = address;
            long endAddr = address + mapSize;
            long curr = startAddr;
            
            byte[] nameBytes = new byte[100];
            
            while (curr < endAddr) {
                int nameLen = 0;
                int hash = 1;
                while (curr < endAddr) {
                    byte b = UNSAFE.getByte(curr++);
                    if (b == ';') break;
                    nameBytes[nameLen++] = b;
                    hash = 31 * hash + b;
                }
                
                if (curr >= endAddr) break; // Check bounds after name read
                
                boolean negative = false;
                int temp = 0;
                while (curr < endAddr) {
                    byte b = UNSAFE.getByte(curr++);
                    if (b == '\n') break;
                    if (b == '-') negative = true;
                    else if (b != '.') {
                        temp = temp * 10 + (b - '0');
                    }
                }
                
                if (negative) temp = -temp;
                
                int slot = hash & HASH_MASK;
                while (result.keysLen[slot] != 0 && !Arrays.equals(result.keys[slot], 0, result.keysLen[slot], nameBytes, 0, nameLen)) {
                    slot = (slot + 1) & HASH_MASK;
                }
                
                if (result.keysLen[slot] == 0) {
                    result.keysLen[slot] = nameLen;
                    System.arraycopy(nameBytes, 0, result.keys[slot], 0, nameLen);
                }
                
                if (temp < result.min[slot]) result.min[slot] = temp;
                if (temp > result.max[slot]) result.max[slot] = temp;
                result.sum[slot] += temp;
                result.count[slot]++;
            }
            
            offset += mapSize;
        }

        return result;
    }
    
    private static long findNextNewline(FileChannel channel, long position, long fileSize) throws Exception {
        MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, position, Math.min(100, fileSize - position));
        long offset = 0;
        while (buffer.hasRemaining()) {
            if (buffer.get() == '\n') {
                return position + offset + 1;
            }
            offset++;
        }
        return position;
    }
}