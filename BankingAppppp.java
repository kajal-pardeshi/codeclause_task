package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class BankingAppppp extends JFrame {

    private JTextField userIdField, passwordField, transferAmountField, recipientAccountField;
    private JTextArea transactionHistoryArea;
    private JButton loginButton, transferButton, viewHistoryButton;
    private int loggedInUserId;

    // Database connection parameters
    private static final String URL = "jdbc:postgresql://localhost:5432/online_banking";
    private static final String USER = "your_username"; // Replace with actual username
    private static final String PASSWORD = "your_password"; // Replace with actual password

    public BankingAppppp() {
        setTitle("Online Banking System");
        setSize(400, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new FlowLayout());

        // User Login Fields
        add(new JLabel("User ID:"));
        userIdField = new JTextField(20);
        add(userIdField);

        add(new JLabel("Password:"));
        passwordField = new JTextField(20);
        add(passwordField);

        loginButton = new JButton("Login");
        add(loginButton);

        // Action for Login Button
        loginButton.addActionListener(e -> login());

        // Transfer Fields (Hidden until Login)
        add(new JLabel("Transfer Amount:"));
        transferAmountField = new JTextField(20);
        transferAmountField.setEnabled(false);
        add(transferAmountField);

        add(new JLabel("Recipient Account:"));
        recipientAccountField = new JTextField(20);
        recipientAccountField.setEnabled(false);
        add(recipientAccountField);

        transferButton = new JButton("Transfer Money");
        transferButton.setEnabled(false);
        add(transferButton);

        // Action for Transfer Button
        transferButton.addActionListener(e -> transferFunds());

        // Transaction History Button
        viewHistoryButton = new JButton("View Transaction History");
        viewHistoryButton.setEnabled(false);
        add(viewHistoryButton);

        // Action for History Button
        viewHistoryButton.addActionListener(e -> viewTransactionHistory());
        
                // Transaction History Area
                transactionHistoryArea = new JTextArea(10, 30);
                transactionHistoryArea.setEditable(false);
                add(new JScrollPane(transactionHistoryArea));
            }
        
            private Object viewTransactionHistory() {
                // TODO Auto-generated method stub
                throw new UnsupportedOperationException("Unimplemented method 'viewTransactionHistory'");
            }
        
            // Login Method
    private void login() {
        String userId = userIdField.getText();
        String password = passwordField.getText();

        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String query = "SELECT * FROM users WHERE username = ? AND password = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, userId);
            stmt.setString(2, password);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                loggedInUserId = rs.getInt("user_id");
                JOptionPane.showMessageDialog(this, "Login successful!");

                transferAmountField.setEnabled(true);
                recipientAccountField.setEnabled(true);
                transferButton.setEnabled(true);
                viewHistoryButton.setEnabled(true);
                userIdField.setEnabled(false);
                passwordField.setEnabled(false);
                loginButton.setEnabled(false);
            } else {
                JOptionPane.showMessageDialog(this, "Invalid login credentials.");
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    // Transfer Funds Method
    private void transferFunds() {
        try {
            double amount = Double.parseDouble(transferAmountField.getText().trim());
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this, "❌ Invalid amount.");
                return;
            }

            String recipientAccount = recipientAccountField.getText().trim();
            if (recipientAccount.isEmpty()) {
                JOptionPane.showMessageDialog(this, "❌ Please enter recipient account.");
                return;
            }

            try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
                connection.setAutoCommit(false);

                // Check if recipient account exists
                String recipientQuery = "SELECT user_id FROM accounts WHERE account_number = ?";
                try (PreparedStatement recipientStmt = connection.prepareStatement(recipientQuery)) {
                    recipientStmt.setString(1, recipientAccount);
                    try (ResultSet recipientRs = recipientStmt.executeQuery()) {
                        if (!recipientRs.next()) {
                            JOptionPane.showMessageDialog(this, "❌ Recipient account does not exist.");
                            connection.rollback();
                            return;
                        }
                        int recipientId = recipientRs.getInt("user_id");

                        // Check sender balance
                        String balanceQuery = "SELECT balance FROM accounts WHERE user_id = ?";
                        try (PreparedStatement balanceStmt = connection.prepareStatement(balanceQuery)) {
                            balanceStmt.setInt(1, loggedInUserId);
                            try (ResultSet balanceRs = balanceStmt.executeQuery()) {
                                if (!balanceRs.next() || balanceRs.getDouble("balance") < amount) {
                                    JOptionPane.showMessageDialog(this, "❌ Insufficient funds.");
                                    connection.rollback();
                                    return;
                                }
                            }
                        }

                        // Deduct amount from sender
                        String deductQuery = "UPDATE accounts SET balance = balance - ? WHERE user_id = ?";
                        try (PreparedStatement deductStmt = connection.prepareStatement(deductQuery)) {
                            deductStmt.setDouble(1, amount);
                            deductStmt.setInt(2, loggedInUserId);
                            deductStmt.executeUpdate();
                        }

                        // Add amount to recipient
                        String addQuery = "UPDATE accounts SET balance = balance + ? WHERE user_id = ?";
                        try (PreparedStatement addStmt = connection.prepareStatement(addQuery)) {
                            addStmt.setDouble(1, amount);
                            addStmt.setInt(2, recipientId);
                            addStmt.executeUpdate();
                        }

                        // Log transaction
                        String transactionQuery = "INSERT INTO transactions (sender_account, receiver_account, amount, status) VALUES (?, ?, ?, 'completed')";
                        try (PreparedStatement transactionStmt = connection.prepareStatement(transactionQuery)) {
                            transactionStmt.setInt(1, loggedInUserId);
                            transactionStmt.setInt(2, recipientId);
                            transactionStmt.setDouble(3, amount);
                            transactionStmt.executeUpdate();
                        }

                        connection.commit();
                        JOptionPane.showMessageDialog(this, "✅ Transaction Successful!");
                    }
                }
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "❌ Invalid amount.");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "❌ Error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new BankingAppppp().setVisible(true);
    }
}
