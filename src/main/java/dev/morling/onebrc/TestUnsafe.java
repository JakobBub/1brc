package dev.morling.onebrc;
import java.nio.channels.FileChannel;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;

public class TestUnsafe {
    public static void main(String[] args) throws Exception {
        RandomAccessFile file = new RandomAccessFile("./measurements.txt", "r");
        MappedByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, 100);
        long addr = ((sun.nio.ch.DirectBuffer)buffer).address();
        System.out.println("Address: " + addr);
    }
}