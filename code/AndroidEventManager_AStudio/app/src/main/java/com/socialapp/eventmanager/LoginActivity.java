/**
 * Copyright (c) 2014-present, Facebook, Inc. All rights reserved.
 *
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Facebook.
 *
 * As with any software that integrates with the Facebook platform, your use of
 * this software is subject to the Facebook Developer Principles and Policies
 * [http://developers.facebook.com/policy/]. This copyright notice shall be
 * included in all copies or substantial portions of the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.socialapp.eventmanager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphRequestBatch;
import com.facebook.GraphResponse;
import com.facebook.Profile;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.CallbackManager;
import com.socialapp.eventmanager.Models.User;
import com.socialapp.eventmanager.R;
import com.socialapp.eventmanager.SplashFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;

public class LoginActivity extends FragmentActivity {

    private static final String USER_SKIPPED_LOGIN_KEY = "user_skipped_login";
    private static String TAG = "Sujith";

    private static final int SPLASH = 0;
    private static final int SELECTION = 1;
    private static final int SETTINGS = 0;
    private static final int FRAGMENT_COUNT = SETTINGS +1;

    private Fragment[] fragments = new Fragment[FRAGMENT_COUNT];
    private boolean isResumed = false;
    private boolean userSkippedLogin = false;
    private AccessTokenTracker accessTokenTracker;
    private CallbackManager callbackManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            userSkippedLogin = savedInstanceState.getBoolean(USER_SKIPPED_LOGIN_KEY);
        }



        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    "com.socialapp.eventmanager",
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String keyHash = Base64.encodeToString(md.digest(), Base64.DEFAULT);
                Log.d("************* KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }
        } catch (PackageManager.NameNotFoundException e) {

        } catch (NoSuchAlgorithmException e) {

        }


        FacebookSdk.sdkInitialize(getApplicationContext());

        callbackManager = CallbackManager.Factory.create();

        accessTokenTracker = new AccessTokenTracker() {
            @Override
            protected void onCurrentAccessTokenChanged(AccessToken oldAccessToken,
                                                       AccessToken currentAccessToken) {

                checkAndRedirectOnFBLogin();

                if (isResumed) {
                    FragmentManager manager = getSupportFragmentManager();
                    int backStackSize = manager.getBackStackEntryCount();
                    for (int i = 0; i < backStackSize; i++) {
                        manager.popBackStack();
                    }
                    if (currentAccessToken != null) {
                        // showFragment(SELECTION, false);
                    } else {
                        showFragment(SPLASH, true);
                    }
                }
            }
        };

        setContentView(R.layout.activity_login);

        FragmentManager fm = getSupportFragmentManager();
        SplashFragment splashFragment = (SplashFragment)fm.findFragmentById(R.id.splashFragment);
        fragments[SPLASH] = splashFragment;


        FragmentTransaction transaction = fm.beginTransaction();
        // for(int i = 0; i < fragments.length; i++) {
        transaction.hide(fragments[0]);
        // }
        transaction.commit();

        splashFragment.setSkipLoginCallback(new SplashFragment.SkipLoginCallback() {
            @Override
            public void onSkipLoginPressed() {
                userSkippedLogin = true;
                //showFragment(SELECTION, false);
            }
        });

        accessTokenTracker.startTracking();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String userLoggedIn = prefs.getString("email", null);
        if(userLoggedIn != null)
        {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }
    }

    public void checkAndRedirectOnFBLogin()
    {
        if (AccessToken.getCurrentAccessToken() != null) {
            // if the user already logged in, try to show the selection fragment
            Toast.makeText(getApplicationContext(), "Facebook Login successful", Toast.LENGTH_SHORT).show();
            AccessToken accessToken = AccessToken.getCurrentAccessToken();
            Profile profile = Profile.getCurrentProfile();

            // Using Facebook API for getting User details as well as
            // friends that are using this app.
            JSONArray friendsIDarray = new JSONArray();
            JSONObject user_friend_list;

            GraphRequestBatch batch = new GraphRequestBatch(
                    GraphRequest.newMeRequest(
                            accessToken,
                            new GraphRequest.GraphJSONObjectCallback() {
                                @Override
                                public void onCompleted(
                                        JSONObject jsonObject,
                                        GraphResponse response) {
                                    Log.i("Info Msg:", "Response = ." + response.toString());

                                    try {
                                        Log.i("Info Msg:", "Logged in Users FB id = ." + jsonObject.getString("id"));
                                        Log.i("Info Msg:", "Logged in Users FB name = ." + jsonObject.getString("name"));
                                        SplashFragment.friendsUsingApp.put(jsonObject.getString("id"),
                                                jsonObject.getString("name"));
                                    }
                                    catch (Exception e)
                                    {
                                    }
                                }
                            }),
                    // FB API for getting users friends that are using this app.
                    GraphRequest.newMyFriendsRequest(
                            accessToken,
                            new GraphRequest.GraphJSONArrayCallback() {
                                @Override
                                public void onCompleted(
                                        JSONArray jsonArray,
                                        GraphResponse response) {

                                    try {
                                       SplashFragment.friendsUsingApp.clear();
                                        for (int i = 0; i < jsonArray.length(); i++) {
                                            Log.i("Info Msg:", "id = ." + jsonArray.getJSONObject(i).getString("id"));
                                            Log.i("Info Msg:", "name = ." + jsonArray.getJSONObject(i).getString("name"));
                                            SplashFragment.friendsUsingApp.put(jsonArray.getJSONObject(i).getString("id"),
                                                    jsonArray.getJSONObject(i).getString("name"));
                                        }

                                        Log.i("Info Msg:", "Response = ." + response.toString());
                                    }
                                    catch (Exception e)
                                    {

                                    }
                                }
                            })
            );
            batch.addCallback(new GraphRequestBatch.Callback() {
                @Override
                public void onBatchCompleted(GraphRequestBatch graphRequests) {
                    // Application code for when the batch finishes
                }
            });
            batch.executeAsync();

            if (null != profile) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = prefs.edit();
                //editor.putString("id", profile.getId());
                editor.putString("fbFirstName", profile.getFirstName());
                editor.putString("fbLastName", profile.getLastName());
                editor.commit();


                // Sujith Addition of code to backend
                final String email = profile.getFirstName() + " " + profile.getLastName() + "@facebook.com";
                final String password = profile.getId();

                Log.d(TAG, "Attempting to signup with email: " + email + " password: " + password);
                Backend.signUp(email, password, new Backend.BackendCallback() {
                    @Override
                    public void onRequestCompleted(final String result) {

                        Log.d(TAG, "Signup success .. so signing in directly");

                        runOnUiThread(new Runnable() {
                            public void run() {
                                executebackendlogin(email,password);

                                //Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onRequestFailed(final String message) {

                        //NOTE: parameter validation and filtering is handled by the backend, just show the
                        //returned error message to the user
                        Log.d(TAG, "Received error from Backend for signup.. so signing in: " + message);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                // Sign in //////////////////////////////////////////////////
                                Log.d(TAG, "Attempting to login with email: " + email + " password: " + password);
                                executebackendlogin(email,password);
                                //Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });










            }
            //Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            //startActivity(intent);

        }
    }




    private void executebackendlogin(final String email,String password){
        Backend.logIn(email, password, new Backend.BackendCallback() {
            @Override
            public void onRequestCompleted(final String result) {

                //Log.d(TAG, "Login success. User: " + user.toString());

                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("email", email);
                        editor.commit();

                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
            }

            @Override
            public void onRequestFailed(final String message) {

                //NOTE: parameter validation and filtering is handled by the backend, just show the
                //returned error message to the user
                Log.d(TAG, "Received error from Backend: " + message);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Unfortunately, unable to register to Backend", Toast.LENGTH_SHORT).show();

                        // Delete this later on
                        //Intent intent = new Intent(currContext, MainActivity.class);
                        //startActivity(intent);

                    }
                });
            }
        });
    }
/*
    private void onSessionStateChange(Session session, SessionState state,
                                      Exception exception) {
        if (state.isOpened()) {
            Log.i(TAG, "Logged in...");
            Request.executeMyFriendsRequestAsync(session,
                    new GraphUserListCallback() {

                        @Override
                        public void onCompleted(List<GraphUser> users,
                                                Response response) {
                            Log.i("Response JSON", response.toString());
                            names = new String[users.size()];
                            id = new String[users.size()];
                            for (int i=0; i<users.size();i++){
                                names[i] = users.get(i).getName();
                                id[i]= users.get(i).getId();
                            }
                        }
                    });
        } else if (state.isClosed()) {
            Log.i(TAG, "Logged out...");
        }
    }
*/

    public void login(View view){
        final EditText emailView = (EditText)findViewById(R.id.email);
        EditText pwView = (EditText) findViewById(R.id.password);
        final Context currContext = this;

        String email = emailView.getText().toString();
        String password = pwView.getText().toString();


        if((emailView.getText().toString()).matches("")){
            Toast.makeText(this, "Please enter your email id", Toast.LENGTH_SHORT).show();
        }else{

            Log.d(TAG, "Attempting to login with email: " + email + " password: " + password);
            Backend.logIn(email, password, new Backend.BackendCallback() {
                @Override
                public void onRequestCompleted(final String result) {

                    //Log.d(TAG, "Login success. User: " + user.toString());

                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("email", emailView.getText().toString());
                            editor.commit();

                            Intent intent = new Intent(currContext, MainActivity.class);
                            startActivity(intent);
                        }
                    });
                }

                @Override
                public void onRequestFailed(final String message) {

                    //NOTE: parameter validation and filtering is handled by the backend, just show the
                    //returned error message to the user
                    Log.d(TAG, "Received error from Backend: " + message);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();

                            // Delete this later on
                            //Intent intent = new Intent(currContext, MainActivity.class);
                            //startActivity(intent);

                        }
                    });
                }
            });

        }
    }


    // This is only for Testing purpose .... remove it later on

    public void login_for_test(View view){
        final EditText emailView = (EditText)findViewById(R.id.email);
        EditText pwView = (EditText) findViewById(R.id.password);
        final Context currContext = this;

        String email = emailView.getText().toString();
        String password = pwView.getText().toString();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("email", emailView.getText().toString());
        editor.commit();

        Intent intent = new Intent(currContext, MainActivity.class);
        startActivity(intent);
    }

    public void signup(View view){

        EditText emailView = (EditText)findViewById(R.id.email);
        EditText pwView = (EditText) findViewById(R.id.password);
        final Context currContext = this;
        String email = emailView.getText().toString();
        String password = pwView.getText().toString();

        if((emailView.getText().toString()).matches("")){
            Toast.makeText(this, "Please enter your email id", Toast.LENGTH_SHORT).show();
        }else {
            Log.d(TAG, "Attempting to login with email: " + email + " password: " + password);
            Backend.signUp(email, password, new Backend.BackendCallback() {
                @Override
                public void onRequestCompleted(final String result) {

                    //Log.d(TAG, "Login success. User: " + user.toString());

                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onRequestFailed(final String message) {

                    //NOTE: parameter validation and filtering is handled by the backend, just show the
                    //returned error message to the user
                    Log.d(TAG, "Received error from Backend: " + message);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        isResumed = true;

        // Call the 'activateApp' method to log an app event for use in analytics and advertising
        // reporting.  Do so in the onResume methods of the primary Activities that an app may be
        // launched into.
        AppEventsLogger.activateApp(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        isResumed = false;

        // Call the 'deactivateApp' method to log an app event for use in analytics and advertising
        // reporting.  Do so in the onPause methods of the primary Activities that an app may be
        // launched into.
        AppEventsLogger.deactivateApp(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        accessTokenTracker.stopTracking();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(USER_SKIPPED_LOGIN_KEY, userSkippedLogin);
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        if (AccessToken.getCurrentAccessToken() != null) {
            // if the user already logged in, try to show the selection fragment
            //showFragment(SELECTION, false);
            userSkippedLogin = false;
        } else {
            // otherwise present the splash screen and ask the user to login,
            // unless the user explicitly skipped.
            showFragment(SPLASH, true);
        }
    }

    public void showSettingsFragment() {
        //showFragment(SETTINGS, true);
    }

    public void showSplashFragment() {
        showFragment(SPLASH, true);
    }


    private void showFragment(int fragmentIndex, boolean addToBackStack) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        for (int i = 0; i < fragments.length; i++) {
            if (i == fragmentIndex) {
                transaction.show(fragments[i]);
            } else {
                transaction.hide(fragments[i]);
            }
        }
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }
}
