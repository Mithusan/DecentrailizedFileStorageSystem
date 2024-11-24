import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerDiscovery {
    private final int tcpPort;
    private final String publicKey;
    private final String uniqueId;
    private final int udpPort;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final int DISCOVERY_PORT_START = 8888;
    private static final int DISCOVERY_PORT_END = 8898;
    private static final String FILE_STORAGE_DIR = "FileStorage";

    // Connection manager
    private final Map<String, Socket> activeConnections = new HashMap<>();

    public PeerDiscovery(int tcpPort, String publicKey, int udpPort) {
        this.tcpPort = tcpPort;
        this.publicKey = publicKey;
        this.uniqueId = UUID.randomUUID().toString();
        this.udpPort = udpPort;

        // Ensure the FileStorage directory exists
        File storageDir = new File(FILE_STORAGE_DIR);
        if (!storageDir.exists()) {
            storageDir.mkdir();
        }
    }

    // Start listening for TCP connections
    public void startListening() {
        executor.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
                //System.out.println("Listening for connections on port: " + tcpPort);
                while (true) {
                    Socket socket = serverSocket.accept();
                    handleConnection(socket);
                }
            } catch (IOException e) {
                System.err.println("Error starting server: " + e.getMessage());
            }
        });
    }

    private void handleConnection(Socket socket) {
        executor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                String incomingKey = reader.readLine();
                System.out.println("Connection attempt from public key: " + incomingKey);

                if (publicKey.equals(incomingKey)) {
                    writer.println("Connection accepted");
                    System.out.println("Authenticated connection with: " + incomingKey);

                    // Add connection to activeConnections
                    String remoteAddress = socket.getInetAddress().getHostAddress();
                    synchronized (activeConnections) {
                        activeConnections.put(remoteAddress, socket);
                    }

                    // Handle commands in the persistent connection
                    while (true) {
                        String command = reader.readLine();
                        if (command == null) break;

                        switch (command) {
                            case "LIST_FILES":
                                listFiles(writer);
                                break;
                            case "REQUEST_FILE":
                                sendFile(reader.readLine(), writer, socket.getOutputStream());
                                break;
                        }
                    }
                } else {
                    writer.println("Connection denied");
                    System.out.println("Authentication failed for: " + incomingKey);
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Error handling connection: " + e.getMessage());
            }
        });
    }

    private void listFiles(PrintWriter writer) {
        File storageDir = new File(FILE_STORAGE_DIR);
        File[] files = storageDir.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    writer.println(file.getName() + " (" + file.length() + " bytes)");
                }
            }
        }
        writer.println("END_OF_LIST");
    }

    private void sendFile(String fileName, PrintWriter writer, OutputStream outputStream) {
        File file = new File(FILE_STORAGE_DIR, fileName);

        if (!file.exists()) {
            writer.println("ERROR: File not found");
            return;
        }

        try (BufferedInputStream fileReader = new BufferedInputStream(new FileInputStream(file));
             DataOutputStream dataOut = new DataOutputStream(outputStream)) {

            writer.println("SENDING_FILE");
            writer.println(fileName);
            writer.println(file.length());

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileReader.read(buffer)) > 0) {
                dataOut.write(buffer, 0, bytesRead);
            }
            dataOut.flush();

            System.out.println("File " + fileName + " sent successfully.");
        } catch (IOException e) {
            System.err.println("Error sending file: " + e.getMessage());
        }
    }

    public void startBroadcasting() {
        executor.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                String message = uniqueId + "|" + publicKey;
                byte[] data = message.getBytes();

                while (true) {
                    for (int port = DISCOVERY_PORT_START; port <= DISCOVERY_PORT_END; port++) {
                        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), port);
                        socket.send(packet);
                    }
                    //System.out.println("Broadcasting public key...");
                    Thread.sleep(5000); // Broadcast every 5 seconds
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Error broadcasting public key: " + e.getMessage());
            }
        });
    }

    public void listenForBroadcasts() {
        executor.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket(udpPort)) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (true) {
                    socket.receive(packet);
                    String receivedMessage = new String(packet.getData(), 0, packet.getLength());
                    String[] parts = receivedMessage.split("\\|");

                    String senderId = parts[0];
                    String receivedKey = parts[1];
                    String senderAddress = packet.getAddress().getHostAddress();

                    if (senderId.equals(uniqueId)) {
                        continue;
                    }

                    //System.out.println("Received broadcasted key: " + receivedKey + " from " + senderAddress);

                    if (publicKey.equals(receivedKey)) {
                        int peerPort = tcpPort; // Assume peers use the same port
                        connectToPeer(senderAddress, peerPort);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error listening for broadcasts: " + e.getMessage());
            }
        });
    }

    private void connectToPeer(String host, int peerPort) {
        executor.submit(() -> {
            synchronized (activeConnections) {
                if (activeConnections.containsKey(host)) {
                    //System.out.println("Already connected to " + host);
                    return;
                }
            }

            try (Socket socket = new Socket(host, peerPort);
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                writer.println(publicKey);
                String response = reader.readLine();
                //System.out.println("Response from peer: " + response);

                if ("Connection accepted".equals(response)) {
                    synchronized (activeConnections) {
                        activeConnections.put(host, socket);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error connecting to peer: " + e.getMessage());
            }
        });
    }

    public static void main(String[] args) throws IOException {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter Key: ");
        String key = consoleReader.readLine();

        System.out.print("Enter TCP port (unique for this peer): ");
        int tcpPort = Integer.parseInt(consoleReader.readLine());

        System.out.print("Enter UDP port (unique for this peer): ");
        int udpPort = Integer.parseInt(consoleReader.readLine());

        PeerDiscovery peer = new PeerDiscovery(tcpPort, key, udpPort);

        peer.startListening();
        peer.startBroadcasting();
        peer.listenForBroadcasts();
    }
}
