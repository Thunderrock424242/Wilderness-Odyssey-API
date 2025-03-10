package com.thunder.wildernessodysseyapi.NovaAPI.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NovaAPIServer {
    private static final int PORT = 25565;
    private static final Set<String> whitelistedServers = new HashSet<>();
    private static final Map<Socket, String> connectedServers = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("[Nova API Server] Starting...");
        loadWhitelist();
        startServer();
    }

    private static void loadWhitelist() {
        File whitelistFile = new File("whitelist.txt");
        if (!whitelistFile.exists()) {
            try (FileWriter writer = new FileWriter(whitelistFile)) {
                writer.write("# Add trusted server IPs here, one per line\n");
            } catch (IOException e) {
                System.err.println("[Nova API Server] Failed to create whitelist.txt");
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(whitelistFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    whitelistedServers.add(line.trim());
                }
            }
            System.out.println("[Nova API Server] Loaded " + whitelistedServers.size() + " whitelisted servers.");
        } catch (IOException e) {
            System.err.println("[Nova API Server] Error loading whitelist.");
        }
    }

    private static void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Nova API Server] Listening on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("[Nova API Server] Failed to start server: " + e.getMessage());
        }
    }

    private static void handleClient(Socket socket) {
        String serverIP = socket.getInetAddress().getHostAddress();

        if (!whitelistedServers.contains(serverIP)) {
            System.err.println("[Nova API Server] Connection denied: " + serverIP + " is not whitelisted.");
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("[Nova API Server] Error closing connection: " + e.getMessage());
            }
            return;
        }

        System.out.println("[Nova API Server] Authorized connection from: " + serverIP);
        connectedServers.put(socket, serverIP);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            writer.write("AUTH_SUCCESS\n");
            writer.flush();

            while (true) {
                String message = reader.readLine();
                if (message == null) break;
                handleMessage(socket, message);
            }

        } catch (IOException e) {
            System.err.println("[Nova API Server] Connection error: " + e.getMessage());
        } finally {
            connectedServers.remove(socket);
        }
    }

    private static void handleMessage(Socket socket, String message) {
        System.out.println("[Nova API Server] Received from " + connectedServers.get(socket) + ": " + message);
        // TODO: Process chunk preloading, pathfinding, and sync requests
    }
}