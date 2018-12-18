package com.github.izerui;

import com.github.izerui.handler.HttpServerRequestHandler;
import com.github.izerui.handler.WebSocketServerHandler;
import com.github.izerui.service.ChannelService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.TimeUnit;

@Slf4j
public class WssServer implements InitializingBean, DisposableBean, Runnable {

    public int port;
    private ChannelService channelService;

    private EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private EventLoopGroup workerGroup = new NioEventLoopGroup();

    public WssServer(int port, ChannelService channelService) {
        this.port = port;
        this.channelService = channelService;
    }

    @Override
    public void run() {
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new WebSocketServerInitializer());

            Channel ch = b.bind(port).sync().channel();
            ch.config().setOption(ChannelOption.TCP_NODELAY, true);
            ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);
            ch.closeFuture().sync();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            try {
                destroy();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        new Thread(this).start();
    }


    private class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("http-codec", new HttpServerCodec());
            pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
            pipeline.addLast("http-chunked", new ChunkedWriteHandler());
            pipeline.addLast(new HttpServerRequestHandler());
            pipeline.addLast(new WebSocketServerHandler(channelService));
            pipeline.addLast("ping", new IdleStateHandler(45, 45, 45, TimeUnit.SECONDS));
            pipeline.addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    ctx.channel().writeAndFlush(new PingWebSocketFrame());
                }
            });
        }
    }

}
