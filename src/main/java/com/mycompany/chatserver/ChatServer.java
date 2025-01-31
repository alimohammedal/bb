/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.chatserver;

/**
 *
 * @author Al-hlaly
 */

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 80; // المنفذ
    private static Set<PrintWriter> clientWriters = new HashSet<>();
    private static Connection connection;

    public static void main(String[] args) throws IOException {
        try {
            // الاتصال بقاعدة البيانات باستخدام متغيرات البيئة
            String dbUrl = System.getenv("DATABASE_URL");
            connection = DriverManager.getConnection(dbUrl, "username", "password");
            System.out.println("Database connected!");

            // بدء الاستماع للطلبات على المنفذ
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server started...");

            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // إنشاء مجرى البيانات مع العميل
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // إضافة العميل إلى مجموعة الكتابة
                synchronized (clientWriters) {
                    clientWriters.add(out);
                }

                // الحصول على اسم المستخدم
                username = in.readLine(); // اسم المستخدم مرسل من العميل
                if (username != null && !username.isEmpty()) {
                    // التحقق من وجود المستخدم في قاعدة البيانات
                    addUserToDatabase(username);
                }

                // التعامل مع الرسائل القادمة من العميل
                String message;
                while ((message = in.readLine()) != null) {
                    // إذا كانت الرسالة لبدء دردشة جديدة
                    if (message.startsWith("New Chat with: ")) {
                        // تسجيل دردشة جديدة في قاعدة البيانات
                        String recipient = message.substring(15);
                        saveMessageToDatabase(username, recipient, "New chat started.");
                    }

                    // إذا كانت الرسالة لطلب تحميل الدردشات السابقة
                    if (message.equals("Load Previous Chats")) {
                        sendPreviousChats();
                    }

                    // إرسال الرسالة إلى جميع العملاء المتصلين
                    sendToAllClients(username + ": " + message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void addUserToDatabase(String username) {
            try {
                // إذا كان المستخدم غير موجود في قاعدة البيانات، يتم إضافته
                String query = "SELECT * FROM users WHERE username = ?";
                PreparedStatement stmt = connection.prepareStatement(query);
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                if (!rs.next()) {
                    query = "INSERT INTO users (username) VALUES (?)";
                    stmt = connection.prepareStatement(query);
                    stmt.setString(1, username);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void saveMessageToDatabase(String sender, String recipient, String message) {
            try {
                String query = "INSERT INTO messages (sender, recipient, message) VALUES (?, ?, ?)";
                PreparedStatement stmt = connection.prepareStatement(query);
                stmt.setString(1, sender);
                stmt.setString(2, recipient);
                stmt.setString(3, message);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void sendPreviousChats() {
            try {
                String query = "SELECT * FROM messages WHERE sender = ? OR recipient = ? ORDER BY timestamp";
                PreparedStatement stmt = connection.prepareStatement(query);
                stmt.setString(1, username);
                stmt.setString(2, username);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String recipient = rs.getString("recipient");
                    String message = rs.getString("message");
                    out.println(sender + " to " + recipient + ": " + message);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void sendToAllClients(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message);
                }
            }
        }
    }
}