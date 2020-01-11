package ch.heigvd.iict.sym_labo4.viewmodels;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.BleManagerCallbacks;
import no.nordicsemi.android.ble.data.Data;

public class BleOperationsViewModel extends AndroidViewModel {

    private static final String TAG = BleOperationsViewModel.class.getSimpleName();

    private MySymBleManager ble = null;
    private BluetoothGatt mConnection = null;

    //live data - observer
    private final MutableLiveData<Boolean> mIsConnected = new MutableLiveData<>();
    private final MutableLiveData<Float> mTemperature = new MutableLiveData<>();
    private final MutableLiveData<Integer> mNbAppuis = new MutableLiveData<>();
    private final MutableLiveData<Calendar>  mDate = new MutableLiveData<>();

    public LiveData<Boolean> isConnected() {
        return mIsConnected;
    }

    public LiveData<Float> getmTemperature() { return mTemperature; }

    public LiveData<Integer> getmNBAppuis() { return mNbAppuis; }

    public LiveData<Calendar> getmDate() { return mDate; }

    //references to the Services and Characteristics of the SYM Pixl
    private BluetoothGattService timeService = null, symService = null;
    private BluetoothGattCharacteristic currentTimeChar = null, integerChar = null, temperatureChar = null, buttonClickChar = null;

