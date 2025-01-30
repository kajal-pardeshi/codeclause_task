package com.example;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

// Database utility for handling PostgreSQL database operations
class DatabaseUtil {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/dfs";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "password";

    // Get a connection to the database
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    // Insert file metadata into the database
    public static int insertFileMetadata(String fileName, int chunkCount) throws SQLException {
        String query = "INSERT INTO files(file_name, chunk_count) VALUES(?, ?) RETURNING file_id";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, fileName);
            ps.setInt(2, chunkCount);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("file_id");
            }
        }
        return -1;
    }

    // Insert file chunk metadata into the database
    public static void insertFileChunk(int fileId, int chunkIndex, int storageNodeId, String filePath) throws SQLException {
        String query = "INSERT INTO file_chunks(file_id, chunk_index, storage_node_id, file_path) VALUES(?, ?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setInt(1, fileId);
            ps.setInt(2, chunkIndex);
            ps.setInt(3, storageNodeId);
            ps.setString(4, filePath);
            ps.executeUpdate();
        }
    }

    // Retrieve storage nodes
    public static ResultSet getStorageNodes() throws SQLException {
        String query = "SELECT * FROM storage_nodes";
        Connection connection = getConnection();
        Statement stmt = connection.createStatement();
        return stmt.executeQuery(query);
    }
}

// File Server for handling client requests
class FileServer {
    private static final int PORT = 12345;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(53545);
        System.out.println("File Server started...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            threadPool.submit(() -> handleClientRequest(clientSocket));
        }
    }

    private static void handleClientRequest(Socket clientSocket) {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            String command = in.readUTF();
            if ("UPLOAD".equals(command)) {
                // Handle file upload
                String fileName = in.readUTF();
                int chunkCount = in.readInt();
                int fileId = DatabaseUtil.insertFileMetadata(fileName, chunkCount);
                out.writeInt(fileId);
                System.out.println("File metadata saved. File ID: " + fileId);
            } else if ("DOWNLOAD".equals(command)) {
                // Handle file download
                int fileId = in.readInt();
                ResultSet rs = getFileChunks(fileId);
                while (rs.next()) {
                    int chunkIndex = rs.getInt("chunk_index");
                    int storageNodeId = rs.getInt("storage_node_id");
                    String filePath = rs.getString("file_path");
                    out.writeInt(chunkIndex);
                    out.writeInt(storageNodeId);
                    out.writeUTF(filePath);
                }
                out.writeInt(-1); // Indicate end of file chunks
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    private static ResultSet getFileChunks(int fileId) throws SQLException {
        String query = "SELECT * FROM file_chunks WHERE file_id = ?";
        Connection connection = DatabaseUtil.getConnection();
        PreparedStatement ps = connection.prepareStatement(query);
        ps.setInt(1, fileId);
        return ps.executeQuery();
    }
}

// Client to upload and download files from the server
class FileClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) throws IOException {
        Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        // Upload a file
        out.writeUTF("UPLOAD");
        out.writeUTF("example.txt");
        out.writeInt(3); // Assume file is divided into 3 chunks
        int fileId = in.readInt();
        System.out.println("File uploaded with ID: " + fileId);

        // Download a file
        out.writeUTF("DOWNLOAD");
        out.writeInt(fileId);
        while (true) {
            int chunkIndex = in.readInt();
            if (chunkIndex == -1) break;
            int storageNodeId = in.readInt();
            String filePath = in.readUTF();
            System.out.println("Downloading chunk " + chunkIndex + " from node " + storageNodeId + " at " + filePath);
            // Simulate file download logic here...
        }

        socket.close();
    }
}

// Main class to run everything
public class DistributedFileSystem {
    public static void main(String[] args) {
        // Start the File Server
        Thread serverThread = new Thread(() -> {
            try {
                FileServer.main(args);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // Start the File Client
        Thread clientThread = new Thread(() -> {
            try {
                FileClient.main(args);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        clientThread.start();
    }
}
