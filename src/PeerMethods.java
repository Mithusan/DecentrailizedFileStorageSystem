import java.io.*;

public class PeerMethods {
    private static final String FILE_STORAGE_DIR = "FileStorage";

    public static void listFiles(PrintWriter writer) {
        File directory = new File(FILE_STORAGE_DIR);
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    writer.println(file.getName());
                }
            }
        }
        writer.println("END_OF_LIST");
    }
}
