import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;

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
    private static final Map<String, PeerConnection> hostConnections = new ConcurrentHashMap<>();

    // Store file lists received from peers
    private final Map<String, List<String>> peerFileLists = new ConcurrentHashMap<>();
    private Scanner scanner=new Scanner(System.in);
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

    public static Map<String, PeerConnection> getConnections() {
        return hostConnections;
    }

    // Method to check if public key exists, otherwise generate it
    private static String loadOrGeneratePublicKey() throws Exception {
        File publicKeyFile = new File("public_key.pem");

        if (publicKeyFile.exists()) {
            // Read the public key from the file
            return new String(Files.readAllBytes(publicKeyFile.toPath()));
        } else {
            int userSelect = JOptionPane.showConfirmDialog(
                null,
                "No Public Key Found. Would you like to generate a key?",
                "Generate Key?",
                JOptionPane.YES_NO_OPTION
                );
            
            if (userSelect == JOptionPane.YES_OPTION){
                // Generate a new key pair and save the public key
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
                keyPairGenerator.initialize(2048);
                KeyPair keyPair = keyPairGenerator.generateKeyPair();

                // Save public key to file
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(publicKeyFile))) {
                    writer.write(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
                }

                return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            } else if (userSelect == JOptionPane.NO_OPTION){
                JOptionPane.showMessageDialog(null, "You need a Key to Proceed.");
                System.exit(0);
            }
        }
        return null;
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
                int remoteListeningPort = Integer.parseInt(reader.readLine());
                //System.out.println("Connection attempt from public key: " + incomingKey);

                if (publicKey.equals(incomingKey)) {
                    // Add connection to activeConnections
                    String remoteAddress = socket.getInetAddress().getHostAddress();
                    String connectionKey = remoteAddress + ":" + remoteListeningPort;

                    writer.println("Connection accepted");
                    System.out.println("Authenticated connection > " + connectionKey);

                    activeConnections.put(remoteAddress, peerConnection);

                    executor.submit(() -> {
                        while (true) {
                            String request = reader.readLine();
                            if (request.startsWith("REQUEST_FILE")) {
                                String requestedFileName = request.split("\\|")[1];
                                peerConnection.sendFile(requestedFileName);
                            }
                        }
                    });


                    while (true) {
                        List<String> receivedFiles = peerConnection.receiveFileList();
                        if (!receivedFiles.isEmpty()) {
                            peerFileLists.put(connectionKey, receivedFiles);
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
                writer.println(tcpPort);
                String response = reader.readLine();
                //System.out.println("Response from peer: " + response);

                if ("Connection accepted".equals(response)) {
                    hostConnections.put(connectionKey, peerConnection);
                    
                    executor.submit(() -> {
                        try {
                            while (true) {
                                String userInput = consoleReader.readLine();
                                if ("/list".equalsIgnoreCase(userInput)||"/ls".equalsIgnoreCase(userInput)) {
                                    System.out.println("Files available from peers:");
                                    for (Map.Entry<String, List<String>> entry : peerFileLists.entrySet()) {
                                        String peerAddress = entry.getKey();
                                        List<String> files = entry.getValue();
                                        System.out.println(peerAddress + ": " + files);
                                    }

                                } else if ("/clients".equalsIgnoreCase(userInput)||"/all".equalsIgnoreCase(userInput)) {
                                    System.out.println("Connected clients:");
                                    for (String client : hostConnections.keySet()) {
                                        System.out.println(client);
                                    }

                                } else if(userInput.startsWith("/download")||userInput.startsWith("/d")){
                                    String[] commandParts = userInput.split("\\s+", 2);
                                    if (commandParts.length < 2) {
                                        System.out.println("Usage: /download <fileName>");
                                    } else {
                                        String fileName = commandParts[1];

                                        // Iterate through peers to find the file
                                        boolean fileRequested = false;
                                        for (Map.Entry<String, List<String>> entry : peerFileLists.entrySet()) {
                                            String peerAddress = entry.getKey();
                                            List<String> files = entry.getValue();

                                            if (files.contains(fileName)) {
                                                System.out.println("Requesting file \"" + fileName + "\" from " + peerAddress);

                                                PeerConnection connection = hostConnections.get(peerAddress);
                                                if (connection != null) {
                                                    connection.downloadFile(fileName);
                                                    fileRequested = true;
                                                    break;
                                                }
                                            }
                                        }

                                        if (!fileRequested) {
                                            System.out.println("File \"" + fileName + "\" not found among connected peers.");
                                        }
                                    }
                                }
                                else if(userInput.startsWith("/upload")||userInput.startsWith("/u")){
                                    peerConnection.uploadFile();

                                }
                                else if(userInput.startsWith("/help")||userInput.startsWith("/h")){
                                    System.out.println("use the following valid commands:\n-------------------\n - /list or /ls \t provides list of avaialble files\n - /clients or /all \t provides list of all connected clients");
                                    System.out.println(" - /download <fileName> or /d <fileName>\t allows download if file is available\n - /upload or /u \t uploads selected file into your FileStorage\n-------------------");
                                }
                                else{
                                    System.out.println("Invalid Command: \""+userInput+"\" unrecognized!");
                                }

                            }
                        } catch (IOException e) {
                            System.err.println("Error reading user input: " + e.getMessage());
                        }
                    });

                    while (true) {
                        peerConnection.sendFileList();
                        Thread.sleep(5000);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error connecting to peer: " + e.getMessage());
            } catch (InterruptedException e) {
                System.err.println("File list update thread interrupted: " + e.getMessage());
            }
        });
    }

    public PeerConnection getConnectionForClient(String clientId) {
        return hostConnections.get(clientId);
    }
    
    public static void main(String[] args) throws IOException {
        try {
            String key = loadOrGeneratePublicKey();

            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

            System.out.print("Enter TCP port (unique for this peer): ");
            int tcpPort = Integer.parseInt(consoleReader.readLine());

            System.out.print("Enter UDP port (unique for this peer): ");
            int udpPort = Integer.parseInt(consoleReader.readLine());

            Peer peer = new Peer(tcpPort, key, udpPort);

            peer.startListening();
            peer.startBroadcasting();
            peer.listenForBroadcasts();
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
}
