package com.github.izerui;

import com.github.izerui.service.ChannelService;
import com.github.izerui.support.ChannelIdRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Application {

    @Bean
    public ChannelIdRedisTemplate channelIdBeanRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new ChannelIdRedisTemplate(redisConnectionFactory);
    }

    @Bean
    public WssServer server(@Value("${websocket.port}") int port, ChannelService channelService) {
        return new WssServer(port, channelService);
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
