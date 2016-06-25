package proffart.org.yecup;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for
 * demonstration purposes.
 */
public class RBLGattAttributes {
    private static HashMap<String, String> attributes = new HashMap<String, String>();

    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String BLE_SHIELD_TX = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public static String BLE_SHIELD_RX = "0000ffe1-0000-1000-8000-00805f9b34fb";

    public static String BLE_SHIELD_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    static {
        // RBL Services.
        attributes.put(BLE_SHIELD_SERVICE,
                "BLE Shield Service");
        // RBL Characteristics.
        attributes.put(BLE_SHIELD_TX, "BLE Shield TX");
        attributes.put(BLE_SHIELD_RX, "BLE Shield RX");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}