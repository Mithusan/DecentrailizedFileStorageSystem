import java.io.*;
import java.net.*;
import java.util.*;

public class PeerNode {
    private static final int PEER_PORT = 12345;  // Port for this peer to listen on
    private static final List<InetSocketAddress> knownPeers = new ArrayList<>();  // List of known peers

    public static void main(String[] args) throws IOException {
        PeerNode peer = new PeerNode();

        // Start the server part of the peer
        new Thread(() -> {
            try {
                peer.startServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Ask the user to provide a peer to connect for bootstrapping (optional)
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter IP of a known peer (or press Enter to start fresh): ");
        String ip = console.readLine();
        if (!ip.isEmpty()) {
            peer.connectToPeer(ip, PEER_PORT);  // Connect to the known peer for bootstrapping
        }

        // Now the peer is running, it can start sending/receiving messages with other peers
        System.out.println("You can now start chatting with the network! Type messages:");
        String message;
        while ((message = console.readLine()) != null) {
            peer.broadcastMessage(message);  // Send message to all known peers
        }
    }

    // Start the server socket to accept incoming connections from other peers
    public void startServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PEER_PORT);
        System.out.println("Peer started, listening on port " + PEER_PORT);

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("Connected with peer: " + socket.getInetAddress());

            // Add the peer to the known peers list
            knownPeers.add(new InetSocketAddress(socket.getInetAddress(), PEER_PORT));

            // Start a thread to handle communication with this peer
            new Thread(new PeerHandler(socket)).start();
        }
    }

    // Connect to another peer for bootstrapping
    public void connectToPeer(String ip, int port) throws IOException {
        Socket socket = new Socket(ip, port);
        System.out.println("Connected to peer " + ip + ":" + port);
        knownPeers.add(new InetSocketAddress(InetAddress.getByName(ip), port));

        // Start a thread to handle communication with this peer
        new Thread(new PeerHandler(socket)).start();
    }

    // Broadcast a message to all known peers
    public void broadcastMessage(String message) {
        for (InetSocketAddress peer : knownPeers) {
            try (Socket socket = new Socket(peer.getAddress(), peer.getPort());
                 PrintWriter output = new PrintWriter(socket.getOutputStream(), true)) {

                output.println(message);  // Send the message to the peer
            } catch (IOException e) {
                System.out.println("Failed to send message to " + peer);
            }
        }
    }

    // Handle communication with a peer (run in a separate thread)
    private class PeerHandler implements Runnable {
        private Socket socket;
        private BufferedReader input;

        public PeerHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String message;

                // Read messages from this peer
                while ((message = input.readLine()) != null) {
                    System.out.println("Received message: " + message);
                }

            } catch (IOException e) {
                System.out.println("Connection with peer lost: " + socket.getInetAddress());
            }
        }
    }
}
