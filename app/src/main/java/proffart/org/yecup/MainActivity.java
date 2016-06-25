package proffart.org.yecup;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {

    private final static String TAG = MainActivity.class.getSimpleName();

    private BluetoothGattCharacteristic characteristicTx = null;
    private BLEService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice = null;
    private String mDeviceAddress;
    private boolean isCelsius = true;
    private int setedTemperature = 42;
    Integer lastTempFromCup = 0;

    private boolean connState = false;
    private boolean scanFlag = false;

    private byte[] data = new byte[3];
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 2000;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((BLEService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(getApplicationContext(), "Disconnected",
                        Toast.LENGTH_SHORT).show();
            } else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                TextView disConnect = (TextView) findViewById(R.id.connect);
                disConnect.setText("Disconnect");
                disConnect.setPadding(0, -10, 0, 0);
                disConnect.setTextSize(18);

            } else if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                data = intent.getByteArrayExtra(BLEService.EXTRA_DATA);

                readAnalogInValue(data);
            }
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView tempUnit = (TextView) findViewById(R.id.celsius);

        // Changing temp unit (C and F).
        tempUnit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final TextView temp = (TextView) findViewById(R.id.temp);
                final String temperature = (String) temp.getText();

                if (((String)((TextView) findViewById(R.id.celsius)).getText()).contains("C")) {
                    tempUnit.setText(" °F");
                    temp.setText(celsiusToFahrenheit(temperature));
                    isCelsius = false;
                } else {
                    tempUnit.setText(" °C");
                    temp.setText(fahrenheitToCelsius(temperature));
                    isCelsius = true;
                }
            }
        });

        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        // Get bluetooth manager.
        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        // Bind service.
        Intent gattServiceIntent = new Intent(MainActivity.this,BLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Circular seek bar.
        SeekBar seekBar = (SeekBar) findViewById(R.id.slider);
        seekBar.setMaxProgress(55);

        seekBar.setRingBackgroundColor(Color.TRANSPARENT);
        seekBar.setBackGroundColor(Color.TRANSPARENT);
        seekBar.setProgressColor(Color.TRANSPARENT);

        seekBar.setBackgroundResource(R.drawable.temp);
        seekBar.invalidate();

        seekBar.setSeekBarChangeListener(new SeekBar.OnSeekChangeListener() {
            @Override
            public void onProgressChange(SeekBar view, final int newProgress) {
                String text = null;
                int progress;

                if (newProgress >= 0 && newProgress <= 26) {
                    progress = newProgress + 44;
                } else{
                    progress = newProgress - 12;
                }
                final TextView temp = (TextView) findViewById(R.id.temp);
                text = Integer.valueOf(progress).toString();
                // convert to Fahrenheits.
                if (!isCelsius) {
                    text = fahrenheitToCelsius(text);
                }
                temp.setText(text);
                // Send selected temperature to Yecup in Celsius.
                if (characteristicTx != null) {
                    characteristicTx.setValue(Integer.valueOf(progress).toString().getBytes());
                    mBluetoothLeService.writeCharacteristic(characteristicTx);
                    setedTemperature = progress;
                }
            }


        });

        // Set Rotation for connect text.
        TextView connectText = (TextView) findViewById(R.id.connect);
        connectText.setRotation(- 45);

        // Start scan for Yecup.
        connectText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (scanFlag == false) {
                    scanLeDevice();

                    Timer mTimer = new Timer();
                    mTimer.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            if (mDevice != null) {
                                mDeviceAddress = mDevice.getAddress();
                                mBluetoothLeService.connect(mDeviceAddress);
                                scanFlag = true;

                            } else {

                            }
                        }
                    }, SCAN_PERIOD);

                }
                if (connState == false) {
                    mBluetoothLeService.connect(mDeviceAddress);
                } else {
                    mBluetoothLeService.disconnect();
                    mBluetoothLeService.close();


                }
            }

        });
        if(scanFlag) {
            TextView disConnect = (TextView) findViewById(R.id.connect);
            disConnect.setText("Disconnect");
        }
    }

    /**
     * Read data.
     *
     * @param data byte array.
     */
    private void readAnalogInValue(final byte[] data) {
        String str = new String(data);
        if (str.length() >= 6) {
            String tmp = str.substring(0,2);
            try {
                Integer currTmp = Integer.parseInt(tmp);
                if (currTmp == setedTemperature ) {
                    final String alertText;
                    if (currTmp > lastTempFromCup) {
                        alertText = " \"Hey! I'm hot. Drink me now!\"";
                    } else {
                        alertText = "\"Brr.. I'm cold! Drink me!";
                    }

                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast toast = Toast.makeText(
                                    MainActivity.this,
                                    alertText,
                                    Toast.LENGTH_LONG);
                            toast.setGravity(0, 0, Gravity.CENTER);
                            toast.show();
                        }
                    });

                }
                lastTempFromCup = currTmp;
            } catch (Exception e) {
                System.out.println("Wrong data! :" + tmp);
            }
            final TextView currentTemp = (TextView) findViewById(R.id.currentTemp);
            if (!isCelsius) {
                tmp = celsiusToFahrenheit(tmp);
            }
            int inCharge = Integer.parseInt(str.substring(2,3));
            Integer chargeValue = Integer.parseInt(str.substring(3,5));
            currentTemp.setText("Current: " + tmp + "°C");
            final ProgressBar battery = (ProgressBar) findViewById(R.id.progress_bar);
            final TextView batteryCharge = (TextView) findViewById(R.id.charge);
            battery.setProgress(chargeValue);
            batteryCharge.setText(chargeValue.toString() + "%") ;
            if (chargeValue < 20) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast toast = Toast.makeText(
                                MainActivity.this,
                                "Buddy, charge me up!",
                                Toast.LENGTH_LONG);
                        toast.setGravity(0, 0, Gravity.CENTER);
                        toast.show();
                    }
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BLEService.ACTION_GATT_RSSI);

        return intentFilter;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void scanLeDevice() {
        new Thread() {

            @Override
            public void run() {
                mBluetoothAdapter.startLeScan(mLeScanCallback);

                try {
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }.start();
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             final byte[] scanRecord) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    byte[] serviceUuidBytes = new byte[16];
                    String serviceUuid = "";
                    for (int i = 32, j = 0; i >= 17; i--, j++) {
                        serviceUuidBytes[j] = scanRecord[i];
                    }
                    //serviceUuid = bytesToHex(serviceUuidBytes);
                    mDevice = device;


                }
            });
        }
    };


    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceConnection != null)
            unbindService(mServiceConnection);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT
                && resultCode == Activity
                .RESULT_CANCELED) {
            finish();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void getGattService(BluetoothGattService gattService) {
        if (gattService == null)
            return;

        characteristicTx = gattService
                .getCharacteristic(BLEService.UUID_BLE_SHIELD_TX);

        BluetoothGattCharacteristic characteristicRx = gattService
                .getCharacteristic(BLEService.UUID_BLE_SHIELD_RX);
        mBluetoothLeService.setCharacteristicNotification(characteristicRx,
                true);
        mBluetoothLeService.readCharacteristic(characteristicRx);
    }

    private String fahrenheitToCelsius(String value) {
        Double fahrenheit = Double.parseDouble(value);
        Double celsius = (5*(fahrenheit - 32))/9;
        return String.format( "%.0f", celsius);
    }

    private String celsiusToFahrenheit (String value) {
        Double celsius = Double.parseDouble(value);
        Double fahrenheit = (9 *celsius)/5 + 32;
        return String.format( "%.0f", fahrenheit);
    }

}
