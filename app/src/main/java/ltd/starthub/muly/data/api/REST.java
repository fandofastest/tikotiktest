package ltd.starthub.muly.data.api;

import androidx.annotation.Nullable;

import ltd.starthub.muly.data.models.Article;
import ltd.starthub.muly.data.models.ArticleSection;
import ltd.starthub.muly.data.models.Clip;
import ltd.starthub.muly.data.models.ClipSection;
import ltd.starthub.muly.data.models.Comment;
import ltd.starthub.muly.data.models.Exists;
import ltd.starthub.muly.data.models.Message;
import ltd.starthub.muly.data.models.Notification;
import ltd.starthub.muly.data.models.Song;
import ltd.starthub.muly.data.models.SongSection;
import ltd.starthub.muly.data.models.Thread;
import ltd.starthub.muly.data.models.Token;
import ltd.starthub.muly.data.models.User;
import ltd.starthub.muly.data.models.Wrappers;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface REST {

    @GET("articles")
    Call<Wrappers.Paginated<Article>> articlesIndex(
            @Query("q") @Nullable String q,
            @Query("sections[]") @Nullable Iterable<Integer> sections,
            @Query("page") int page
    );

    @GET("articles/sections")
    Call<Wrappers.Paginated<ArticleSection>> articleSectionsIndex(
            @Query("q") @Nullable String q,
            @Query("page") int page
    );

    @GET("articles/sections/{id}")
    Call<Wrappers.Single<ArticleSection>> articleSectionsShow(@Path("id") int section);

    @GET("articles/{id}")
    Call<Wrappers.Single<Article>> articlesShow(@Path("id") int article);

    @Headers("Accept: application/json")
    @Multipart
    @POST("clips")
    Call<Wrappers.Single<Clip>> clipsCreate(
            @Part MultipartBody.Part video,
            @Part MultipartBody.Part screenshot,
            @Part MultipartBody.Part preview,
            @Part("song") @Nullable RequestBody song,
            @Part("description") @Nullable RequestBody description,
            @Part("language") RequestBody language,
            @Part("private") RequestBody _private,
            @Part("comments") RequestBody comments,
            @Part("duration") RequestBody duration
    );

    @DELETE("clips/{id}")
    Call<ResponseBody> clipsDelete(@Path("id") int clip);

    @GET("clips")
    Call<Wrappers.Paginated<Clip>> clipsIndex(
            @Query("mine") @Nullable Boolean mine,
            @Query("q") @Nullable String q,
            @Query("liked") @Nullable Boolean liked,
            @Query("saved") @Nullable Boolean saved,
            @Query("following") @Nullable Boolean following,
            @Query("user") @Nullable Integer user,
            @Query("song") @Nullable Integer song,
            @Query("sections[]") @Nullable Iterable<Integer> sections,
            @Query("hashtags") @Nullable Iterable<String> hashtags,
            @Query("seed") @Nullable Integer seed,
            @Query("seen") @Nullable Long seen,
            @Query("first") @Nullable Integer first,
            @Query("before") @Nullable Integer before,
            @Query("after") @Nullable Integer after,
            @Query("page") @Nullable Integer page
    );

    @GET("clips/sections")
    Call<Wrappers.Paginated<ClipSection>> clipSectionsIndex(
            @Query("q") String q,
            @Query("page") int page
    );

    @GET("clips/sections/{id}")
    Call<Wrappers.Single<ClipSection>> clipSectionsShow(@Path("id") int section);

    @GET("clips/{id}")
    Call<Wrappers.Single<Clip>> clipsShow(@Path("id") int clip);

    @GET("clips/{id}/comments")
    Call<Wrappers.Paginated<Comment>> commentsIndex(
            @Path("id") int clip,
            @Query("page") int page
    );

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("clips/{id}/comments")
    Call<Wrappers.Single<Comment>> commentsCreate(
            @Path("id") int clip,
            @Field("text") String text
    );

    @GET("clips/{id1}/comments/{id2}")
    Call<Wrappers.Single<Comment>> commentsShow(@Path("id1") int clip, @Path("id2") int comment);

    @DELETE("clips/{id1}/comments/{id2}")
    Call<ResponseBody> commentsDelete(@Path("id1") int clip, @Path("id2") int comment);

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("devices")
    Call<ResponseBody> devicesCreate(
            @Field("platform") String platform,
            @Field("push_service") String pushService,
            @Field("push_token") String pushToken
    );

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @PUT("devices/{id}")
    Call<ResponseBody> devicesUpdate(
            @Path("id") int device,
            @Field("push_token") String pushToken
    );

    @GET("users/{id}/followers")
    Call<Wrappers.Paginated<User>> followersIndex(
            @Path("id") int user,
            @Query("following") boolean following,
            @Query("page") int page
    );

    @POST("users/{id}/followers")
    Call<ResponseBody> followersFollow(@Path("id") int user);

    @DELETE("users/{id}/followers")
    Call<ResponseBody> followersUnfollow(@Path("id") int user);

    @POST("clips/{id}/likes")
    Call<ResponseBody> likesLike(@Path("id") int clip);

    @DELETE("clips/{id}/likes")
    Call<ResponseBody> likesUnlike(@Path("id") int clip);

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("login/facebook")
    Call<Token> loginFacebook(@Field("token") String token);

    @POST("login/firebase")
    Call<Token> loginFirebase();

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("login/google")
    Call<Token> loginGoogle(@Field("google") String token);

    @FormUrlEncoded
    @POST("login/password")
    Call<Token> loginPassword(
            @Field("username") String username,
            @Field("password") String password
    );

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("login/phone")
    Call<Token> loginPhone(
            @Field("cc") String cc,
            @Field("phone") String phone,
            @Field("otp") String otp,
            @Field("name") String name
    );

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("login/phone/otp")
    Call<Exists> loginPhoneOtp(
            @Field("cc") String cc,
            @Field("phone") String phone
    );

    @GET("threads/{id}/messages")
    Call<Wrappers.Paginated<Message>> messagesIndex(
            @Path("id") int thread,
            @Query("page") int page
    );

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("threads/{id}/messages")
    Call<Wrappers.Single<Message>> messagesCreate(
            @Path("id") int thread,
            @Field("body") String body
    );

    @GET("notifications")
    Call<Wrappers.Paginated<Notification>> notificationsIndex(@Query("page") int page);

    @DELETE("notifications")
    Call<ResponseBody> notificationsDelete();

    @GET("profile")
    Call<Wrappers.Single<User>> profileShow();

    @Headers("Accept: application/json")
    @Multipart
    @POST("profile")
    Call<ResponseBody> profileUpdate(
            @Part MultipartBody.Part photo,
            @Part("username") RequestBody username,
            @Part("bio") RequestBody bio,
            @Part("name") RequestBody name,
            @Part("email") RequestBody email,
            @Part("phone") RequestBody phone
    );

    @DELETE("profile")
    Call<ResponseBody> profileDelete();

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("reports")
    Call<ResponseBody> reportsCreate(
            @Field("subject_type") String subjectType,
            @Field("subject_id") long subjectId,
            @Field("reason") String reason,
            @Field("message") String message
    );

    @POST("clips/{id}/saves")
    Call<ResponseBody> savesSave(@Path("id") int clip);

    @DELETE("clips/{id}/saves")
    Call<ResponseBody> savesUnsave(@Path("id") int clip);

    @GET("songs")
    Call<Wrappers.Paginated<Song>> songsIndex(
            @Query("q") String q,
            @Query("sections[]") Iterable<Integer> sections,
            @Query("page") int page
    );

    @GET("songs/sections")
    Call<Wrappers.Paginated<SongSection>> songSectionsIndex(
            @Query("q") String q,
            @Query("page") int page
    );

    @GET("songs/sections/{id}")
    Call<Wrappers.Single<SongSection>> songSectionsShow(@Path("id") int section);

    @GET("songs/{id}")
    Call<Wrappers.Single<Song>> songsShow(@Path("id") int song);

    @GET("threads")
    Call<Wrappers.Paginated<Thread>> threadsIndex(@Query("page") int page);

    @Headers("Accept: application/json")
    @FormUrlEncoded
    @POST("threads")
    Call<Wrappers.Single<Thread>> threadsCreate(@Field("user") int user);

    @GET("threads/{id}")
    Call<Wrappers.Single<Thread>> threadsShow(@Path("id") int thread);

    @GET("users")
    Call<Wrappers.Paginated<User>> usersIndex(
            @Query("q") String q,
            @Query("page") int page
    );

    @GET("users/{id}")
    Call<Wrappers.Single<User>> usersShow(@Path("id") int user);
}
