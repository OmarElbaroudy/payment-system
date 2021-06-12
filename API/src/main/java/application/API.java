package application;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.*;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import persistence.MongoHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;


public class API {
    private static final String exchangeName = "BLOCKCHAIN";
    private static final String queueName = "API";
    private static HashMap<String, HttpServletResponse> mp;
    private static Channel channel;
    private static Connection connection;
    private static Server server;

    public static void initRabbit() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        connection = factory.newConnection();
        channel = connection.createChannel();

        channel.queueDeclare(queueName, false, false, true, null);
        channel.queueBind(queueName, exchangeName, queueName);
    }

    public static void listen() throws Exception {
        channel.basicConsume(queueName, false, queueName,
                new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag,
                                               Envelope envelope,
                                               AMQP.BasicProperties properties,
                                               byte[] body)
                            throws IOException {

                        long deliveryTag = envelope.getDeliveryTag();

                        LongString ls = (LongString) properties.getHeaders().get("pubKey");
                        String key = new String(ls.getBytes());

                        try {
                            HttpServletResponse resp = mp.get(key);
                            resp.setContentType("application/json");
                            resp.setStatus(HttpServletResponse.SC_OK);

                            String s = new String(body);
                            JsonObject json = JsonParser.parseString(s).getAsJsonObject();

                            PrintWriter out = resp.getWriter();
                            out.print(json);
                            mp.remove(key);
                        } catch (Exception e) {
                            e.printStackTrace();
                            HttpServletResponse resp = mp.get(key);
                            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        }

                        channel.basicAck(deliveryTag, false);
                    }
                });
    }

    public static void addResponse(String key, HttpServletResponse resp) {
        mp.put(key, resp);
    }

    public static void main(String[] args) throws Exception {
        start();
    }

    public static void start() throws Exception {
        server = new Server();
        mp = new HashMap<>();

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(3000);
        server.setConnectors(new Connector[]{connector});

        ServletContextHandler context = new ServletContextHandler();

        MongoHandler handler = new MongoHandler();

        Register.setHandler(handler);
        Login.setHandler(handler);
        Balance.setHandler(handler);
        Transfer.setHandler(handler);
        Buy.setHandler(handler);
        Sell.setHandler(handler);

        context.addServlet(Register.class, "/register");
        context.addServlet(Login.class, "/login");
        context.addServlet(Balance.class, "/getBalance");
        context.addServlet(Transfer.class, "/transfer");
        context.addServlet(Buy.class, "/buy");
        context.addServlet(Sell.class, "/sell");

        server.setHandler(context);
        initRabbit();
        listen();

        server.start();
    }

    public static void createTransaction(HttpServletResponse resp,
                                         String recKey,
                                         double amount, String privKey) throws IOException {
        Map<String, Object> mp = new HashMap<>();
        mp.put("task", "createTransaction");

        AMQP.BasicProperties props =
                new AMQP.BasicProperties().
                        builder().
                        headers(mp).
                        contentType("application/json").
                        build();

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("privKey", privKey);
        jsonObject.addProperty("recKey", recKey);
        jsonObject.addProperty("amount", amount);

        String json = jsonObject.toString();

        channel.basicPublish(exchangeName, "SIGNALING_SERVER", props, json.getBytes());

        resp.setContentType("application/json");
        resp.setStatus(HttpServletResponse.SC_OK);

        jsonObject = new JsonObject();
        jsonObject.addProperty("message", "transaction created!");

        PrintWriter out = resp.getWriter();
        out.print(jsonObject);
    }

    public static void getBalance(HttpServletResponse resp, String pubKey) throws IOException{
        Map<String, Object> mp = new HashMap<>();
        mp.put("task", "getBalance");

        AMQP.BasicProperties props =
                new AMQP.BasicProperties().
                        builder().
                        headers(mp).
                        contentType("application/json").
                        build();

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("pubKey", pubKey);

        String json = jsonObject.toString();
        channel.basicPublish(exchangeName, "SIGNALING_SERVER", props, json.getBytes());

        addResponse(pubKey, resp);
    }



    public void stop() throws Exception {
        channel.close();
        connection.close();
        server.stop();
    }
}
