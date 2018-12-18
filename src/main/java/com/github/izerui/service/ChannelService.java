package com.github.izerui.service;

import com.github.izerui.support.ChannelIdRedisTemplate;
import com.github.izerui.support.WebsocketMsg;
import com.google.gson.Gson;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Transactional
public class ChannelService {

    private static final ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private static String CHANNEL_REDIS_PREFIX = "wss:channel:";

    /**
     * 保存在redis中的临时websocket channelId的有效时长 单位：5分钟
     */
    private int CHANNEL_ID_TIMEOUT = 5;

    @Autowired
    private ChannelIdRedisTemplate redisTemplate;

    /**
     * 发送消息给前端
     */
    public void sendMessage(WebsocketMsg msg) {
        // 如果msg中userId 为null表示群发给当前所有前端连接
        List<Channel> channels = findChannels(msg.getUserId());
        for (Channel channel : channels) {
            channel.writeAndFlush(new TextWebSocketFrame(msg.getPayload()));
            activeConnect(channel);
        }
    }


    /**
     * 接收前端发送过来的消息
     */
    public void readMessage(Channel channel, String body) throws Exception {
        Gson gson = new Gson();
        WebsocketMsg msg = gson.fromJson(body, WebsocketMsg.class);
        Assert.notNull(msg.getUserId(), "用户标示不能为空");
        String key = CHANNEL_REDIS_PREFIX + msg.getUserId() + ":" + channel.id().asLongText();
        //如果是第一次连接
        if (!channel.hasAttr(AttributeKey.valueOf("key"))) {
            channel.attr(AttributeKey.valueOf("key")).set(key);
            channel.attr(AttributeKey.valueOf("userId")).set(msg.getUserId());
            channelGroup.add(channel);
        }
        // 激活key
        redisTemplate.boundValueOps(key).set(channel.id(), CHANNEL_ID_TIMEOUT, TimeUnit.MINUTES);
        //TODO 转发消息 处理业务
        log.info("接收到来自{}的消息:{}", msg.getUserId(), msg.getPayload());
    }

    /**
     * 断开连接
     */
    public void disconnect(Channel channel) {
        String key = (String) channel.attr(AttributeKey.valueOf("key")).get();
        if (key != null) {
            redisTemplate.delete(key);
        }
        channel.close();
        channelGroup.remove(channel);
    }


    /**
     * 激活通道
     *
     * @param channel
     */
    public void activeConnect(Channel channel) {
        String key = (String) channel.attr(AttributeKey.valueOf("key")).get();
        if (key != null && !"".equals(key)) {
            redisTemplate.boundValueOps(key).expire(CHANNEL_ID_TIMEOUT, TimeUnit.MINUTES);
        }
    }

    /**
     * 在线连接的客户端数量
     *
     * @return
     */
    public int connections() {
        return channelGroup.size();
    }

    private List<Channel> findChannels(String userId) {
        if (userId == null) {
            userId = "";
        }
        Set<String> keys = redisTemplate.keys(CHANNEL_REDIS_PREFIX + userId + "*");
        List<Channel> channels = new ArrayList<>();
        for (String key : keys) {
            ChannelId channelId = redisTemplate.boundValueOps(key).get();
            if (channelId != null) {
                Channel channel = channelGroup.find(channelId);
                if (channel != null) {
                    channels.add(channel);
                }
            }
        }
        return channels;
    }

}
