package utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import system.ConfigSetup;

import java.io.IOException;

public class SlackUtilities {

    @SneakyThrows
    public static void sendMessage(SlackMessage message) {
        final String slackWebhookUrl = ConfigSetup.getSlackWebhookUrl();

        if(slackWebhookUrl.length() > 0) {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(slackWebhookUrl);

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writeValueAsString(message);

                StringEntity entity = new StringEntity(json);
                httpPost.setEntity(entity);
                httpPost.setHeader("Accept", "application/json");
                httpPost.setHeader("Content-type", "application/json");

                client.execute(httpPost);
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}