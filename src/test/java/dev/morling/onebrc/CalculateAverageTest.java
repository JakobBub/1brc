package dev.morling.onebrc;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CalculateAverageTest {

    public static void main(String[] args) throws Exception {
        // Create a known measurements.txt
        String testData = "Hamburg;12.0\nHamburg;14.0\nBerlin;10.5\nBerlin;10.5\nBerlin;10.5\n";
        Files.writeString(Path.of("./measurements.txt"), testData);

        // Capture standard out
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        try {
            // Run the program under test
            CalculateAverage.main(new String[0]);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("TEST FAILED: Exception thrown");
            System.exit(1);
        } finally {
            // Restore standard out
            System.setOut(originalOut);
        }

        String output = outContent.toString().trim();
        String expected = "{Berlin=10.5/10.5/10.5, Hamburg=12.0/13.0/14.0}";

        if (expected.equals(output)) {
            System.out.println("TEST PASSED!");
        } else {
            System.err.println("TEST FAILED!");
            System.err.println("Expected: " + expected);
            System.err.println("Actual:   " + output);
            System.exit(1);
        }
    }
}