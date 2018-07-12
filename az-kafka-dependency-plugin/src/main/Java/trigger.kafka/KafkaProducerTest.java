package trigger.kafka;

import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;


public class KafkaProducerTest {
  private final static String TOPIC = "AzEvent_Topic4";
  private KafkaProducer producer;
  public byte[] createAvroRecord(String name, String username) {
    String userSchema = "{\"namespace\": \"example.avro\", \"type\": \"record\", " +
        "\"name\": \"User\"," +
        "\"fields\": [{\"name\": \"name\", \"type\": \"string\"},{\"name\": \"username\", \"type\": \"string\"}]}";
    Schema.Parser parser = new Schema.Parser();
    Schema schema = parser.parse(userSchema);
    GenericRecord avroRecord = new GenericData.Record(schema);
    avroRecord.put("name", name);
    avroRecord.put("username", username);

    GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<GenericRecord>(schema);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    Encoder encoder = EncoderFactory.get().binaryEncoder(os, null);
    try {
      writer.write(avroRecord, encoder);
      encoder.flush();
      os.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return os.toByteArray();
  }

  public KafkaProducerTest(String name, String username) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    // Configure the KafkaAvroSerializer.
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,ByteArraySerializer.class);
    // Schema Registry location.
    props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
        "http://localhost:8000");
    this.producer = new KafkaProducer<>(props);
    byte[] schemaRecord = createAvroRecord(name,username);
    ProducerRecord<String, byte[]> record = new ProducerRecord<>(TOPIC, schemaRecord);
    try {
      System.out.println("+++++++++++++++"+record+"+++++++++++++++");
      producer.send(record);
    }catch (Exception ex){
      System.out.println(ex);
    }
    producer.flush();
    producer.close();
  }

}

