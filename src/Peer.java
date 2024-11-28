import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Peer {
    private final int tcpPort;
    private final String publicKey;
    private final String uniqueId;
    private final int udpPort;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final int DISCOVERY_PORT_START = 8888;
    private static final int DISCOVERY_PORT_END = 8898;
    private static final String FILE_STORAGE_DIR = "FileStorage";
    private static final BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));


    // Connection manager
    private final Map<String, PeerConnection> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, PeerConnection> hostConnections = new ConcurrentHashMap<>();

    public Peer(int tcpPort, String publicKey, int udpPort) {
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

    public void startBroadcasting() {
        executor.submit(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                String message = uniqueId + "|" + publicKey + "|" + tcpPort;
                byte[] data = message.getBytes();

                while (true) {
                    for (int port = DISCOVERY_PORT_START; port <= DISCOVERY_PORT_END; port++) {
                        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"),port);
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
                    int peerPort = Integer.parseInt(parts[2]);
        
                    String senderAddress = packet.getAddress().getHostAddress();
                    //System.out.println(senderAddress);
                    String connectionKey = senderAddress + ":" + peerPort;

                    if (senderId.equals(uniqueId)) {
                        continue;
                    }
                    //System.out.println(peerPort);
                    //System.out.println("Received broadcasted key: " + receivedKey + " from " + senderAddress);

                    if (publicKey.equals(receivedKey)) {
                        if (!hostConnections.containsKey(connectionKey)) {
                            connectToPeer(senderAddress, peerPort);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error listening for broadcasts: " + e.getMessage());
            }
        });
    }

    private void handleConnection(Socket socket) {
        executor.submit(() -> {
            try {
                PeerConnection peerConnection = new PeerConnection(socket);

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                OutputStream outputStream = socket.getOutputStream();

                String incomingKey = reader.readLine();
                //System.out.println("Connection attempt from public key: " + incomingKey);

                if (publicKey.equals(incomingKey)) {
                    writer.println("Connection accepted");
                    System.out.println("Authenticated connection with: " + incomingKey);

                    // Add connection to activeConnections
                    String remoteAddress = socket.getInetAddress().getHostAddress();
                
                    activeConnections.put(remoteAddress, peerConnection);

                    while (true) {
                        // Read file list from the peer
                        //peerConnection.receiveFileList();

                        String request = reader.readLine();
                        if (request.startsWith("REQUEST_FILE")) {
                            String requestedFileName = request.split("\\|")[1];
                            peerConnection.sendFile(requestedFileName);
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

    private void connectToPeer(String host, int peerPort) { 
            executor.submit(() -> { 
                try (Socket socket = new Socket(host, peerPort)){
                    PeerConnection peerConnection = new PeerConnection(socket);

                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String connectionKey = host + ":" + peerPort;

                    writer.println(publicKey);
                    String response = reader.readLine();
                    //System.out.println("Response from peer: " + response);

                    if ("Connection accepted".equals(response)) {
                        hostConnections.put(connectionKey, peerConnection);
                        
                        while (true) {
                            //writer.println("REQUEST_FILE|" + fileName); // Request a file from the peer
                            //peerConnection.receiveFile(); // Receive and save the file
                            //peerConnection.sendFileList();
                            //Thread.sleep(5000);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error connecting to peer: " + e.getMessage());
                } //catch (InterruptedException e) {
                  //  System.err.println("File list update thread interrupted: " + e.getMessage());
                //}
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

        Peer peer = new Peer(tcpPort, key, udpPort);

        peer.startListening();
        peer.startBroadcasting();
        peer.listenForBroadcasts();
    }
}
