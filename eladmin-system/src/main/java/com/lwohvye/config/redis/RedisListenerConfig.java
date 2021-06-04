package com.lwohvye.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * @author lWoHvYe
 * @date 2018/10/27 20:56
 * @description 监听
 */
@Configuration
public class RedisListenerConfig {

    // TODO: 2021/6/4 待验证。多redis场景

    /**
     * @description 需修改redis.conf。  notify-keyspace-events Ex
     * K：keyspace事件，事件以__keyspace@<db>__为前缀进行发布；
     * E：Keyevent事件，事件以__keyevent@<db>__为前缀进行发布；
     * g：一般性的，非特定类型的命令，比如del，expire，rename等；
     * $：字符串特定命令；
     * l：列表特定命令；
     * s：集合特定命令；
     * h：哈希特定命令；
     * z：有序集合特定命令；
     * x：过期事件，当某个键过期并删除时会产生该事件；
     * e：驱逐事件，当某个键因maxmemore策略而被删除时，产生该事件；
     * A：g$lshzxe的别名，因此”AKE”意味着所有事件。
     * @author Hongyan Wang
     * @date 2021/6/4 1:46 下午
     * @param connectionFactory
     * @return org.springframework.data.redis.listener.RedisMessageListenerContainer
     */
    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
