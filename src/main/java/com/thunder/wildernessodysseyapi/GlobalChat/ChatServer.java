package com.thunder.wildernessodysseyapi.GlobalChat;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 25582;
    private static final CopyOnWriteArrayList<PrintWriter> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        System.out.println("[GlobalChat] Starting server...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[GlobalChat] New client connected.");

                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                clients.add(out);

                new Thread(() -> handleClient(clientSocket, out)).start();
            }
        } catch (IOException e) {
            System.err.println("[GlobalChat] Server failed: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket, PrintWriter out) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("[GlobalChat] " + message);
                broadcast(message);
            }
        } catch (IOException e) {
            System.err.println("[GlobalChat] Client disconnected.");
        } finally {
            clients.remove(out);
        }
    }

    private static void broadcast(String message) {
        for (PrintWriter client : clients) {
            client.println(message);
        }
    }
}
