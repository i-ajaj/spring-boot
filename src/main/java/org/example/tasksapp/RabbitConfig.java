package org.example.tasksapp;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public Queue taskQueue() {
        return new Queue("task-queue", false); // false = not durable
    }
}
