package com.alexhart.maglev2;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Size;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class PreferencesFragment extends Activity {

    private static String mCameraID;
    private static final String TAG = "PrefFrag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCameraID = getIntent().getStringExtra("cameraID");
        //display preferences fragment as only fragment in activity
        getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFrag()).commit();
    }

    public static class PrefsFrag extends PreferenceFragment implements Preference.OnPreferenceChangeListener{

        private ListPreference mPictureQuality;
        private ListPreference mVideoQuality;
        private ListPreference mVideoFormat;
        private CheckBoxPreference mCameraStartup;
        private EditTextPreference mButtonTimer;
        private CameraManager mCameraManager;
        private CameraCharacteristics mCameraCharacteristics;

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_frag);
            initUI();
        }

        private void initUI() {

            mPictureQuality = (ListPreference) findPreference(getString(R.string.pref_picture_quality_key));
            mVideoQuality = (ListPreference) findPreference(getString(R.string.pref_video_quality_key));
            mVideoFormat = (ListPreference) findPreference(getString(R.string.pref_video_format_key));
            mCameraStartup = (CheckBoxPreference) findPreference(getString(R.string.pref_camera_startup_key));
            mButtonTimer = (EditTextPreference) findPreference(getString(R.string.pref_camera_button_delay));

            mPictureQuality.setOnPreferenceChangeListener(this);
            mVideoQuality.setOnPreferenceChangeListener(this);
            mButtonTimer.setOnPreferenceChangeListener(this);

            mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
            try {
                mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraID);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            StreamConfigurationMap map = mCameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map != null) {
                Size[] vidSizes = map.getOutputSizes(MediaRecorder.class);
                Size[] picSizes = map.getOutputSizes(ImageFormat.JPEG);

                String[] picSizeArray = new String[picSizes.length];
                String[] picSizeEntryVals = new String[picSizes.length];

                List<String> vidSizeList = new ArrayList<>();
                List<String> vidSizeEntryList = new ArrayList<>();

                int i = 0;
                for (Size picSize : picSizes){
                    picSizeArray[i] = picSize.toString();
                    picSizeEntryVals[i] = Integer.toString(i);
                    i++;
                }

                //TODO fix default
                mPictureQuality.setEntries(picSizeArray);
                mPictureQuality.setEntryValues(picSizeArray);

                i = 0;
                for (Size vidSize : vidSizes){
                    //4k resolution is max despite what is outputted
                    if (vidSize.getHeight() * vidSize.getWidth() > 8294400){
                        continue;
                    } else if (vidSize.getHeight() * vidSize.getWidth() == 8294400){
                        String entry = vidSize.toString() + " (4k resolution)";
                        vidSizeList.add(entry);
//                        vidSizeEntryList.add(Integer.toString(i));
                        vidSizeEntryList.add(vidSize.toString());
                    } else if (vidSize.getHeight() * vidSize.getWidth() == 2073600){
                        String entry = vidSize.toString() + " (1080p!)";
                        vidSizeList.add(entry);
//                        vidSizeEntryList.add(Integer.toString(i));
                        vidSizeEntryList.add(vidSize.toString());
                    } else {
                        vidSizeList.add(vidSize.toString());
//                        vidSizeEntryList.add(Integer.toString(i));
                        vidSizeEntryList.add(vidSize.toString());
                    }
                    i++;
                }

                mVideoQuality.setEntries(vidSizeList.toArray(new String[vidSizeList.size()]));
                mVideoQuality.setEntryValues(vidSizeEntryList.toArray(new String[vidSizeEntryList.size()]));
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object val) {
            if (preference.getKey().equals(getString(R.string.pref_picture_quality_key))) {
                int index= mPictureQuality.findIndexOfValue(val.toString());
                if (index != -1){
                    makeToast("Res Set: " + mPictureQuality.getEntries()[index]);
                }
            } else if (preference.getKey().equals(getString(R.string.pref_video_quality_key))) {
                int index = mVideoQuality.findIndexOfValue(val.toString());
                if (index != -1) {
                    makeToast("Res Set: " + mVideoQuality.getEntries()[index]);
                }
            } else if (preference.getKey().equals(getString(R.string.pref_camera_button_delay))) {
                if (Integer.parseInt(val.toString()) > 5) {
                    makeToast(getString(R.string.pref_button_delay_warning));
                }
            }
            return true;
        }

        private void makeToast(String msg) {
            Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStop() {
            super.onStop();
        }

        @Override
        public void onResume() {
            super.onResume();
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
    }
}
