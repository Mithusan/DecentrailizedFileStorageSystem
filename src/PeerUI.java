import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerUI {
    private Peer peer;  // Reference to the Peer class
    private PeerConnection selectedPeerConnection;
    // Swing components
    private JFrame frame;
    private JList<String> clientList;
    private JList<String> fileList;
    private DefaultListModel<String> clientListModel;
    private DefaultListModel<String> fileListModel;
    private JTextField fileNameField;
    private JButton downloadButton;
    private JButton uploadButton;
    private JTextArea fileDetailsArea;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PeerUI(Peer peer) {
        this.peer = peer;
        // Set up the UI components
        frame = new JFrame("Peer-to-Peer File Sharing");
        clientListModel = new DefaultListModel<>();
        fileListModel = new DefaultListModel<>();
        clientList = new JList<>(clientListModel);
        fileList = new JList<>(fileListModel);
        fileNameField = new JTextField(20);
        downloadButton = new JButton("Download File");
        uploadButton = new JButton("Upload File");
        fileDetailsArea = new JTextArea(10, 30);  // Text area to display file details
        fileDetailsArea.setEditable(false);  // Make the text area non-editable

        // Set up layout
        frame.setLayout(new BorderLayout());

        // North panel for file name input and buttons
        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Enter filename:"));
        topPanel.add(fileNameField);

        // Left panel for client list (smaller width)
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(150, 0));  // Smaller width for client list
        leftPanel.add(new JScrollPane(clientList), BorderLayout.CENTER);

        // Right panel for file list (larger width)
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        // Panel for file details (left of the buttons)
        JPanel detailsPanel = new JPanel();
        detailsPanel.setLayout(new BorderLayout());
        detailsPanel.add(new JScrollPane(fileDetailsArea), BorderLayout.CENTER);

        // Panel for download and upload buttons (below the file list)
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(uploadButton);
        buttonPanel.add(downloadButton);

        // Panel for both client list and file list
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BorderLayout()); // Use BorderLayout instead of GridLayout
        centerPanel.add(leftPanel, BorderLayout.WEST);  // Clients on the left
        centerPanel.add(rightPanel, BorderLayout.CENTER); // Files on the right

        // Panel for bottom buttons and file details
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.add(detailsPanel, BorderLayout.CENTER);  // File details
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);  // Buttons at the bottom

        // Add components to the frame
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Action listeners for buttons
        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedFile = fileList.getSelectedValue();
                if (selectedFile != null && selectedPeerConnection != null) {
                    selectedPeerConnection.sendFile(selectedFile);
                }
            }
        });

        // Add action listener to upload button
        uploadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Handle file upload action (existing code or modify as needed)
            }
        });

        // Set frame properties
        frame.setSize(800, 500);  // Increase size for more room
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Start listening and broadcasting
        peer.startListening();  // Start the peer listening for connections

        // Update client and file list when available
        
    }

    public void updateClientList() {
        // Clear current list
        clientListModel.clear();
        // Fetch active peers and add them to the client list
                
        for (PeerConnection peerConnection : Peer.getConnections().values()) {
            clientListModel.addElement(peerConnection.getClientIdentifier());
            clientList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    String selectedClient = clientList.getSelectedValue();
                    if (selectedClient != null) {
                        updateFileList(peerConnection); // Update file list for the selected client
                    }
                }
            });
        }
    
    }

    // Update the file list based on the selected client
    public void updateFileList(PeerConnection connection) {
        // Request the file list from the peer
        List<String> fileListFromPeer = connection.receiveFileList();

        // Clear current file list and add new file names
        fileListModel.clear(); // Clear the file list before adding new files

        for (String file : fileListFromPeer) {
            fileListModel.addElement(file); // Add each file to the file list
        }

        // Listen for file selection and update file details
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedFile = fileList.getSelectedValue();
                if (selectedFile != null) {
                    updateFileDetails(selectedFile);
                }
            }
        });
    }

    // Update the file details (name, size, type, and date modified)
    public void updateFileDetails(String fileName) {
        // Simulate file details. Replace with actual data fetching logic.
        File file = new File(fileName);

        // Simulate file properties
        String fileType = getFileType(fileName);
        long fileSize = file.length();
        String formattedDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(file.lastModified());

        // Prepare the file details string
        String details = String.format("File Name: %s\nFile Type: %s\nSize: %d bytes\nDate Modified: %s",
                fileName, fileType, fileSize, formattedDate);

        // Update the text area with the file details
        fileDetailsArea.setText(details);
    }

    // Utility method to get file type (based on file extension)
    private String getFileType(String fileName) {
        String type = "Unknown";
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex > 0) {
            type = fileName.substring(dotIndex + 1);
        }
        return type;
    }
}
