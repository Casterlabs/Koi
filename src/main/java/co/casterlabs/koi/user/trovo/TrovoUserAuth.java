package co.casterlabs.koi.user.trovo;

import java.io.IOException;

import com.google.gson.JsonObject;

import co.casterlabs.apiutil.auth.ApiAuthException;
import co.casterlabs.apiutil.web.ApiException;
import co.casterlabs.koi.Koi;
import co.casterlabs.koi.user.KoiAuthProvider;
import co.casterlabs.koi.user.UserPlatform;
import lombok.NonNull;

public class TrovoUserAuth extends co.casterlabs.trovoapi.TrovoUserAuth implements KoiAuthProvider {

    public TrovoUserAuth(@NonNull String accessToken) throws ApiException, ApiAuthException, IOException {
        super(Koi.getInstance().getConfig().getTrovoId(), accessToken);
    }

    @Override
    public UserPlatform getPlatform() {
        return UserPlatform.TROVO;
    }

    @Override
    public boolean isValid() {
        try {
            this.validate();

            return true;
        } catch (ApiException | IOException e) {
            return false;
        }
    }

    @Override
    public JsonObject getCredentials() {
        throw new UnsupportedOperationException();
        /*
        JsonObject json = new JsonObject();
        
        json.addProperty("client_id", this.clientId);
        json.addProperty("access_token", this.access_token);
        
        return json;*/
    }

}
