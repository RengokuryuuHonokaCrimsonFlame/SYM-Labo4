package ch.heigvd.iict.sym_labo4;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProviders;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import ch.heigvd.iict.sym_labo4.abstractactivies.BaseTemplateActivity;
import ch.heigvd.iict.sym_labo4.adapters.ResultsAdapter;
import ch.heigvd.iict.sym_labo4.viewmodels.BleOperationsViewModel;

/**
 * Project: Labo4
 * Created by fabien.dutoit on 09.08.2019
 * (C) 2019 - HEIG-VD, IICT
 */

public class BleActivity extends BaseTemplateActivity {

    private static final String TAG = BleActivity.class.getSimpleName();

    //system services
    private BluetoothAdapter bluetoothAdapter = null;

    //view model
    private BleOperationsViewModel bleViewModel = null;

    //gui elements
    private View operationPanel = null;
    private View scanPanel = null;

    private ListView scanResults = null;
    private TextView emptyScanResults = null;

    //menu elements
    private MenuItem scanMenuBtn = null;
    private MenuItem disconnectMenuBtn = null;

    //adapters
    private ResultsAdapter scanResultsAdapter = null;

    //states
    private Handler handler = null;
    private boolean isScanning = false;

    //Interface
    private TextView sync;
    private Button synchronize;
    private TextView temperature;
    private Button get_temp;
    private TextView nbAppuis;
    private EditText valeur;
    private Button envoyer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble);

        this.handler = new Handler();

        //enable and start bluetooth - initialize bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();

        //link GUI
        this.operationPanel = findViewById(R.id.ble_operation);
        this.scanPanel = findViewById(R.id.ble_scan);
        this.scanResults = findViewById(R.id.ble_scanresults);
        this.emptyScanResults = findViewById(R.id.ble_scanresults_empty);

        this.sync = findViewById(R.id.sync);
        this.synchronize = findViewById(R.id.synchronize);
        this.temperature = findViewById(R.id.temperature);
        this.get_temp = findViewById(R.id.get_temp);
        this.nbAppuis = findViewById(R.id.nbAppuis);
        this.valeur = findViewById(R.id.valeur);
        this.envoyer = findViewById(R.id.envoyer);

        this.synchronize.setOnClickListener((v) -> {
            if(bleViewModel.readTemperature()) {
                runOnUiThread(() -> {
                    //we connect to the clicked device
                    this.bleViewModel.getmTemperature().observe(this, (temperature) -> {
                    });
                });
            }
        });

        //manage scanned item
        this.scanResultsAdapter = new ResultsAdapter(this);
        this.scanResults.setAdapter(this.scanResultsAdapter);
        this.scanResults.setEmptyView(this.emptyScanResults);

        //connect to view model
        this.bleViewModel = ViewModelProviders.of(this).get(BleOperationsViewModel.class);

        updateGui();

        //events
        this.scanResults.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> runOnUiThread(() -> {
            //we stop scanning
            scanLeDevice(false);
            //we connect to the clicked device
            bleViewModel.connect(((ScanResult)scanResultsAdapter.getItem(position)).getDevice());
        }));

        //ble events
        this.bleViewModel.isConnected().observe(this, (isConnected) -> updateGui());

        this.bleViewModel.getmNBAppuis().observe(this, (nbConnected) -> getNbClicks());

        this.get_temp.setOnClickListener((v) -> {
            bleViewModel.readTemperature();
            if (this.bleViewModel.getmTemperature().getValue() != null) {
                String temp = this.bleViewModel.getmTemperature().getValue().toString() + " °C";
                temperature.setText(temp);
            }
        });

        this.synchronize.setOnClickListener((v) -> {
            if(!bleViewModel.writeDate(Calendar.getInstance())){
                Toast.makeText(getApplicationContext(),"Echec de synchronisation",Toast.LENGTH_LONG).show();
            }
            else if (this.bleViewModel.getmDate().getValue() != null){
                sync.setText(this.bleViewModel.getmDate().getValue().getTime().toString());
            }
        });

        this.envoyer.setOnClickListener((v) ->{
            try{
                Integer i = Integer.parseInt(valeur.getText().toString());
                if(!bleViewModel.writeInteger(i)){
                    Toast.makeText(getApplicationContext(),"Echec d'envois de données",Toast.LENGTH_LONG).show();
                }
            }catch (Exception e){
                Toast.makeText(getApplicationContext(),"N'accepte que les entiers",Toast.LENGTH_LONG).show();
            }
        });
    }

    private void getNbClicks(){
        Integer nbClicks = 0;
        if (this.bleViewModel.getmNBAppuis().getValue() != null){
            nbClicks = this.bleViewModel.getmNBAppuis().getValue();
        }
        nbAppuis.setText(nbClicks.toString());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.ble_menu, menu);
        //we link the two menu items
        this.scanMenuBtn = menu.findItem(R.id.menu_ble_search);
        this.disconnectMenuBtn = menu.findItem(R.id.menu_ble_disconnect);
        //we update the gui
        updateGui();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_ble_search) {
            if(isScanning)
                scanLeDevice(false);
            else
                scanLeDevice(true);
            return true;
        }
        else if (id == R.id.menu_ble_disconnect) {
            bleViewModel.disconnect();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(this.isScanning)
            scanLeDevice(false);
        if(isFinishing())
            this.bleViewModel.disconnect();
    }

    /*
     * Method used to update the GUI according to BLE status:
     * - connected: display operation panel (BLE control panel)
     * - not connected: display scan result
     */
    private void updateGui() {
        Boolean isConnected = this.bleViewModel.isConnected().getValue();
        if(isConnected != null && isConnected) {
            this.scanPanel.setVisibility(View.GONE);
            this.operationPanel.setVisibility(View.VISIBLE);

            if(this.scanMenuBtn != null && this.disconnectMenuBtn != null) {
                this.scanMenuBtn.setVisible(false);
                this.disconnectMenuBtn.setVisible(true);
            }
        } else {
            this.operationPanel.setVisibility(View.GONE);
            this.scanPanel.setVisibility(View.VISIBLE);

            if(this.scanMenuBtn != null && this.disconnectMenuBtn != null) {
                this.disconnectMenuBtn.setVisible(false);
                this.scanMenuBtn.setVisible(true);
            }
        }
    }

    //this method need user granted localisation permission, our demo app is requesting it on MainActivity
    private void scanLeDevice(final boolean enable) {
        final BluetoothLeScanner bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (enable) {

            //config
            ScanSettings.Builder builderScanSettings = new ScanSettings.Builder();
            builderScanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            builderScanSettings.setReportDelay(0);

            //we scan for any BLE device
            //we don't filter them based on advertised services...
            //Rappel : Activer Ble et geoloc
            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter scanFilter = new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(UUID.fromString("3c0a1000-281d-4b48-b2a7-f15579a1c38f")))
                    .build();
            filters.add(scanFilter);

            //reset display
            scanResultsAdapter.clear();

            bluetoothScanner.startScan(filters, builderScanSettings.build(), leScanCallback);
            Log.d(TAG,"Start scanning...");
            isScanning = true;

            //we scan only for 15 seconds
            handler.postDelayed(() -> scanLeDevice(false), 15*1000L);

        } else {
            bluetoothScanner.stopScan(leScanCallback);
            isScanning = false;
            Log.d(TAG,"Stop scanning (manual)");
        }
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            runOnUiThread(() -> scanResultsAdapter.addDevice(result));
        }
    };

}
