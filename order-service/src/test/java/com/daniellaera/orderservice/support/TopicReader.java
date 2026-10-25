package com.daniellaera.orderservice.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads topics in tests without a consumer group: the partitions are assigned directly and read from the beginning
 * up to their end offsets, so no group coordinator is involved. On a fresh broker the first groups wait seconds for
 * the coordinator (__consumer_offsets is created and loaded on first use); a reader in its own group came back empty
 * when that outlasted its window on a busy runner (main run 259).
 */
public final class TopicReader {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private TopicReader() {
    }

    /** Every record on the topic, created if it does not exist yet (as a subscribing consumer would). */
    public static List<String> readAll(String bootstrapServers, String topic) {
        try (KafkaConsumer<String, String> consumer = consumer(bootstrapServers)) {
            return readToEnd(consumer, toPartitions(consumer.partitionsFor(topic, TIMEOUT)));
        }
    }

    /** Every record on the existing topics matching the pattern; at least one must match. */
    public static List<String> readAll(String bootstrapServers, Pattern topics) {
        try (KafkaConsumer<String, String> consumer = consumer(bootstrapServers)) {
            List<TopicPartition> partitions = toPartitions(consumer.listTopics(TIMEOUT).values().stream()
                    .flatMap(List::stream)
                    .filter(partition -> topics.matcher(partition.topic()).matches())
                    .toList());
            assertThat(partitions).as("partitions of the topics matching %s", topics).isNotEmpty();
            return readToEnd(consumer, partitions);
        }
    }

    private static List<String> readToEnd(KafkaConsumer<String, String> consumer, List<TopicPartition> partitions) {
        consumer.assign(partitions);
        consumer.seekToBeginning(partitions);
        Map<TopicPartition, Long> end = consumer.endOffsets(partitions, TIMEOUT);
        List<String> messages = new ArrayList<>();
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (partitions.stream().anyMatch(partition -> consumer.position(partition, TIMEOUT) < end.get(partition))) {
            assertThat(System.nanoTime()).as("read %s up to %s within %s", partitions, end, TIMEOUT).isLessThan(deadline);
            consumer.poll(Duration.ofMillis(200)).forEach(record -> messages.add(record.value()));
        }
        return messages;
    }

    private static List<TopicPartition> toPartitions(List<PartitionInfo> partitions) {
        return partitions.stream().map(partition -> new TopicPartition(partition.topic(), partition.partition())).toList();
    }

    private static KafkaConsumer<String, String> consumer(String bootstrapServers) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer());
    }
}
