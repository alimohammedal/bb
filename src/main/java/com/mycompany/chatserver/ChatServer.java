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
import java.sql.*;
import java.net.*;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class ChatServer {
   private static final String DB_URL = "jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?user=postgres.vchbhoampyefrguzofuv&password=19988242fa19988242";
private static final String DB_USER = "postgres"; // أو اسم المستخدم الصحيح لحساب Supabase
private static final String DB_PASSWORD = "19988242fa19988242";

    public static void main(String[] args) {
        
        try (ServerSocket serverSocket = new ServerSocket(5432)) {
            System.out.println("Server is running on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            ) {
                System.out.println("Client connected: " + socket.getInetAddress());

                String request;
                while ((request = in.readLine()) != null) {
                    String[] parts = request.split(" ", 2);
                    String command = parts[0];

                    if (command.equals("REGISTER")) {
                        String phoneNumber = parts[1];
                        PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (phone_number) VALUES (?) ON CONFLICT DO NOTHING");
                        stmt.setString(1, phoneNumber);
                        stmt.executeUpdate();
                        out.println("REGISTERED");
                    }

                    else if (command.equals("SEND")) {
                        String[] messageParts = parts[1].split(",", 3);
                        int senderId = Integer.parseInt(messageParts[0]);
                        int receiverId = Integer.parseInt(messageParts[1]);
                        String message = messageParts[2];

                        PreparedStatement stmt = conn.prepareStatement("INSERT INTO messages (sender_id, receiver_id, message) VALUES (?, ?, ?)");
                        stmt.setInt(1, senderId);
                        stmt.setInt(2, receiverId);
                        stmt.setString(3, message);
                        stmt.executeUpdate();

                        out.println("MESSAGE_SENT");
                    }

                    else if (command.equals("FETCH")) {
                        int userId = Integer.parseInt(parts[1]);
                        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM messages WHERE receiver_id = ?");
                        stmt.setInt(1, userId);
                        ResultSet rs = stmt.executeQuery();

                        while (rs.next()) {
                            String msg = rs.getString("message");
                            out.println("MESSAGE: " + msg);
                        }
                        out.println("END_OF_MESSAGES");
                    }
                }

            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }
    }
    }

