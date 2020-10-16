package ltd.starthub.muly.providers;

import android.content.Context;
import android.text.TextUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.pixplicity.easyprefs.library.Prefs;
import com.vaibhavpandey.katora.contracts.MutableContainer;
import com.vaibhavpandey.katora.contracts.Provider;

import java.util.Collections;

import ltd.starthub.muly.BuildConfig;
import ltd.starthub.muly.R;
import ltd.starthub.muly.SharedConstants;
import ltd.starthub.muly.data.api.REST;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class RetrofitProvider implements Provider {

    private final Context mContext;

    public RetrofitProvider(Context context) {
        mContext = context;
    }

    @Override
    public void provide(MutableContainer container) {
        container.factory(OkHttpClient.Builder.class, c -> {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .protocols(Collections.singletonList(Protocol.HTTP_1_1));
            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
                interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
                builder.addInterceptor(interceptor);
            }
            return builder;
        });
        container.factory(Retrofit.Builder.class, c -> {
            ObjectMapper om = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
            return new Retrofit.Builder()
                    .baseUrl(mContext.getString(R.string.server_url))
                    .addConverterFactory(JacksonConverterFactory.create(om));
        });
        container.factory(Retrofit.class, c -> {
            OkHttpClient client = c.get(OkHttpClient.Builder.class)
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        String token =
                                Prefs.getString(SharedConstants.PREF_SERVER_TOKEN, null);
                        if (!TextUtils.isEmpty(token)) {
                            request = request.newBuilder()
                                    .header("Authorization", "Bearer " + token)
                                    .build();
                        }
                        return chain.proceed(request);
                    })
                    .build();
            return c.get(Retrofit.Builder.class)
                    .client(client)
                    .build();
        });
        container.singleton(REST.class, c -> c.get(Retrofit.class).create(REST.class));
    }
}
