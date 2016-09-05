package com.alexhart.maglev2;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity implements GalleryViewFrag.OnFragmentInteractionListener{

    private File file;
    private boolean isVideo;
    private static int prevPosition = 0;
    private boolean backToExit = false;
    public static boolean surfaceTextInitiated = false;
    public static final String CAMERA_RESTART = "com.alexhart.maglev2.mainactivity.camera_restart";
    public static final String CAMERA_CLOSE = "com.alexhart.maglev2.mainactivity.camera_close";
    ViewPager mViewPager = null;
    public static boolean inPreview = false;

    private final static String TAG = "MainActivity";

    ViewPager.OnPageChangeListener mOnPageChangeListener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            //gets called before views are created
            switch (position) {

                case 0:
                    inPreview = false;
                    surfaceTextInitiated = true;
                    prevPosition = 0;
                    break;
                case 1:

                    if (prevPosition == 2) {
                        mViewPager.setCurrentItem(0);
                        getSupportActionBar().show();
                        break;
                    }
                    inPreview = true;
                    prevPosition = 1;
                    break;
                case 2:
                    surfaceTextInitiated = false;
                    sendCameraBroadcast(MainActivity.CAMERA_CLOSE);
                    sendCameraBroadcast(GalleryViewFrag.CAMERA_ACTION);
                    inPreview = true;
                    prevPosition = 2;
                    break;
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        private void sendCameraBroadcast(String action) {
            Intent i = new Intent(action);

            switch (action) {
                case (MagLevControlFrag.CAMERA_PREVIEW):
                    break;

                case (MainActivity.CAMERA_RESTART):
                    break;

                case (MainActivity.CAMERA_CLOSE):
                    break;
            }
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mViewPager = (ViewPager) findViewById(R.id.pager);
        FragmentManager fm = getSupportFragmentManager();
        mViewPager.setAdapter(new pagerAdapter(fm));
        mViewPager.addOnPageChangeListener(mOnPageChangeListener);
        PreferenceManager.setDefaultValues(this, R.xml.pref_frag, false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.main, menu);
        invalidateOptionsMenu();
        return true;
    }

    @Override
    public void onFragmentInteraction(File file,boolean isVideo){
        this.file = file;
        this.isVideo = isVideo;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if(inPreview){
            getSupportActionBar().hide();
        }
        else{
            getSupportActionBar().show();
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public File getdatafile(){
        return file;
    }

    public boolean isVideo(){
        return isVideo;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (backToExit) {
            super.onBackPressed();
            return;
        }
        this.backToExit= true;
        Toast.makeText(this, "Press back again to exit!", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                backToExit = false;

            }
        }, 2000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        surfaceTextInitiated = false;
    }
}

class pagerAdapter extends FragmentPagerAdapter {

    public pagerAdapter (FragmentManager fm) {
        super(fm);
    }

    //return fragment at given position
    @Override
    public Fragment getItem(int position) {
        Fragment fragment = null;

        switch (position){
            case 0:
                fragment = new MagLevControlFrag();
                break;
            case 1:
                fragment = new PreviewFrag();
                break;
            case 2:
                fragment = new GalleryViewFrag();
                break;
            case 3:
                fragment = new HistogramFrag();
        }
        return fragment;
    }

    @Override
    public int getCount() {
        //# pages
        return 4;
    }

    @Override
    public CharSequence getPageTitle(int position) {
//        Log.d("MainActivity", "GetPageTitle");
        switch (position){
            case 0:
                return "MagLev Control";
            case 1:
                return "Preview";
            case 2:
                return "GalleryView";
            case 3:
                return "Histogram";
        }
        return null;
    }
}