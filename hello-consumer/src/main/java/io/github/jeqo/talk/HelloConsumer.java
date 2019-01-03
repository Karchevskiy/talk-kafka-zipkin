package io.github.jeqo.talk;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import brave.sampler.Sampler;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.github.jeqo.poc.tracing.TracingHelper;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import static java.lang.System.out;

public class HelloConsumer {

	public static void main(String[] args) {

		final Config config = ConfigFactory.load();
		/* START TRACING INSTRUMENTATION */
		final Sender sender = TracingHelper.sender(config);
		final AsyncReporter<Span> reporter = AsyncReporter.builder(sender).build();
		final Tracing tracing = Tracing.newBuilder().localServiceName("hello-consumer")
				.sampler(Sampler.ALWAYS_SAMPLE).spanReporter(reporter).build();
		final KafkaTracing kafkaTracing = KafkaTracing.newBuilder(tracing)
				.remoteServiceName("kafka").build();
		/* END TRACING INSTRUMENTATION */

		final String kafkaBootstrapServers = config.getString("kafka.bootstrap-servers");
		final Properties consumerConfigs = new Properties();
		consumerConfigs.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
				kafkaBootstrapServers);
		consumerConfigs.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
				StringDeserializer.class.getName());
		consumerConfigs.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
				StringDeserializer.class.getName());
		consumerConfigs.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "hello-consumer");
		consumerConfigs.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
				OffsetResetStrategy.EARLIEST.name().toLowerCase());
		final Consumer<String, String> kafkaConsumer = new KafkaConsumer<>(
				consumerConfigs);
		final Consumer<String, String> tracingConsumer = kafkaTracing
				.consumer(kafkaConsumer);

		tracingConsumer.subscribe(Collections.singletonList("hello"));

		while (!Thread.interrupted()) {
			final ConsumerRecords<String, String> records = tracingConsumer
					.poll(Duration.ofMillis(Long.MAX_VALUE));
			for (ConsumerRecord<String, String> record : records) {
				brave.Span span = kafkaTracing.nextSpan(record).name("print-hello")
						.start();
				span.annotate("starting printing");
				out.println(String.format("Record: %s", record));
				span.annotate("printing finished");
				span.finish();
			}
		}

	}

}
