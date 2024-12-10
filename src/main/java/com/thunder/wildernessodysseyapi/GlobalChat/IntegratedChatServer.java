package com.thunder.wildernessodysseyapi.GlobalChat;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class IntegratedChatServer {

    private static final int PORT = 25582;
    private static final CopyOnWriteArrayList<PrintWriter> clients = new CopyOnWriteArrayList<>();
    private static ServerSocket serverSocket;

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        startServer(event.getServer());
    }

    public static void startServer(MinecraftServer server) {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("[GlobalChat] Chat server started on port " + PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[GlobalChat] Client connected.");

                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    clients.add(out);

                    new Thread(() -> handleClient(clientSocket, out)).start();
                }
            } catch (IOException e) {
                System.err.println("[GlobalChat] Server error: " + e.getMessage());
            }
        }).start();
    }

    private static void handleClient(Socket socket, PrintWriter out) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("[GlobalChat] Message received: " + message);
                broadcast(message);
            }
        } catch (IOException e) {
            System.err.println("[GlobalChat] Client disconnected.");
        } finally {
            clients.remove(out);
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("[GlobalChat] Failed to close socket: " + e.getMessage());
            }
        }
    }

    private static void broadcast(String message) {
        for (PrintWriter client : clients) {
            client.println(message);
        }
    }
}
