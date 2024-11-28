import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
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
    }

    // Send a file to the connected peer
    public void sendFile(String fileName) {
        try {
            File file = new File(FILE_STORAGE_DIR, fileName);
            if (file.exists() && file.isFile()) {
                // Notify the peer about the file transfer
                writer.println("FILE_TRANSFER_START|" + fileName + "|" + file.length());
                
                // Send file bytes
                try (FileInputStream fis = new FileInputStream(file);
                    BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream())) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                    bos.flush();
                    System.out.println("File sent: " + fileName);
                }
            } else {
                writer.println("ERROR|File not found: " + fileName);
            }
        } catch (IOException e) {
            System.err.println("Error sending file: " + e.getMessage());
        }
    }

    // Receive a file from the connected peer
    public void receiveFile() {
        try {
            String header = reader.readLine();
            if (header != null && header.startsWith("FILE_TRANSFER_START")) {
                String[] parts = header.split("\\|");
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);

                File file = new File(FILE_STORAGE_DIR, fileName);
                try (FileOutputStream fos = new FileOutputStream(file);
                    BufferedInputStream bis = new BufferedInputStream(socket.getInputStream())) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;

                    while (totalRead < fileSize && (bytesRead = bis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                    System.out.println("File received: " + fileName);
                }
            } else {
                System.out.println("Invalid file transfer request");
            }
        } catch (IOException e) {
            System.err.println("Error receiving file: " + e.getMessage());
        }
    }
}
