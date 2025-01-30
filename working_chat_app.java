package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class working_chat_app {
    private static final int PORT = 12345;
    private static Set<PrintWriter> clientWriters = new HashSet<>();
    private static JTextArea chatArea;
    private static JTextField messageField;
    private static PrintWriter out;
    private static BufferedReader in;
    private static Socket socket;
    private static String clientName;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            createUI();
            String mode = JOptionPane.showInputDialog("Enter 'server' to start as server or 'client' to start as client:");
            if (mode != null && mode.equalsIgnoreCase("server")) {
                startServer();
            } else if (mode != null && mode.equalsIgnoreCase("client")) {
                startClient();
            }
        });
    }

    private static void createUI() {
        JFrame frame = new JFrame("Chat Application");
        frame.setSize(400, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Chat display area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // Message input area
        messageField = new JTextField();
        frame.add(messageField, BorderLayout.SOUTH);

        // Send message button
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendMessage());
        frame.add(sendButton, BorderLayout.EAST);

        frame.setVisible(true);
        messageField.requestFocus();
    }

    private static void sendMessage() {
        String message = messageField.getText();
        if (!message.trim().isEmpty()) {
            out.println(message);
            messageField.setText("");
        }
    }

    private static void startServer() {
        System.out.println("Chat server started...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startClient() {
        try {
            socket = new Socket("localhost", PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Start the listening thread
            Thread listenerThread = new Thread(working_chat_app::listenForMessages);
            listenerThread.start();

            // Prompt for name and send to server
            String name = JOptionPane.showInputDialog("Enter your name:");
            if (name != null && !name.trim().isEmpty()) {
                clientName = name;
                out.println(clientName);
            } else {
                clientName = "Anonymous";
                out.println(clientName);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void listenForMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                chatArea.append(message + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                synchronized (clientWriters) {
                    clientWriters.add(out);
                }

                out.println("Enter your name: ");
                clientName = in.readLine();
                System.out.println(clientName + " has joined the chat.");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) {
                        break;
                    }
                    broadcast(clientName + ": " + message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                    synchronized (clientWriters) {
                        clientWriters.remove(out);
                    }
                    broadcast(clientName + " has left the chat.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message);
                }
            }
        }
    }
}
