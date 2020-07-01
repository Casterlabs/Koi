package co.casterlabs.koi.user.caffeine;

import java.util.Collections;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;

import com.google.gson.JsonObject;

import co.casterlabs.koi.Koi;
import co.casterlabs.koi.events.StreamStatusEvent;
import co.casterlabs.koi.util.WebUtil;
import lombok.SneakyThrows;

public class CaffeineQuery extends WebSocketClient {
    private static final Draft_6455 draft = new Draft_6455(Collections.<IExtension>emptyList(), Collections.<IProtocol>singletonList(new Protocol("graphql-ws")));
    private static final String query = "{\"id\":\"1\",\"type\":\"start\",\"payload\":{\"variables\":{\"clientId\":\"Anonymous\",\"clientType\":\"WEB\",\"constrainedBaseline\":false,\"username\":\"%USERNAME%\",\"viewerStreams\":[]},\"extensions\":{},\"operationName\":\"Stage\",\"query\":\"subscription Stage($clientId: ID!, $clientType: ClientType!, $constrainedBaseline: Boolean, $username: String!, $viewerStreams: [StageSubscriptionViewerStreamInput!]) {\\n  stage(clientId: $clientId, clientType: $clientType, clientTypeForMetrics: \\\"WEB\\\", constrainedBaseline: $constrainedBaseline, username: $username, viewerStreams: $viewerStreams) {\\n    error {\\n      __typename\\n      title\\n      message\\n    }\\n    stage {\\n      id\\n      username\\n      title\\n      broadcastId\\n      contentRating\\n      live\\n      feeds {\\n        id\\n        clientId\\n        clientType\\n        gameId\\n        liveHost {\\n          __typename\\n          ... on LiveHostable {\\n            address\\n          }\\n          ... on LiveHosting {\\n            address\\n            volume\\n            ownerId\\n            ownerUsername\\n          }\\n        }\\n        sourceConnectionQuality\\n        capabilities\\n        role\\n        restrictions\\n        stream {\\n          __typename\\n          ... on BroadcasterStream {\\n            id\\n            sdpAnswer\\n            url\\n          }\\n          ... on ViewerStream {\\n            id\\n            sdpOffer\\n            url\\n          }\\n        }\\n      }\\n    }\\n  }\\n}\\n\"}}";
    private static final String auth = "{\"type\":\"connection_init\",\"payload\":{\"X-Credential\":\"%CREDENTIAL%\"}}";

    private CaffeineUser user;
    private String credential;
    private int live = -1; // Allows there to always be a status available

    @SneakyThrows
    public CaffeineQuery(CaffeineUser user) {
        super(CaffeineLinks.getQueryLink(), draft);

        JsonObject json = WebUtil.jsonSendHttpGet(CaffeineLinks.getAnonymousCredentialLink(), null, JsonObject.class);

        this.credential = json.get("credential").getAsString();
        this.user = user;

        this.connect();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        this.send(auth.replace("%CREDENTIAL%", this.credential));
        this.send(query.replace("%USERNAME%", this.user.getUsername()));
    }

    @Override
    public void onMessage(String raw) {
        try {
            JsonObject message = Koi.GSON.fromJson(raw, JsonObject.class);

            if (message.get("type").getAsString().equalsIgnoreCase("data")) {
                JsonObject payload = message.getAsJsonObject("payload");
                JsonObject data = payload.getAsJsonObject("data");
                JsonObject stageContainer = data.getAsJsonObject("stage");
                JsonObject stage = stageContainer.getAsJsonObject("stage");

                boolean isLive = stage.get("live").getAsBoolean();
                int liveInt = isLive ? 1 : 0;

                if (this.live != liveInt) {
                    this.live = liveInt;

                    this.user.broadcastEvent(new StreamStatusEvent(isLive, this.user));
                }
            }
        } catch (Exception e) {
            Koi.getInstance().getLogger().exception(e); // Prevents the socket from closing.
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println(reason);
        
        if (this.user.hasListeners()) {
            Koi.getMiscThreadPool().submit(() -> this.reconnect());
        }
    }

    @Override
    public void onError(Exception e) {
        e.printStackTrace();
    }

}
