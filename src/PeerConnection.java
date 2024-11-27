import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerConnection {
    private final Socket socket;
    private final PrintWriter writer;
    private final BufferedReader reader;
    private final Timer timer;
    private static final String FILE_STORAGE_DIR = "FileStorage";
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PeerConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.timer = new Timer(true); // Daemon timer thread
    }

    // Start sending file list every 5 seconds
    public void startFileListUpdates() {
        executor.submit(() -> {
            try {
                while (true) {
                    sendFileList();
                    Thread.sleep(5000); // Wait 5 seconds before sending again
                }
            } catch (InterruptedException e) {
                System.err.println("File list update thread interrupted: " + e.getMessage());
            }
        });
    }

    // Send the file list to the connected peer
    public void sendFileList() {
        try {
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            // List files in the directory and send them to the connected peer
            File directory = new File(FILE_STORAGE_DIR);
            File[] files = directory.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        writer.println(file.getName()); // Send each file name
                    }
                }
            }
            writer.println("END_OF_LIST"); // Signal end of the list
        } catch (IOException e) {
            System.err.println("Error sending file list: " + e.getMessage());
        }
    }

    // Method to read the file list from a peer
    public void receiveFileList() {
        executor.submit(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while (true) { // Keep listening for file lists
                    String line;
                    List<String> receivedFiles = new ArrayList<>();

                    while ((line = reader.readLine()) != null) {
                        if ("END_OF_LIST".equals(line)) {
                            break; // End of the file list
                        }
                        receivedFiles.add(line); // Add file to the list
                    }

                    System.out.println("Received file list: " + receivedFiles);
                }
            } catch (IOException e) {
                System.err.println("Error receiving file list: " + e.getMessage());
            }
        });
    }
}
