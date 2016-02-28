package com.unmannedairlines.dronepan;

import android.content.Context;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.GimbalApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.apis.solo.SoloApi;
import com.o3dr.android.client.apis.solo.SoloCameraApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionResult;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

public class MainActivity extends AppCompatActivity implements TowerListener, DroneListener {
    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    private boolean panoInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Hide the status bar
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Hide the action bar
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        // Initialize the drone objects
        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        // Link up the buttons
        final Button armButton = (Button) findViewById(R.id.button);
        armButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                armDrone();
            }
        });

        // Yaw button test
        final Button yawButton = (Button) findViewById(R.id.button2);
        yawButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //yawTimer();
            }
        });

        // Pitch gimbal button test
        final Button pitchButton = (Button) findViewById(R.id.button3);
        pitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitchGimbal(0);
            }
        });

        // Timer loop test button
        final Button timerButton = (Button) findViewById(R.id.button4);
        timerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setupPano();
            }
        });


    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            //updateConnectedButton(false);
        }
        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    @Override
    public void onTowerConnected() {
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);

        // Connect to drone
        Bundle extraParams = new Bundle();
        extraParams.putInt(ConnectionType.EXTRA_UDP_SERVER_PORT, 14550);
        ConnectionParameter connectionParams = new ConnectionParameter(ConnectionType.TYPE_UDP, extraParams, null);
        this.drone.connect(connectionParams);
    }

    @Override
    public void onTowerDisconnected() {

    }

    @Override
    public void onDroneConnectionFailed(ConnectionResult result) {

    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                TextView connectionStatusTextView = (TextView)findViewById(R.id.connectionStatus);
                connectionStatusTextView.setText("Connected");
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                connectionStatusTextView = (TextView)findViewById(R.id.connectionStatus);
                connectionStatusTextView.setText("Disconnected");
                //updateConnectedButton(this.drone.isConnected());
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                TextView altitudeTextView = (TextView)findViewById(R.id.altitudeTextView);
                Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
                altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                State vehicleState = this.drone.getAttribute(AttributeType.STATE);
                VehicleMode vehicleMode = vehicleState.getVehicleMode();
                TextView flightModeTextView = (TextView)findViewById(R.id.flightModeTextView);
                flightModeTextView.setText(vehicleMode.getLabel());

                // If the mode is switched and the pano is in progress let's release gimbal control
                if(vehicleMode.getLabel() != "Guided" && panoInProgress) {
                    panoInProgress = false;
                    GimbalApi.getApi(this.drone).stopGimbalControl(gimbalListener);
                }
                break;

            default:
                break;
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    public void showToast(String toast) {
        Toast.makeText(getApplicationContext(), toast, Toast.LENGTH_SHORT).show();
    }

    public void armDrone() {

        // Arm the drone
        VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {

            // On successful arming let's takeoff
            @Override
            public void onSuccess() {
                takeOff();
            }

            @Override
            public void onError(int executionError) {
                showToast("Error arming");
            }

            @Override
            public void onTimeout() {
                showToast("Arming timed out");
            }

        });

    }

    public void takeOff() {

        ControlApi.getApi(this.drone).takeoff(10, new AbstractCommandListener() {

            @Override
            public void onSuccess() {
                showToast("Taking off to 10m");
                //gotoWaypoint();
                //ControlApi.getApi(this.drone).goTo();

            }

            @Override
            public void onError(int executionError) {

            }

            @Override
            public void onTimeout() {

            }
        });
    }

    private static final int NUM_COLUMNS = 6;
    private int photo_count = 0;
    private int loop_count = 0;
    private int[] pitches = {0, -30, -60, -90};

    // Change to guided mode, start gimbal control, reset the gimbal to 0, and set mode to guided
    private void setupPano() {

        panoInProgress = true;

        // Set copter to guided
        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_GUIDED, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                showToast("Solo changed to GUIDED mode");
            }
        });

        // Enable gimbal control
        GimbalApi.getApi(this.drone).startGimbalControl(gimbalListener);

        // Reset gimbal to 0
        pitchGimbal(0);

        // Give the gimbal a couple seconds to reset and begin pano
        final Handler h = new Handler();

        final Runnable begin = new Runnable() {

            @Override
            public void run() {
                loopAndShoot(); // Let's being the pano process
            }
        };

        h.postDelayed(begin, 3000);

    }

    private void loopAndShoot() {

        final Handler h = new Handler();

        // Pitch the gimbal
        final Runnable pitch = new Runnable() {

            // Pitch gimbal and either continue with loop or take nadir
            @Override
            public void run() {

                if(Build.MODEL.contains("SDK")) {

                    showToast("Pitching gimbal to: " + pitches[loop_count]);

                } else {

                    pitchGimbal(pitches[loop_count]);

                }

                photo_count = 0;

                // Take nadir shot
                if(loop_count == 3) {

                    loop_count = 0;

                    takeNadirPhotoAndFinishPano();

                    // Continue with photo loop
                } else {

                    loopAndShoot();

                }
            }
        }; // End pitch gimbal

        // Yaw the drone
        final Runnable yaw = new Runnable() {

            @Override
            public void run() {

                if(Build.MODEL.contains("SDK")) {

                    showToast("Yaw drone: " + photo_count);

                } else {

                    yawDrone(60);

                }

                if(photo_count == NUM_COLUMNS) {
                    loop_count++;

                    // Now let's pitch gimbal and then we'll begin the next loop
                    h.postDelayed(pitch, 3000);

                } else {

                    loopAndShoot();

                }
            }
        }; // End yaw drone

        // Take a photo
        final Runnable photo = new Runnable() {

            @Override
            public void run() {

                if(Build.MODEL.contains("SDK")) {

                    showToast("Take photo");

                } else {

                    takePhoto();

                }

                h.postDelayed(yaw, 3000);

            }
        }; // End take photo

        h.postDelayed(photo, 3000);

        photo_count++;
    }

    private void yawDrone(int angle) {

        ControlApi.getApi(this.drone).turnTo(angle, 1, true, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                showToast("Yawing 60 degrees...");
            }

            @Override
            public void onError(int executionError) {
                showToast("Error yawing...");
            }

            @Override
            public void onTimeout() {

            }
        });

        showToast("Yaw drone");

    }

    private void gotoWaypoint() {
        //LatLongAlt vehicle3DPosition = new LatLongAlt(vehiclePosition.getLatitude(), vehiclePosition.getLongitude(), vehicleAltitude);
        //LatLong point = new LatLong();
        //MissionApi.getApi(this.drone).


        ControlApi.getApi(this.drone).goTo(new LatLong(1, 1), false, new AbstractCommandListener() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onError(int executionError) {

            }

            @Override
            public void onTimeout() {

            }
        });
    }

    private void takePhoto() {
        SoloCameraApi.getApi(this.drone).takePhoto(new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                //showToast("Photo taken.");
            }

            @Override
            public void onError(int executionError) {
                showToast("Error while trying to take the photo: " + executionError);
            }

            @Override
            public void onTimeout() {
                showToast("Timeout while trying to take the photo.");
            }
        });
    }

    // Take the nadir shot, release drone and gimbal control back to the user
    private void takeNadirPhotoAndFinishPano() {

        final Handler h = new Handler();

        final Runnable pitch = new Runnable() {

            @Override
            public void run() {

                if(Build.MODEL.contains("SDK")) {

                    showToast("Panorama complete!!!");

                } else {

                    pitchGimbal(0);

                    // Change mode back to FLY mode, which is Loiter
                    VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener() {
                        @Override
                        public void onSuccess() {
                            showToast("Panorama complete! You can now take control of Solo.");
                        }
                    });

                }
            }

        };

        final Runnable finish = new Runnable() {

            @Override
            public void run() {

                if(Build.MODEL.contains("SDK")) {

                    showToast("Taking nadir shot");

                } else {

                    takePhoto();

                }

                h.postDelayed(pitch, 3000);

            }
        };

        h.postDelayed(finish, 3000);

    }

    GimbalApi.GimbalOrientationListener gimbalListener = new GimbalApi.GimbalOrientationListener() {

        @Override
        public void onGimbalOrientationUpdate(GimbalApi.GimbalOrientation orientation) {

        }

        @Override
        public void onGimbalOrientationCommandError(int error) {
            showToast("Gimbal error");
        }
    };

    private void pitchGimbal(int angle) {

        GimbalApi.getApi(this.drone).updateGimbalOrientation(angle, 0, 0, gimbalListener);
    }

    // This is called from the webappinterface
    public void parseMission(String missionStr) {
        showToast(missionStr);
    }
}
