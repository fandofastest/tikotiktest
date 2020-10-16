package ltd.starthub.muly.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.kaopiz.kprogresshud.KProgressHUD;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import ltd.starthub.muly.MainApplication;
import ltd.starthub.muly.R;
import ltd.starthub.muly.activities.MainActivity;
import ltd.starthub.muly.common.LoadingState;
import ltd.starthub.muly.data.api.REST;
import ltd.starthub.muly.data.models.User;
import ltd.starthub.muly.data.models.Wrappers;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileEditFragment extends Fragment {

    private static final String TAG = "ProfileEditFragment";

    private ProfileEditFragmentModel mModel1;
    private MainActivity.MainActivityViewModel mModel2;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            Uri uri = result.getUri();
            Log.v(TAG, "Copped image as saved to " + uri);
            mModel1.photo = uri.getPath();
            refreshPhoto();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mModel1 = new ViewModelProvider(this).get(ProfileEditFragmentModel.class);
        mModel2 = new ViewModelProvider(requireActivity())
                .get(MainActivity.MainActivityViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_edit, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        LoadingState state = mModel1.state.getValue();
        User user = mModel1.user.getValue();
        if (user == null && state != LoadingState.LOADING) {
            loadUser();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ImageButton close = view.findViewById(R.id.header_back);
        close.setImageResource(R.drawable.ic_baseline_close_24);
        close.setOnClickListener(v -> requireActivity()
                .getSupportFragmentManager()
                .popBackStack());
        TextView title = view.findViewById(R.id.header_title);
        title.setText(R.string.edit_label);
        ImageButton done = view.findViewById(R.id.header_more);
        done.setImageResource(R.drawable.ic_baseline_check_24);
        done.setOnClickListener(v -> saveProfile());
        SimpleDraweeView photo = view.findViewById(R.id.photo);
        photo.setOnClickListener(v -> choosePhotoAction());
        TextInputLayout name = view.findViewById(R.id.name);
        //noinspection ConstantConditions
        name.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
                mModel1.name = editable.toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });
        TextInputLayout username = view.findViewById(R.id.username);
        //noinspection ConstantConditions
        username.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
                mModel1.username = editable.toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });
        TextInputLayout email = view.findViewById(R.id.email);
        //noinspection ConstantConditions
        email.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
                mModel1.email = editable.toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });
        TextInputLayout phone = view.findViewById(R.id.phone);
        //noinspection ConstantConditions
        phone.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
                mModel1.phone = editable.toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });
        TextInputLayout bio = view.findViewById(R.id.bio);
        //noinspection ConstantConditions
        bio.getEditText().addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
                mModel1.bio = editable.toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });
        View content = view.findViewById(R.id.content);
        View loading = view.findViewById(R.id.loading);
        mModel1.errors.observe(getViewLifecycleOwner(), errors -> {
            name.setError(null);
            username.setError(null);
            email.setError(null);
            phone.setError(null);
            bio.setError(null);
            if (errors == null) {
                return;
            }
            if (errors.containsKey("name")) {
                name.setError(errors.get("name"));
            }
            if (errors.containsKey("username")) {
                username.setError(errors.get("username"));
            }
            if (errors.containsKey("email")) {
                email.setError(errors.get("email"));
            }
            if (errors.containsKey("phone")) {
                phone.setError(errors.get("phone"));
            }
            if (errors.containsKey("bio")) {
                bio.setError(errors.get("bio"));
            }
        });
        mModel1.state.observe(getViewLifecycleOwner(), state -> {
            loading.setVisibility(state == LoadingState.LOADING ? View.VISIBLE : View.GONE);
            content.setVisibility(state == LoadingState.LOADED ? View.VISIBLE : View.GONE);
        });
        mModel1.user.observe(getViewLifecycleOwner(), user -> {
            name.getEditText().setText(mModel1.name = user.name);
            username.getEditText().setText(mModel1.username = user.username);
            email.getEditText().setText(mModel1.email = user.email);
            phone.getEditText().setText(mModel1.phone = user.phone);
            bio.getEditText().setText(mModel1.bio = user.bio);
            refreshPhoto();
        });
    }

    private void choosePhotoAction() {
        new MaterialAlertDialogBuilder(requireContext())
                .setItems(R.array.photo_options, (dialogInterface, i) -> {
                    if (i == 0) {
                        CropImage.activity()
                                .setGuidelines(CropImageView.Guidelines.ON)
                                .setMinCropResultSize(256,256)
                                .start(requireContext(), ProfileEditFragment.this);
                    } else {
                        removePhoto();
                    }
                })
                .show();
    }

    private void loadUser() {
        mModel1.state.setValue(LoadingState.LOADING);
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.profileShow()
                .enqueue(new Callback<Wrappers.Single<User>>() {

                    @Override
                    public void onResponse(
                            @Nullable Call<Wrappers.Single<User>> call,
                            @Nullable Response<Wrappers.Single<User>> response
                    ) {
                        if (response != null && response.isSuccessful()) {
                            //noinspection ConstantConditions
                            User user = response.body().data;
                            mModel1.user.setValue(user);
                            mModel1.state.setValue(LoadingState.LOADED);
                        } else {
                            mModel1.state.setValue(LoadingState.ERROR);
                        }
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<Wrappers.Single<User>> call,
                            @Nullable Throwable t
                    ) {
                        Log.e(TAG, "Failed when trying to fetch profile.", t);
                        mModel1.state.setValue(LoadingState.ERROR);
                    }
                });
    }

    private void removePhoto() {
        KProgressHUD progress = KProgressHUD.create(requireActivity())
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setLabel(getString(R.string.progress_title))
                .setCancellable(false)
                .show();
        REST rest = MainApplication.getContainer().get(REST.class);
        rest.profileDelete()
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(
                            @Nullable Call<ResponseBody> call,
                            @Nullable Response<ResponseBody> response
                    ) {
                        if (response != null && response.isSuccessful()) {
                            mModel1.photo = null;
                            refreshPhoto();
                        }
                        progress.dismiss();
                    }

                    @Override
                    public void onFailure(
                            @Nullable Call<ResponseBody> call,
                            @Nullable Throwable t
                    ) {
                        progress.dismiss();
                    }
                });
    }

    private void saveProfile() {
        KProgressHUD progress = KProgressHUD.create(requireActivity())
                .setStyle(KProgressHUD.Style.SPIN_INDETERMINATE)
                .setLabel(getString(R.string.progress_title))
                .setCancellable(false)
                .show();
        mModel1.errors.postValue(null);
        REST rest = MainApplication.getContainer().get(REST.class);
        MultipartBody.Part photo = null;
        if (!TextUtils.isEmpty(mModel1.photo)) {
            RequestBody body = RequestBody.create(null, new File(mModel1.photo));
            photo = MultipartBody.Part.createFormData("photo", "photo.png", body);
        }
        Call<ResponseBody> call = rest.profileUpdate(
                photo,
                RequestBody.create(null, mModel1.username),
                RequestBody.create(null, mModel1.bio),
                RequestBody.create(null, mModel1.name),
                RequestBody.create(null, mModel1.email),
                RequestBody.create(null, mModel1.phone)
        );
        call.enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(
                    @Nullable Call<ResponseBody> call,
                    @Nullable Response<ResponseBody> response
            ) {
                if (response != null) {
                    if (response.isSuccessful()) {
                        mModel2.isProfileInvalid = true;
                        requireActivity()
                                .getSupportFragmentManager()
                                .popBackStack();
                    } else if (response.code() == 422) {
                        try {
                            //noinspection ConstantConditions
                            String content = response.errorBody().string();
                            Log.d(TAG, content);
                            showErrors(new JSONObject(content));
                        } catch (Exception ignore) {
                        }
                    }
                }
                progress.dismiss();
            }

            @Override
            public void onFailure(
                    @Nullable Call<ResponseBody> call,
                    @Nullable Throwable t
            ) {
                Log.e(TAG, "Failed when trying to update profile.", t);
                progress.dismiss();
            }
        });
    }

    public static ProfileEditFragment newInstance() {
        return new ProfileEditFragment();
    }

    private void refreshPhoto() {
        User user = mModel1.user.getValue();
        //noinspection ConstantConditions
        SimpleDraweeView photo = getView().findViewById(R.id.photo);
        if (!TextUtils.isEmpty(mModel1.photo)) {
            photo.setImageURI(Uri.fromFile(new File(mModel1.photo)));
        } else if (user != null && !TextUtils.isEmpty(user.photo)) {
            photo.setImageURI(user.photo);
        } else {
            photo.setActualImageResource(R.drawable.photo_placeholder);
        }
    }

    private void showErrors(JSONObject json) throws Exception {
        JSONObject errors = json.getJSONObject("errors");
        Map<String, String> messages = new HashMap<>();
        String[] keys = new String[]{"username", "bio", "name", "email", "phone"};
        for (String key : keys) {
            JSONArray fields = errors.optJSONArray(key);
            if (fields != null) {
                messages.put(key, fields.getString(0));
            }
        }

        mModel1.errors.postValue(messages);
    }

    public static class ProfileEditFragmentModel extends ViewModel {

        public String photo;
        public String username;
        public String bio;
        public String name;
        public String email;
        public String phone;

        public final MutableLiveData<Map<String, String>> errors = new MutableLiveData<>();
        public final MutableLiveData<LoadingState> state = new MutableLiveData<>();
        public final MutableLiveData<User> user = new MutableLiveData<>();
    }
}
