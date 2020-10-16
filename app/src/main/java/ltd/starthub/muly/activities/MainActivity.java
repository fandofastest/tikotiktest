package ltd.starthub.muly.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.pixplicity.easyprefs.library.Prefs;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.R;
import ltd.starthub.muly.SharedConstants;
import ltd.starthub.muly.data.ClipDataSource;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Token;
import ltd.starthub.muly.fragments.CommentFragment;
import ltd.starthub.muly.fragments.FollowersFragment;
import ltd.starthub.muly.fragments.MainFragment;
import ltd.starthub.muly.fragments.MessageFragment;
import ltd.starthub.muly.fragments.PlayerSliderFragment;
import ltd.starthub.muly.fragments.ProfileEditFragment;
import ltd.starthub.muly.fragments.ProfileFragment;
import ltd.starthub.muly.workers.DeviceTokenWorker;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private CallbackManager mCallbackManager;
    private final Handler mHandler = new Handler();
    private MainActivityViewModel mModel;
    private GoogleSignInClient mSignInClient;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Log.v(TAG, "Received request: " + requestCode + ", result: " + resultCode + ".");
        if (requestCode == SharedConstants.REQUEST_CODE_LOGIN_GOOGLE && resultCode == RESULT_OK && data != null) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                loginWithGoogle(task.getResult(ApiException.class));
            } catch (ApiException e) {
                Log.e(TAG, "Unable to login with Google account.");
            }
        } else if (requestCode == SharedConstants.REQUEST_CODE_LOGIN_PHONE && resultCode == RESULT_OK && data != null) {
            String token = data.getStringExtra(PhoneLoginActivity.EXTRA_TOKEN);
            updateLoginState(token);
        }
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mModel = new ViewModelProvider(this).get(MainActivityViewModel.class);
        String token = Prefs.getString(SharedConstants.PREF_SERVER_TOKEN, null);
        mModel.isLoggedIn = !TextUtils.isEmpty(token);
        View sheet = findViewById(R.id.login_sheet);
        final BottomSheetBehavior<View> bsb = BottomSheetBehavior.from(sheet);
        ImageButton close = sheet.findViewById(R.id.header_back);
        close.setImageResource(R.drawable.ic_baseline_close_24);
        close.setOnClickListener(view -> bsb.setState(BottomSheetBehavior.STATE_COLLAPSED));
        TextView title = sheet.findViewById(R.id.header_title);
        title.setText(R.string.login_label);
        sheet.findViewById(R.id.header_more).setVisibility(View.GONE);
        mCallbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance()
                .registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {

                    @Override
                    public void onCancel() {
                        Log.w(TAG, "Login with Facebook was cancelled.");
                    }

                    @Override
                    public void onError(FacebookException error) {
                        Log.e(TAG, "Login with Facebook returned error.", error);
                        Toast.makeText(MainActivity.this, R.string.error_internet, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSuccess(LoginResult result) {
                        loginWithFacebook(result);
                    }
                });
        View facebook = findViewById(R.id.facebook);
        facebook.setOnClickListener(view -> {
            bsb.setState(BottomSheetBehavior.STATE_COLLAPSED);
            LoginManager.getInstance()
                    .logInWithReadPermissions(
                            MainActivity.this, Collections.singletonList("email"));
        });
        View otp = findViewById(R.id.otp);
        otp.setOnClickListener(view -> {
            bsb.setState(BottomSheetBehavior.STATE_COLLAPSED);
            startActivityForResult(
                    new Intent(this, PhoneLoginActivity.class),
                    SharedConstants.REQUEST_CODE_LOGIN_PHONE
            );
        });
        GoogleSignInOptions options =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestIdToken(getString(R.string.google_client_id))
                        .requestProfile()
                        .build();
        mSignInClient = GoogleSignIn.getClient(this, options);
        View google = findViewById(R.id.google);
        google.setOnClickListener(view -> {
            bsb.setState(BottomSheetBehavior.STATE_COLLAPSED);
            startActivityForResult(
                    mSignInClient.getSignInIntent(), SharedConstants.REQUEST_CODE_LOGIN_GOOGLE);
        });
        syncFcmToken();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.host, MainFragment.newInstance())
                .commit();
        boolean intro = Prefs.getBoolean(SharedConstants.PREF_INTRO_SHOWN, false);
        if (!intro) {
            Prefs.putBoolean(SharedConstants.PREF_INTRO_SHOWN, true);
            startActivity(new Intent(this, FirstLaunchActivity.class));
        }
    }

    private void loginWithFacebook(LoginResult result) {
        Log.d(TAG, "User logged in Facebook ID " + result.getAccessToken().getUserId() + '.');
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.loginFacebook(result.getAccessToken().getToken())
                .enqueue(new Callback<Token>() {
                    @Override
                    public void onResponse(
                            @Nullable Call<Token> call,
                            @Nullable Response<Token> response
                    ) {
                        if (response != null && response.isSuccessful()) {
                            //noinspection ConstantConditions
                            mHandler.post(() -> updateLoginState(response.body().token));
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Token> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Login request with Facebook has failed.", t);
                    }
                });
    }

    private void loginWithGoogle(@Nullable GoogleSignInAccount account) {
        if (account == null) {
            Log.v(TAG, "Could not retrieve a Google account after login.");
            return;
        }

        REST rest = MainApplication.getContainer().get(REST.class);
        rest.loginGoogle(account.getIdToken())
                .enqueue(new Callback<Token>() {
                    @Override
                    public void onResponse(
                            @Nullable Call<Token> call,
                            @Nullable Response<Token> response
                    ) {
                        if (response != null && response.isSuccessful()) {
                            //noinspection ConstantConditions
                            mHandler.post(() -> updateLoginState(response.body().token));
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Token> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Login request with Facebook has failed.", t);
                    }
                });
    }

    public void logout() {
        Prefs.remove(SharedConstants.PREF_SERVER_TOKEN);
        Prefs.remove(SharedConstants.PREF_FCM_TOKEN_SYNCED_AT);
        mModel.isLoggedIn = false;
        restartActivity();
    }

    public void reportSubject(String type, int id) {
        Intent intent = new Intent(this, ReportActivity.class);
        intent.putExtra(ReportActivity.EXTRA_REPORT_SUBJECT_TYPE, type);
        intent.putExtra(ReportActivity.EXTRA_REPORT_SUBJECT_ID, id);
        startActivity(intent);
    }

    private void restartActivity() {
        startActivity(Intent.makeRestartActivityTask(getComponentName()));
    }

    public void showCommentsPage(int clip) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.host, CommentFragment.newInstance(clip))
                .addToBackStack(null)
                .commit();
    }

    public void showFollowerFollowing(int user, boolean following) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.host, FollowersFragment.newInstance(user, following))
                .addToBackStack(null)
                .commit();
    }

    public void showLoginSheet() {
        final BottomSheetBehavior<View> bsb =
                BottomSheetBehavior.from(findViewById(R.id.login_sheet));
        bsb.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public void showPlayerSlider(int clip, @Nullable Bundle params) {
        if (params == null) {
            params = new Bundle();
        }
        params.putInt(ClipDataSource.PARAM_FIRST, clip);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.host, PlayerSliderFragment.newInstance(params))
                .addToBackStack(null)
                .commit();
    }

    public void showProfilePage(int user) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.host, ProfileFragment.newInstance(user))
                .addToBackStack(null)
                .commit();
    }

    public void showProfileEditor() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.host, ProfileEditFragment.newInstance())
                .addToBackStack(null)
                .commit();
    }

    public void showMessenger(int thread, String username) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.host, MessageFragment.newInstance(thread, username))
                .addToBackStack(null)
                .commit();
    }

    private void syncFcmToken() {
        String token = Prefs.getString(SharedConstants.PREF_FCM_TOKEN, null);
        if (TextUtils.isEmpty(token)) {
            return;
        }

        long synced = Prefs.getLong(SharedConstants.PREF_FCM_TOKEN_SYNCED_AT, 0);
        if (synced <= 0 || synced < (System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15))) {
            Prefs.putLong(SharedConstants.PREF_FCM_TOKEN_SYNCED_AT, System.currentTimeMillis());
            WorkRequest request = OneTimeWorkRequest.from(DeviceTokenWorker.class);
            WorkManager.getInstance(this).enqueue(request);
        }
    }

    private void updateLoginState(String token) {
        Log.v(TAG, "Received token from server i.e., " + token);
        Prefs.putString(SharedConstants.PREF_SERVER_TOKEN, token);
        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
        restartActivity();
    }

    public static class MainActivityViewModel extends ViewModel {

        public boolean areThreadsInvalid;
        public boolean isLoggedIn;
        public boolean isProfileInvalid;
    }
}
