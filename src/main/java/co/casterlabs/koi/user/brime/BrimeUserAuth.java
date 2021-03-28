package co.casterlabs.koi.user.brime;

import com.google.gson.JsonObject;

import co.casterlabs.apiutil.auth.ApiAuthException;
import co.casterlabs.apiutil.web.ApiException;
import co.casterlabs.brimeapijava.requests.BrimeGetChannelRequest;
import co.casterlabs.brimeapijava.types.BrimeChannel;
import co.casterlabs.koi.client.ClientAuthProvider;
import co.casterlabs.koi.user.UserPlatform;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class BrimeUserAuth extends co.casterlabs.brimeapijava.BrimeUserAuth implements ClientAuthProvider {
    private String UUID;
    private String channelName;

    public BrimeUserAuth(@NonNull String clientId, @NonNull String accessToken) throws ApiAuthException, ApiException {
        super(clientId, accessToken);

        BrimeChannel channel = new BrimeGetChannelRequest(this).setChannel("me").send();

        this.UUID = channel.getChannelId();
        this.channelName = channel.getChannelName();
    }

    @Override
    public UserPlatform getPlatform() {
        return UserPlatform.BRIME;
    }

    @Override
    public boolean isValid() {
        return true; // No way to test PEPEGACLAP
    }

    @Override
    public void refresh() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JsonObject getCredentials() {
        throw new UnsupportedOperationException();
    }

}
