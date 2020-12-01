package co.casterlabs.koi.serialization;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import co.casterlabs.koi.user.SerializedUser;

// TODO better class name
public class SerializedUserSerializer implements JsonSerializer<SerializedUser> {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @Override
    public JsonElement serialize(SerializedUser user, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = GSON.toJsonTree(user).getAsJsonObject();

        result.addProperty("link", user.getPlatform().getLinkForUser(user.getUsername()));

        return result;
    }

}
