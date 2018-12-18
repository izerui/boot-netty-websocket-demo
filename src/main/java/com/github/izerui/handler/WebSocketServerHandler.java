package com.github.izerui.handler;

import com.github.izerui.service.ChannelService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private ChannelService channelService;

    public WebSocketServerHandler(ChannelService channelService) {
        this.channelService = channelService;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channelService.disconnect(ctx.channel());
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.info(cause.getMessage(), cause);
        try {
            channelService.disconnect(ctx.channel());
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        ctx.close();
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            channelService.activeConnect(ctx.channel());
            return;
        }
        if (frame instanceof PongWebSocketFrame) {
            channelService.activeConnect(ctx.channel());
            return;
        }
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            channelService.readMessage(ctx.channel(), text);
            return;
        }
    }
}
