/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.chatserver;

/**
 *
 * @author Al-hlaly
 */

import java.net.*;
import java.io.*;
import java.sql.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 1234;
    private static HashMap<String, ClientHandler> clients = new HashMap<>();
    private static Connection dbConnection;

    public static void main(String[] args) {
        connectToDB();
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running on port " + PORT);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void connectToDB() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            dbConnection = DriverManager.getConnection(
                "jdbc:mysql://your-db-host:3306/chat_app",
                "db_user",
                "db_password"
            );
            System.out.println("Connected to database");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String phoneNumber;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                phoneNumber = in.readLine();
                clients.put(phoneNumber, this);
                System.out.println(phoneNumber + " connected");

                handleMessages();

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                    clients.remove(phoneNumber);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleMessages() throws IOException {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String[] parts = inputLine.split(":", 3);
                String command = parts[0];
                
                switch (command) {
                    case "REGISTER":
                        registerUser(parts[1]);
                        break;
                    case "MESSAGE":
                        sendMessage(parts[1], parts[2]);
                        break;
                    case "GET_HISTORY":
                        sendHistory(parts[1]);
                        break;
                }
            }
        }

        private void registerUser(String phone) {
            try (PreparedStatement stmt = dbConnection.prepareStatement(
                "INSERT INTO users (phone_number) VALUES (?)")) {
                stmt.setString(1, phone);
                stmt.executeUpdate();
                out.println("REGISTER_SUCCESS");
            } catch (SQLException e) {
                out.println("REGISTER_FAIL");
            }
        }

        private void sendMessage(String receiverPhone, String message) {
            try {
                // Get user IDs
                int senderId = getUserId(phoneNumber);
                int receiverId = getUserId(receiverPhone);

                // Save to DB
                PreparedStatement stmt = dbConnection.prepareStatement(
                    "INSERT INTO messages (sender_id, receiver_id, message) VALUES (?, ?, ?)");
                stmt.setInt(1, senderId);
                stmt.setInt(2, receiverId);
                stmt.setString(3, message);
                stmt.executeUpdate();

                // Forward to receiver if online
                ClientHandler receiver = clients.get(receiverPhone);
                if (receiver != null) {
                    receiver.out.println("MESSAGE:" + phoneNumber + ":" + message);
                }

                out.println("MESSAGE_SENT");

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private int getUserId(String phone) throws SQLException {
            PreparedStatement stmt = dbConnection.prepareStatement(
                "SELECT id FROM users WHERE phone_number = ?");
            stmt.setString(1, phone);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }

        private void sendHistory(String contactPhone) {
            try {
                int userId = getUserId(phoneNumber);
                int contactId = getUserId(contactPhone);

                PreparedStatement stmt = dbConnection.prepareStatement(
                    "SELECT * FROM messages WHERE (sender_id = ? AND receiver_id = ?) " +
                    "OR (sender_id = ? AND receiver_id = ?) ORDER BY sent_at");
                stmt.setInt(1, userId);
                stmt.setInt(2, contactId);
                stmt.setInt(3, contactId);
                stmt.setInt(4, userId);

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String historyMsg = String.format("HISTORY:%s:%s",
                        rs.getString("sender_id").equals(String.valueOf(userId)) ? "You" : contactPhone,
                        rs.getString("message"));
                    out.println(historyMsg);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
