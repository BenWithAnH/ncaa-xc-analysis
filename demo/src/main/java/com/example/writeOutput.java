package com.example;

import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.util.List;
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

    public boolean write(List<getCAF.AthleteRating> ratings) {
        boolean success = false;

        Path csvPath = getRelativePath("data.csv");
        File savedData = csvPath.toFile();
        boolean newFile = false;

        try {
            if (savedData.createNewFile()) {
                newFile = true;
            } else {
                // file exists alr
                System.out.println("CSV file already exists at: " + savedData.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("An error occurred while creating CSV file (WriteOutput.write.createNewFile)" + e);
            return false;
        }

        try (CSVWriter writer = new CSVWriter(new FileWriter(savedData, true))) {
            if (newFile) {
                String[] header = { "Name", "Time", "Link", "Rating"};
                writer.writeNext(header);
            }
            
            for (getCAF.AthleteRating rating : ratings) {
                String[] payload = {
                    rating.name(),
                    rating.time(),
                    rating.link(),
                    String.valueOf(rating.rating())
                };
                writer.writeNext(payload);
            }
            
            System.out.println("CSV saved successfully");
            success = true;
        } catch (IOException e) {
            System.out.println("Failed to write data to CSV file" + e);
        }
        return success;
    }

}