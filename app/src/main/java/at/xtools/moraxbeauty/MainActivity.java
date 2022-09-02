package at.xtools.moraxbeauty;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import at.xtools.moraxbeauty.ui.UIManager;
import at.xtools.moraxbeauty.webview.WebViewHelper;

public class MainActivity extends AppCompatActivity {
    // Globals
    private UIManager uiManager;
    private WebViewHelper webViewHelper;
    private boolean intentHandled = false;


    public static final int REQUEST_SELECT_FILE = 100;
    public static final int FILECHOOSER_RESULTCODE = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Setup Theme
        setTheme(R.style.AppTheme_NoActionBar);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Setup Helpers
        uiManager = new UIManager(this);
        webViewHelper = new WebViewHelper(this, uiManager);

        // Setup App
        webViewHelper.setupWebView();
        uiManager.changeRecentAppsIcon();
        checkAndRequestPermissions();

        // Check for Intents
        try {
            Intent i = getIntent();
            String intentAction = i.getAction();
            // Handle URLs opened in Browser
             if (!intentHandled && intentAction != null && intentAction.equals(Intent.ACTION_VIEW)){
                    Uri intentUri = i.getData();
                    String intentText = "";
                    if (intentUri != null){
                        intentText = intentUri.toString();
                    }
                    // Load up the URL specified in the Intent
                    if (!intentText.equals("")) {
                        intentHandled = true;
                        webViewHelper.loadIntentUrl(intentText);
                    }
             } else {
                 // Load up the Web App
                 webViewHelper.loadHome();
             }
        } catch (Exception e) {
            // Load up the Web App
            webViewHelper.loadHome();
        }
    }

    @Override
    protected void onPause() {
        webViewHelper.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        webViewHelper.onResume();
        // retrieve content from cache primarily if not connected
        webViewHelper.forceCacheIfOffline();
        super.onResume();
    }

    // Handle back-press in browser
    @Override
    public void onBackPressed() {
        if (!webViewHelper.goBack()) {
            super.onBackPressed();
        }
    }

    private void checkAndRequestPermissions() {
        int cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int recordPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        int audioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int audioPermission2 = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        List<String> listPermissionsNeeded = new ArrayList<>();

        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (recordPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (audioPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (audioPermission2 != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),1);
        }
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            if (requestCode == REQUEST_SELECT_FILE)
            {
                if (webViewHelper.uploadMessage == null)
                    return;
                webViewHelper.uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
                webViewHelper.uploadMessage = null;
            }
        }
        else if (requestCode == FILECHOOSER_RESULTCODE)
        {
            if (null == webViewHelper.mUploadMessage)
                return;
            // Use MainActivity.RESULT_OK if you're implementing WebView inside Fragment
            // Use RESULT_OK only if you're implementing WebView inside an Activity
            Uri result = intent == null || resultCode != MainActivity.RESULT_OK ? null : intent.getData();
            webViewHelper.mUploadMessage.onReceiveValue(result);
            webViewHelper.mUploadMessage = null;
        }
        else
            Toast.makeText(getApplicationContext(), "Failed to Upload Image", Toast.LENGTH_LONG).show();
    }
}
