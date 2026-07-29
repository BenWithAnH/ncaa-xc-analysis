package com.example;

import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

public class writeOutput {


    public static Path getRelativePath(String fileName) {
        try {
            URI jarUri = writeOutput.class.getProtectionDomain().getCodeSource().getLocation().toURI();

            Path jarDir = Paths.get(jarUri).getParent();

            return jarDir.resolve(fileName);
        } catch (Exception e) {
            // send err
            return Paths.get(fileName);
        }
    }

    public boolean write(String[] payload) {
        boolean success = false;

        Path csvPath = getRelativePath("data.csv");
        File savedData = csvPath.toFile();
        boolean newFile = false;

        try {
            if (savedData.createNewFile()) {
                newFile = true;
            } else {
                // file exists alr
                System.out.print("CSV file already exists at: " + savedData.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.print("An error occurred while creating CSV file (WriteOutput.write.createNewFile)" + e);
            return false;
        }

        try (CSVWriter writer = new CSVWriter(new FileWriter(savedData, true))) {
            if (newFile) {
                String[] header = { "Ticker:", "Insider Name:", "Owner Type:", "Date:", "Code:", "Shares:",
                        "Price/Share:", "Context/Summary:" };
                writer.writeNext(header);
            }
            writer.writeNext(payload);
            System.out.print("CSV saved successfully");
            success = true;
        } catch (IOException e) {
            System.out.print("Failed to write data to CSV file" + e);
        }
        return success;
    }

}