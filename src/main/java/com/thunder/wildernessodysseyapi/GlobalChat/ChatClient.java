package com.thunder.wildernessodysseyapi.GlobalChat;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;

public class ChatClient {
    private static Socket socket;
    private static BufferedReader input;
    private static PrintWriter output;

    public static void startClient(String ip, int port) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                socket = new Socket(ip, port);
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                output = new PrintWriter(socket.getOutputStream(), true);

                System.out.println("[GlobalChat] Connected to global chat server.");

                while (!socket.isClosed()) {
                    String message = input.readLine();
                    if (message != null) {
                        System.out.println("[GlobalChat] " + message); // Replace it with chat GUI display logic
                    }
                }
            } catch (IOException e) {
                System.err.println("[GlobalChat] Connection failed: " + e.getMessage());
            }
        });
    }

    public static void sendMessage(String message) {
        if (output != null) {
            output.println(message);
        }
    }
}