    public BleOperationsViewModel(Application application) {
        super(application);
        this.mIsConnected.setValue(false); //to be sure that it's never null
        this.ble = new MySymBleManager();
        this.ble.setGattCallbacks(this.bleManagerCallbacks);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "onCleared");
        this.ble.disconnect();
    }

    public void connect(BluetoothDevice device) {
        Log.d(TAG, "User request connection to: " + device);
        if(!mIsConnected.getValue()) {
            this.ble.connect(device)
                    .retry(1, 100)
                    .useAutoConnect(false)
                    .enqueue();
        }
    }

    public void disconnect() {
        Log.d(TAG, "User request disconnection");
        this.ble.disconnect();
        if(mConnection != null) {
            mConnection.disconnect();
        }
    }
    /* TODO
        vous pouvez placer ici les différentes méthodes permettant à l'utilisateur
        d'interagir avec le périphérique depuis l'activité
     */
    public boolean writeDate(Calendar calendar){
        if (currentTimeChar == null) {
            return false;
        }
        byte[] value = new byte[7];
        value[0] = (byte)calendar.get(Calendar.YEAR);
        value[2] = (byte)(calendar.get(Calendar.MONTH) - 1);
        value[3] = (byte)calendar.get(Calendar.DAY_OF_MONTH);
        value[4] = (byte)calendar.get(Calendar.HOUR_OF_DAY);
        value[5] = (byte)calendar.get(Calendar.MINUTE);
        value[6] = (byte)calendar.get(Calendar.SECOND);
        currentTimeChar.setValue(value);
        return mConnection.writeCharacteristic(currentTimeChar);
    }

    public boolean writeInteger(Integer i){
        if (integerChar == null) {
            return false;
        }
        byte[] value = new byte[1];
        value[0] = i.byteValue();
        integerChar.setValue(value);
        return mConnection.writeCharacteristic(integerChar);
    }

    public boolean readTemperature() {
        if(!isConnected().getValue() || temperatureChar == null) return false;
        return ble.readTemperature();
    }

    private BleManagerCallbacks bleManagerCallbacks = new BleManagerCallbacks() {
        @Override
        public void onDeviceConnecting(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceConnecting");
            mIsConnected.setValue(false);
        }

        @Override
        public void onDeviceConnected(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceConnected");
            mIsConnected.setValue(true);
        }

        @Override
        public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceDisconnecting");
            mIsConnected.setValue(false);
        }

        @Override
        public void onDeviceDisconnected(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceDisconnected");
            mIsConnected.setValue(false);
        }

        @Override
        public void onLinkLossOccurred(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onLinkLossOccurred");
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothDevice device, boolean optionalServicesFound) {
            Log.d(TAG, "onServicesDiscovered");
        }

        @Override
        public void onDeviceReady(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceReady");
        }

        @Override
        public void onBondingRequired(@NonNull BluetoothDevice device) {
            Log.w(TAG, "onBondingRequired");
        }

        @Override
        public void onBonded(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onBonded");
        }

        @Override
        public void onBondingFailed(@NonNull BluetoothDevice device) {
            Log.e(TAG, "onBondingFailed");
        }

        @Override
        public void onError(@NonNull BluetoothDevice device, @NonNull String message, int errorCode) {
            Log.e(TAG, "onError:" + errorCode);
        }

        @Override
        public void onDeviceNotSupported(@NonNull BluetoothDevice device) {
            Log.e(TAG, "onDeviceNotSupported");
            Toast.makeText(getApplication(), "Device not supported", Toast.LENGTH_SHORT).show();
        }
    };

    /*
     *  This class is used to implement the protocol to communicate with the BLE device
     */
    private class MySymBleManager extends BleManager<BleManagerCallbacks> {

        private MySymBleManager() {
            super(getApplication());
        }

        @Override
        public BleManagerGattCallback getGattCallback() { return mGattCallback; }

        /**
         * BluetoothGatt callbacks object.
         */
        private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {

            @Override
            public boolean isRequiredServiceSupported(@NonNull final BluetoothGatt gatt) {
                mConnection = gatt; //trick to force disconnection
                Log.d(TAG, "isRequiredServiceSupported - discovered services:");

                UUID timeServiceUUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
                UUID symServiceUUID = UUID.fromString("3c0a1000-281d-4b48-b2a7-f15579a1c38f");
                UUID currentTimeCharUUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");
                UUID integerCharUUID = UUID.fromString("3c0a1001-281d-4b48-b2a7-f15579a1c38f");
                UUID temperatureCharUUID = UUID.fromString("3c0a1002-281d-4b48-b2a7-f15579a1c38f");
                UUID buttonClickCharUUID = UUID.fromString("3c0a1003-281d-4b48-b2a7-f15579a1c38f");
                for(BluetoothGattService service : mConnection.getServices()){
                    if(timeServiceUUID.equals(service.getUuid())){
                        symService = service;
                        for(BluetoothGattCharacteristic characteristique : symService.getCharacteristics()){
                            if(currentTimeCharUUID.equals(characteristique.getUuid()))
                            {
                                currentTimeChar = characteristique;
                            }
                        }
                    }else if(symServiceUUID.equals(service.getUuid())){
                        timeService = service;
                        for(BluetoothGattCharacteristic characteristique : timeService.getCharacteristics()){
                            if(integerCharUUID.equals(characteristique.getUuid()))
                            {
                                integerChar = characteristique;
                            }else if(temperatureCharUUID.equals(characteristique.getUuid()))
                            {
                                temperatureChar = characteristique;
                            }else if(buttonClickCharUUID.equals(characteristique.getUuid()))
                            {
                                buttonClickChar = characteristique;
                            }
                        }
                    }
                }
                return buttonClickChar != null && temperatureChar != null
                        && integerChar != null && currentTimeChar != null;
            }

            @Override
            protected void initialize() {
                /* TODO
                    Ici nous somme sûr que le périphérique possède bien tous les services et caractéristiques
                    attendus et que nous y sommes connectés. Nous pouvous effectuer les premiers échanges BLE:
                    Dans notre cas il s'agit de s'enregistrer pour recevoir les notifications proposées par certaines
                    caractéristiques, on en profitera aussi pour mettre en place les callbacks correspondants.
                 */

                setNotificationCallback(currentTimeChar).with((device, data) -> {

                });
                setNotificationCallback(buttonClickChar).with((device, data) -> {
                    Integer dat = data.getIntValue(Data.FORMAT_UINT8, 0);

                });

                enableNotifications(currentTimeChar).enqueue();
                enableNotifications(buttonClickChar).enqueue();
            }

            @Override
            protected void onDeviceDisconnected() {
                //we reset services and characteristics
                timeService = null;
                currentTimeChar = null;

                symService = null;
                integerChar = null;
                temperatureChar = null;
                buttonClickChar = null;
            }
        };

        public boolean readTemperature() {
            if(temperatureChar == null){
                return false;
            }
            readCharacteristic(temperatureChar).with((device, data) -> {
                data.getIntValue(Data.FORMAT_UINT16, 0);
            }).enqueue();
            mTemperature.setValue(temperatureChar.getFloatValue(Data.FORMAT_FLOAT, 0)/ 10);
            return true;
        }

        public boolean readNbButtonClicked(){
            if(buttonClickChar == null){
                return false;
            }
            readCharacteristic(buttonClickChar).with((device, data) -> {
                data.getIntValue(Data.FORMAT_UINT8, 0);
            }).enqueue();
            mNbAppuis.setValue(buttonClickChar.getIntValue(Data.FORMAT_UINT8, 0));
            return true;
        }

        public boolean readDate() {
            if(currentTimeChar == null){
                return false;
            }
            readCharacteristic(currentTimeChar).with((device, data) -> {
                data.getIntValue(Data.FORMAT_UINT16, 0);
                data.getIntValue(Data.FORMAT_UINT8, 2);
                data.getIntValue(Data.FORMAT_UINT8, 3);
                data.getIntValue(Data.FORMAT_UINT8, 4);
                data.getIntValue(Data.FORMAT_UINT8, 5);
                data.getIntValue(Data.FORMAT_UINT8, 6);
            }).enqueue();
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.YEAR, currentTimeChar.getIntValue(Data.FORMAT_UINT16, 0));
            calendar.set(Calendar.MONTH, currentTimeChar.getIntValue(Data.FORMAT_UINT8, 2) - 1);
            calendar.set(Calendar.DAY_OF_MONTH, currentTimeChar.getIntValue(Data.FORMAT_UINT8, 3));
            calendar.set(Calendar.HOUR_OF_DAY, currentTimeChar.getIntValue(Data.FORMAT_UINT8, 4));
            calendar.set(Calendar.MINUTE, currentTimeChar.getIntValue(Data.FORMAT_UINT8, 5));
            calendar.set(Calendar.SECOND, currentTimeChar.getIntValue(Data.FORMAT_UINT8, 6));
            mDate.setValue(calendar);
            return true;
        }
    }
}
