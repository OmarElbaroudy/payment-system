package application;

import application.controllers.LoggerController;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;

import java.io.IOException;

public class Listener {
    private static final String QUEUE_NAME = "Logger";
    private static final String EXCHANGE_NAME = "BLOCKCHAIN";
    private static Channel channel;

    public static void listen() throws Exception {
        initRabbit();
        LoggerController.mp.put("API", LoggerController.APILoggerQueue);
        LoggerController.mp.put("signaling", LoggerController.signalingLoggerQueue);

        channel.basicConsume(QUEUE_NAME, false, QUEUE_NAME,
                new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag,
                                               Envelope envelope,
                                               AMQP.BasicProperties properties,
                                               byte[] body)
                            throws IOException {

                        long deliveryTag = envelope.getDeliveryTag();

                        String s = new String(body);
                        JsonObject json = JsonParser.parseString(s).getAsJsonObject();

                        StatsContainer.update(json);

                        channel.basicAck(deliveryTag, false);
                    }
                });
    }

    private static void initRabbit() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();

        factory.setUri(System.getenv("RABBIT_CONNECTION_URL"));
        Connection connection = factory.newConnection();
        channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, false, false, true, null);
    }
}
