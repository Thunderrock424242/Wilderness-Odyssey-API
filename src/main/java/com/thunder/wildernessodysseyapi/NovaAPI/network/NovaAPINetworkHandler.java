package com.thunder.wildernessodysseyapi.NovaAPI.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class NovaAPINetworkHandler {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 25565;

    public static void connectToDedicatedServer() {
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                    if (msg.equals("AUTH_SUCCESS")) {
                                        NovaAPI.LOGGER.info("[Nova API] Successfully connected to dedicated server.");
                                    } else {
                                        NovaAPI.LOGGER.warn("[Nova API] Server denied access: " + msg);
                                    }
                                }
                            });
                        }
                    });

            ChannelFuture future = bootstrap.connect(SERVER_IP, SERVER_PORT).sync();
            future.channel().writeAndFlush("AUTH_REQUEST\n");
        } catch (Exception e) {
            NovaAPI.LOGGER.error("[Nova API] Failed to connect to dedicated server.");
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}