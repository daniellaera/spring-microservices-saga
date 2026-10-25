package com.daniellaera.inventoryservice.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.charset.StandardCharsets;

/** What a dead-lettered record says about its last failure, for the listeners' @DltHandler methods. */
final class DeadLetters {

    private DeadLetters() {
    }

    // the retry topics record it as kafka_exception-message, a plain dead-letter recoverer as kafka_dlt-...
    static String errorOf(ConsumerRecord<String, String> record) {
        String error = headerValue(record, KafkaHeaders.EXCEPTION_MESSAGE);
        return error != null ? error : headerValue(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
    }

    private static String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
