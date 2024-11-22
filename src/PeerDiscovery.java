import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerDiscovery {

    private final int tcpPort;
    private final String publicKey;
    private final String uniqueId; // Unique identifier for this peer
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final int DISCOVERY_PORT = 8888; // Fixed port for UDP broadcast

    public PeerDiscovery(int tcpPort, String publicKey) {
        this.tcpPort = tcpPort;
        this.publicKey = publicKey;
        this.uniqueId = UUID.randomUUID().toString(); // Generate a unique ID
    }

    // Start listening for TCP connections
    public void startListening() {
        executor.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
                System.out.println("Listening for connections on port: " + tcpPort);
                while (true) {
                    Socket socket = serverSocket.accept();
                    handleConnection(socket);
                }
            } catch (IOException e) {
                System.err.println("Error starting server: " + e.getMessage());
            }
        });
    }

    // Handle incoming TCP connections
    private void handleConnection(Socket socket) {
        executor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                // Receive public key from peer
                String incomingKey = reader.readLine();
                System.out.println("Connection attempt from public key: " + incomingKey);

                if (publicKey.equals(incomingKey)) {
                    writer.println("Connection accepted");
                    System.out.println("Authenticated connection with: " + incomingKey);
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

    // Broadcast public key for discovery
    public void startBroadcasting() {
        executor.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                String message = uniqueId + "|" + publicKey;
                byte[] data = message.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT);

                while (true) {
                    socket.send(packet);
                    System.out.println("Broadcasting public key...");
                    Thread.sleep(5000); // Broadcast every 5 seconds
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Error broadcasting public key: " + e.getMessage());
            }
        });
    }

    // Listen for broadcasts and attempt connections
    public void listenForBroadcasts() {
        executor.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (true) {
                    socket.receive(packet);
                    String receivedMessage = new String(packet.getData(), 0, packet.getLength());
                    String[] parts = receivedMessage.split("\\|");

                    // Parse the broadcast message
                    String senderId = parts[0];
                    String receivedKey = parts[1];
                    String senderAddress = packet.getAddress().getHostAddress();

                    // Skip self-connections based on unique ID
                    if (senderId.equals(uniqueId)) {
                        continue;
                    }

                    System.out.println("Received broadcasted key: " + receivedKey + " from " + senderAddress);

                    if (publicKey.equals(receivedKey)) {
                        int peerPort = tcpPort; // Assume peers use the same port
                        System.out.println("Matching public key found! Connecting to peer: " + senderAddress);

                        connectToPeer(senderAddress, peerPort);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error listening for broadcasts: " + e.getMessage());
            }
        });
    }

    // Connect to a discovered peer
    private void connectToPeer(String host, int peerPort) {
        executor.submit(() -> {
            try (Socket socket = new Socket(host, peerPort);
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send public key to the peer
                writer.println(publicKey);

                // Read response from the peer
                String response = reader.readLine();
                System.out.println("Response from peer: " + response);
            } catch (IOException e) {
                System.err.println("Error connecting to peer: " + e.getMessage());
            }
        });
    }

    public static void main(String[] args) throws IOException {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        // Prompt for public key and TCP port
        System.out.print("Enter Key: ");
        String key = consoleReader.readLine();

        System.out.print("Enter TCP port (unique for this peer): ");
        int port = Integer.parseInt(consoleReader.readLine());

        PeerDiscovery peer = new PeerDiscovery(port, key);

        // Start listening for connections
        peer.startListening();

        // Start broadcasting the public key
        peer.startBroadcasting();

        // Listen for incoming broadcasts
        peer.listenForBroadcasts();
    }
}
