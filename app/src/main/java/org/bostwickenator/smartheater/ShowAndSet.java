package org.bostwickenator.smartheater;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.text.DecimalFormat;

import javax.net.ssl.HttpsURLConnection;

public class ShowAndSet extends AppCompatActivity {

    public static final String DEVICE_ID = "[YOUR DEVICE ID]";
    public static final String AUTH = "[YOUR AUTH]";
    public static final String API_BASE = "https://api.particle.io/v1/devices/";
    public static final float NO_TEMP = 999;

    Context mContext;
    EditText targetTempEditText;
    TextView temperatureTextView;
    Button setTargetButton;
    ActionBar mActionBar;

    float currentTemp = NO_TEMP;
    float targetTemp = NO_TEMP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_and_set);

        mContext = this;

        temperatureTextView = (TextView) findViewById(R.id.temperatureTextView);
        targetTempEditText = (EditText) findViewById(R.id.targetTempEditText);
        setTargetButton = (Button) findViewById(R.id.setTargeBbutton);

        mActionBar = this.getSupportActionBar();

        setTargetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new SetTarget().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });

    }

    long timeOfLastUpdate;

    @Override
    protected void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (now - timeOfLastUpdate > 5000) {
            timeOfLastUpdate = now;
            new GetTemperature().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            new GetTargetTemp().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    class GetTemperature extends AsyncTask {

        String result;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            temperatureTextView.setText("--");
        }

        @Override
        protected Object doInBackground(Object[] objects) {

            try {
                result = getJsonFromApi(API_BASE + DEVICE_ID + "/temp/?", null).get("result").toString();
            } catch (Exception e) {
                result = "Error";
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);

            float temperature = NO_TEMP;

            try {
                temperature = Float.parseFloat(result);
            } catch (Exception e) {
                e.printStackTrace();
            }


            String prettyTemp = temperature == NO_TEMP ? "??" : new DecimalFormat("#.##").format(temperature) + "°C";

            temperatureTextView.setText(prettyTemp);
            currentTemp = temperature;
            animateColor();
        }
    }

    class GetTargetTemp extends AsyncTask {

        String result;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            targetTempEditText.setText("Fetching...");
            setUpdatingTarget(true);
        }

        @Override
        protected Object doInBackground(Object[] objects) {

            try {
                result = getJsonFromApi(API_BASE + DEVICE_ID + "/target/?", null).get("result").toString();
            } catch (Exception e) {
                result = "Error";
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            targetTempEditText.setText(result);

            float temperature = NO_TEMP;

            try {
                temperature = Float.parseFloat(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
            targetTemp = temperature;

            setUpdatingTarget(false);
            animateColor();
        }
    }

    public void setUpdatingTarget(boolean updating) {
        setTargetButton.setEnabled(!updating);
    }

    class SetTarget extends AsyncTask {

        String target;
        String result;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setUpdatingTarget(true);
            target = targetTempEditText.getText().toString();
        }

        @Override
        protected Object doInBackground(Object[] objects) {

            try {
                result = getJsonFromApi(API_BASE + DEVICE_ID + "/setTarget", "{\"arg\":\"" + target + "\"}").get("return_value").toString();
            } catch (Exception e) {
                result = e.toString();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            System.out.println(result);
            super.onPostExecute(o);
            setUpdatingTarget(false);

            if (result.equals("1")) {

                float temperature = NO_TEMP;

                try {
                    temperature = Float.parseFloat(target);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                targetTemp = temperature;
                animateColor();
            } else {
                Toast.makeText(mContext, "Could not set target", Toast.LENGTH_LONG).show();
            }
        }
    }


    /**
     * Either GET a value from the API or POST one to it. If you pass a body string it will be sent
     * as the body of the post. Otherwise a GET will be performed
     *
     * @param urlString
     * @param body
     * @return
     * @throws Exception
     */
    public JSONObject getJsonFromApi(String urlString, String body) throws Exception {
        JSONObject response = new JSONObject(getStringFromApi(urlString, body));
        return response;
    }

    public String getStringFromApi(String urlString, String body) throws Exception {

        URL url = new URL(urlString);
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.setUseCaches(false);
        urlConnection.setRequestProperty("Authorization", "Bearer " + AUTH);

        if (body != null) {
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            byte[] outputInBytes = body.getBytes("UTF-8");
            OutputStream os = urlConnection.getOutputStream();
            os.write(outputInBytes);
            os.close();
        }
        return streamToString(urlConnection.getInputStream());
    }

    /**
     *
     */
    private String streamToString(InputStream input) throws IOException {
        BufferedReader streamReader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder responseStrBuilder = new StringBuilder();

        String inputStr;
        while ((inputStr = streamReader.readLine()) != null) {
            responseStrBuilder.append(inputStr);
        }
        return responseStrBuilder.toString();
    }


    /**
     * Start a color animation cycle. This tweens from whatever the current color it to the
     * appropriate color for the current temperature.
     */
    public void animateColor() {
        Integer colorFrom = temperatureTextView.getCurrentTextColor();
        Integer colorTo = getColorForTemperature();
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                int color = (Integer) animator.getAnimatedValue();
                int colorMute = getMuteColor(color);

                temperatureTextView.setTextColor(color);
                mActionBar.setBackgroundDrawable(new ColorDrawable(color));
                if (Build.VERSION.SDK_INT >= 21) {
                    getWindow().setStatusBarColor(colorMute
                    );
                }
            }

        });
        colorAnimation.setDuration(1000);
        colorAnimation.start();
    }

    /**
     * @param color
     * @return
     */
    private int getMuteColor(int color) {
        float[] hsv = {0.0f, 0.0f, 0.0f};
        Color.colorToHSV(color, hsv);
        hsv[2] *= .7f;
        return Color.HSVToColor(hsv);
    }

    private int getColorForTemperature() {
        if (currentTemp == NO_TEMP || targetTemp == NO_TEMP) {
            return getResources().getColor(R.color.colorPrimary);
        }
        float delta = currentTemp - targetTemp;
        if (delta > 1) {
            return getResources().getColor(R.color.colorWarm);
        } else if (delta < -1) {
            return getResources().getColor(R.color.colorCold);
        } else {
            return getResources().getColor(R.color.colorNeutral);
        }

    }
}
