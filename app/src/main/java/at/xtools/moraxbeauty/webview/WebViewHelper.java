package at.xtools.moraxbeauty.webview;

import static android.content.Context.DOWNLOAD_SERVICE;
import static at.xtools.moraxbeauty.MainActivity.FILECHOOSER_RESULTCODE;
import static at.xtools.moraxbeauty.MainActivity.REQUEST_SELECT_FILE;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import at.xtools.moraxbeauty.Constants;
import at.xtools.moraxbeauty.R;
import at.xtools.moraxbeauty.ui.UIManager;

public class WebViewHelper {
    // Instance variables
    private Activity activity;
    private UIManager uiManager;
    private WebView webView;
    private WebSettings webSettings;
    public ValueCallback<Uri[]> uploadMessage;
    public ValueCallback<Uri> mUploadMessage;

    public WebViewHelper(Activity activity, UIManager uiManager) {
        this.activity = activity;
        this.uiManager = uiManager;
        this.webView = (WebView) activity.findViewById(R.id.webView);
        this.webSettings = webView.getSettings();
    }

    public WebView getWebView(){
        return this.webView;
    }

    /**
     * Simple helper method checking if connected to Network.
     * Doesn't check for actual Internet connection!
     *
     * @return {boolean} True if connected to Network.
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager manager =
                (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = manager.getActiveNetworkInfo();

        boolean isAvailable = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            // Wifi or Mobile Network is present and connected
            isAvailable = true;
        }
        return isAvailable;
    }

    // manipulate cache settings to make sure our PWA gets updated
    private void useCache(Boolean use) {
        if (use) {
            webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        } else {
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        }
    }

    // public method changing cache settings according to network availability.
    // retrieve content from cache primarily if not connected,
    // allow fetching from web too otherwise to get updates.
    public void forceCacheIfOffline() {
        useCache(!isNetworkAvailable());
    }

    // handles initial setup of webview
    public void setupWebView() {
        // accept cookies
        CookieManager.getInstance().setAcceptCookie(true);
        // enable JS
        webSettings.setJavaScriptEnabled(true);
        // must be set for our js-popup-blocker:
        webSettings.setSupportMultipleWindows(true);
        webSettings.setSupportZoom(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        // PWA settings
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            webSettings.setDatabasePath(activity.getApplicationContext().getFilesDir().getAbsolutePath());
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            webSettings.setAppCacheMaxSize(Long.MAX_VALUE);
        }
        webSettings.setDomStorageEnabled(true);
        webSettings.setAppCachePath(activity.getApplicationContext().getCacheDir().getAbsolutePath());
        webSettings.setAppCacheEnabled(true);
        webSettings.setDatabaseEnabled(true);

        // enable mixed content mode conditionally
        if (Constants.ENABLE_MIXED_CONTENT
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        // retrieve content from cache primarily if not connected
        forceCacheIfOffline();

        // set User Agent
        if (Constants.OVERRIDE_USER_AGENT || Constants.POSTFIX_USER_AGENT) {
            String userAgent = webSettings.getUserAgentString();
            if (Constants.OVERRIDE_USER_AGENT) {
                userAgent = Constants.USER_AGENT;
            }
            if (Constants.POSTFIX_USER_AGENT) {
                userAgent = userAgent + " " + Constants.USER_AGENT_POSTFIX;
            }
            webSettings.setUserAgentString(userAgent);
        }

        // enable HTML5-support
        webView.setWebChromeClient(new WebChromeClient() {
            //simple yet effective redirect/popup blocker
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                Message href = view.getHandler().obtainMessage();
                view.requestFocusNodeHref(href);
                final String popupUrl = href.getData().getString("url");
                if (popupUrl != null) {
                    //it's null for most rouge browser hijack ads
                    webView.loadUrl(popupUrl);
                    return true;
                }
                return false;
            }

            // For 3.0+ Devices (Start)
            // onActivityResult attached before constructor
            protected void openFileChooser(ValueCallback uploadMsg, String acceptType) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(i, "File Browser"), FILECHOOSER_RESULTCODE);
            }

            // For Lollipop 5.0+ Devices
            public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }

                uploadMessage = filePathCallback;

                Intent intent = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    intent = fileChooserParams.createIntent();
                    List<String> validMimeTypes = extractValidMimeTypes(fileChooserParams.getAcceptTypes());
                    if (validMimeTypes.isEmpty()) {
                        intent.setType("image/*");
                    } else {
                        intent.setType(String.join(" ", validMimeTypes));
                    }
                }
                try {
                    activity.startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (ActivityNotFoundException e) {
                    uploadMessage = null;
                    Toast.makeText(activity.getApplicationContext(), "Cannot Open File Chooser", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }


            public void showFileChooser(ValueCallback<String[]> filePathCallback,
                                        String acceptType, boolean paramBoolean) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
            }

            public void showFileChooser(ValueCallback<String[]> uploadFileCallback,
                                        FileChooserParams fileChooserParams) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
            }

            // update ProgressBar
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                uiManager.setLoadingProgress(newProgress);
                super.onProgressChanged(view, newProgress);
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(webView.getContext())
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        result.confirm();
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        result.cancel();
                                    }
                                })
                        .create()
                        .show();

                return true;
            }
        });

        // Set up Webview client
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                handleUrlLoad(view, url);
            }

            // handle loading error by showing the offline screen
            @Deprecated
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    handleLoadError(errorCode);
                }
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // new API method calls this on every error for each resource.
                    // we only want to interfere if the page itself got problems.
                    String url = request.getUrl().toString();
                    if (view.getUrl().equals(url)) {
                        handleLoadError(error.getErrorCode());
                    }
                }
            }
        });
    }

    // Lifecycle callbacks
    public void onPause() {
        webView.onPause();
    }

    public void onResume() {
        webView.onResume();
    }

    // show "no app found" dialog
    private void showNoAppDialog(Activity thisActivity) {
        new AlertDialog.Builder(thisActivity)
                .setTitle(R.string.noapp_heading)
                .setMessage(R.string.noapp_description)
                .show();
    }

    // handle load errors
    private void handleLoadError(int errorCode) {
        if (errorCode != WebViewClient.ERROR_UNSUPPORTED_SCHEME) {
            uiManager.setOffline(true);
        } else {
            // Unsupported Scheme, recover
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    goBack();
                }
            }, 100);
        }
    }
    private List<String> extractValidMimeTypes(String[] mimeTypes) {
        List<String> results = new ArrayList<String>();
        List<String> mimes;
        if (mimeTypes.length == 1 && mimeTypes[0].contains(",")) {
            mimes = Arrays.asList(mimeTypes[0].split(","));
        } else {
            mimes = Arrays.asList(mimeTypes);
        }
        MimeTypeMap mtm = MimeTypeMap.getSingleton();
        for (String mime : mimes) {
            if (mime != null && mime.trim().startsWith(".")) {
                String extensionWithoutDot = mime.trim().substring(1, mime.trim().length());
                String derivedMime = mtm.getMimeTypeFromExtension(extensionWithoutDot);
                if (derivedMime != null && !results.contains(derivedMime)) {
                    // adds valid mime type derived from the file extension
                    results.add(derivedMime);
                }
            } else if (mtm.getExtensionFromMimeType(mime) != null && !results.contains(mime)) {
                // adds valid mime type checked agains file extensions mappings
                results.add(mime);
            }
        }
        return results;
    }
    // handle external urls
    private boolean handleUrlLoad(WebView view, String url) {
        // prevent loading content that isn't ours
        if (!url.startsWith(Constants.WEBAPP_URL)) {
            // stop loading
            // stopping only would cause the PWA to freeze, need to reload the app as a workaround
//            view.stopLoading();
//            view.reload();

            // open external URL in Browser/3rd party apps instead
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(intent);
                } else {
                    showNoAppDialog(activity);
                }
            } catch (Exception e) {
                showNoAppDialog(activity);
            }

            view.loadUrl(Constants.WEBAPP_URL);
            // return value for shouldOverrideUrlLoading
            return true;
        } else {
            // let WebView load the page!
            // activate loading animation screen
            uiManager.setLoading(true);
            // return value for shouldOverrideUrlLoading
            return false;
        }
    }

    // handle back button press
    public boolean goBack() {
        if (webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

    // load app startpage
    public void loadHome() {
        webView.loadUrl(Constants.WEBAPP_URL);
    }

    // load URL from intent
    public void loadIntentUrl(String url) {
        if (!url.equals("") && url.contains(Constants.WEBAPP_HOST)) {
            webView.loadUrl(url);
        } else {
            // Fallback
            loadHome();
        }
    }
}
