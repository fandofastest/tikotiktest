package ltd.starthub.muly.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.hbb20.CountryCodePicker;
import com.kaopiz.kprogresshud.KProgressHUD;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.R;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.Exists;
import ltd.starthub.muly.data.models.Token;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PhoneLoginActivity extends AppCompatActivity {

    public static final String EXTRA_TOKEN = "token";
    public static final String TAG = "PhoneLoginActivity";

    private PhoneLoginActivityViewModel mModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_login);
        ImageButton close = findViewById(R.id.header_back);
        close.setImageResource(R.drawable.ic_baseline_close_24);
        close.setOnClickListener(view -> finish());
        TextView title = findViewById(R.id.header_title);
        title.setText(R.string.otp_label);
        findViewById(R.id.header_more).setVisibility(View.GONE);
        mModel = new ViewModelProvider(this).get(PhoneLoginActivityViewModel.class);
        CountryCodePicker cc = findViewById(R.id.cc);
        cc.setCountryForPhoneCode(mModel.cc);
        cc.setOnCountryChangeListener(() -> mModel.cc = cc.getSelectedCountryCodeAsInt());
        TextInputLayout phone = findViewById(R.id.phone);
        cc.registerCarrierNumberEditText(phone.getEditText());
        //noinspection ConstantConditions
        phone.getEditText().setText(mModel.phone);
        phone.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
                mModel.phone = editable.toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });
        TextInputLayout otp = findViewById(R.id.otp);
        //noinspection ConstantConditions
        otp.getEditText().setText(mModel.otp);
        otp.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
                mModel.otp = editable.toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });
        TextInputLayout name = findViewById(R.id.name);
        //noinspection ConstantConditions
        name.getEditText().setText(mModel.name);
        name.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
                mModel.name = editable.toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });
        View generate = findViewById(R.id.generate);
        generate.setOnClickListener(v -> generateOtp());
        View verify = findViewById(R.id.verify);
        verify.setOnClickListener(v -> verifyOtp());
        mModel.doesExist.observe(this, exists -> {
            Boolean sent = mModel.isSent.getValue();
            //noinspection ConstantConditions
            name.setVisibility(sent && !exists ? View.VISIBLE : View.GONE);
        });
        mModel.isSent.observe(this, sent -> {
            Boolean exists = mModel.doesExist.getValue();
            //noinspection ConstantConditions
            name.setVisibility(sent && !exists ? View.VISIBLE : View.GONE);
            otp.setVisibility(sent ? View.VISIBLE : View.GONE);
            if (sent) {
                otp.requestFocus();
            }
            verify.setEnabled(sent);
        });
        mModel.errors.observe(this, errors -> {
            phone.setError(null);
            otp.setError(null);
            name.setError(null);
            if (errors == null) {
                return;
            }
            if (errors.containsKey("phone")) {
                phone.setError(errors.get("phone"));
            }
            if (errors.containsKey("otp")) {
                otp.setError(errors.get("otp"));
            }
            if (errors.containsKey("name")) {
                name.setError(errors.get("name"));
            }
        });
    }

    private void generateOtp() {
        mModel.errors.postValue(null);
        KProgressHUD progress = KProgressHUD.create(this)
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setLabel(getString(R.string.progress_title))
                .setCancellable(false)
                .show();
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.loginPhoneOtp(mModel.cc + "", mModel.phone)
                .enqueue(new Callback<Exists>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Exists> call,
                            @Nullable Response<Exists> response
                    ) {
                        int code = response != null ? response.code() : -1;
                        int message = -1;
                        if (code == 200) {
                            //noinspection ConstantConditions
                            boolean exists = response.body().exists;
                            mModel.doesExist.postValue(exists);
                            mModel.isSent.postValue(true);
                            message = R.string.login_otp_sent;
                        } else if (code == 422) {
                            try {
                                //noinspection ConstantConditions
                                String content = response.errorBody().string();
                                showErrors(new JSONObject(content));
                            } catch (Exception e) {
                                Log.e(TAG, "Error error", e);
                            }
                        } else {
                            message = R.string.error_internet;
                        }
                        if (message != -1) {
                            Toast.makeText(PhoneLoginActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                        progress.dismiss();
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Exists> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Failed when trying to generate OTP.", t);
                        Toast.makeText(PhoneLoginActivity.this, R.string.error_internet, Toast.LENGTH_SHORT).show();
                        progress.dismiss();
                    }
                });
    }

    private void showErrors(JSONObject json) throws Exception {
        JSONObject errors = json.getJSONObject("errors");
        Map<String, String> messages = new HashMap<>();
        String[] keys = new String[]{"cc", "phone", "otp", "name"};
        for (String key : keys) {
            JSONArray fields = errors.optJSONArray(key);
            if (fields != null) {
                messages.put(key, fields.getString(0));
            }
        }
        mModel.errors.postValue(messages);
    }

    private void verifyOtp() {
        mModel.errors.postValue(null);
        KProgressHUD progress = KProgressHUD.create(this)
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setLabel(getString(R.string.progress_title))
                .setCancellable(false)
                .show();
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.loginPhone(mModel.cc + "", mModel.phone, mModel.otp, mModel.name)
                .enqueue(new Callback<Token>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Token> call,
                            @Nullable Response<Token> response
                    ) {
                        if (response != null) {
                            if (response.isSuccessful()) {
                                //noinspection ConstantConditions
                                String token = response.body().token;
                                Intent data = new Intent();
                                data.putExtra(EXTRA_TOKEN, token);
                                setResult(RESULT_OK, data);
                                finish();
                            } else if (response.code() == 422) {
                                try {
                                    //noinspection ConstantConditions
                                    String content = response.errorBody().string();
                                    showErrors(new JSONObject(content));
                                } catch (Exception ignore) {
                                }
                            } else {
                                Toast.makeText(PhoneLoginActivity.this, R.string.error_internet, Toast.LENGTH_SHORT).show();
                            }
                        }
                        progress.dismiss();
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Token> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Failed when trying to verify OTP.", t);
                        Toast.makeText(PhoneLoginActivity.this, R.string.error_internet, Toast.LENGTH_SHORT).show();
                        progress.dismiss();
                    }
                });
    }

    public static class PhoneLoginActivityViewModel extends ViewModel {

        public int cc = 91;
        public String phone = "";
        public String otp = "";
        public String name = "";
        public MutableLiveData<Boolean> doesExist = new MutableLiveData<>(false);
        public MutableLiveData<Boolean> isSent = new MutableLiveData<>(false);

        private MutableLiveData<Map<String, String>> errors = new MutableLiveData<>();
    }
}
