package co.casterlabs.koi.user;

import com.google.gson.annotations.SerializedName;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class SerializedUser {
    private final UserPlatform platform;
    private String UUID = "?";
    private String username = "?";
    private String displayname = "?";
    @SerializedName("image_link")
    private String imageLink = "data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==";
    @SerializedName("follower_count")
    private long followerCount = 0;
    @SerializedName("following_count")
    private long followingCount = 0;
    private String color = "#FF0000";

}
