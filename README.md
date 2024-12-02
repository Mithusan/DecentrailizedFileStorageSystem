# Decentralized File Storage System

 In this system, clients can upload files to a dedicated File Storage folder, ensuring their data is easily accessible. Users can then download available files from other peers. The system also allows clients to list all files they have stored, along with their associated peers, making it easier to discover and retrieve content. With the ability to view all connected clients through their public keys, users can gain transparency and trust in the network. A built-in set of commands enables fluid interaction with the system, and clients can exit the application at any time. This decentralized architecture eliminates the need for central servers, enhancing both privacy and scalability. In this design, TCP sockets were utilized to establish reliable connections for file transfers between clients, while UDP datagram sockets facilitated efficient peer discovery by enabling connectionless communication. 

## How to Run
1. **Compile the Java Files:**
   ```bash
   javac *.java
   ```
   
2. **Run the Client:**
   ```bash
   java Peer
   ```

After running the system, share the generated public key with individuals who would like to join your network to allow the P2P Connection
