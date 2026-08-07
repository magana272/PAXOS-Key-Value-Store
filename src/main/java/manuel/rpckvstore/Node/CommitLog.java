package manuel.rpckvstore.Node;

import manuel.rpckvstore.Logger.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CommitLog {

    private BufferedWriter writer;

    public CommitLog(String directory) {
        try {
            File dir = new File(directory);
            dir.mkdirs();
            this.writer = new BufferedWriter(new FileWriter(new File(dir, "log.txt")));
        } catch (IOException e) {
            Logger.error("Failed to open commit log", e);
        }
    }

    public synchronized void append(String line) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            Logger.error("Failed to write commit to log", e);
        }
    }
}
