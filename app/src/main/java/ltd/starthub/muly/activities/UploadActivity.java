package ltd.starthub.muly.activities;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.switchmaterial.SwitchMaterial;
import ltd.starthub.muly.R;
import ltd.starthub.muly.utils.VideoUtil;
import ltd.starthub.muly.workers.GeneratePreviewWorker;
import ltd.starthub.muly.workers.UploadClipWorker;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class UploadActivity extends AppCompatActivity {

    public static final String EXTRA_SONG = "song";
    public static final String EXTRA_VIDEO = "video";
    public static final String TAG = "PostClipActivity";

    private UploadActivityViewModel mModel;
    private int mSong;
    private String mClip;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);
        ImageButton close = findViewById(R.id.header_back);
        close.setImageResource(R.drawable.ic_baseline_close_24);
        close.setOnClickListener(view -> finish());
        TextView title = findViewById(R.id.header_title);
        title.setText(R.string.upload_label);
        ImageButton done = findViewById(R.id.header_more);
        done.setImageResource(R.drawable.ic_baseline_check_24);
        done.setOnClickListener(v -> uploadToServer());
        mModel = new ViewModelProvider(this).get(UploadActivityViewModel.class);
        mSong = getIntent().getIntExtra(EXTRA_SONG, 0);
        mClip = getIntent().getStringExtra(EXTRA_VIDEO);
        EditText description = findViewById(R.id.description);
        description.setText(mModel.description);
        description.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable editable) {
                mModel.description = editable.toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
        });
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.language_names, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner language = findViewById(R.id.language);
        language.setAdapter(adapter);
        List<String> codes = Arrays.asList(
                getResources().getStringArray(R.array.language_codes)
        );
        language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mModel.language = codes.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        if (TextUtils.isEmpty(mModel.language)) {
            LocaleListCompat locales =
                    ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration());
            String locale = locales.get(0).getISO3Language();
            if (codes.contains(locale)) {
                mModel.language = locale;
            } else {
                mModel.language = codes.get(0);
            }
        }
        language.setSelection(codes.indexOf(mModel.language));
        Bitmap image = VideoUtil.getFrameAtTime(mClip, TimeUnit.SECONDS.toMicros(3));
        ImageView thumbnail = findViewById(R.id.thumbnail);
        thumbnail.setImageBitmap(image);
        SwitchMaterial private2 = findViewById(R.id.private2);
        private2.setChecked(mModel.private2);
        private2.setOnCheckedChangeListener((button, checked) -> mModel.private2 = checked);
        SwitchMaterial comments = findViewById(R.id.comments);
        comments.setChecked(mModel.comments);
        comments.setOnCheckedChangeListener((button, checked) -> mModel.comments = checked);
    }

    private void uploadToServer() {
        File preview = new File(getFilesDir(), UUID.randomUUID().toString());
        File screenshot = new File(getFilesDir(), UUID.randomUUID().toString());
        Data data1 = new Data.Builder()
                .putString(GeneratePreviewWorker.KEY_INPUT, mClip)
                .putString(GeneratePreviewWorker.KEY_SCREENSHOT, screenshot.getAbsolutePath())
                .putString(GeneratePreviewWorker.KEY_PREVIEW, preview.getAbsolutePath())
                .build();
        OneTimeWorkRequest request1 = new OneTimeWorkRequest.Builder(GeneratePreviewWorker.class)
                .setInputData(data1)
                .build();
        Data data2 = new Data.Builder()
                .putInt(UploadClipWorker.KEY_SONG, mSong)
                .putString(UploadClipWorker.KEY_VIDEO, mClip)
                .putString(UploadClipWorker.KEY_SCREENSHOT, screenshot.getAbsolutePath())
                .putString(UploadClipWorker.KEY_PREVIEW, preview.getAbsolutePath())
                .putString(UploadClipWorker.KEY_DESCRIPTION, mModel.description)
                .putString(UploadClipWorker.KEY_LANGUAGE, mModel.language)
                .putBoolean(UploadClipWorker.KEY_PRIVATE, mModel.private2)
                .putBoolean(UploadClipWorker.KEY_COMMENTS, mModel.comments)
                .build();
        OneTimeWorkRequest request2 = new OneTimeWorkRequest.Builder(UploadClipWorker.class)
                .setInputData(data2)
                .build();
        WorkManager.getInstance(this).beginWith(request1).then(request2).enqueue();
        Toast.makeText(this, R.string.uploading_message, Toast.LENGTH_SHORT).show();
        finish();
    }

    public static class UploadActivityViewModel extends ViewModel {

        public String description = "";
        public String language = null;
        public boolean private2 = false;
        public boolean comments = true;
    }
}
