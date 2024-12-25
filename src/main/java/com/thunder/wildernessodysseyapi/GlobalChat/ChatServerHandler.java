package com.thunder.wildernessodysseyapi.GlobalChat;

public class ChatServerHandler {

    private final ChatServer chatServer;

    public ChatServerHandler() {
        // Initialize the ChatServer instance with a default port (e.g., 25565)
        int defaultPort = 25582;
        chatServer = new ChatServer(defaultPort);
    }

    /**
     * Starts the chat server.
     */
    public void startChatServer() {
        if (chatServer != null) {
            chatServer.start();
            System.out.println("Chat server started");
        } else {
            System.err.println("Chat server instance is null, unable to start.");
        }
    }

    /**
     * Stops the chat server.
     */
    public void stopChatServer() {
        if (chatServer != null) {
            chatServer.stop();
            System.out.println("Chat server stopped.");
        } else {
            System.err.println("Chat server instance is null, unable to stop.");
        }
    }
}
