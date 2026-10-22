package com.daniellaera.sagae2e.support;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.errors.WakeupException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records audit-topic and payment-topic from the beginning, can publish raw messages, and can tell whether a consumer
 * group has consumed past a given record.
 */
public final class KafkaTraffic {

    public static final String AUDIT_TOPIC = "audit-topic";
    public static final String PAYMENT_TOPIC = "payment-topic";

    public record Message(String topic, int partition, long offset, String value, JsonNode json) {

        public boolean consumedBy(Map<TopicPartition, OffsetAndMetadata> committed) {
            OffsetAndMetadata position = committed.get(new TopicPartition(topic, partition));
            return position != null && position.offset() > offset;
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String bootstrapServers;
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private final KafkaProducer<String, String> producer;
    private final Admin admin;

    KafkaTraffic(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        this.producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        this.admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        Thread recorder = new Thread(this::record, "saga-e2e-kafka-recorder");
        recorder.setDaemon(true);
        recorder.start();
    }

    private void record() {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "saga-e2e-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                // the services create the topics on first use: notice them quickly
                ConsumerConfig.METADATA_MAX_AGE_CONFIG, 1000,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(AUDIT_TOPIC, PAYMENT_TOPIC));
            while (!Thread.currentThread().isInterrupted()) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    messages.add(new Message(record.topic(), record.partition(), record.offset(), record.value(),
                            JSON.readTree(record.value())));
                }
            }
        } catch (WakeupException e) {
            // shutting down
        }
    }

    public List<Message> messages(String topic) {
        return messages.stream().filter(message -> message.topic().equals(topic)).toList();
    }

    /** Publishes the value as is and returns where it landed. */
    public Message publish(String topic, String value) {
        try {
            var metadata = producer.send(new ProducerRecord<>(topic, value)).get();
            return new Message(topic, metadata.partition(), metadata.offset(), value, JSON.readTree(value));
        } catch (Exception e) {
            throw new IllegalStateException("Could not publish to " + topic, e);
        }
    }

    public Map<TopicPartition, OffsetAndMetadata> committedOffsets(String groupId) {
        try {
            return admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the offsets of " + groupId, e);
        }
    }
}
