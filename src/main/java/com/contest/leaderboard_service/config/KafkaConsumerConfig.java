package com.contest.leaderboard_service.config;

import com.contest.leaderboard_service.dto.ContestStartedEvent;
import com.contest.leaderboard_service.dto.SubmissionResultEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "leaderboard-service");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }

    // ── Factory for SubmissionResultEvent ──
    @Bean
    public ConsumerFactory<String, SubmissionResultEvent> submissionConsumerFactory() {
        JsonDeserializer<SubmissionResultEvent> deserializer =
                new JsonDeserializer<>(SubmissionResultEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SubmissionResultEvent>
    submissionKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, SubmissionResultEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(submissionConsumerFactory());
        return factory;
    }

    // ── Factory for ContestStartedEvent ──
    @Bean
    public ConsumerFactory<String, ContestStartedEvent> contestConsumerFactory() {
        JsonDeserializer<ContestStartedEvent> deserializer =
                new JsonDeserializer<>(ContestStartedEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeMapperForKey(true);

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ContestStartedEvent>
    contestKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, ContestStartedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(contestConsumerFactory());
        return factory;
    }
}
